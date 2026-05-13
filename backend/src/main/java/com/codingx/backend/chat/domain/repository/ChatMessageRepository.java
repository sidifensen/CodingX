package com.codingx.backend.chat.domain.repository;
import com.codingx.backend.chat.domain.model.ChatMessage;
import java.util.List;

/**
 * Defines the repository contract exposed by ChatMessageRepository.
 */
public interface ChatMessageRepository {

    /**
     * Persists the state handled by save.
     * @param message input argument.
     */
    void save(ChatMessage message);

    /**
     * Finds the data required by findByConversationId.
     * @param conversationId input argument.
     * @return processing result.
     */
    List<ChatMessage> findByConversationId(Long conversationId);
}
