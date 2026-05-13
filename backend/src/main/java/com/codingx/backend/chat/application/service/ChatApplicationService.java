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
import com.codingx.backend.common.exception.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orchestrates application flow for ChatApplicationService by coordinating domain objects and infrastructure services.
 */
@Service
@RequiredArgsConstructor
public class ChatApplicationService {

    /**
     * ChatConversationRepository dependency.
     */
    private final ChatConversationRepository chatConversationRepository;

    /**
     * ChatMessageRepository dependency.
     */
    private final ChatMessageRepository chatMessageRepository;

    /**
     * aiChatClient value.
     */
    private final AiChatClient aiChatClient;

    /**
     * chatStreamPublisher value.
     */
    private final ChatStreamPublisher chatStreamPublisher;

    /**
     * Sends the message or payload handled by sendMessage.
     * @param command input argument.
     * @param userId input argument.
     */
    public void sendMessage(SendChatMessageCommand command, Long userId) {
        if (StrUtil.isBlank(command.content())) {
            throw new IllegalArgumentException("Message content is required");
        }
        ChatConversation conversation = chatConversationRepository.requireById(command.conversationId());
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException("You cannot access this conversation");
        }
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
