package com.codingx.chat.infrastructure.ai;

import com.codingx.support.ai.AiConversationRequest;
import com.codingx.support.ai.AiProviderCandidate;
import com.codingx.support.ai.AiProviderClient;
import com.codingx.support.ai.AiStreamHandler;
import org.springframework.stereotype.Component;

/**
 * 提供用于本地验证与 fallback 的 mock provider。
 */
@Component
public class StubAiChatClient implements AiProviderClient {

    /**
     * Stub provider 永远可以处理请求，作为保底候选存在。
     * @param request 统一请求对象。
     * @return 是否支持。
     */
    @Override
    public boolean supports(AiConversationRequest request) {
        return true;
    }

    @Override
    public AiProviderCandidate candidate() {
        return new AiProviderCandidate("stub", "stub-chat", 10, true);
    }

    @Override
    public void streamChat(AiConversationRequest request, AiStreamHandler handler) {
        handler.onContentDelta("Stub response");
        handler.onComplete();
    }
}
