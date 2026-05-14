package com.codingx.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codingx.chat.domain.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证模型路由服务的候选选择与 fallback 行为。
 */
class AiModelDispatchServiceTest {

    /**
     * 首个候选失败后应回退到下一个可用候选，并继续输出增量。
     */
    @Test
    void streamChatFallsBackToNextAvailableProvider() {
        RecordingProvider failingProvider = RecordingProvider.failing("primary", "deepseek-chat");
        RecordingProvider healthyProvider = RecordingProvider.success("secondary", "qwen-plus", List.of("hello", " world"));
        AiModelDispatchService service = new AiModelDispatchService(List.of(failingProvider, healthyProvider));
        AiConversationRequest request = AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .preferredModel("deepseek-chat")
            .stream(true)
            .build();
        List<String> deltas = new ArrayList<>();
        List<String> terminals = new ArrayList<>();

        service.streamChat(request, new AiStreamHandler() {
            @Override
            public void onContentDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete() {
                terminals.add("done");
            }
        });

        assertEquals(List.of("primary", "secondary"), service.getLastAttemptedProviders());
        assertEquals(List.of("hello", " world"), deltas);
        assertEquals(List.of("done"), terminals);
    }

    /**
     * 没有任何候选成功时应抛出异常，而不是静默吞掉错误。
     */
    @Test
    void streamChatThrowsWhenAllProvidersFail() {
        AiModelDispatchService service = new AiModelDispatchService(List.of(
            RecordingProvider.failing("primary", "deepseek-chat"),
            RecordingProvider.failing("secondary", "qwen-plus")
        ));
        AiConversationRequest request = AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .preferredModel("deepseek-chat")
            .stream(true)
            .build();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.streamChat(request, new AiStreamHandler() {
        }));

        assertEquals("No available AI provider could complete the request", exception.getMessage());
    }

    /**
     * 用于验证路由行为的内存 provider。
     */
    private record RecordingProvider(
        String providerName,
        String modelName,
        boolean fail,
        List<String> deltas
    ) implements AiProviderClient {

        private static RecordingProvider failing(String providerName, String modelName) {
            return new RecordingProvider(providerName, modelName, true, List.of());
        }

        private static RecordingProvider success(String providerName, String modelName, List<String> deltas) {
            return new RecordingProvider(providerName, modelName, false, deltas);
        }

        @Override
        public boolean supports(AiConversationRequest request) {
            return true;
        }

        @Override
        public AiProviderCandidate candidate() {
            return new AiProviderCandidate(providerName, modelName, 100, true);
        }

        @Override
        public void streamChat(AiConversationRequest request, AiStreamHandler handler) {
            if (fail) {
                throw new IllegalStateException(providerName + " unavailable");
            }
            for (String delta : deltas) {
                handler.onContentDelta(delta);
            }
            handler.onComplete();
        }
    }
}
