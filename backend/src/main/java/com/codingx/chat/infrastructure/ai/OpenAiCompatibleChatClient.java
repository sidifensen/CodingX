package com.codingx.chat.infrastructure.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.support.ai.AiConversationRequest;
import com.codingx.common.support.ai.AiModelTarget;
import com.codingx.common.support.ai.AiProviderClient;
import com.codingx.common.support.ai.AiStreamHandler;
import com.codingx.common.support.ai.AiStreamSession;
import com.codingx.common.support.ai.OpenAiStyleStreamParser;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.stereotype.Component;

/**
 * 负责承接 OpenAI 兼容协议的多 provider 聊天客户端，目前用于百炼和 SiliconFlow。
 */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleChatClient implements AiProviderClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** HTTP 客户端，用于向 OpenAI 兼容 provider 发起流式请求。 */
    private final OkHttpClient okHttpClient;
    /** OpenAI 风格流解析器，用于把 SSE 数据块转换为统一增量事件。 */
    private final OpenAiStyleStreamParser openAiStyleStreamParser;
    /** 附件服务，用于读取用户上传附件并转换为模型请求内容。 */
    private final ChatAttachmentService chatAttachmentService;

    @Override
    public String provider() {
        return "openai-compatible";
    }

    @Override
    public AiStreamSession streamChat(AiConversationRequest request, AiModelTarget target, AiStreamHandler handler) {
        // 步骤 1：校验 provider 与 API Key，避免把错误配置延迟到网络请求阶段才暴露。
        String providerName = target.candidate().getProvider();
        if (!supportsProvider(providerName)) {
            throw new IllegalStateException(ErrorMessageCatalog.AI_PROVIDER_UNSUPPORTED + "：" + providerName);
        }
        if (StrUtil.isBlank(resolveApiKey(target))) {
            throw new IllegalStateException(ErrorMessageCatalog.AI_API_KEY_NOT_CONFIGURED + "：" + providerName);
        }
        // 步骤 2：组装 OpenAI 兼容请求体和 OkHttp 请求，completion 用于路由层等待流式收口。
        JSONObject requestBody = buildRequestBody(request, target);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Request httpRequest = new Request.Builder()
            .url(resolveChatCompletionsUrl(target))
            .header("Authorization", "Bearer " + resolveApiKey(target))
            .header("Content-Type", "application/json")
            .post(RequestBody.create(requestBody.toString(), JSON))
            .build();
        Call call = okHttpClient.newCall(httpRequest);
        CompletableFuture.runAsync(() -> {
            try (Response response = call.execute()) {
                // 步骤 3：检查 HTTP 状态与响应体，失败时保留状态码和响应内容便于后端排查。
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    throw new IllegalStateException(ErrorMessageCatalog.AI_REQUEST_FAILED + "：HTTP " + response.code() + " " + body);
                }
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new IllegalStateException(ErrorMessageCatalog.AI_RESPONSE_BODY_EMPTY);
                }
                BufferedSource source = responseBody.source();
                OpenAiStyleStreamParser.StreamState streamState = openAiStyleStreamParser.newStreamState();
                // 步骤 4：把 OpenAI SSE 片段转发给统一处理器，取消后停止向上游回调，避免旧流污染新候选。
                OpenAiStyleStreamParser.StreamConsumer streamConsumer = new OpenAiStyleStreamParser.StreamConsumer() {
                    @Override
                    public void onContentDelta(String delta) {
                        if (!cancelled.get()) {
                            handler.onContentDelta(delta);
                        }
                    }

                    @Override
                    public void onThinkingDelta(String delta) {
                        if (!cancelled.get() && request.thinkingEnabled()) {
                            // 深度思考开关是后端展示边界；普通请求即使上游返回 reasoning 也不能透出。
                            handler.onThinkingDelta(delta);
                        }
                    }

                    @Override
                    public void onDone() {
                        if (!cancelled.get()) {
                            handler.onComplete();
                        }
                    }

                    @Override
                    public void onToolCall(com.codingx.common.support.ai.AiToolCall toolCall) {
                        if (!cancelled.get()) {
                            handler.onToolCall(toolCall);
                        }
                    }

                    @Override
                    public void onToolCallDelta(com.codingx.common.support.ai.AiToolCallDelta toolCallDelta) {
                        if (!cancelled.get()) {
                            handler.onToolCallDelta(toolCallDelta);
                        }
                    }
                };
                while (!cancelled.get()) {
                    String line = source.readUtf8Line();
                    if (line == null) {
                        break;
                    }
                    openAiStyleStreamParser.parseChunk(line + "\n", streamState, streamConsumer);
                }
                // 步骤 5：读取结束后 flush 累积状态并完成 Future，让路由层能准确判断流式会话已收口。
                openAiStyleStreamParser.flush(streamState, streamConsumer);
                completion.complete(null);
            } catch (IOException exception) {
                // 步骤 6：网络异常只在未取消时上报给业务处理器；取消导致的 IOException 不再打扰前端。
                if (!cancelled.get()) {
                    handler.onError(exception);
                }
                completion.completeExceptionally(exception);
            }
        });
        return new AiStreamSession(() -> {
            // 步骤 7：候选 fallback 或用户取消时同时标记取消并中断真实 HTTP 调用。
            cancelled.set(true);
            // 业务约束：首包超时 fallback 时必须打断真实网络读，避免旧 provider 持续占用连接与后台线程。
            call.cancel();
        }, completion);
    }

    /**
     * 当前客户端仅接管两个 OpenAI 兼容 provider，避免误接管 stub / deepseek。
     * @param providerName provider 名称。
     * @return 是否支持。
     */
    private boolean supportsProvider(String providerName) {
        return "bailian".equals(providerName) || "siliconflow".equals(providerName);
    }

    /**
     * 组装统一 OpenAI 兼容请求体。
     * @param request 对话请求。
     * @param target 模型目标。
     * @return JSON 请求体。
     */
    private JSONObject buildRequestBody(AiConversationRequest request, AiModelTarget target) {
        JSONObject body = JSONUtil.createObj()
            .set("model", target.candidate().getModel())
            .set("stream", request.stream())
            .set("messages", request.messages().stream()
                .map(message -> toMessagePayload(message, request.attachments()))
                .collect(Collectors.toList()));
        if (request.tools() != null && !request.tools().isEmpty()) {
            body.set("tools", request.tools().stream()
                .map(this::toToolPayload)
                .collect(Collectors.toList()));
        }
        if (request.thinkingEnabled()) {
            // 百炼和兼容 provider 需要显式开关才会在流式响应中返回 reasoning_content。
            body.set("enable_thinking", true);
        }
        return body;
    }

    /**
     * 将本地工具 schema 转换为 OpenAI function tool 格式。
     * @param toolSpec 本地工具定义。
     * @return OpenAI 请求体工具对象。
     */
    private Object toToolPayload(com.codingx.tool.application.service.ChatToolSpec toolSpec) {
        return JSONUtil.createObj()
            .set("type", "function")
            .set("function", JSONUtil.createObj()
                .set("name", toolSpec.name())
                .set("description", toolSpec.description())
                .set("parameters", toolSpec.parameters()));
    }

    /**
     * 组装单条消息负载。
     * @param message 聊天消息。
     * @return JSON 消息对象。
     */
    private Object toMessagePayload(ChatMessage message, java.util.List<ChatAttachment> attachments) {
        // 步骤 1：先筛出属于当前消息的附件，避免把同会话其他消息附件误注入模型。
        java.util.List<ChatAttachment> messageAttachments = attachments == null
            ? java.util.List.of()
            : attachments.stream()
                .filter(attachment -> attachment.getMessageId() != null && attachment.getMessageId().equals(message.getId()))
                .toList();
        // 步骤 2：用户消息存在附件时使用 OpenAI 多模态 content 数组，支持文本、附件摘要和图片 data URL。
        if (message.getRole() == ChatMessageRole.USER && !messageAttachments.isEmpty()) {
            java.util.List<Object> contentItems = new java.util.ArrayList<>();
            if (StrUtil.isNotBlank(message.getContent())) {
                contentItems.add(JSONUtil.createObj()
                    .set("type", "text")
                    .set("text", message.getContent()));
            }
            String textAttachmentContext = buildTextAttachmentContext(messageAttachments);
            if (StrUtil.isNotBlank(textAttachmentContext)) {
                contentItems.add(JSONUtil.createObj()
                    .set("type", "text")
                    .set("text", textAttachmentContext));
            }
            for (ChatAttachment attachment : messageAttachments) {
                if (!"image".equalsIgnoreCase(attachment.getAttachmentType())) {
                    continue;
                }
                byte[] bytes = chatAttachmentService.downloadContent(attachment);
                String base64 = Base64.encode(bytes);
                String dataUrl = "data:" + attachment.getMimeType() + ";base64," + base64;
                contentItems.add(JSONUtil.createObj()
                    .set("type", "image_url")
                    .set("image_url", JSONUtil.createObj().set("url", dataUrl)));
            }
            // 步骤 3：附件内容至少生成一个 content item 时返回多模态结构，否则回退普通文本消息。
            if (!contentItems.isEmpty()) {
                return JSONUtil.createObj()
                    .set("role", message.getRole().name().toLowerCase())
                    .set("content", contentItems);
            }
        }
        // 步骤 4：非用户消息或无有效附件时使用普通 role/content 结构，兼容所有 OpenAI 风格 provider。
        return JSONUtil.createObj()
            .set("role", message.getRole().name().toLowerCase())
            .set("content", message.getContent());
    }

    /**
     * 将非图片附件摘要拼接为文本上下文，确保文档上传后模型可读到核心内容。
     * @param messageAttachments 用户消息绑定的附件列表。
     * @return 可注入模型的文本上下文。
     */
    private String buildTextAttachmentContext(java.util.List<ChatAttachment> messageAttachments) {
        StringBuilder contextBuilder = new StringBuilder();
        for (ChatAttachment attachment : messageAttachments) {
            if ("image".equalsIgnoreCase(attachment.getAttachmentType())) {
                continue;
            }
            String summary = StrUtil.trimToNull(attachment.getContentSummary());
            if (summary == null) {
                continue;
            }
            if (contextBuilder.length() == 0) {
                contextBuilder.append("以下是用户上传文件的文本摘要，请结合摘要回答：\n");
            }
            contextBuilder
                .append("- 文件：")
                .append(StrUtil.blankToDefault(attachment.getFileName(), "未命名文件"))
                .append('\n')
                .append("  摘要：")
                .append(summary)
                .append('\n');
        }
        return StrUtil.trimToNull(contextBuilder.toString());
    }

    /**
     * 解析目标 provider 的 completions 地址。
     * @param target 模型目标。
     * @return 聊天 completions 地址。
     */
    private String resolveChatCompletionsUrl(AiModelTarget target) {
        HttpUrl baseUrl = HttpUrl.parse(StrUtil.removeSuffix(target.provider().getBaseUrl(), "/"));
        if (baseUrl == null) {
            throw new IllegalStateException(ErrorMessageCatalog.AI_BASE_URL_INVALID + "：" + target.candidate().getProvider());
        }
        String endpoint = resolveChatEndpoint(target.provider().getEndpoints());
        if (StrUtil.isBlank(endpoint)) {
            throw new IllegalStateException(ErrorMessageCatalog.AI_CHAT_ENDPOINT_NOT_CONFIGURED + "：" + target.candidate().getProvider());
        }
        HttpUrl endpointUrl = HttpUrl.parse(StrUtil.removeSuffix(target.provider().getBaseUrl(), "/") + endpoint);
        if (endpointUrl == null) {
            throw new IllegalStateException(ErrorMessageCatalog.AI_CHAT_ENDPOINT_INVALID + "：" + target.candidate().getProvider());
        }
        return endpointUrl.toString();
    }

    /**
     * 优先读取 provider 自定义 chat endpoint，缺失时回退标准 OpenAI 路径。
     * @param endpoints provider 端点配置。
     * @return 聊天 endpoint。
     */
    private String resolveChatEndpoint(Map<String, String> endpoints) {
        String endpoint = endpoints == null ? null : endpoints.get("chat");
        return StrUtil.blankToDefault(endpoint, "/v1/chat/completions");
    }

    /**
     * 读取模型目标 provider 的 API Key。
     * @param target 模型目标。
     * @return API Key。
     */
    private String resolveApiKey(AiModelTarget target) {
        return target.provider() == null ? null : target.provider().getApiKey();
    }
}

