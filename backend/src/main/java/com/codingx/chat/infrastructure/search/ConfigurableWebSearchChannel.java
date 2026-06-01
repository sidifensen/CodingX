package com.codingx.chat.infrastructure.search;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.application.service.RuntimeSettingService;
import com.codingx.chat.application.service.SearchChannel;
import com.codingx.chat.application.service.SearchReferenceCandidate;
import com.codingx.chat.application.service.SearchRequestContext;
import com.codingx.common.error.ErrorMessageCatalog;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StringReader;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

/**
 * 基于系统配置表按顺序执行联网搜索 provider，并归一化成统一来源候选。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigurableWebSearchChannel implements SearchChannel {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** HTTP 客户端，用于调用 Tavily、SerpAPI、Exa 和 HTML 搜索源。 */
    private final OkHttpClient okHttpClient;
    /** 运行时配置服务，用于读取 provider 顺序、密钥、超时和结果数量。 */
    private final RuntimeSettingService runtimeSettingService;
    /** provider 健康注册表，用于跳过熔断中的搜索源并记录成功/失败。 */
    private final SearchProviderHealthRegistry searchProviderHealthRegistry;

    @Override
    public String getName() {
        return "configurable-web";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public boolean isEnabled(SearchRequestContext context) {
        return runtimeSettingService.webSearchEnabled()
            && orderedProviders().stream().anyMatch(provider -> providerConfig(provider) != null);
    }

    @Override
    public List<SearchReferenceCandidate> search(SearchRequestContext context) {
        // 步骤 1：先检查联网搜索总开关和问题内容，未启用时直接让上层走不可用兜底。
        if (!runtimeSettingService.webSearchEnabled() || StrUtil.isBlank(context.question())) {
            throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE);
        }
        List<String> attemptedProviders = new ArrayList<>();
        RuntimeException lastFailure = null;
        // 步骤 2：按动态配置的 provider 顺序逐个尝试，配置缺失或熔断中的 provider 直接跳过。
        for (String provider : orderedProviders()) {
            ProviderConfig config = providerConfig(provider);
            if (config == null) {
                log.info("联网搜索跳过提供方: provider={}, reason=配置缺失", provider);
                continue;
            }
            if (!searchProviderHealthRegistry.allowCall(provider)) {
                log.info("联网搜索跳过提供方: provider={}, reason=熔断中", provider);
                continue;
            }
            attemptedProviders.add(provider);
            try {
                // 步骤 3：调用当前 provider；空结果视为失败并进入下一个候选 provider。
                List<SearchReferenceCandidate> candidates = executeProvider(config, context);
                if (candidates.isEmpty()) {
                    searchProviderHealthRegistry.markFailure(provider);
                    log.warn("联网搜索提供方无有效结果: provider={}, question={}", provider, StrUtil.maxLength(context.question(), 120));
                    continue;
                }
                searchProviderHealthRegistry.markSuccess(provider);
                log.info(
                    "联网搜索使用提供方: provider={}, results={}, attempted={}, question={}",
                    provider,
                    candidates.size(),
                    attemptedProviders,
                    StrUtil.maxLength(context.question(), 120)
                );
                return candidates;
            } catch (RuntimeException exception) {
                // 步骤 4：单 provider 异常不立刻中断搜索，记录健康状态后继续尝试后续 provider。
                searchProviderHealthRegistry.markFailure(provider);
                lastFailure = exception;
                log.warn(
                    "联网搜索提供方失败: provider={}, attempted={}, failure={}",
                    provider,
                    attemptedProviders,
                    exception.getClass().getSimpleName()
                );
            }
        }
        // 步骤 5：所有 provider 均不可用时统一抛出中文不可用错误，并保留最后一次失败作为 cause。
        IllegalStateException exception = new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE);
        if (lastFailure != null) {
            exception.initCause(lastFailure);
        }
        throw exception;
    }

    /**
     * 读取去重后的 provider 顺序；配置服务负责在缺省时提供内置默认链路。
     * @return provider 编码列表。
     */
    private List<String> orderedProviders() {
        List<String> configuredOrder = runtimeSettingService.webSearchProviderOrder();
        List<String> source = configuredOrder == null ? List.of() : configuredOrder;
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String provider : source) {
            String normalized = normalizeProvider(provider);
            if (StrUtil.isNotBlank(normalized) && isSupportedProvider(normalized)) {
                deduplicated.add(normalized);
            }
        }
        return deduplicated.stream().toList();
    }

    /**
     * 读取 provider 级配置，HTML provider 不要求 API Key。
     * @param provider provider 编码。
     * @return provider 配置；配置不可用时返回 null。
     */
    private ProviderConfig providerConfig(String provider) {
        String baseUrl = runtimeSettingService.webSearchProviderBaseUrl(provider);
        String apiKey = runtimeSettingService.webSearchProviderApiKey(provider);
        if (StrUtil.isBlank(baseUrl)) {
            return null;
        }
        if (requiresApiKey(provider) && StrUtil.isBlank(apiKey)) {
            return null;
        }
        return new ProviderConfig(provider, baseUrl, StrUtil.blankToDefault(apiKey, ""));
    }

    /**
     * 执行单个 provider 请求并按协议解析结果。
     * @param config provider 配置。
     * @param context 搜索上下文。
     * @return 标准化候选结果。
     */
    private List<SearchReferenceCandidate> executeProvider(ProviderConfig config, SearchRequestContext context) {
        // 步骤 1：按 provider 类型构造 HTTP 请求，并把本轮搜索超时下沉到 OkHttp 调用级别。
        Request request = buildRequest(config, context);
        try (Response response = okHttpClient
            .newBuilder()
            .callTimeout(context.timeoutMs(), TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
            .execute()) {
            // 步骤 2：先校验 HTTP 状态和响应体，失败时统一包装为搜索不可用错误。
            if (!response.isSuccessful()) {
                throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE + "：HTTP " + response.code());
            }
            if (response.body() == null) {
                throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE);
            }
            // 步骤 3：读取响应正文并交给 provider 协议解析器转换成标准候选。
            String body = response.body().string();
            return parseResponse(config.provider(), body);
        } catch (IOException exception) {
            // 步骤 4：网络异常按超时和普通不可用区分，便于上层日志和错误文案归类。
            if (isTimeout(exception)) {
                throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_TIMEOUT, exception);
            }
            throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE, exception);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE, exception);
        }
    }

    /**
     * 按 provider 协议构造 HTTP 请求。
     * @param config provider 配置。
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildRequest(ProviderConfig config, SearchRequestContext context) {
        return switch (config.provider()) {
            case "bing" -> buildBingRequest(config, context);
            case "tavily" -> buildTavilyRequest(config, context);
            case "serper" -> buildSerperRequest(config, context);
            case "serpapi" -> buildSerpApiRequest(config, context);
            case "exa" -> buildExaRequest(config, context);
            case "bing_html", "duckduckgo_html" -> buildHtmlSearchRequest(config, context);
            default -> throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_PROVIDER_UNSUPPORTED + "：" + config.provider());
        };
    }

    /**
     * 按 provider 协议解析响应。
     * @param provider provider 编码。
     * @param body 响应体。
     * @return 标准化候选结果。
     */
    private List<SearchReferenceCandidate> parseResponse(String provider, String body) {
        if ("bing_html".equals(provider)) {
            return parseBingHtmlResults(body);
        }
        if ("duckduckgo_html".equals(provider)) {
            return parseDuckDuckGoHtmlResults(body);
        }
        JSONObject root = JSONUtil.parseObj(body);
        return switch (provider) {
            case "bing" -> parseBingResults(root);
            case "tavily" -> parseTavilyResults(root);
            case "serper" -> parseSerperResults(root);
            case "serpapi" -> parseSerpApiResults(root);
            case "exa" -> parseExaResults(root);
            default -> throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_PROVIDER_UNSUPPORTED + "：" + provider);
        };
    }

    /**
     * 构造 Bing Web Search API 请求。
     * @param config provider 配置。
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildBingRequest(ProviderConfig config, SearchRequestContext context) {
        HttpUrl baseUrl = parseBaseUrl(config.baseUrl());
        HttpUrl url = baseUrl.newBuilder()
            .addQueryParameter("q", context.question())
            .addQueryParameter("count", String.valueOf(Math.max(1, Math.min(50, runtimeSettingService.webSearchMaxResults()))))
            .addQueryParameter("mkt", buildBingMarket())
            .addQueryParameter("responseFilter", "Webpages")
            .build();
        return new Request.Builder()
            .url(url)
            .header("Ocp-Apim-Subscription-Key", config.apiKey())
            .header("Accept", "application/json")
            .get()
            .build();
    }

    /**
     * 构造 Serper 搜索请求。
     * @param config provider 配置。
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildSerperRequest(ProviderConfig config, SearchRequestContext context) {
        JSONObject payload = JSONUtil.createObj()
            .set("q", context.question())
            .set("gl", StrUtil.blankToDefault(runtimeSettingService.webSearchCountry(), "cn"))
            .set("hl", StrUtil.blankToDefault(runtimeSettingService.webSearchLanguage(), "zh-cn"))
            .set("num", Math.max(1, Math.min(10, runtimeSettingService.webSearchMaxResults())));
        return new Request.Builder()
            .url(config.baseUrl())
            .header("X-API-KEY", config.apiKey())
            .header("Content-Type", "application/json")
            .post(RequestBody.create(payload.toString(), JSON))
            .build();
    }

    /**
     * 构造 Tavily 搜索请求。
     * @param config provider 配置。
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildTavilyRequest(ProviderConfig config, SearchRequestContext context) {
        JSONObject payload = JSONUtil.createObj()
            .set("query", context.question())
            .set("max_results", Math.max(1, Math.min(10, runtimeSettingService.webSearchMaxResults())))
            .set("topic", "general")
            .set("search_depth", "basic")
            .set("include_answer", false)
            .set("include_images", false);
        return new Request.Builder()
            .url(config.baseUrl())
            .header("Authorization", "Bearer " + config.apiKey())
            .header("Content-Type", "application/json")
            .post(RequestBody.create(payload.toString(), JSON))
            .build();
    }

    /**
     * 构造 SerpApi 搜索请求。
     * @param config provider 配置。
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildSerpApiRequest(ProviderConfig config, SearchRequestContext context) {
        HttpUrl url = parseBaseUrl(config.baseUrl()).newBuilder()
            .addQueryParameter("engine", "google")
            .addQueryParameter("q", context.question())
            .addQueryParameter("api_key", config.apiKey())
            .addQueryParameter("hl", StrUtil.blankToDefault(runtimeSettingService.webSearchLanguage(), "zh-cn"))
            .addQueryParameter("gl", StrUtil.blankToDefault(runtimeSettingService.webSearchCountry(), "cn"))
            .addQueryParameter("num", String.valueOf(Math.max(1, Math.min(10, runtimeSettingService.webSearchMaxResults()))))
            .build();
        return new Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build();
    }

    /**
     * 构造 Exa 搜索请求。
     * @param config provider 配置。
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildExaRequest(ProviderConfig config, SearchRequestContext context) {
        JSONObject payload = JSONUtil.createObj()
            .set("query", context.question())
            .set("numResults", Math.max(1, Math.min(10, runtimeSettingService.webSearchMaxResults())))
            .set("type", "auto");
        return new Request.Builder()
            .url(config.baseUrl())
            .header("x-api-key", config.apiKey())
            .header("Content-Type", "application/json")
            .post(RequestBody.create(payload.toString(), JSON))
            .build();
    }

    /**
     * 构造 HTML 搜索请求。
     * @param config provider 配置。
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildHtmlSearchRequest(ProviderConfig config, SearchRequestContext context) {
        HttpUrl url = parseBaseUrl(config.baseUrl()).newBuilder()
            .addQueryParameter("q", context.question())
            .build();
        return new Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("User-Agent", "Mozilla/5.0 CodingXSearch/1.0")
            .get()
            .build();
    }

    /**
     * 解析 Serper 返回的 organic 结果。
     * @param root 响应根节点。
     * @return 标准化来源候选。
     */
    private List<SearchReferenceCandidate> parseSerperResults(JSONObject root) {
        return parseRankedJsonResults(root.getJSONArray("organic"), "link", "title", "snippet");
    }

    /**
     * 解析 SerpApi 返回的 organic_results 结果。
     * @param root 响应根节点。
     * @return 标准化来源候选。
     */
    private List<SearchReferenceCandidate> parseSerpApiResults(JSONObject root) {
        return parseRankedJsonResults(root.getJSONArray("organic_results"), "link", "title", "snippet");
    }

    /**
     * 解析 Tavily 返回的 results 结果。
     * @param root 响应根节点。
     * @return 标准化来源候选。
     */
    private List<SearchReferenceCandidate> parseTavilyResults(JSONObject root) {
        // 步骤 1：读取 Tavily results 数组，缺失或为空时直接返回空候选。
        JSONArray results = root.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        // 步骤 2：逐条提取 URL、标题、正文摘要和 provider 分数，跳过缺少核心字段的结果。
        List<SearchReferenceCandidate> candidates = new ArrayList<>();
        for (Object item : results) {
            if (!(item instanceof JSONObject result)) {
                continue;
            }
            String url = result.getStr("url");
            String title = result.getStr("title");
            if (StrUtil.hasBlank(url, title)) {
                continue;
            }
            candidates.add(new SearchReferenceCandidate(
                title,
                url,
                extractSiteName(url),
                StrUtil.blankToDefault(result.getStr("content"), ""),
                result.getDouble("score", 0D)
            ));
        }
        // 步骤 3：返回已标准化候选，后续去重、截断由后处理链负责。
        return candidates;
    }

    /**
     * 解析 Exa 返回的 results 结果。
     * @param root 响应根节点。
     * @return 标准化来源候选。
     */
    private List<SearchReferenceCandidate> parseExaResults(JSONObject root) {
        // 步骤 1：读取 Exa results 数组，缺失或为空时直接返回空候选。
        JSONArray results = root.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        // 步骤 2：逐条提取 URL、标题、正文或摘要和 provider 分数，跳过缺少核心字段的结果。
        List<SearchReferenceCandidate> candidates = new ArrayList<>();
        for (Object item : results) {
            if (!(item instanceof JSONObject result)) {
                continue;
            }
            String url = result.getStr("url");
            String title = result.getStr("title");
            if (StrUtil.hasBlank(url, title)) {
                continue;
            }
            candidates.add(new SearchReferenceCandidate(
                title,
                url,
                extractSiteName(url),
                StrUtil.blankToDefault(result.getStr("text"), StrUtil.blankToDefault(result.getStr("summary"), "")),
                result.getDouble("score", 0D)
            ));
        }
        // 步骤 3：返回已标准化候选，保持 Exa 原始排序给后续链路使用。
        return candidates;
    }

    /**
     * 解析 Bing 返回的 webPages 结果。
     * @param root 响应根节点。
     * @return 标准化来源候选。
     */
    private List<SearchReferenceCandidate> parseBingResults(JSONObject root) {
        JSONObject webPages = root.getJSONObject("webPages");
        return webPages == null ? List.of() : parseRankedJsonResults(webPages.getJSONArray("value"), "url", "name", "snippet");
    }

    /**
     * 解析 Bing HTML 结果页。
     * @param html HTML 内容。
     * @return 标准化来源候选。
     */
    private List<SearchReferenceCandidate> parseBingHtmlResults(String html) {
        return new SearchHtmlParser(HtmlProvider.BING).parse(html);
    }

    /**
     * 解析 DuckDuckGo HTML 结果页。
     * @param html HTML 内容。
     * @return 标准化来源候选。
     */
    private List<SearchReferenceCandidate> parseDuckDuckGoHtmlResults(String html) {
        return new SearchHtmlParser(HtmlProvider.DUCKDUCKGO).parse(html);
    }

    /**
     * 解析按排名递减计分的 JSON 结果数组。
     * @param results 结果数组。
     * @param urlField URL 字段名。
     * @param titleField 标题字段名。
     * @param snippetField 摘要字段名。
     * @return 标准化来源候选。
     */
    private List<SearchReferenceCandidate> parseRankedJsonResults(JSONArray results, String urlField, String titleField, String snippetField) {
        // 步骤 1：统一处理 provider JSON 结果数组为空的情况，避免调用方重复判空。
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        // 步骤 2：按原始排名递减生成兜底分数，并跳过非对象或缺少标题/URL 的条目。
        List<SearchReferenceCandidate> candidates = new ArrayList<>();
        int total = results.size();
        int index = 0;
        for (Object item : results) {
            index++;
            if (!(item instanceof JSONObject result)) {
                continue;
            }
            String url = result.getStr(urlField);
            String title = result.getStr(titleField);
            if (StrUtil.hasBlank(url, title)) {
                continue;
            }
            candidates.add(new SearchReferenceCandidate(
                title,
                url,
                extractSiteName(url),
                StrUtil.blankToDefault(result.getStr(snippetField), ""),
                Math.max(0.01D, (double) (total - index + 1) / total)
            ));
        }
        // 步骤 3：返回统一字段命名后的候选，具体 provider 字段差异在本方法参数中收敛。
        return candidates;
    }

    /**
     * 统一提取 URL host 作为站点名，避免 provider 字段不一致。
     * @param url 来源地址。
     * @return 站点 host，解析失败时回退空字符串。
     */
    private String extractSiteName(String url) {
        try {
            URI uri = URI.create(url);
            return StrUtil.blankToDefault(uri.getHost(), "");
        } catch (RuntimeException exception) {
            HttpUrl parsed = HttpUrl.parse(url);
            return parsed == null ? "" : StrUtil.blankToDefault(parsed.host(), "");
        }
    }

    /**
     * 解析基础 URL，失败时统一抛出中文配置错误。
     * @param baseUrl 基础地址。
     * @return URL 对象。
     */
    private HttpUrl parseBaseUrl(String baseUrl) {
        HttpUrl parsed = HttpUrl.parse(baseUrl);
        if (parsed == null) {
            throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_BASE_URL_INVALID);
        }
        return parsed;
    }

    /**
     * 判断 provider 是否需要 API Key。
     * @param provider provider 编码。
     * @return 是否需要 API Key。
     */
    private boolean requiresApiKey(String provider) {
        return switch (provider) {
            case "bing_html", "duckduckgo_html" -> false;
            default -> true;
        };
    }

    /**
     * 判断 provider 编码是否受支持。
     * @param provider provider 编码。
     * @return 是否支持。
     */
    private boolean isSupportedProvider(String provider) {
        return switch (provider) {
            case "bing", "tavily", "serper", "serpapi", "exa", "bing_html", "duckduckgo_html" -> true;
            default -> false;
        };
    }

    /**
     * 统一输出小写 provider 编码，降低配置差异对分支判断的影响。
     * @param provider provider 编码。
     * @return provider 编码。
     */
    private String normalizeProvider(String provider) {
        return StrUtil.blankToDefault(provider, "").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Bing 需要 mkt 参数，而现有配置拆成 language/country，这里做最小映射。
     * @return Bing 市场代码。
     */
    private String buildBingMarket() {
        String language = StrUtil.blankToDefault(runtimeSettingService.webSearchLanguage(), "zh-cn");
        String country = StrUtil.blankToDefault(runtimeSettingService.webSearchCountry(), "cn");
        return language + "-" + country.toUpperCase(Locale.ROOT);
    }

    private boolean isTimeout(IOException exception) {
        return exception instanceof SocketTimeoutException
            || exception instanceof InterruptedIOException
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(exception.getMessage()), "timeout");
    }

    /**
     * provider 配置快照，避免请求构造阶段重复读取系统配置造成同一次搜索内不一致。
     */
    private record ProviderConfig(String provider, String baseUrl, String apiKey) {
    }

    /**
     * HTML 搜索结果页类型。
     */
    private enum HtmlProvider {
        BING,
        DUCKDUCKGO
    }

    /**
     * 使用 JDK HTML parser 按结构提取搜索结果，避免正则依赖整页字符串布局。
     */
    private final class SearchHtmlParser extends HTMLEditorKit.ParserCallback {

        /** HTML provider 类型，用于按 Bing 或 DuckDuckGo 的 DOM 结构选择解析规则。 */
        private final HtmlProvider provider;
        /** 已解析出的搜索候选结果列表，按页面出现顺序保留原始相关性。 */
        private final List<SearchReferenceCandidate> candidates = new ArrayList<>();
        /** 当前正在解析的 HTML 条目，遇到结果容器结束标签时收敛为候选结果。 */
        private HtmlResult current;
        /** 标题文本捕获开关，仅在进入结果标题链接或标题节点时开启。 */
        private boolean captureTitle;
        /** 摘要文本捕获开关，仅在进入结果摘要节点时开启。 */
        private boolean captureSnippet;

        private SearchHtmlParser(HtmlProvider provider) {
            this.provider = provider;
        }

        private List<SearchReferenceCandidate> parse(String html) {
            try {
                new ParserDelegator().parse(new StringReader(html), this, true);
            } catch (IOException exception) {
                throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE, exception);
            }
            finishCurrent();
            return candidates;
        }

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            handleTag(tag, attributes);
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            handleTag(tag, attributes);
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            if (provider == HtmlProvider.BING && tag == HTML.Tag.LI) {
                finishCurrent();
            }
            if (provider == HtmlProvider.DUCKDUCKGO && tag == HTML.Tag.DIV) {
                finishCurrent();
            }
            if (tag == HTML.Tag.A || tag == HTML.Tag.H2) {
                captureTitle = false;
            }
            if (tag == HTML.Tag.P || tag == HTML.Tag.A || tag == HTML.Tag.SPAN) {
                captureSnippet = false;
            }
        }

        @Override
        public void handleText(char[] data, int position) {
            if (current == null) {
                return;
            }
            String text = HtmlUtil.unescape(new String(data)).trim();
            if (StrUtil.isBlank(text)) {
                return;
            }
            if (captureTitle) {
                current.title = joinText(current.title, text);
            }
            if (captureSnippet) {
                current.snippet = joinText(current.snippet, text);
            }
        }

        /**
         * 处理 HTML 开始标签并根据 provider 结构更新捕获状态。
         * @param tag 标签。
         * @param attributes 标签属性。
         */
        private void handleTag(HTML.Tag tag, MutableAttributeSet attributes) {
            // 步骤 1：先识别不同 provider 的结果容器边界，进入新条目前收敛上一条结果。
            String cssClass = attribute(attributes, HTML.Attribute.CLASS);
            if (provider == HtmlProvider.BING && tag == HTML.Tag.LI && containsClass(cssClass, "b_algo")) {
                finishCurrent();
                current = new HtmlResult();
                return;
            }
            if (provider == HtmlProvider.DUCKDUCKGO && tag == HTML.Tag.DIV && containsClass(cssClass, "result")) {
                finishCurrent();
                current = new HtmlResult();
                return;
            }
            if (current == null) {
                return;
            }
            // 步骤 2：在结果条目内捕获第一个链接作为 URL，并按 provider 规则决定标题和摘要捕获状态。
            if (tag == HTML.Tag.A) {
                String href = attribute(attributes, HTML.Attribute.HREF);
                if (StrUtil.isNotBlank(href) && StrUtil.isBlank(current.url)) {
                    current.url = href;
                }
                captureTitle = provider == HtmlProvider.DUCKDUCKGO
                    ? containsClass(cssClass, "result__a")
                    : StrUtil.isNotBlank(href);
                captureSnippet = provider == HtmlProvider.DUCKDUCKGO && containsClass(cssClass, "result__snippet");
            }
            // 步骤 3：Bing 摘要通常位于段落标签中，单独打开摘要捕获开关。
            if (provider == HtmlProvider.BING && tag == HTML.Tag.P) {
                captureSnippet = true;
            }
        }

        /**
         * 收敛当前 HTML 结果条目。
         */
        private void finishCurrent() {
            if (current == null) {
                return;
            }
            if (StrUtil.isAllNotBlank(current.title, current.url)) {
                candidates.add(new SearchReferenceCandidate(
                    current.title,
                    current.url,
                    extractSiteName(current.url),
                    StrUtil.blankToDefault(current.snippet, ""),
                    Math.max(0.01D, 1.0D / (candidates.size() + 1))
                ));
            }
            current = null;
            captureTitle = false;
            captureSnippet = false;
        }

        private String attribute(MutableAttributeSet attributes, HTML.Attribute attribute) {
            Object value = attributes.getAttribute(attribute);
            return value == null ? "" : value.toString();
        }

        private boolean containsClass(String cssClass, String expected) {
            return StrUtil.splitTrim(StrUtil.blankToDefault(cssClass, ""), " ")
                .stream()
                .anyMatch(item -> item.equals(expected));
        }

        private String joinText(String currentText, String nextText) {
            return StrUtil.isBlank(currentText) ? nextText : currentText + " " + nextText;
        }
    }

    /**
     * HTML 解析过程中的临时结果。
     */
    private static final class HtmlResult {
        /** 结果标题，来自搜索结果条目中的标题链接文本。 */
        private String title;
        /** 结果地址，来自搜索结果条目中的首个有效链接。 */
        private String url;
        /** 结果摘要，来自搜索结果条目的描述文本，可为空字符串。 */
        private String snippet;
    }
}
