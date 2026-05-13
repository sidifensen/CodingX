package com.codingx.backend.chat.domain.repository;

import com.codingx.backend.chat.domain.model.ChatMessage;
import java.util.List;

public interface ChatMessageRepository {

    void save(ChatMessage message);

    List<ChatMessage> findByConversationId(Long conversationId);
}