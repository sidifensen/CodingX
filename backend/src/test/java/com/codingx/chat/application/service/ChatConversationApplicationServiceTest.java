package com.codingx.chat.application.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.common.exception.ForbiddenException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 ChatConversationApplicationService 的关键场景。
 */
@ExtendWith(MockitoExtension.class)
class ChatConversationApplicationServiceTest {

    /**
     * ChatConversationRepository 依赖。
     */
    @Mock
    private ChatConversationRepository chatConversationRepository;

    /**
     * ChatMessageRepository 依赖。
     */
    @Mock
    private ChatMessageRepository chatMessageRepository;

    /**
     * ChatConversationApplicationService 依赖。
     */
    @InjectMocks
    private ChatConversationApplicationService chatConversationApplicationService;

    /**
     * 创建 createConversationDefaultsBlankTitle 所需数据并返回结果。
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
     * 返回 listMessagesReturnsOwnedConversationMessages 需要的结果集合。
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
     * 返回 listMessagesRejectsNonOwner 需要的结果集合。
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
