package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.codingx.admin.application.service.AdminChatConversationService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatGoal;
import com.codingx.chat.domain.model.ChatGoalStatus;
import com.codingx.chat.domain.model.ChatGoalStep;
import com.codingx.chat.domain.model.ChatGoalStepStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatGoalRepository;
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

    @Mock
    private ChatGoalRepository chatGoalRepository;

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
        message.restoreRuntimeState(
            6001L,
            "用户消息没有思考内容",
            0,
            LocalDateTime.of(2026, 5, 16, 10, 2, 0),
            LocalDateTime.of(2026, 5, 16, 10, 3, 0)
        );
        when(chatConversationRepository.findById(2001L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationId(2001L)).thenReturn(List.of(message));
        when(chatAttachmentService.listByMessageId(3001L)).thenReturn(List.of());

        var result = adminChatConversationService.getConversationDetail(2001L);

        assertEquals(2001L, result.id());
        assertEquals(1, result.messages().size());
        assertEquals("怎么报销？", result.messages().getFirst().content());
        assertEquals(6001L, result.messages().getFirst().runId());
        assertEquals(0, result.messages().getFirst().deleted());
        assertEquals(LocalDateTime.of(2026, 5, 16, 10, 3, 0), result.messages().getFirst().updatedAt());
    }

    /**
     * 详情查询应额外聚合目标主表、步骤快照和事件流水，供管理端只读排障。
     */
    @Test
    void getConversationDetailAggregatesGoalTables() {
        ChatConversation conversation = ChatConversation.create(2001L, "目标模式排查", 1002L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 6, 9, 10, 2, 0), 5001L);
        conversation.restorePersistenceState(LocalDateTime.of(2026, 6, 9, 10, 0, 0), LocalDateTime.of(2026, 6, 9, 10, 5, 0));
        ChatGoal firstGoal = ChatGoal.builder()
            .id(7001L)
            .conversationId(2001L)
            .userId(1002L)
            .goalKey("default")
            .title("修复聊天目标展示")
            .description("管理端展示目标三表")
            .status(ChatGoalStatus.ACTIVE)
            .progressSummary("已进入实现阶段")
            .createdRunId(5001L)
            .updatedRunId(5002L)
            .createdAt(LocalDateTime.of(2026, 6, 9, 10, 1, 0))
            .updatedAt(LocalDateTime.of(2026, 6, 9, 10, 4, 0))
            .completedAt(null)
            .deleted(0)
            .build();
        ChatGoal completedGoal = ChatGoal.builder()
            .id(7002L)
            .conversationId(2001L)
            .userId(1002L)
            .goalKey("previous")
            .title("完成历史目标")
            .description("历史终态目标也需要展示")
            .status(ChatGoalStatus.COMPLETED)
            .progressSummary("已完成")
            .createdRunId(4001L)
            .updatedRunId(4002L)
            .createdAt(LocalDateTime.of(2026, 6, 9, 9, 1, 0))
            .updatedAt(LocalDateTime.of(2026, 6, 9, 9, 4, 0))
            .completedAt(LocalDateTime.of(2026, 6, 9, 9, 4, 0))
            .deleted(0)
            .build();
        ChatGoalStep step = ChatGoalStep.builder()
            .id(8001L)
            .goalId(7001L)
            .stepKey("step-1")
            .title("补充管理端测试")
            .status(ChatGoalStepStatus.COMPLETED)
            .sortNo(0)
            .detail("先证明目标三表还未返回")
            .startedAt(LocalDateTime.of(2026, 6, 9, 10, 2, 0))
            .completedAt(LocalDateTime.of(2026, 6, 9, 10, 3, 0))
            .updatedAt(LocalDateTime.of(2026, 6, 9, 10, 3, 0))
            .deleted(0)
            .build();
        ChatGoalRepository.ChatGoalEventRecord event = new ChatGoalRepository.ChatGoalEventRecord(
            9001L,
            7001L,
            2001L,
            5002L,
            "GOAL_UPDATED",
            "{\"goal\":{\"title\":\"修复聊天目标展示\"}}",
            LocalDateTime.of(2026, 6, 9, 10, 4, 0)
        );
        when(chatConversationRepository.findById(2001L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationId(2001L)).thenReturn(List.of());
        when(chatGoalRepository.findAllByConversationId(2001L)).thenReturn(List.of(firstGoal, completedGoal));
        when(chatGoalRepository.findStepsByGoalIds(List.of(7001L, 7002L))).thenReturn(List.of(step));
        when(chatGoalRepository.findEventsByConversationId(2001L)).thenReturn(List.of(event));

        var result = adminChatConversationService.getConversationDetail(2001L);

        assertEquals(2, result.goals().size());
        assertEquals("7001", result.goals().getFirst().id());
        assertEquals("1002", result.goals().getFirst().userId());
        assertEquals("修复聊天目标展示", result.goals().getFirst().title());
        assertEquals("ACTIVE", result.goals().getFirst().status());
        assertEquals("8001", result.goals().getFirst().steps().getFirst().id());
        assertEquals("补充管理端测试", result.goals().getFirst().steps().getFirst().title());
        assertEquals("9001", result.goals().getFirst().events().getFirst().id());
        assertEquals("GOAL_UPDATED", result.goals().getFirst().events().getFirst().eventType());
        assertEquals("{\"goal\":{\"title\":\"修复聊天目标展示\"}}", result.goals().getFirst().events().getFirst().payloadJson());
        assertEquals(0, result.goals().get(1).steps().size());
        assertEquals(0, result.goals().get(1).events().size());
    }
}
