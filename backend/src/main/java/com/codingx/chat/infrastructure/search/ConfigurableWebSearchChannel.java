package com.codingx.chat.infrastructure.search;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.application.service.SearchChannel;
import com.codingx.chat.application.service.SearchReferenceCandidate;
import com.codingx.chat.application.service.SearchRequestContext;
import com.codingx.chat.application.service.RuntimeSettingService;
import com.codingx.common.error.ErrorMessageCatalog;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 基于可配置外部搜索 provider 执行真实联网检索，并归一化成统一来源候选。
 */
@Component
@RequiredArgsConstructor
public class ConfigurableWebSearchChannel implements SearchChannel {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final RuntimeSettingService runtimeSettingService;

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
            && StrUtil.isNotBlank(runtimeSettingService.webSearchApiKey())
            && StrUtil.isNotBlank(runtimeSettingService.webSearchBaseUrl())
            && StrUtil.isNotBlank(runtimeSettingService.webSearchProvider());
    }

    @Override
    public List<SearchReferenceCandidate> search(SearchRequestContext context) {
        if (!isEnabled(context) || StrUtil.isBlank(context.question())) {
            throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE);
        }
        Request request = buildRequest(context);
        try (Response response = okHttpClient
            .newBuilder()
            .callTimeout(context.timeoutMs(), TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
            .execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE + "：HTTP " + response.code());
            }
            if (response.body() == null) {
                throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE);
            }
            String body = response.body().string();
            JSONObject root = JSONUtil.parseObj(body);
            return switch (normalizedProvider()) {
                case "bing" -> parseBingResults(root);
                case "tavily" -> parseTavilyResults(root);
                case "serper" -> parseSerperResults(root);
                default -> throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_PROVIDER_UNSUPPORTED + "：" + normalizedProvider());
            };
        } catch (IOException exception) {
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
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildRequest(SearchRequestContext context) {
        return switch (normalizedProvider()) {
            case "bing" -> buildBingRequest(context);
            case "tavily" -> buildTavilyRequest(context);
            case "serper" -> buildSerperRequest(context);
            default -> throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_PROVIDER_UNSUPPORTED + "：" + normalizedProvider());
        };
    }

    /**
     * 构造 Bing Web Search 请求。
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildBingRequest(SearchRequestContext context) {
        HttpUrl baseUrl = HttpUrl.parse(runtimeSettingService.webSearchBaseUrl());
        if (baseUrl == null) {
            throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_BASE_URL_INVALID);
        }
        HttpUrl url = baseUrl.newBuilder()
            .addQueryParameter("q", context.question())
            .addQueryParameter("count", String.valueOf(Math.max(1, Math.min(50, runtimeSettingService.webSearchMaxResults()))))
            .addQueryParameter("mkt", buildBingMarket())
            .addQueryParameter("responseFilter", "Webpages")
            .build();
        return new Request.Builder()
            .url(url)
            .header("Ocp-Apim-Subscription-Key", runtimeSettingService.webSearchApiKey())
            .header("Accept", "application/json")
            .get()
            .build();
    }

    /**
     * 构造 Serper 搜索请求。
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildSerperRequest(SearchRequestContext context) {
        JSONObject payload = JSONUtil.createObj()
            .set("q", context.question())
            .set("gl", StrUtil.blankToDefault(runtimeSettingService.webSearchCountry(), "cn"))
            .set("hl", StrUtil.blankToDefault(runtimeSettingService.webSearchLanguage(), "zh-cn"))
            .set("num", Math.max(1, Math.min(10, runtimeSettingService.webSearchMaxResults())));
        return new Request.Builder()
            .url(runtimeSettingService.webSearchBaseUrl())
            .header("X-API-KEY", runtimeSettingService.webSearchApiKey())
            .header("Content-Type", "application/json")
            .post(RequestBody.create(payload.toString(), JSON))
            .build();
    }

    /**
     * 构造 Tavily 搜索请求。
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildTavilyRequest(SearchRequestContext context) {
        JSONObject payload = JSONUtil.createObj()
            .set("query", context.question())
            .set("max_results", Math.max(1, Math.min(10, runtimeSettingService.webSearchMaxResults())))
            .set("topic", "general")
            .set("search_depth", "basic")
            .set("include_answer", false)
            .set("include_images", false);
        return new Request.Builder()
            .url(runtimeSettingService.webSearchBaseUrl())
            .header("Authorization", "Bearer " + runtimeSettingService.webSearchApiKey())
            .header("Content-Type", "application/json")
            .post(RequestBody.create(payload.toString(), JSON))
            .build();
    }

    /**
     * 解析 Serper 返回的 organic 结果。
     * @param root 响应根节点。
     * @return 标准化来源候选。
     */
    private List<SearchReferenceCandidate> parseSerperResults(JSONObject root) {
        JSONArray organic = root.getJSONArray("organic");
        if (organic == null || organic.isEmpty()) {
            return List.of();
        }
        List<SearchReferenceCandidate> candidates = new ArrayList<>();
        int total = organic.size();
        int index = 0;
        for (Object item : organic) {
            index++;
            if (!(item instanceof JSONObject result)) {
                continue;
            }
            String url = result.getStr("link");
            String title = result.getStr("title");
            if (StrUtil.hasBlank(url, title)) {
                continue;
            }
            candidates.add(new SearchReferenceCandidate(
                title,
                url,
                extractSiteName(url),
                StrUtil.blankToDefault(result.getStr("snippet"), ""),
                Math.max(0.01D, (double) (total - index + 1) / total)
            ));
        }
        return candidates;
    }

    /**
     * 解析 Tavily 返回的 results 结果。
     * @param root 响应根节点。
     * @return 标准化来源候选。
     */
    private List<SearchReferenceCandidate> parseTavilyResults(JSONObject root) {
        JSONArray results = root.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            return List.of();
        }
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
        return candidates;
    }

    /**
     * 解析 Bing 返回的 webPages 结果。
     * @param root 响应根节点。
     * @return 标准化来源候选。
     */
    private List<SearchReferenceCandidate> parseBingResults(JSONObject root) {
        JSONObject webPages = root.getJSONObject("webPages");
        if (webPages == null) {
            return List.of();
        }
        JSONArray values = webPages.getJSONArray("value");
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<SearchReferenceCandidate> candidates = new ArrayList<>();
        int total = values.size();
        int index = 0;
        for (Object item : values) {
            index++;
            if (!(item instanceof JSONObject result)) {
                continue;
            }
            String url = result.getStr("url");
            String title = result.getStr("name");
            if (StrUtil.hasBlank(url, title)) {
                continue;
            }
            candidates.add(new SearchReferenceCandidate(
                title,
                url,
                extractSiteName(url),
                StrUtil.blankToDefault(result.getStr("snippet"), ""),
                Math.max(0.01D, (double) (total - index + 1) / total)
            ));
        }
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
     * 统一输出小写 provider 编码，降低配置差异对分支判断的影响。
     * @return provider 编码。
     */
    private String normalizedProvider() {
        return StrUtil.blankToDefault(runtimeSettingService.webSearchProvider(), "").trim().toLowerCase();
    }

    /**
     * Bing 需要 mkt 参数，而现有配置拆成 language/country，这里做最小映射。
     * @return Bing 市场代码。
     */
    private String buildBingMarket() {
        String language = StrUtil.blankToDefault(runtimeSettingService.webSearchLanguage(), "zh-cn");
        String country = StrUtil.blankToDefault(runtimeSettingService.webSearchCountry(), "cn");
        return language + "-" + country.toUpperCase();
    }

    private boolean isTimeout(IOException exception) {
        return exception instanceof SocketTimeoutException
            || exception instanceof InterruptedIOException
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(exception.getMessage()), "timeout");
    }
}
