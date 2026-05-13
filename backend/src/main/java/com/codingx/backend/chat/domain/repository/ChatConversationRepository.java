package com.codingx.backend.chat.domain.repository;

import com.codingx.backend.chat.domain.model.ChatConversation;
import java.util.List;

public interface ChatConversationRepository {

    ChatConversation requireById(Long conversationId);

    void save(ChatConversation conversation);

    List<ChatConversation> findByCreatedBy(Long userId);
}