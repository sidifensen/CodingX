package com.codingx.support.ai;

import com.codingx.chat.domain.model.ChatMessage;
import java.util.List;

/**
 * 定义会话 token 预算估算能力。
 */
public interface TokenCounterService {

    /**
     * 估算整段会话消息的 token 数。
     * @param messages 会话消息。
     * @return 估算 token。
     */
    int estimateConversationTokens(List<ChatMessage> messages);
}
