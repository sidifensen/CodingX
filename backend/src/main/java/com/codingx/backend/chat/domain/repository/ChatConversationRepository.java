package com.codingx.backend.chat.domain.repository;
import com.codingx.backend.chat.domain.model.ChatConversation;
import java.util.List;

/**
 * Defines the repository contract exposed by ChatConversationRepository.
 */
public interface ChatConversationRepository {

    /**
     * Resolves the required data for requireById or throws when it is missing.
     * @param conversationId input argument.
     * @return processing result.
     */
    ChatConversation requireById(Long conversationId);

    /**
     * Persists the state handled by save.
     * @param conversation input argument.
     */
    void save(ChatConversation conversation);

    /**
     * Finds the data required by findByCreatedBy.
     * @param userId input argument.
     * @return processing result.
     */
    List<ChatConversation> findByCreatedBy(Long userId);
}
