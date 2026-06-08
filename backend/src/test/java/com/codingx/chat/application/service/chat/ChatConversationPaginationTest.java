package com.codingx.chat.application.service.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.interfaces.response.CursorPageResponse;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证聊天会话与消息历史的 cursor 分页编排，避免前端一次性加载全量历史。
 */
@ExtendWith(MockitoExtension.class)
class ChatConversationPaginationTest {

    /**
     * 会话仓储端口，分页测试通过 mock 验证应用服务只请求一页加一条。
     */
    @Mock
    private ChatConversationRepository chatConversationRepository;

    /**
     * 消息仓储端口，分页测试通过 mock 验证归属校验后才查询消息。
     */
    @Mock
    private ChatMessageRepository chatMessageRepository;

    /**
     * 工作空间仓储端口，当前分页逻辑不直接依赖但构造服务需要。
     */
    @Mock
    private WorkspaceRepository workspaceRepository;

    /**
     * 工作空间仓储实现，用于校验显式工作空间归属和默认云端空间。
     */
    @Mock
    private WorkspaceRepositoryImpl workspaceRepositoryImpl;

    /**
     * 被测会话应用服务。
     */
    @InjectMocks
    private ChatConversationApplicationService chatConversationApplicationService;

    @Test
    void pageConversationsReturnsLimitedItemsAndNextCursor() {
        LocalDateTime cursorTime = LocalDateTime.of(2026, 6, 8, 10, 0);
        ChatConversation first = conversation(1L, cursorTime.plusMinutes(3));
        ChatConversation second = conversation(2L, cursorTime.plusMinutes(2));
        ChatConversation extra = conversation(3L, cursorTime.plusMinutes(1));
        when(chatConversationRepository.findPageByCreatedByAndWorkspaceId(1001L, 2001L, 3, null, null, null))
            .thenReturn(List.of(first, second, extra));

        CursorPageResponse<ChatConversation> page =
            chatConversationApplicationService.pageConversations(1001L, 2001L, 2, null, null);

        assertEquals(List.of(first, second), page.items());
        assertTrue(page.hasMore());
        assertEquals(second.getUpdatedAt(), page.nextCursor().cursorUpdatedAt());
        assertEquals(second.getId(), page.nextCursor().cursorId());
        verify(workspaceRepositoryImpl).requireOwnedWorkspace(2001L, 1001L);
    }

    @Test
    void pageMessagesReturnsRecentPageAscendingAndNextCursor() {
        ChatConversation conversation = conversation(10L, LocalDateTime.of(2026, 6, 8, 10, 0));
        LocalDateTime base = LocalDateTime.of(2026, 6, 8, 11, 0);
        ChatMessage newest = message(5L, base.plusMinutes(5));
        ChatMessage second = message(4L, base.plusMinutes(4));
        ChatMessage extra = message(3L, base.plusMinutes(3));
        when(chatConversationRepository.requireById(10L)).thenReturn(conversation);
        when(chatMessageRepository.findRecentPageByConversationId(10L, 3, null, null))
            .thenReturn(List.of(newest, second, extra));

        CursorPageResponse<ChatMessage> page =
            chatConversationApplicationService.pageMessages(10L, 1001L, 2, null, null);

        assertEquals(List.of(second, newest), page.items());
        assertTrue(page.hasMore());
        assertEquals(second.getCreatedAt(), page.nextCursor().cursorCreatedAt());
        assertEquals(second.getId(), page.nextCursor().cursorId());
        verify(chatMessageRepository).findRecentPageByConversationId(eq(10L), eq(3), eq(null), eq(null));
    }

    @Test
    void pageMessagesReturnsEmptyCursorWhenNoMoreData() {
        ChatConversation conversation = conversation(10L, LocalDateTime.of(2026, 6, 8, 10, 0));
        ChatMessage only = message(5L, LocalDateTime.of(2026, 6, 8, 11, 5));
        when(chatConversationRepository.requireById(10L)).thenReturn(conversation);
        when(chatMessageRepository.findRecentPageByConversationId(10L, 21, null, null)).thenReturn(List.of(only));

        CursorPageResponse<ChatMessage> page =
            chatConversationApplicationService.pageMessages(10L, 1001L, 20, null, null);

        assertEquals(List.of(only), page.items());
        assertEquals(false, page.hasMore());
        assertNull(page.nextCursor());
    }

    private ChatConversation conversation(Long id, LocalDateTime updatedAt) {
        ChatConversation conversation = ChatConversation.create(id, "会话" + id, 1001L, 2001L, ChatConversationStatus.ACTIVE);
        conversation.restorePersistenceState(updatedAt.minusHours(1), updatedAt);
        return conversation;
    }

    private ChatMessage message(Long id, LocalDateTime createdAt) {
        ChatMessage message = ChatMessage.create(id, 10L, ChatMessageRole.USER, "消息" + id, ChatMessageStatus.COMPLETED, null, null, null);
        message.restoreRuntimeState(null, null, null, createdAt, createdAt);
        return message;
    }
}
