package com.codingx.support.ai;

import com.codingx.chat.domain.model.ChatMessage;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 使用字符长度做启发式 token 估算，先满足预算保护的最小需求。
 */
@Service
public class HeuristicTokenCounterService implements TokenCounterService {

    @Override
    public int estimateConversationTokens(List<ChatMessage> messages) {
        int totalCharacters = messages == null ? 0 : messages.stream()
            .map(ChatMessage::getContent)
            .filter(java.util.Objects::nonNull)
            .mapToInt(String::length)
            .sum();
        return Math.max(8, (int) Math.round(totalCharacters / 1.9D));
    }
}
