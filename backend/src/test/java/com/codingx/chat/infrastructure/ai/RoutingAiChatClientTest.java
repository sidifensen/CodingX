package com.codingx.chat.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.support.ai.AiConversationRequest;
import com.codingx.support.ai.AiModelDispatchService;
import com.codingx.support.ai.AiProviderCandidate;
import com.codingx.support.ai.AiProviderClient;
import com.codingx.support.ai.AiStreamHandler;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证旧聊天接口已经通过路由层驱动 provider 调用。
 */
class RoutingAiChatClientTest {

    /**
     * 旧接口调用时应将消息历史透传给路由服务，并把增量回放给旧 handler。
     */
    @Test
    void streamChatBridgesLegacyHandlerToDispatchService() {
        RoutingAiChatClient client = new RoutingAiChatClient(
            new AiModelDispatchService(List.of(new EchoProvider()))
        );
        List<String> deltas = new ArrayList<>();
        List<String> terminals = new ArrayList<>();

        client.streamChat(List.of(ChatMessage.userMessage(1L, "你好")), new AiChatClient.StreamHandler() {
            @Override
            public void onDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete() {
                terminals.add("done");
            }
        });

        assertEquals(List.of("echo:你好"), deltas);
        assertEquals(List.of("done"), terminals);
    }

    /**
     * 用于桥接测试的内存 provider。
     */
    private static final class EchoProvider implements AiProviderClient {

        @Override
        public boolean supports(AiConversationRequest request) {
            return true;
        }

        @Override
        public AiProviderCandidate candidate() {
            return new AiProviderCandidate("echo", "echo-model", 100, true);
        }

        @Override
        public void streamChat(AiConversationRequest request, AiStreamHandler handler) {
            handler.onContentDelta("echo:" + request.messages().getFirst().getContent());
            handler.onComplete();
        }
    }
}
