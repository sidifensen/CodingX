package com.codingx.chat.infrastructure.ai;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.config.AiProperties;
import com.codingx.config.DynamicAiProperties;
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
import okhttp3.Call;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 提供 DeepSeek 风格模型的流式 provider 实现。
 */
@Component
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
     * 动态 AI 配置依赖，用于覆盖旧式全局默认值与密钥读取。
     */
    private final DynamicAiProperties dynamicAiProperties;

    /**
     * 解析 OpenAI 风格 SSE 文本的通用解析器。
     */
    private final OpenAiStyleStreamParser openAiStyleStreamParser;

    /**
     * Spring 主构造器，显式注入动态 AI 配置，避免额外兼容构造影响 Bean 选择。
     * @param okHttpClient HTTP 客户端。
     * @param aiProperties 静态 AI 配置。
     * @param dynamicAiProperties 动态 AI 配置。
     * @param openAiStyleStreamParser 流解析器。
     */
    @Autowired
    public DeepSeekOkHttpChatClient(
        OkHttpClient okHttpClient,
        AiProperties aiProperties,
        DynamicAiProperties dynamicAiProperties,
        OpenAiStyleStreamParser openAiStyleStreamParser
    ) {
        this.okHttpClient = okHttpClient;
        this.aiProperties = aiProperties;
        this.dynamicAiProperties = dynamicAiProperties;
        this.openAiStyleStreamParser = openAiStyleStreamParser;
    }

    /**
     * 兼容旧测试与旧调用签名，未显式注入动态配置时回退到静态 AI 配置。
     * @param okHttpClient HTTP 客户端。
     * @param aiProperties 静态 AI 配置。
     * @param openAiStyleStreamParser 流解析器。
     */
    public DeepSeekOkHttpChatClient(
        OkHttpClient okHttpClient,
        AiProperties aiProperties,
        OpenAiStyleStreamParser openAiStyleStreamParser
    ) {
        this(okHttpClient, aiProperties, null, openAiStyleStreamParser);
    }

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
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    throw new IllegalStateException(ErrorMessageCatalog.AI_REQUEST_FAILED + "：HTTP " + response.code() + " " + body);
                }
                ResponseBody responseBodyValue = response.body();
                if (responseBodyValue == null) {
                    throw new IllegalStateException(ErrorMessageCatalog.AI_RESPONSE_BODY_EMPTY);
                }
                BufferedSource source = responseBodyValue.source();
                OpenAiStyleStreamParser.StreamState streamState = openAiStyleStreamParser.newStreamState();
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

                    @Override
                    public void onToolCall(com.codingx.common.support.ai.AiToolCall toolCall) {
                        if (!cancelled.get()) {
                            handler.onToolCall(toolCall);
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
                openAiStyleStreamParser.flush(streamState, streamConsumer);
                completion.complete(null);
            } catch (IOException exception) {
                if (!cancelled.get()) {
                    handler.onError(exception);
                }
                completion.completeExceptionally(exception);
            }
        });
        return new AiStreamSession(() -> {
            cancelled.set(true);
            // 业务约束：路由层 fallback 时必须取消真实 HTTP 调用，避免慢 provider 继续阻塞后台读流。
            call.cancel();
        }, completion);
    }

    /**
     * 组装 provider 需要的统一请求体。
     * @param request 统一请求对象。
     * @return JSON 请求体。
     */
    private JSONObject buildRequestBody(AiConversationRequest request, AiModelTarget target) {
        return JSONUtil.createObj()
            .set("model", target != null && target.candidate() != null ? target.candidate().getModel() : targetModel(request))
            .set("stream", request.stream())
            .set("messages", request.messages().stream()
                .map(this::toMessagePayload)
                .collect(Collectors.toList()))
            .set("tools", request.tools() == null || request.tools().isEmpty()
                ? null
                : request.tools().stream().map(this::toToolPayload).collect(Collectors.toList()));
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
            : fallbackBaseUrl();
    }

    /**
     * 优先从模型目标 provider 配置解析 API Key，缺失时回退旧式全局配置。
     * @param target 模型目标。
     * @return API Key。
     */
    private String resolveApiKey(AiModelTarget target) {
        return target != null && target.provider() != null && StrUtil.isNotBlank(target.provider().getApiKey())
            ? target.provider().getApiKey()
            : fallbackApiKey();
    }

    private String fallbackBaseUrl() {
        return dynamicAiProperties == null ? aiProperties.getBaseUrl() : dynamicAiProperties.baseUrl();
    }

    private String fallbackApiKey() {
        return dynamicAiProperties == null ? aiProperties.getApiKey() : dynamicAiProperties.apiKey();
    }

    private String fallbackChatModel() {
        return dynamicAiProperties == null ? aiProperties.getChatModel() : dynamicAiProperties.chatModel();
    }

    private String targetModel(AiConversationRequest request) {
        return StrUtil.blankToDefault(request.preferredModel(), fallbackChatModel());
    }
}

