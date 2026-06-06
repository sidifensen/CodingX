package com.codingx.mcp.application.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.mcp.application.executor.ChatMcpToolResult;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.tool.application.service.ChatToolSpec;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;

/**
 * 外部 MCP Server 运行时服务。
 * <p>
 * 业务意图：把管理端保存的外部 MCP 配置转成模型可见工具，并在模型请求
 * {@code mcp__{mcpCode}__{toolName}} 时通过 JSON-RPC 代理到远程 MCP Server。
 */
@Service
@RequiredArgsConstructor
public class McpServerRuntimeService {

    /** JSON 请求体媒体类型。 */
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** MCP 配置仓储，用于读取连接信息并回写发现结果和健康状态。 */
    private final ChatMcpRepository chatMcpRepository;

    /** HTTP 客户端，用于调用 http/sse 形态的远程 JSON-RPC MCP Server。 */
    private final OkHttpClient okHttpClient;

    /**
     * 发现外部 MCP Server 暴露的工具列表，并持久化工具 schema 快照。
     *
     * @param mcpCode MCP 服务编码。
     * @return 模型可见工具规格。
     */
    public List<ChatToolSpec> discoverTools(String mcpCode) {
        ChatMcp chatMcp = requireExternalMcp(mcpCode);
        try {
            JSONObject result = callJsonRpc(chatMcp, "tools/list", JSONUtil.createObj());
            JSONArray tools = result == null ? null : result.getJSONArray("tools");
            List<ChatToolSpec> specs = toToolSpecs(chatMcp.getMcpCode(), tools);
            chatMcpRepository.save(chatMcp.toBuilder()
                .toolSchemaJson(JSONUtil.toJsonStr(tools == null ? new JSONArray() : tools))
                .healthStatus("AVAILABLE")
                .lastConnectedAt(LocalDateTime.now())
                .lastErrorMessage(null)
                .build());
            return specs;
        } catch (RuntimeException exception) {
            markRuntimeError(chatMcp, exception.getMessage());
            throw exception;
        }
    }

    /**
     * 从已发现成功的外部 MCP 配置中恢复模型可见工具清单。
     * <p>
     * 业务约束：该方法只读取持久化 schema 快照，不主动联网发现，避免每轮聊天都阻塞在外部 MCP 探测上。
     *
     * @return 可直接合并到模型 function tools 的外部 MCP 工具规格。
     */
    public List<ChatToolSpec> listDiscoveredToolSpecs() {
        List<ChatToolSpec> specs = new ArrayList<>();
        for (ChatMcp chatMcp : chatMcpRepository.findAllEnabled()) {
            if (!isDiscoveredExternalMcp(chatMcp)) {
                continue;
            }
            try {
                specs.addAll(toToolSpecs(chatMcp.getMcpCode(), JSONUtil.parseArray(chatMcp.getToolSchemaJson())));
            } catch (RuntimeException ignored) {
                // 单条 schema 快照损坏不能影响其他 MCP；下次 discoverTools 成功后会覆盖旧快照。
            }
        }
        return specs;
    }

    /**
     * 执行模型请求的外部 MCP 命名空间工具。
     *
     * @param namespacedToolName 模型可见工具名，格式为 mcp__{mcpCode}__{toolName}。
     * @param argumentsJson 模型生成的工具参数 JSON。
     * @return MCP 工具执行结果。
     */
    public ChatMcpToolResult execute(String namespacedToolName, String argumentsJson) {
        McpToolNameSupport.NamespacedToolName parsedToolName = McpToolNameSupport.parseToolName(namespacedToolName);
        ChatMcp chatMcp = requireExternalMcp(parsedToolName.mcpCode());
        try {
            JSONObject params = JSONUtil.createObj()
                .set("name", parsedToolName.toolName())
                .set("arguments", parseArguments(argumentsJson));
            JSONObject result = callJsonRpc(chatMcp, "tools/call", params);
            ChatMcpToolResult toolResult = toToolResult(namespacedToolName, parsedToolName, result);
            chatMcpRepository.save(chatMcp.toBuilder()
                .healthStatus("AVAILABLE")
                .lastConnectedAt(LocalDateTime.now())
                .lastErrorMessage(null)
                .build());
            return toolResult;
        } catch (RuntimeException exception) {
            markRuntimeError(chatMcp, exception.getMessage());
            throw exception;
        }
    }

    private ChatMcp requireExternalMcp(String mcpCode) {
        ChatMcp chatMcp = chatMcpRepository.findByMcpCode(mcpCode);
        if (chatMcp == null || chatMcp.getDeleted() != null && chatMcp.getDeleted() == 1) {
            throw new IllegalArgumentException("MCP 配置不存在：" + StrUtil.blankToDefault(mcpCode, ""));
        }
        if (!"external".equalsIgnoreCase(StrUtil.blankToDefault(chatMcp.getSourceType(), ""))) {
            throw new IllegalArgumentException("MCP 不是外部服务：" + StrUtil.blankToDefault(mcpCode, ""));
        }
        String transportType = StrUtil.blankToDefault(chatMcp.getTransportType(), "").trim().toLowerCase();
        if ("stdio".equals(transportType)) {
            throw new IllegalArgumentException("暂不支持 stdio MCP 直连，请先配置 http 或 sse 端点");
        }
        if (!List.of("http", "sse").contains(transportType)) {
            throw new IllegalArgumentException("MCP 传输类型不支持：" + StrUtil.blankToDefault(chatMcp.getTransportType(), ""));
        }
        if (StrUtil.isBlank(chatMcp.getEndpointUrl())) {
            throw new IllegalArgumentException("MCP 远程端点不能为空");
        }
        return chatMcp;
    }

    private boolean isDiscoveredExternalMcp(ChatMcp chatMcp) {
        return chatMcp != null
            && "external".equalsIgnoreCase(StrUtil.blankToDefault(chatMcp.getSourceType(), ""))
            && "AVAILABLE".equalsIgnoreCase(StrUtil.blankToDefault(chatMcp.getHealthStatus(), ""))
            && StrUtil.isNotBlank(chatMcp.getToolSchemaJson());
    }

    private JSONObject callJsonRpc(ChatMcp chatMcp, String method, JSONObject params) {
        JSONObject payload = JSONUtil.createObj()
            .set("jsonrpc", "2.0")
            .set("id", 1)
            .set("method", method)
            .set("params", params == null ? JSONUtil.createObj() : params);
        RequestBody requestBody = RequestBody.create(JSONUtil.toJsonStr(payload), JSON_MEDIA_TYPE);
        Request.Builder requestBuilder = new Request.Builder()
            .url(chatMcp.getEndpointUrl())
            .post(requestBody);
        applyHeaders(requestBuilder, chatMcp.getHeadersJson());
        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            ResponseBody responseBody = response.body();
            String bodyText = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IllegalArgumentException("MCP 服务响应失败：" + response.code());
            }
            JSONObject root = JSONUtil.parseObj(bodyText);
            JSONObject error = root.getJSONObject("error");
            if (error != null) {
                throw new IllegalArgumentException("MCP 服务返回错误：" + StrUtil.blankToDefault(error.getStr("message"), error.toString()));
            }
            JSONObject result = root.getJSONObject("result");
            return result == null ? JSONUtil.createObj() : result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("MCP 服务连接失败：" + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("MCP 响应解析失败：" + exception.getMessage(), exception);
        }
    }

    private void applyHeaders(Request.Builder requestBuilder, String headersJson) {
        if (StrUtil.isBlank(headersJson)) {
            return;
        }
        JSONObject headers = JSONUtil.parseObj(headersJson);
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (StrUtil.isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
        }
    }

    private List<ChatToolSpec> toToolSpecs(String mcpCode, JSONArray tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<ChatToolSpec> specs = new ArrayList<>();
        for (Object toolItem : tools) {
            JSONObject tool = JSONUtil.parseObj(toolItem);
            String toolName = tool.getStr("name");
            if (StrUtil.isBlank(toolName)) {
                continue;
            }
            String namespacedToolName = McpToolNameSupport.buildToolName(mcpCode, toolName);
            JSONObject inputSchema = tool.getJSONObject("inputSchema");
            specs.add(new ChatToolSpec(
                namespacedToolName,
                StrUtil.blankToDefault(tool.getStr("description"), namespacedToolName),
                inputSchema == null ? defaultInputSchema() : new LinkedHashMap<>(inputSchema),
                namespacedToolName
            ));
        }
        return specs;
    }

    private Map<String, Object> defaultInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        schema.put("required", List.of());
        return schema;
    }

    private Object parseArguments(String argumentsJson) {
        if (StrUtil.isBlank(argumentsJson)) {
            return JSONUtil.createObj();
        }
        Object parsed = JSONUtil.parse(argumentsJson);
        return parsed == null ? JSONUtil.createObj() : parsed;
    }

    private ChatMcpToolResult toToolResult(
        String namespacedToolName,
        McpToolNameSupport.NamespacedToolName parsedToolName,
        JSONObject result
    ) {
        JSONArray contentItems = result == null ? null : result.getJSONArray("content");
        String content = joinTextContent(contentItems);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mcpCode", parsedToolName.mcpCode());
        metadata.put("toolName", parsedToolName.toolName());
        if (result != null && result.containsKey("structuredContent")) {
            metadata.put("structuredContent", result.get("structuredContent"));
        }
        return new ChatMcpToolResult(namespacedToolName, content, metadata);
    }

    private String joinTextContent(JSONArray contentItems) {
        if (contentItems == null || contentItems.isEmpty()) {
            return "";
        }
        List<String> texts = new ArrayList<>();
        for (Object contentItem : contentItems) {
            JSONObject contentObject = JSONUtil.parseObj(contentItem);
            String text = contentObject.getStr("text");
            if (StrUtil.isNotBlank(text)) {
                texts.add(text);
            }
        }
        return String.join("\n", texts);
    }

    private void markRuntimeError(ChatMcp chatMcp, String errorMessage) {
        chatMcpRepository.save(chatMcp.toBuilder()
            .healthStatus("ERROR")
            .lastErrorMessage(StrUtil.blankToDefault(errorMessage, "MCP 运行时异常"))
            .build());
    }
}
