package com.codingx.backend.chat.application.service;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.backend.chat.application.command.CreateConversationCommand;
import com.codingx.backend.chat.domain.model.ChatConversation;
import com.codingx.backend.chat.domain.model.ChatConversationStatus;
import com.codingx.backend.chat.domain.model.ChatMessage;
import com.codingx.backend.chat.domain.repository.ChatConversationRepository;
import com.codingx.backend.chat.domain.repository.ChatMessageRepository;
import com.codingx.backend.common.exception.ForbiddenException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orchestrates application flow for ChatConversationApplicationService by coordinating domain objects and infrastructure services.
 */
@Service
@RequiredArgsConstructor
public class ChatConversationApplicationService {

    /**
     * ChatConversationRepository dependency.
     */
    private final ChatConversationRepository chatConversationRepository;

    /**
     * ChatMessageRepository dependency.
     */
    private final ChatMessageRepository chatMessageRepository;

    /**
     * Creates the data required by createConversation and returns the result.
     * @param command input argument.
     * @param userId input argument.
     * @return processing result.
     */
    public ChatConversation createConversation(CreateConversationCommand command, Long userId) {
        String title = StrUtil.blankToDefault(command.title(), "New Conversation");
        ChatConversation conversation = ChatConversation.create(
            IdUtil.getSnowflakeNextId(),
            title,
            userId,
            ChatConversationStatus.ACTIVE
        );
        chatConversationRepository.save(conversation);
        return conversation;
    }

    /**
     * Returns the collection required by listConversations.
     * @param userId input argument.
     * @return processing result.
     */
    public List<ChatConversation> listConversations(Long userId) {
        return chatConversationRepository.findByCreatedBy(userId);
    }

    /**
     * Returns the collection required by listMessages.
     * @param conversationId input argument.
     * @param userId input argument.
     * @return processing result.
     */
    public List<ChatMessage> listMessages(Long conversationId, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException("You cannot access this conversation");
        }
        return chatMessageRepository.findByConversationId(conversationId);
    }
}
