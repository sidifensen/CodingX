package com.codingx.chat.infrastructure.ai;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.service.AiChatClient;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 实现 StubAiChatClient 的外部 AI 能力接入。
 */
@Component
public class StubAiChatClient implements AiChatClient {

    /**
     * 以流式方式处理 streamChat 的结果。
     * @param history 输入参数。
     * @param handler 输入参数。
     */
    @Override
    public void streamChat(List<ChatMessage> history, StreamHandler handler) {
        handler.onDelta("Stub response");
        handler.onComplete();
    }
}
