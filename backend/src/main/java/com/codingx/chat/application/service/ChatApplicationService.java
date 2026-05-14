package com.codingx.chat.application.service;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import com.codingx.common.exception.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责协调 ChatApplicationService 的应用流程，串联领域对象与基础设施服务。
 */
@Service
@RequiredArgsConstructor
public class ChatApplicationService {

    /**
     * ChatConversationRepository 依赖。
     */
    private final ChatConversationRepository chatConversationRepository;

    /**
     * ChatMessageRepository 依赖。
     */
    private final ChatMessageRepository chatMessageRepository;

    /**
     * AiChatClient 依赖。
     */
    private final AiChatClient aiChatClient;

    /**
     * ChatStreamPublisher 依赖。
     */
    private final ChatStreamPublisher chatStreamPublisher;

    /**
     * 发送 sendMessage 处理的消息或请求。
     * @param command 输入参数。
     * @param userId 输入参数。
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
