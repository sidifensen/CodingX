package com.codingx.backend.chat.infrastructure.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.backend.chat.domain.model.ChatMessage;
import com.codingx.backend.chat.domain.model.ChatMessageRole;
import com.codingx.backend.chat.domain.service.AiChatClient;
import com.codingx.backend.config.AiProperties;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class DeepSeekOkHttpChatClient implements AiChatClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final AiProperties aiProperties;

    @Override
    public void streamChat(List<ChatMessage> history, StreamHandler handler) {
        if (StrUtil.isBlank(aiProperties.getApiKey())) {
            handler.onDelta("AI API key is not configured.");
            handler.onComplete();
            return;
        }

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", aiProperties.getChatModel());
        requestBody.set("stream", true);
        requestBody.set("messages", buildMessages(history));

        Request request = new Request.Builder()
            .url(StrUtil.removeSuffix(aiProperties.getBaseUrl(), "/") + "/chat/completions")
            .header("Authorization", "Bearer " + aiProperties.getApiKey())
            .header("Content-Type", "application/json")
            .post(RequestBody.create(requestBody.toString(), JSON))
            .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                handler.onError(new IllegalStateException("AI request failed: HTTP " + response.code() + " " + body));
                return;
            }
            ResponseBody responseBodyValue = response.body();
            if (responseBodyValue == null) {
                handler.onError(new IllegalStateException("AI response body is empty"));
                return;
            }
            BufferedSource source = responseBodyValue.source();
            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (StrUtil.isBlank(line) || !line.startsWith("data:")) {
                    continue;
                }
                String payload = StrUtil.trim(line.substring(5));
                if ("[DONE]".equals(payload)) {
                    handler.onComplete();
                    return;
                }
                String delta = extractDelta(payload);
                if (delta != null) {
                    handler.onDelta(delta);
                }
            }
            handler.onComplete();
        } catch (IOException exception) {
            handler.onError(exception);
        }
    }

    private JSONArray buildMessages(List<ChatMessage> history) {
        JSONArray messages = new JSONArray();
        messages.add(JSONUtil.createObj().set("role", "system").set("content", aiProperties.getSystemPrompt()));
        for (ChatMessage message : history) {
            messages.add(JSONUtil.createObj().set("role", mapRole(message.getRole())).set("content", message.getContent()));
        }
        return messages;
    }

    private String mapRole(ChatMessageRole role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
        };
    }

    private String extractDelta(String payload) {
        try {
            JSONObject root = JSONUtil.parseObj(payload);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.getJSONObject("delta");
            if (delta == null) {
                return null;
            }
            return StrUtil.nullToEmpty(delta.getStr("content"));
        } catch (Exception exception) {
            return null;
        }
    }
}