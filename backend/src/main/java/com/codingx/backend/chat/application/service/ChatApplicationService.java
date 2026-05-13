package com.codingx.backend.chat.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.backend.chat.application.command.SendChatMessageCommand;
import com.codingx.backend.chat.domain.model.ChatConversation;
import com.codingx.backend.chat.domain.model.ChatMessage;
import com.codingx.backend.chat.domain.model.ChatMessageStatus;
import com.codingx.backend.chat.domain.repository.ChatConversationRepository;
import com.codingx.backend.chat.domain.repository.ChatMessageRepository;
import com.codingx.backend.chat.domain.service.AiChatClient;
import com.codingx.backend.chat.domain.service.ChatStreamPublisher;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatApplicationService {

    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiChatClient aiChatClient;
    private final ChatStreamPublisher chatStreamPublisher;

    public void sendMessage(SendChatMessageCommand command, Long userId) {
        if (StrUtil.isBlank(command.content())) {
            throw new IllegalArgumentException("Message content is required");
        }
        ChatConversation conversation = chatConversationRepository.requireById(command.conversationId());
        List<ChatMessage> history = new ArrayList<>(chatMessageRepository.findByConversationId(command.conversationId()));
        ChatMessage userMessage = ChatMessage.userMessage(command.conversationId(), command.content());
        chatMessageRepository.save(userMessage);
        chatStreamPublisher.publishUserMessage(command.conversationId(), userMessage.getContent());
        history.add(userMessage);

        StringBuilder builder = new StringBuilder();
        final Throwable[] streamError = new Throwable[1];
        aiChatClient.streamChat(history, new AiChatClient.StreamHandler() {
            @Override
            public void onDelta(String delta) {
                builder.append(delta);
                chatStreamPublisher.publishAssistantDelta(command.conversationId(), delta);
            }

            @Override
            public void onComplete() {
                // no-op
            }

            @Override
            public void onError(Throwable throwable) {
                streamError[0] = throwable;
            }
        });

        if (streamError[0] != null) {
            ChatMessage failedMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                StrUtil.blankToDefault(builder.toString(), "AI response failed"),
                ChatMessageStatus.FAILED,
                null,
                null,
                streamError[0].getMessage()
            );
            chatMessageRepository.save(failedMessage);
            chatStreamPublisher.publishError(command.conversationId(), streamError[0].getMessage());
            return;
        }

        ChatMessage assistantMessage = ChatMessage.assistantMessage(
            command.conversationId(),
            StrUtil.blankToDefault(builder.toString(), ""),
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        );
        chatMessageRepository.save(assistantMessage);
        conversation.touch();
        chatConversationRepository.save(conversation);
        chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent());
    }
}