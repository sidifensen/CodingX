package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.codingx.admin.application.service.AdminChatConversationService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.interfaces.response.AdminChatConversationListItemResponse;
import com.codingx.chat.interfaces.response.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证管理端会话服务的分页与详情聚合逻辑。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatConversationServiceTest {

    @Mock
    private ChatConversationRepository chatConversationRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatAttachmentService chatAttachmentService;

    @InjectMocks
    private AdminChatConversationService adminChatConversationService;

    /**
     * 分页查询应映射会话列表字段，并返回稳定分页结构。
     */
    @Test
    void pageConversationsMapsRows() {
        ChatConversation conversation = ChatConversation.create(2001L, "差旅报销说明", 1002L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 16, 10, 2, 0), 5001L);
        conversation.restorePersistenceState(LocalDateTime.of(2026, 5, 16, 10, 0, 0), LocalDateTime.of(2026, 5, 16, 10, 5, 0));
        when(chatConversationRepository.findAll("报销")).thenReturn(List.of(conversation));

        PageResult<AdminChatConversationListItemResponse> result = adminChatConversationService.pageConversations(1, 10, "报销");

        assertEquals(1L, result.total());
        assertEquals(1, result.records().size());
        assertEquals(2001L, result.records().getFirst().id());
        assertEquals("活跃", result.records().getFirst().statusLabel());
    }

    /**
     * 详情查询应聚合会话与消息列表。
     */
    @Test
    void getConversationDetailAggregatesMessages() {
        ChatConversation conversation = ChatConversation.create(2001L, "差旅报销说明", 1002L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 16, 10, 2, 0), 5001L);
        conversation.restorePersistenceState(LocalDateTime.of(2026, 5, 16, 10, 0, 0), LocalDateTime.of(2026, 5, 16, 10, 5, 0));
        ChatMessage message = ChatMessage.create(
            3001L,
            2001L,
            ChatMessageRole.USER,
            "怎么报销？",
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        );
        when(chatConversationRepository.findById(2001L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationId(2001L)).thenReturn(List.of(message));
        when(chatAttachmentService.listByMessageId(3001L)).thenReturn(List.of());

        var result = adminChatConversationService.getConversationDetail(2001L);

        assertEquals(2001L, result.id());
        assertEquals(1, result.messages().size());
        assertEquals("怎么报销？", result.messages().getFirst().content());
    }
}
