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
     * HTTP 客户端，用于向 DeepSeek/OpenAI 兼容接口发起流式 chat completions 请求。
     */
    private final OkHttpClient okHttpClient;

    /**
     * 静态 AI 配置，用作动态配置缺失时的 baseUrl、apiKey 和模型名回退来源。
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
            // 步骤 1：API Key 缺失时不发起 HTTP 请求，直接用模型流事件返回可读错误。
            handler.onContentDelta(ErrorMessageCatalog.AI_API_KEY_NOT_CONFIGURED);
            handler.onComplete();
            return new AiStreamSession(() -> {}, CompletableFuture.completedFuture(null));
        }
        // 步骤 2：组装 OpenAI 兼容请求体，并创建可被 fallback 或用户取消中断的 HTTP 调用。
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
        // 步骤 3：异步执行阻塞式 HTTP 流读取，避免调用线程一直占用 provider 响应读取过程。
        CompletableFuture.runAsync(() -> {
            try (Response response = call.execute()) {
                if (!response.isSuccessful()) {
                    // 步骤 4：HTTP 非 2xx 时把状态码和响应体写入异常，供路由层记录失败并 fallback。
                    String body = response.body() != null ? response.body().string() : "";
                    throw new IllegalStateException(ErrorMessageCatalog.AI_REQUEST_FAILED + "：HTTP " + response.code() + " " + body);
                }
                ResponseBody responseBodyValue = response.body();
                if (responseBodyValue == null) {
                    // 步骤 5：响应体为空说明 provider 协议异常，交由路由层按首包失败处理。
                    throw new IllegalStateException(ErrorMessageCatalog.AI_RESPONSE_BODY_EMPTY);
                }
                BufferedSource source = responseBodyValue.source();
                // 步骤 6：每次模型流创建独立解析状态，避免并发 tool_call 分片互相串线。
                OpenAiStyleStreamParser.StreamState streamState = openAiStyleStreamParser.newStreamState();
                OpenAiStyleStreamParser.StreamConsumer streamConsumer = new OpenAiStyleStreamParser.StreamConsumer() {
                    @Override
                    public void onContentDelta(String delta) {
                        if (!cancelled.get()) {
                            // 取消后不再向下游推送正文增量，避免 fallback 旧流污染新响应。
                            handler.onContentDelta(delta);
                        }
                    }

                    @Override
                    public void onThinkingDelta(String delta) {
                        if (!cancelled.get()) {
                            // thinking 增量单独透传，前端可与最终正文分区展示。
                            handler.onThinkingDelta(delta);
                        }
                    }

                    @Override
                    public void onDone() {
                        if (!cancelled.get()) {
                            // provider 发送 [DONE] 后通知下游完成，路由层据此收口当前 session。
                            handler.onComplete();
                        }
                    }

                    @Override
                    public void onToolCall(com.codingx.common.support.ai.AiToolCall toolCall) {
                        if (!cancelled.get()) {
                            // 工具调用保持原始参数字符串，由工具执行层负责解析和校验。
                            handler.onToolCall(toolCall);
                        }
                    }
                };
                while (!cancelled.get()) {
                    // 步骤 7：逐行读取 SSE 文本，并交给共享解析器拆解正文、thinking 和工具调用。
                    String line = source.readUtf8Line();
                    if (line == null) {
                        break;
                    }
                    openAiStyleStreamParser.parseChunk(line + "\n", streamState, streamConsumer);
                }
                // 步骤 8：连接结束时 flush 残留半行和已累积的工具调用，避免尾包数据丢失。
                openAiStyleStreamParser.flush(streamState, streamConsumer);
                completion.complete(null);
            } catch (IOException exception) {
                if (!cancelled.get()) {
                    // 步骤 9：未取消场景的 IO 异常要通知下游，取消导致的异常只标记 Future 失败。
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
        // 步骤 1：模型名优先取路由目标候选，缺失时兼容旧式 preferredModel/default model。
        return JSONUtil.createObj()
            .set("model", target != null && target.candidate() != null ? target.candidate().getModel() : targetModel(request))
            .set("stream", request.stream())
            // 步骤 2：聊天历史统一转换为 OpenAI message 数组，保持 provider 入参格式稳定。
            .set("messages", request.messages().stream()
                .map(this::toMessagePayload)
                .collect(Collectors.toList()))
            // 步骤 3：没有工具时传 null，避免 provider 把空工具数组误判为工具调用模式。
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
        // 步骤 1：把内部工具 schema 映射为 OpenAI function tool 结构，参数 schema 原样透传。
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
        // 步骤 1：领域角色枚举转换为 provider 所需的小写 role，正文原样传递。
        return JSONUtil.createObj()
            .set("role", message.getRole().name().toLowerCase())
            .set("content", message.getContent());
    }

    /**
     * 解析聊天 completions 的完整 URL，避免调用方重复拼接。
     * @return completions 接口 URL。
     */
    private String resolveChatCompletionsUrl(AiModelTarget target) {
        // 步骤 1：先清理 baseUrl 末尾斜杠，再交给 HttpUrl 校验合法性。
        HttpUrl baseUrl = HttpUrl.parse(StrUtil.removeSuffix(resolveBaseUrl(target), "/"));
        if (baseUrl == null) {
            throw new IllegalStateException(ErrorMessageCatalog.AI_BASE_URL_INVALID);
        }
        // 步骤 2：统一拼接 /chat/completions，避免配置中重复携带 endpoint 造成路径错乱。
        return baseUrl.newBuilder().addPathSegment("chat").addPathSegment("completions").build().toString();
    }

    /**
     * 优先从模型目标 provider 配置解析基础地址，缺失时回退旧式全局配置。
     * @param target 模型目标。
     * @return 基础地址。
     */
    private String resolveBaseUrl(AiModelTarget target) {
        // 步骤 1：路由目标 provider 配置优先，支持不同候选模型使用不同 baseUrl。
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
        // 步骤 1：路由目标 provider 配置优先，缺失时回退旧式全局配置保证兼容。
        return target != null && target.provider() != null && StrUtil.isNotBlank(target.provider().getApiKey())
            ? target.provider().getApiKey()
            : fallbackApiKey();
    }

    private String fallbackBaseUrl() {
        // 步骤 1：动态配置存在时优先使用系统配置表值，否则回退 application.yml 静态配置。
        return dynamicAiProperties == null ? aiProperties.getBaseUrl() : dynamicAiProperties.baseUrl();
    }

    private String fallbackApiKey() {
        // 步骤 1：动态配置存在时优先使用系统配置表密钥，否则回退静态配置。
        return dynamicAiProperties == null ? aiProperties.getApiKey() : dynamicAiProperties.apiKey();
    }

    private String fallbackChatModel() {
        // 步骤 1：旧式单模型路径优先读取动态配置，未启用动态配置时使用静态 chatModel。
        return dynamicAiProperties == null ? aiProperties.getChatModel() : dynamicAiProperties.chatModel();
    }

    private String targetModel(AiConversationRequest request) {
        // 步骤 1：请求显式指定 preferredModel 时优先使用，否则使用兼容旧式默认模型。
        return StrUtil.blankToDefault(request.preferredModel(), fallbackChatModel());
    }
}

