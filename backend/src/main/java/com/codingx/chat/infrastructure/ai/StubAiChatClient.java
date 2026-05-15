package com.codingx.chat.infrastructure.ai;

import com.codingx.support.ai.AiConversationRequest;
import com.codingx.support.ai.AiModelTarget;
import com.codingx.support.ai.AiProviderClient;
import com.codingx.support.ai.AiStreamSession;
import com.codingx.support.ai.AiStreamHandler;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

/**
 * 提供用于本地验证与 fallback 的 mock provider。
 */
@Component
public class StubAiChatClient implements AiProviderClient {

    /**
     * 返回当前客户端负责的 provider 名称。
     * @return provider 名称。
     */
    @Override
    public String provider() {
        return "stub";
    }

    @Override
    public AiStreamSession streamChat(AiConversationRequest request, AiModelTarget target, AiStreamHandler handler) {
        handler.onContentDelta("Stub response");
        handler.onComplete();
        return new AiStreamSession(() -> {}, CompletableFuture.completedFuture(null));
    }
}
