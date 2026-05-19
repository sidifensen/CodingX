package com.codingx.chat.infrastructure.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.support.ai.AiConversationRequest;
import com.codingx.support.ai.AiModelTarget;
import com.codingx.support.ai.AiProviderClient;
import com.codingx.support.ai.AiStreamHandler;
import com.codingx.support.ai.AiStreamSession;
import com.codingx.support.ai.OpenAiStyleStreamParser;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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

    private final OkHttpClient okHttpClient;
    private final OpenAiStyleStreamParser openAiStyleStreamParser;
    private final ChatAttachmentService chatAttachmentService;

    @Override
    public String provider() {
        return "openai-compatible";
    }

    @Override
    public AiStreamSession streamChat(AiConversationRequest request, AiModelTarget target, AiStreamHandler handler) {
        String providerName = target.candidate().getProvider();
        if (!supportsProvider(providerName)) {
            throw new IllegalStateException("Unsupported provider: " + providerName);
        }
        if (StrUtil.isBlank(resolveApiKey(target))) {
            throw new IllegalStateException("AI API key is not configured for provider: " + providerName);
        }
        JSONObject requestBody = buildRequestBody(request, target);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Request httpRequest = new Request.Builder()
            .url(resolveChatCompletionsUrl(target))
            .header("Authorization", "Bearer " + resolveApiKey(target))
            .header("Content-Type", "application/json")
            .post(RequestBody.create(requestBody.toString(), JSON))
            .build();
        CompletableFuture.runAsync(() -> {
            try (Response response = okHttpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    throw new IllegalStateException("AI request failed: HTTP " + response.code() + " " + body);
                }
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new IllegalStateException("AI response body is empty");
                }
                BufferedSource source = responseBody.source();
                StringBuilder chunkBuffer = new StringBuilder();
                OpenAiStyleStreamParser.StreamConsumer streamConsumer = new OpenAiStyleStreamParser.StreamConsumer() {
                    @Override
                    public void onContentDelta(String delta) {
                        if (!cancelled.get()) {
                            handler.onContentDelta(delta);
                        }
                    }

                    @Override
                    public void onThinkingDelta(String delta) {
                        if (!cancelled.get()) {
                            handler.onThinkingDelta(delta);
                        }
                    }

                    @Override
                    public void onDone() {
                        if (!cancelled.get()) {
                            handler.onComplete();
                        }
                    }
                };
                while (!cancelled.get()) {
                    String line = source.readUtf8Line();
                    if (line == null) {
                        break;
                    }
                    openAiStyleStreamParser.parseChunk(line + "\n", chunkBuffer, streamConsumer);
                }
                openAiStyleStreamParser.flush(chunkBuffer, streamConsumer);
                completion.complete(null);
            } catch (IOException exception) {
                if (!cancelled.get()) {
                    handler.onError(exception);
                }
                completion.completeExceptionally(exception);
            }
        });
        return new AiStreamSession(() -> cancelled.set(true), completion);
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
        if (request.thinkingEnabled()) {
            // 百炼和兼容 provider 需要显式开关才会在流式响应中返回 reasoning_content。
            body.set("enable_thinking", true);
        }
        return body;
    }

    /**
     * 组装单条消息负载。
     * @param message 聊天消息。
     * @return JSON 消息对象。
     */
    private Object toMessagePayload(ChatMessage message, java.util.List<ChatAttachment> attachments) {
        java.util.List<ChatAttachment> messageAttachments = attachments == null
            ? java.util.List.of()
            : attachments.stream()
                .filter(attachment -> attachment.getMessageId() != null && attachment.getMessageId().equals(message.getId()))
                .toList();
        if (message.getRole() == ChatMessageRole.USER && !messageAttachments.isEmpty()) {
            java.util.List<Object> contentItems = new java.util.ArrayList<>();
            if (StrUtil.isNotBlank(message.getContent())) {
                contentItems.add(JSONUtil.createObj()
                    .set("type", "text")
                    .set("text", message.getContent()));
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
            if (!contentItems.isEmpty()) {
                return JSONUtil.createObj()
                    .set("role", message.getRole().name().toLowerCase())
                    .set("content", contentItems);
            }
        }
        return JSONUtil.createObj()
            .set("role", message.getRole().name().toLowerCase())
            .set("content", message.getContent());
    }

    /**
     * 解析目标 provider 的 completions 地址。
     * @param target 模型目标。
     * @return 聊天 completions 地址。
     */
    private String resolveChatCompletionsUrl(AiModelTarget target) {
        HttpUrl baseUrl = HttpUrl.parse(StrUtil.removeSuffix(target.provider().getBaseUrl(), "/"));
        if (baseUrl == null) {
            throw new IllegalStateException("Invalid AI base URL for provider: " + target.candidate().getProvider());
        }
        String endpoint = resolveChatEndpoint(target.provider().getEndpoints());
        if (StrUtil.isBlank(endpoint)) {
            throw new IllegalStateException("Chat endpoint is not configured for provider: " + target.candidate().getProvider());
        }
        HttpUrl endpointUrl = HttpUrl.parse(StrUtil.removeSuffix(target.provider().getBaseUrl(), "/") + endpoint);
        if (endpointUrl == null) {
            throw new IllegalStateException("Invalid AI chat endpoint for provider: " + target.candidate().getProvider());
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
