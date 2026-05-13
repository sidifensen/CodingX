package com.codingx.backend.chat.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.backend.chat.application.command.CreateConversationCommand;
import com.codingx.backend.chat.domain.model.ChatConversation;
import com.codingx.backend.chat.domain.model.ChatConversationStatus;
import com.codingx.backend.chat.domain.model.ChatMessage;
import com.codingx.backend.chat.domain.repository.ChatConversationRepository;
import com.codingx.backend.chat.domain.repository.ChatMessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatConversationApplicationService {

    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;

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

    public List<ChatConversation> listConversations(Long userId) {
        return chatConversationRepository.findByCreatedBy(userId);
    }

    public List<ChatMessage> listMessages(Long conversationId) {
        return chatMessageRepository.findByConversationId(conversationId);
    }
}