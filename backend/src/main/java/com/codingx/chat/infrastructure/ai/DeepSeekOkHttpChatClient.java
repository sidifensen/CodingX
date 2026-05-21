package com.codingx.chat.infrastructure.ai;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.config.AiProperties;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.support.ai.AiConversationRequest;
import com.codingx.common.support.ai.AiModelTarget;
import com.codingx.common.support.ai.AiProviderClient;
import com.codingx.common.support.ai.AiStreamSession;
import com.codingx.common.support.ai.AiStreamHandler;
import com.codingx.common.support.ai.OpenAiStyleStreamParser;
import java.io.IOException;
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
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

/**
 * 提供 DeepSeek 风格模型的流式 provider 实现。
 */
@Component
@RequiredArgsConstructor
public class DeepSeekOkHttpChatClient implements AiProviderClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * OkHttpClient 依赖。
     */
    private final OkHttpClient okHttpClient;

    /**
     * AiProperties 依赖。
     */
    private final AiProperties aiProperties;

    /**
     * 解析 OpenAI 风格 SSE 文本的通用解析器。
     */
    private final OpenAiStyleStreamParser openAiStyleStreamParser;

    @Override
    public String provider() {
        return "deepseek";
    }

    @Override
    public AiStreamSession streamChat(AiConversationRequest request, AiModelTarget target, AiStreamHandler handler) {
        if (StrUtil.isBlank(resolveApiKey(target))) {
            handler.onContentDelta(ErrorMessageCatalog.AI_API_KEY_NOT_CONFIGURED);
            handler.onComplete();
            return new AiStreamSession(() -> {}, CompletableFuture.completedFuture(null));
        }
        JSONObject requestBody = buildRequestBody(request);
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
                    throw new IllegalStateException(ErrorMessageCatalog.AI_REQUEST_FAILED + "：HTTP " + response.code() + " " + body);
                }
                ResponseBody responseBodyValue = response.body();
                if (responseBodyValue == null) {
                    throw new IllegalStateException(ErrorMessageCatalog.AI_RESPONSE_BODY_EMPTY);
                }
                BufferedSource source = responseBodyValue.source();
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
     * 组装 provider 需要的统一请求体。
     * @param request 统一请求对象。
     * @return JSON 请求体。
     */
    private JSONObject buildRequestBody(AiConversationRequest request) {
        return JSONUtil.createObj()
            .set("model", StrUtil.blankToDefault(request.preferredModel(), aiProperties.getChatModel()))
            .set("stream", request.stream())
            .set("messages", request.messages().stream()
                .map(this::toMessagePayload)
                .collect(Collectors.toList()));
    }

    /**
     * 组装单条消息负载。
     * @param message 聊天消息。
     * @return JSON 消息对象。
     */
    private Object toMessagePayload(ChatMessage message) {
        return JSONUtil.createObj()
            .set("role", message.getRole().name().toLowerCase())
            .set("content", message.getContent());
    }

    /**
     * 解析聊天 completions 的完整 URL，避免调用方重复拼接。
     * @return completions 接口 URL。
     */
    private String resolveChatCompletionsUrl(AiModelTarget target) {
        HttpUrl baseUrl = HttpUrl.parse(StrUtil.removeSuffix(resolveBaseUrl(target), "/"));
        if (baseUrl == null) {
            throw new IllegalStateException(ErrorMessageCatalog.AI_BASE_URL_INVALID);
        }
        return baseUrl.newBuilder().addPathSegment("chat").addPathSegment("completions").build().toString();
    }

    /**
     * 优先从模型目标 provider 配置解析基础地址，缺失时回退旧式全局配置。
     * @param target 模型目标。
     * @return 基础地址。
     */
    private String resolveBaseUrl(AiModelTarget target) {
        return target != null && target.provider() != null && StrUtil.isNotBlank(target.provider().getBaseUrl())
            ? target.provider().getBaseUrl()
            : aiProperties.getBaseUrl();
    }

    /**
     * 优先从模型目标 provider 配置解析 API Key，缺失时回退旧式全局配置。
     * @param target 模型目标。
     * @return API Key。
     */
    private String resolveApiKey(AiModelTarget target) {
        return target != null && target.provider() != null && StrUtil.isNotBlank(target.provider().getApiKey())
            ? target.provider().getApiKey()
            : aiProperties.getApiKey();
    }
}

