package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageFeedback;
import com.codingx.chat.domain.model.ChatMessageReference;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageFeedbackRepository;
import com.codingx.chat.domain.repository.ChatMessageReferenceRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.interfaces.response.AdminChatMessageFeedbackDetailResponse;
import com.codingx.chat.interfaces.response.AdminChatMessageFeedbackListItemResponse;
import com.codingx.chat.interfaces.response.AdminChatMessageReferenceResponse;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证管理端反馈查询服务的聚合行为。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatFeedbackServiceTest {

    @Mock
    private ChatMessageFeedbackRepository chatMessageFeedbackRepository;

    @Mock
    private ChatMessageReferenceRepository chatMessageReferenceRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatConversationRepository chatConversationRepository;

    @InjectMocks
    private AdminChatFeedbackService adminChatFeedbackService;

    /**
     * 分页查询应映射反馈列表字段。
     */
    @Test
    void pageFeedbackMapsPagedRows() {
        when(chatMessageFeedbackRepository.pageQuery(1, 10, "", null)).thenReturn(
            PageResult.<ChatMessageFeedback>builder()
                .records(List.of(ChatMessageFeedback.builder()
                    .id(9001L)
                    .messageId(102L)
                    .conversationId(2001L)
                    .userId(1002L)
                    .vote(1)
                    .reason("helpful")
                    .comment("good")
                    .createdAt(LocalDateTime.of(2026, 5, 17, 10, 0, 0))
                    .updatedAt(LocalDateTime.of(2026, 5, 17, 10, 1, 0))
                    .build()))
                .total(1L)
                .size(10L)
                .current(1L)
                .pages(1L)
                .build()
        );

        PageResult<AdminChatMessageFeedbackListItemResponse> result = adminChatFeedbackService.pageFeedback(1, 10, "", null);

        assertEquals(1L, result.total());
        assertEquals(1, result.records().size());
        assertEquals(9001L, result.records().getFirst().id());
        assertEquals(102L, result.records().getFirst().messageId());
        assertEquals(1, result.records().getFirst().vote());
    }

    /**
     * 详情查询应聚合反馈、消息与会话标题。
     */
    @Test
    void getFeedbackDetailAggregatesFeedbackAndMessage() {
        ChatMessageFeedback feedback = ChatMessageFeedback.builder()
            .id(9001L)
            .messageId(102L)
            .conversationId(2001L)
            .userId(1002L)
            .vote(1)
            .reason("helpful")
            .comment("good")
            .createdAt(LocalDateTime.of(2026, 5, 17, 10, 0, 0))
            .updatedAt(LocalDateTime.of(2026, 5, 17, 10, 1, 0))
            .build();
        ChatMessage message = ChatMessage.create(
            102L,
            2001L,
            ChatMessageRole.ASSISTANT,
            "请准备以下报销材料",
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        );
        ChatConversation conversation = ChatConversation.create(2001L, "差旅报销说明", 1002L, ChatConversationStatus.ACTIVE);

        when(chatMessageFeedbackRepository.findById(9001L)).thenReturn(Optional.of(feedback));
        when(chatMessageRepository.findById(102L)).thenReturn(Optional.of(message));
        when(chatConversationRepository.findById(2001L)).thenReturn(Optional.of(conversation));

        AdminChatMessageFeedbackDetailResponse result = adminChatFeedbackService.getFeedbackDetail(9001L);

        assertEquals(9001L, result.id());
        assertEquals(102L, result.messageId());
        assertEquals("ASSISTANT", result.messageRole());
        assertEquals("请准备以下报销材料", result.messageContent());
        assertEquals("差旅报销说明", result.conversationTitle());
    }

    /**
     * 反馈不存在时应抛出资源未找到异常。
     */
    @Test
    void getFeedbackDetailThrowsWhenFeedbackMissing() {
        when(chatMessageFeedbackRepository.findById(9001L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> adminChatFeedbackService.getFeedbackDetail(9001L));
    }

    /**
     * 引用查询应通过反馈消息 ID 回溯来源列表。
     */
    @Test
    void listReferencesByFeedbackMapsReferenceRows() {
        ChatMessageFeedback feedback = ChatMessageFeedback.builder()
            .id(9001L)
            .messageId(102L)
            .conversationId(2001L)
            .userId(1002L)
            .vote(1)
            .build();
        when(chatMessageFeedbackRepository.findById(9001L)).thenReturn(Optional.of(feedback));
        when(chatMessageReferenceRepository.findByMessageId(102L)).thenReturn(List.of(
            ChatMessageReference.builder()
                .id(7001L)
                .runId(5002L)
                .messageId(102L)
                .conversationId(2001L)
                .sourceType("web")
                .title("报销制度说明")
                .url("https://example.com/policy")
                .siteName("内部门户")
                .snippet("差旅报销需提供票据")
                .rankNo(1)
                .createdAt(LocalDateTime.of(2026, 5, 17, 9, 55, 0))
                .build()
        ));

        List<AdminChatMessageReferenceResponse> result = adminChatFeedbackService.listReferencesByFeedback(9001L);

        assertEquals(1, result.size());
        assertEquals(7001L, result.getFirst().id());
        assertEquals("报销制度说明", result.getFirst().title());
        assertEquals("https://example.com/policy", result.getFirst().url());
    }
}
