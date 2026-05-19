package com.codingx.chat.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.config.AiProperties;
import com.codingx.common.support.ai.AiConversationRequest;
import com.codingx.common.support.ai.AiModelDispatchService;
import com.codingx.common.support.ai.AiModelSelector;
import com.codingx.common.support.ai.AiModelTarget;
import com.codingx.common.support.ai.AiProviderClient;
import com.codingx.common.support.ai.AiProviderHealthRegistry;
import com.codingx.common.support.ai.AiStreamSession;
import com.codingx.common.support.ai.AiStreamHandler;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
        ChatAttachmentService chatAttachmentService = mock(ChatAttachmentService.class);
        when(chatAttachmentService.listByMessageId(1L)).thenReturn(List.of());
        RoutingAiChatClient client = new RoutingAiChatClient(
            new AiModelDispatchService(
                List.of(new EchoProvider()),
                new AiProviderHealthRegistry(2, 30_000L),
                new AiModelSelector(buildAiProperties())
            ),
            chatAttachmentService
        );
        List<String> deltas = new ArrayList<>();
        List<String> terminals = new ArrayList<>();

        client.streamChat(List.of(ChatMessage.userMessage(1L, "你好")), false, new AiChatClient.StreamHandler() {
            @Override
            public void onDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete() {
                terminals.add("done");
            }

            @Override
            public void onMetadata(String provider, String model) {
                terminals.add(provider + ":" + model);
            }
        });

        assertEquals(List.of("echo:你好"), deltas);
        assertEquals(List.of("echo:echo-model", "done"), terminals);
    }

    /**
     * 用于桥接测试的内存 provider。
     */
    private static final class EchoProvider implements AiProviderClient {

        @Override
        public String provider() {
            return "echo";
        }

        @Override
        public AiStreamSession streamChat(AiConversationRequest request, AiModelTarget target, AiStreamHandler handler) {
            handler.onContentDelta("echo:" + request.messages().getFirst().getContent());
            handler.onComplete();
            return new AiStreamSession(() -> {}, CompletableFuture.completedFuture(null));
        }
    }

    /**
     * 组装最小模型配置，让桥接测试能覆盖 provider/model 元信息透传。
     * @return 测试配置。
     */
    private AiProperties buildAiProperties() {
        AiProperties properties = new AiProperties();
        AiProperties.Provider provider = new AiProperties.Provider();
        provider.setBaseUrl("http://127.0.0.1:1");
        provider.setApiKey("");
        HashMap<String, AiProperties.Provider> providers = new HashMap<>();
        providers.put("echo", provider);
        properties.setProviders(providers);

        AiProperties.ChatCandidate candidate = new AiProperties.ChatCandidate();
        candidate.setId("echo-model");
        candidate.setProvider("echo");
        candidate.setModel("echo-model");
        candidate.setPriority(1);
        candidate.setEnabled(true);

        AiProperties.ChatModelGroup chat = new AiProperties.ChatModelGroup();
        chat.setDefaultModel("echo-model");
        chat.setCandidates(List.of(candidate));
        properties.setChat(chat);
        return properties;
    }
}
