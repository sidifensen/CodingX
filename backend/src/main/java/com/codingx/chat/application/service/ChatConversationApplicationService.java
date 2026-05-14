package com.codingx.chat.application.service;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.common.exception.ForbiddenException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责协调 ChatConversationApplicationService 的应用流程，串联领域对象与基础设施服务。
 */
@Service
@RequiredArgsConstructor
public class ChatConversationApplicationService {

    /**
     * ChatConversationRepository 依赖。
     */
    private final ChatConversationRepository chatConversationRepository;

    /**
     * ChatMessageRepository 依赖。
     */
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 创建 createConversation 所需数据并返回结果。
     * @param command 输入参数。
     * @param userId 输入参数。
     * @return 输入参数。
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
     * 返回 listConversations 需要的结果集合。
     * @param userId 输入参数。
     * @return 输入参数。
     */
    public List<ChatConversation> listConversations(Long userId) {
        return chatConversationRepository.findByCreatedBy(userId);
    }

    /**
     * 返回 listMessages 需要的结果集合。
     * @param conversationId 输入参数。
     * @param userId 输入参数。
     * @return 输入参数。
     */
    public List<ChatMessage> listMessages(Long conversationId, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException("You cannot access this conversation");
        }
        return chatMessageRepository.findByConversationId(conversationId);
    }
}
