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
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    @Mock
    private WorkspaceRepositoryImpl workspaceRepositoryImpl;

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

        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(3001L);
        workspace.setRuntimeTarget("local");
        workspace.setName("codingx");
        when(workspaceRepositoryImpl.requireOwnedWorkspace(3001L, 1002L)).thenReturn(workspace);
        ChatConversation conversation = chatConversationApplicationService.createConversation(
            new CreateConversationCommand(" ", 3001L),
            1002L

        );
        assertEquals(ErrorMessageCatalog.CHAT_CONVERSATION_DEFAULT_TITLE, conversation.getTitle());
        assertEquals(3001L, conversation.getWorkspaceId());
        verify(workspaceRepositoryImpl).requireOwnedWorkspace(3001L, 1002L);
        verify(chatConversationRepository).save(conversation);
    }

    /**
     * 未传 workspaceId 时应自动归入用户默认云端空间，避免会话落在 null 分区。
     */
    @Test
    void createConversationUsesDefaultCloudWorkspaceWhenWorkspaceIdMissing() {
        WorkspaceDO cloudWorkspace = new WorkspaceDO();
        cloudWorkspace.setId(8001L);
        cloudWorkspace.setRuntimeTarget("cloud");
        cloudWorkspace.setName("云端历史记录");
        when(workspaceRepositoryImpl.ensureDefaultCloudWorkspace(1002L, null)).thenReturn(cloudWorkspace);

        ChatConversation conversation = chatConversationApplicationService.createConversation(
            new CreateConversationCommand("我的会话", null),
            1002L
        );

        assertEquals(8001L, conversation.getWorkspaceId());
        verify(workspaceRepositoryImpl).ensureDefaultCloudWorkspace(1002L, null);
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
        assertEquals(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN, exception.getMessage());
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
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(3001L);
        workspace.setRuntimeTarget("local");
        workspace.setName("codingx");
        when(workspaceRepositoryImpl.requireOwnedWorkspace(3001L, 1002L)).thenReturn(workspace);
        when(chatConversationRepository.findByCreatedByAndWorkspaceId(1002L, 3001L)).thenReturn(List.of(conversation));

        List<ChatConversation> result = chatConversationApplicationService.listConversations(1002L, 3001L);

        assertEquals(1, result.size());
        verify(workspaceRepositoryImpl).requireOwnedWorkspace(3001L, 1002L);
        verify(chatConversationRepository).findByCreatedByAndWorkspaceId(1002L, 3001L);
    }

    /**
     * 默认会话列表应合并默认云端空间与历史遗留未归属记录，避免本地工作空间会话混入云端历史。
     */
    @Test
    void listConversationsDefaultsToCloudHistoryAndLegacyUnassignedRecords() {
        WorkspaceDO cloudWorkspace = new WorkspaceDO();
        cloudWorkspace.setId(8001L);
        cloudWorkspace.setRuntimeTarget("cloud");
        cloudWorkspace.setName("云端历史记录");
        when(workspaceRepositoryImpl.findDefaultCloudWorkspaceByUserId(1002L)).thenReturn(Optional.of(cloudWorkspace));

        ChatConversation cloudConversation = ChatConversation.create(1L, "云端会话", 1002L, 8001L, ChatConversationStatus.ACTIVE);
        cloudConversation.restorePersistenceState(LocalDateTime.of(2026, 5, 18, 9, 0, 0), LocalDateTime.of(2026, 5, 18, 9, 0, 0));
        ChatConversation legacyConversation = ChatConversation.create(2L, "历史遗留会话", 1002L, ChatConversationStatus.ACTIVE);
        legacyConversation.restorePersistenceState(LocalDateTime.of(2026, 5, 18, 10, 0, 0), LocalDateTime.of(2026, 5, 18, 10, 0, 0));
        when(chatConversationRepository.findByCreatedByAndWorkspaceId(1002L, 8001L)).thenReturn(List.of(cloudConversation));
        when(chatConversationRepository.findByCreatedByAndWorkspaceId(1002L, null)).thenReturn(List.of(legacyConversation));

        List<ChatConversation> result = chatConversationApplicationService.listConversations(1002L, null);

        assertEquals(2, result.size());
        assertEquals(2L, result.getFirst().getId());
        assertEquals(1L, result.getLast().getId());
        verify(workspaceRepositoryImpl).findDefaultCloudWorkspaceByUserId(1002L);
        verify(chatConversationRepository).findByCreatedByAndWorkspaceId(1002L, 8001L);
        verify(chatConversationRepository).findByCreatedByAndWorkspaceId(1002L, null);
    }
}
