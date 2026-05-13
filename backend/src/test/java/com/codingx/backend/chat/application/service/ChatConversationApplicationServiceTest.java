package com.codingx.backend.chat.application.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.codingx.backend.chat.application.command.CreateConversationCommand;
import com.codingx.backend.chat.domain.model.ChatConversation;
import com.codingx.backend.chat.domain.model.ChatConversationStatus;
import com.codingx.backend.chat.domain.model.ChatMessage;
import com.codingx.backend.chat.domain.model.ChatMessageRole;
import com.codingx.backend.chat.domain.model.ChatMessageStatus;
import com.codingx.backend.chat.domain.repository.ChatConversationRepository;
import com.codingx.backend.chat.domain.repository.ChatMessageRepository;
import com.codingx.backend.common.exception.ForbiddenException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the key scenarios covered by ChatConversationApplicationService.
 */
@ExtendWith(MockitoExtension.class)
class ChatConversationApplicationServiceTest {

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
     * ChatConversationApplicationService dependency.
     */
    @InjectMocks
    private ChatConversationApplicationService chatConversationApplicationService;

    /**
     * Creates the data required by createConversationDefaultsBlankTitle and returns the result.
     */
    @Test
    void createConversationDefaultsBlankTitle() {

        ChatConversation conversation = chatConversationApplicationService.createConversation(
            new CreateConversationCommand(" "),
            1002L

        );
        assertEquals("New Conversation", conversation.getTitle());
        verify(chatConversationRepository).save(conversation);
    }

    /**
     * Returns the collection required by listMessagesReturnsOwnedConversationMessages.
     */
    @Test
    void listMessagesReturnsOwnedConversationMessages() {

        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        List<ChatMessage> messages = List.of(
            ChatMessage.assistantMessage(
                1L,
                "hello",
                ChatMessageStatus.COMPLETED,
                "deepseek",
                "deepseek-chat",
                null
            )

        );
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(messages);
        List<ChatMessage> result = chatConversationApplicationService.listMessages(1L, 1002L);
        assertEquals(1, result.size());
        assertEquals(ChatMessageRole.ASSISTANT, result.getFirst().getRole());
    }

    /**
     * Returns the collection required by listMessagesRejectsNonOwner.
     */
    @Test
    void listMessagesRejectsNonOwner() {

        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        ForbiddenException exception = assertThrows(
            ForbiddenException.class,
            () -> chatConversationApplicationService.listMessages(1L, 2001L)

        );
        assertEquals("You cannot access this conversation", exception.getMessage());
    }
}
