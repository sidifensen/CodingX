package com.codingx.cli.session;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.codingx.cli.config.CliConfig;
import com.codingx.cli.config.CliConfigStore;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 后端会话列表客户端，复用 Web 的 `/api/chat/conversations` 接口供 CLI 展示和恢复会话。
 */
public class BackendConversationClient implements CliConversationService {

    /**
     * 用户级配置存储，提供 serverUrl 与 satoken，避免命令层直接读取 YAML 字段。
     */
    private final CliConfigStore configStore;

    /**
     * JDK HTTP 客户端，保持 CLI 无额外网络依赖。
     */
    private final HttpClient httpClient;

    /**
     * @param configStore 用户级配置存储。
     */
    public BackendConversationClient(CliConfigStore configStore) {
        this(
            configStore,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
        );
    }

    /**
     * 测试可注入构造器。
     */
    BackendConversationClient(CliConfigStore configStore, HttpClient httpClient) {
        this.configStore = configStore;
        this.httpClient = httpClient;
    }

    @Override
    public List<CliConversation> listRecentConversations(int pageSize) {
        CliConfig config = configStore.load();
        if (StrUtil.isBlank(config.token())) {
            throw new IllegalStateException("未登录或登录已失效，请先运行 codingx auth login。");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(buildUri(config.serverUrl(), pageSize))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .header("satoken", config.token().trim())
                .header("Accept", "application/json")
                .build();
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(resolveErrorMessage(response.body()));
            }
            return parseConversations(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("会话列表请求已中断。", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("会话列表请求失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 构造 Web 会话列表 URI，始终使用 cursor 分页协议读取最近一页。
     */
    private URI buildUri(String serverUrl, int pageSize) {
        int normalizedPageSize = Math.max(1, Math.min(pageSize, 50));
        String normalizedServerUrl = StrUtil.removeSuffix(StrUtil.trimToEmpty(serverUrl), "/");
        String query = "pageSize=" + URLEncoder.encode(String.valueOf(normalizedPageSize), StandardCharsets.UTF_8);
        return URI.create(normalizedServerUrl + "/api/chat/conversations?" + query);
    }

    /**
     * 解析统一 ApiResponse；兼容旧数组响应和 cursor page 响应。
     */
    private List<CliConversation> parseConversations(String responseText) {
        JSONObject envelope = JSONUtil.parseObj(responseText);
        if (!envelope.getBool("success", false)) {
            throw new IllegalStateException(StrUtil.blankToDefault(envelope.getStr("message"), "会话列表请求失败"));
        }
        Object data = envelope.get("data");
        JSONArray items = resolveItems(data);
        return items.stream()
            .map(JSONUtil::parseObj)
            .map(this::toConversation)
            .filter(conversation -> StrUtil.isNotBlank(conversation.id()))
            .toList();
    }

    /**
     * Web 旧接口返回数组，新接口返回 `{ items, hasMore, nextCursor }`，CLI 两者都支持。
     */
    private JSONArray resolveItems(Object data) {
        if (data instanceof JSONArray array) {
            return array;
        }
        if (data instanceof JSONObject object) {
            JSONArray items = object.getJSONArray("items");
            return items == null ? JSONUtil.createArray() : items;
        }
        return JSONUtil.createArray();
    }

    /**
     * 将后端 JSON 规整为终端展示快照，避免 Long 主键和 null 字段泄漏到命令层。
     */
    private CliConversation toConversation(JSONObject item) {
        String updatedAt = firstNonBlank(item.getStr("updatedAt"), item.getStr("lastMessageAt"));
        String workspaceType = firstNonBlank(item.getStr("workspaceType"), item.getStr("runtimeTarget"));
        return new CliConversation(
            StrUtil.toStringOrNull(item.get("id")),
            StrUtil.blankToDefault(item.getStr("title"), "未命名会话"),
            StrUtil.trimToEmpty(item.getStr("status")),
            StrUtil.trimToEmpty(updatedAt),
            StrUtil.toStringOrNull(item.get("workspaceId")),
            StrUtil.trimToEmpty(workspaceType)
        );
    }

    /**
     * HTTP 错误和业务失败都优先采用后端中文 message。
     */
    private String resolveErrorMessage(String responseText) {
        if (StrUtil.isBlank(responseText)) {
            return "会话列表请求失败";
        }
        try {
            String message = JSONUtil.parseObj(responseText).getStr("message");
            return StrUtil.blankToDefault(message, "会话列表请求失败");
        } catch (RuntimeException exception) {
            return responseText;
        }
    }

    /**
     * 返回第一个非空文本。
     */
    private String firstNonBlank(String first, String second) {
        return StrUtil.isNotBlank(first) ? first : second;
    }
}
