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
import com.codingx.workspace.domain.repository.WorkspaceRepository;
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
     * WorkspaceRepository 依赖。
     */
    @Mock
    private WorkspaceRepository workspaceRepository;

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
            new CreateConversationCommand(" ", 3001L),
            1002L

        );
        assertEquals("New Conversation", conversation.getTitle());
        assertEquals(3001L, conversation.getWorkspaceId());
        verify(workspaceRepository).ensureExists(3001L);
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

    /**
     * 会话标题更新应直接作用到会话对象并持久化保存。
     */
    @Test
    void updateConversationTitlePersistsNewTitle() {
        ChatConversation conversation = ChatConversation.create(1L, "New Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);

        chatConversationApplicationService.updateConversationTitle(1L, "AI搜索重构计划", 1002L);

        assertEquals("AI搜索重构计划", conversation.getTitle());
        verify(chatConversationRepository).save(conversation);
    }

    /**
     * 删除会话时应校验归属并转发到仓储层执行逻辑删除。
     */
    @Test
    void deleteConversationDelegatesToRepositoryDelete() {
        ChatConversation conversation = ChatConversation.create(1L, "待删除会话", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);

        chatConversationApplicationService.deleteConversation(1L, 1002L);

        verify(chatConversationRepository).deleteById(1L);
    }

    /**
     * 会话列表应按工作空间过滤，避免不同工作空间会话互相串线。
     */
    @Test
    void listConversationsUsesWorkspaceScope() {
        ChatConversation conversation = ChatConversation.create(1L, "会话A", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.findByCreatedByAndWorkspaceId(1002L, 3001L)).thenReturn(List.of(conversation));

        List<ChatConversation> result = chatConversationApplicationService.listConversations(1002L, 3001L);

        assertEquals(1, result.size());
        verify(workspaceRepository).ensureExists(3001L);
        verify(chatConversationRepository).findByCreatedByAndWorkspaceId(1002L, 3001L);
    }
}
