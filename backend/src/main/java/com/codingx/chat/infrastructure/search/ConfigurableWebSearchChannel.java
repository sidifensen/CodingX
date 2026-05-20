package com.codingx.chat.infrastructure.search;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.application.service.SearchChannel;
import com.codingx.chat.application.service.SearchReferenceCandidate;
import com.codingx.chat.application.service.SearchRequestContext;
import com.codingx.chat.application.service.RuntimeSettingService;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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
            return List.of();
        }
        try (Response response = okHttpClient.newCall(buildRequest(context)).execute()) {
            if (!response.isSuccessful()) {
                return List.of();
            }
            if (response.body() == null) {
                return List.of();
            }
            String body = response.body().string();
            JSONObject root = JSONUtil.parseObj(body);
            return switch (normalizedProvider()) {
                case "tavily" -> parseTavilyResults(root);
                case "serper" -> parseSerperResults(root);
                default -> List.of();
            };
        } catch (IOException | RuntimeException exception) {
            // 搜索链路允许单通道静默失败，由上层统一做降级与空结果兜底。
            return List.of();
        }
    }

    /**
     * 按 provider 协议构造 HTTP 请求。
     * @param context 搜索上下文。
     * @return HTTP 请求。
     */
    private Request buildRequest(SearchRequestContext context) {
        return switch (normalizedProvider()) {
            case "tavily" -> buildTavilyRequest(context);
            case "serper" -> buildSerperRequest(context);
            default -> throw new IllegalStateException("Unsupported web search provider: " + normalizedProvider());
        };
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
}
