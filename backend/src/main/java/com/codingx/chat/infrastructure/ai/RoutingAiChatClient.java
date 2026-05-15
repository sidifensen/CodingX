package com.codingx.chat.infrastructure.ai;

import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.support.ai.AiConversationRequest;
import com.codingx.support.ai.AiModelDispatchService;
import com.codingx.support.ai.AiStreamHandler;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 通过新的模型路由层适配旧的聊天领域接口，避免上层应用服务一次性大改。
 */
@Component
@Primary
public class RoutingAiChatClient implements AiChatClient {

    private final AiModelDispatchService aiModelDispatchService;

    /**
     * 注入模型路由服务，统一承接所有流式对话请求。
     * @param aiModelDispatchService 模型路由服务。
     */
    public RoutingAiChatClient(AiModelDispatchService aiModelDispatchService) {
        this.aiModelDispatchService = aiModelDispatchService;
    }

    @Override
    public void streamChat(List<ChatMessage> history, boolean deepThinking, StreamHandler handler) {
        AiConversationRequest request = AiConversationRequest.builder()
            .messages(history)
            .stream(true)
            .thinkingEnabled(deepThinking)
            .build();
        aiModelDispatchService.streamChat(request, new AiStreamHandler() {
            @Override
            public void onMetadata(String provider, String model) {
                handler.onMetadata(provider, model);
            }

            @Override
            public void onThinkingDelta(String delta) {
                handler.onThinkingDelta(delta);
            }

            @Override
            public void onContentDelta(String delta) {
                handler.onDelta(delta);
            }

            @Override
            public void onComplete() {
                handler.onComplete();
            }

            @Override
            public void onError(Throwable throwable) {
                handler.onError(throwable);
            }
        });
    }
}
