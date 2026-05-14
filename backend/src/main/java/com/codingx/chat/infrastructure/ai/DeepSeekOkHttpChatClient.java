package com.codingx.chat.infrastructure.ai;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.config.AiProperties;
import com.codingx.support.ai.AiConversationRequest;
import com.codingx.support.ai.AiProviderCandidate;
import com.codingx.support.ai.AiProviderClient;
import com.codingx.support.ai.AiStreamHandler;
import com.codingx.support.ai.OpenAiStyleStreamParser;
import java.io.IOException;
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
    public boolean supports(AiConversationRequest request) {
        return true;
    }

    @Override
    public AiProviderCandidate candidate() {
        return new AiProviderCandidate("deepseek", aiProperties.getChatModel(), 100, true);
    }

    @Override
    public void streamChat(AiConversationRequest request, AiStreamHandler handler) {
        if (StrUtil.isBlank(aiProperties.getApiKey())) {
            handler.onContentDelta("AI API key is not configured.");
            handler.onComplete();
            return;
        }
        JSONObject requestBody = buildRequestBody(request);
        Request httpRequest = new Request.Builder()
            .url(resolveChatCompletionsUrl())
            .header("Authorization", "Bearer " + aiProperties.getApiKey())
            .header("Content-Type", "application/json")
            .post(RequestBody.create(requestBody.toString(), JSON))
            .build();
        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new IllegalStateException("AI request failed: HTTP " + response.code() + " " + body);
            }
            ResponseBody responseBodyValue = response.body();
            if (responseBodyValue == null) {
                throw new IllegalStateException("AI response body is empty");
            }
            BufferedSource source = responseBodyValue.source();
            StringBuilder rawStream = new StringBuilder();
            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (line != null) {
                    rawStream.append(line).append('\n');
                }
            }
            openAiStyleStreamParser.parse(rawStream.toString(), new OpenAiStyleStreamParser.StreamConsumer() {
                @Override
                public void onContentDelta(String delta) {
                    handler.onContentDelta(delta);
                }

                @Override
                public void onDone() {
                    handler.onComplete();
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("AI request failed", exception);
        }
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
    private String resolveChatCompletionsUrl() {
        HttpUrl baseUrl = HttpUrl.parse(StrUtil.removeSuffix(aiProperties.getBaseUrl(), "/"));
        if (baseUrl == null) {
            throw new IllegalStateException("Invalid AI base URL");
        }
        return baseUrl.newBuilder().addPathSegment("chat").addPathSegment("completions").build().toString();
    }
}
