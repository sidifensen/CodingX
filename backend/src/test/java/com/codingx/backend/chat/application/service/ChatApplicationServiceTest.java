package com.codingx.backend.chat.application.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.codingx.backend.chat.application.command.SendChatMessageCommand;
import com.codingx.backend.chat.domain.model.ChatConversation;
import com.codingx.backend.chat.domain.model.ChatConversationStatus;
import com.codingx.backend.chat.domain.model.ChatMessage;
import com.codingx.backend.chat.domain.model.ChatMessageRole;
import com.codingx.backend.chat.domain.model.ChatMessageStatus;
import com.codingx.backend.chat.domain.repository.ChatConversationRepository;
import com.codingx.backend.chat.domain.repository.ChatMessageRepository;
import com.codingx.backend.chat.domain.service.AiChatClient;
import com.codingx.backend.chat.domain.service.ChatStreamPublisher;
import com.codingx.backend.common.exception.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the key scenarios covered by ChatApplicationService.
 */
@ExtendWith(MockitoExtension.class)
class ChatApplicationServiceTest {

    /**
     * ChatConversationRepository dependency.
     */
    @Mock
    private ChatConversationRepository chatConversationRepository;

    /**
     * ChatMessageRepository dependency.
     */
    @Mock
    private ChatMessageRepository chatMessageRepository;

    /**
     * aiChatClient value.
     */
    @Mock
    private AiChatClient aiChatClient;

    /**
     * chatStreamPublisher value.
     */
    @Mock
    private ChatStreamPublisher chatStreamPublisher;

    /**
     * ChatApplicationService dependency.
     */
    @InjectMocks
    private ChatApplicationService chatApplicationService;

    /**
     * Sends the message or payload handled by sendMessagePersistsUserAndAssistantMessages.
     */
    @Test
    void sendMessagePersistsUserAndAssistantMessages() {

        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        List<ChatMessage> history = new ArrayList<>();
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(history);
        doAnswer(invocation -> {

            AiChatClient.StreamHandler handler = invocation.getArgument(1);
            handler.onDelta("Hello");
            handler.onDelta(" world");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), any());
        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi"), 1002L);        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(ChatMessageRole.ASSISTANT, captor.getAllValues().get(1).getRole());
        assertEquals("Hello world", captor.getAllValues().get(1).getContent());
        assertEquals(ChatMessageStatus.COMPLETED, captor.getAllValues().get(1).getStatus());
    }

    /**
     * Sends the message or payload handled by sendMessageRejectsNonOwnerConversation.
     */
    @Test
    void sendMessageRejectsNonOwnerConversation() {

        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        ForbiddenException exception = assertThrows(
            ForbiddenException.class,
            () -> chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi"), 2001L)

        );
        assertEquals("You cannot access this conversation", exception.getMessage());
    }
}
