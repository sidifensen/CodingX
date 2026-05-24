package com.codingx.chat.application.service.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证会话管理应用服务的关键用户动作。
 */
@ExtendWith(MockitoExtension.class)
class ChatConversationApplicationServiceBehaviorTest {

    @Mock
    private ChatConversationRepository chatConversationRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceRepositoryImpl workspaceRepositoryImpl;

    @InjectMocks
    private ChatConversationApplicationService chatConversationApplicationService;

    @Test
    void pinConversationUpdatesPinnedFlagAndPersistsConversation() {
        ChatConversation conversation = ChatConversation.create(1L, "置顶测试", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);

        chatConversationApplicationService.updateConversationPinnedState(1L, true, 1002L);

        assertTrue(Boolean.TRUE.equals(conversation.getPinned()));
        verify(chatConversationRepository).save(conversation);
    }

    @Test
    void shareConversationGeneratesStableShareToken() {
        ChatConversation conversation = ChatConversation.create(1L, "分享测试", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);

        String shareToken = chatConversationApplicationService.generateShareToken(1L, 1002L);

        assertNotNull(shareToken);
        assertEquals(shareToken, conversation.getShareToken());
        verify(chatConversationRepository).save(conversation);
    }

    @Test
    void shareConversationLookupIsReadOnlyForPublicViewer() {
        ChatConversation conversation = ChatConversation.create(1L, "公开分享", 1002L, ChatConversationStatus.ACTIVE);
        conversation.restoreSharingState(false, "share-token");
        when(chatConversationRepository.findByShareToken("share-token")).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(List.of(
            ChatMessage.create(2L, 1L, ChatMessageRole.USER, "你好", ChatMessageStatus.COMPLETED, null, null, null),
            ChatMessage.create(3L, 1L, ChatMessageRole.ASSISTANT, "你好", ChatMessageStatus.COMPLETED, null, null, null)
        ));

        ChatConversation foundConversation = chatConversationApplicationService.requireSharedConversation("share-token");
        List<ChatMessage> messages = chatConversationApplicationService.listSharedMessages("share-token");

        assertEquals(1L, foundConversation.getId());
        assertEquals(2, messages.size());
        verify(chatConversationRepository, times(2)).findByShareToken("share-token");
        verify(chatMessageRepository).findByConversationId(1L);
    }

    @Test
    void shareConversationLookupReturnsNotFoundWhenTokenMissing() {
        when(chatConversationRepository.findByShareToken("missing-token")).thenReturn(Optional.empty());

        assertThrows(
            com.codingx.common.exception.NotFoundException.class,
            () -> chatConversationApplicationService.requireSharedConversation("missing-token")
        );
    }

    @Test
    void batchPinConversationAppliesToOwnedConversationsOnly() {
        ChatConversation first = ChatConversation.create(1L, "A", 1002L, ChatConversationStatus.ACTIVE);
        ChatConversation second = ChatConversation.create(2L, "B", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(first);
        when(chatConversationRepository.requireById(2L)).thenReturn(second);

        chatConversationApplicationService.batchUpdatePinnedState(List.of(1L, 2L), true, 1002L);

        assertTrue(Boolean.TRUE.equals(first.getPinned()));
        assertTrue(Boolean.TRUE.equals(second.getPinned()));
        verify(chatConversationRepository).save(first);
        verify(chatConversationRepository).save(second);
    }

    @Test
    void batchUpdateRejectsBlankConversationIds() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> chatConversationApplicationService.batchUpdatePinnedState(List.of(), true, 1002L)
        );

        assertEquals(ErrorMessageCatalog.CHAT_CONVERSATION_BATCH_IDS_REQUIRED, exception.getMessage());
    }

    @Test
    void listConversationsSortsPinnedBeforeUpdatedAt() {
        ChatConversation pinnedConversation = ChatConversation.create(1L, "Pinned", 1002L, ChatConversationStatus.ACTIVE);
        pinnedConversation.restorePersistenceState(LocalDateTime.of(2026, 5, 24, 10, 0, 0), LocalDateTime.of(2026, 5, 24, 10, 0, 0));
        pinnedConversation.restoreSharingState(true, null);
        ChatConversation normalConversation = ChatConversation.create(2L, "Normal", 1002L, ChatConversationStatus.ACTIVE);
        normalConversation.restorePersistenceState(LocalDateTime.of(2026, 5, 24, 11, 0, 0), LocalDateTime.of(2026, 5, 24, 11, 0, 0));
        when(workspaceRepositoryImpl.findDefaultCloudWorkspaceByUserId(1002L)).thenReturn(Optional.empty());
        when(chatConversationRepository.findByCreatedByAndWorkspaceId(1002L, null)).thenReturn(List.of(normalConversation, pinnedConversation));

        List<ChatConversation> result = chatConversationApplicationService.listConversations(1002L, null);

        assertEquals(2, result.size());
        assertEquals(1L, result.getFirst().getId());
    }
}
