package com.codingx.automation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.codingx.automation.domain.model.AutomationScheduleType;
import com.codingx.automation.domain.model.AutomationTask;
import com.codingx.automation.domain.model.AutomationTaskSourceType;
import com.codingx.automation.domain.repository.AutomationTaskRepository;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.application.service.chat.ChatStreamExecutionService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
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
 * 验证自动化任务执行服务会把已认领任务转换为后台聊天运行。
 */
@ExtendWith(MockitoExtension.class)
class AutomationTaskExecutionServiceTest {

    /** 自动化任务仓储，用于回写手动任务的交付会话和失败状态。 */
    @Mock
    private AutomationTaskRepository automationTaskRepository;

    /** 聊天会话仓储，用于校验来源会话归属或创建手动任务交付会话。 */
    @Mock
    private ChatConversationRepository chatConversationRepository;

    /** 聊天后台派发服务，负责真实模型、搜索和 SSE 交付链路。 */
    @Mock
    private ChatStreamExecutionService chatStreamExecutionService;

    /** 被测执行服务。 */
    @InjectMocks
    private AutomationTaskExecutionService automationTaskExecutionService;

    /**
     * 会话创建任务到期后应直接复用来源会话执行，结果由聊天链路写回该会话。
     */
    @Test
    void executeTriggeredTasksShouldDispatchChatSourceTaskToSourceConversation() {
        AutomationTask task = chatTask(1001L, 2001L, 3001L);
        ChatConversation conversation = ChatConversation.create(3001L, "自动化来源", 2001L, 9001L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.findById(3001L)).thenReturn(Optional.of(conversation));

        automationTaskExecutionService.executeTriggeredTasks(List.of(task));

        ArgumentCaptor<SendChatMessageCommand> commandCaptor = ArgumentCaptor.forClass(SendChatMessageCommand.class);
        verify(chatStreamExecutionService).dispatch(any(Long.class), commandCaptor.capture(), eq(2001L));
        SendChatMessageCommand command = commandCaptor.getValue();
        assertEquals(3001L, command.conversationId());
        assertTrue(command.content().contains("推送 AI 新闻"));
        assertTrue(command.content().contains("自动化任务"));
        assertTrue(command.attachmentIds().isEmpty());
        assertTrue(command.mcpCodes().isEmpty());
        assertTrue(command.skillCodes().isEmpty());
        assertTrue(!command.localOnly());
    }

    /**
     * 手动任务没有来源会话时应创建交付会话并写回任务，避免每次周期重复新建会话。
     */
    @Test
    void executeTriggeredTasksShouldCreateConversationForManualTaskWithoutSourceConversation() {
        AutomationTask task = manualTask(1002L, 2002L);

        automationTaskExecutionService.executeTriggeredTasks(List.of(task));

        ArgumentCaptor<ChatConversation> conversationCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chatConversationRepository).save(conversationCaptor.capture());
        ChatConversation savedConversation = conversationCaptor.getValue();
        assertEquals("自动化任务：AI 新闻", savedConversation.getTitle());
        assertEquals(2002L, savedConversation.getCreatedBy());

        ArgumentCaptor<AutomationTask> taskCaptor = ArgumentCaptor.forClass(AutomationTask.class);
        verify(automationTaskRepository).save(taskCaptor.capture());
        assertEquals(savedConversation.getId(), taskCaptor.getValue().getSourceConversationId());

        ArgumentCaptor<SendChatMessageCommand> commandCaptor = ArgumentCaptor.forClass(SendChatMessageCommand.class);
        verify(chatStreamExecutionService).dispatch(any(Long.class), commandCaptor.capture(), eq(2002L));
        assertEquals(savedConversation.getId(), commandCaptor.getValue().conversationId());
    }

    /**
     * 来源会话缺失或不属于任务用户时不能执行，避免把自动化结果写入错误会话。
     */
    @Test
    void executeTriggeredTasksShouldMarkFailedWhenSourceConversationIsForeign() {
        AutomationTask task = chatTask(1003L, 2003L, 3003L);
        ChatConversation foreignConversation = ChatConversation.create(3003L, "他人会话", 9999L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.findById(3003L)).thenReturn(Optional.of(foreignConversation));

        automationTaskExecutionService.executeTriggeredTasks(List.of(task));

        verify(chatStreamExecutionService, never()).dispatch(any(Long.class), any(SendChatMessageCommand.class), any(Long.class));
        ArgumentCaptor<AutomationTask> taskCaptor = ArgumentCaptor.forClass(AutomationTask.class);
        verify(automationTaskRepository).save(taskCaptor.capture());
        assertEquals("FAILED", taskCaptor.getValue().getLastRunStatus());
    }

    /**
     * 手动任务创建交付会话后即使派发失败，也必须保留会话回写，避免下一轮重复创建结果会话。
     */
    @Test
    void executeTriggeredTasksShouldKeepCreatedConversationWhenDispatchFails() {
        AutomationTask task = manualTask(1004L, 2004L);
        org.mockito.Mockito.doThrow(new IllegalStateException("派发失败"))
            .when(chatStreamExecutionService)
            .dispatch(any(Long.class), any(SendChatMessageCommand.class), eq(2004L));

        automationTaskExecutionService.executeTriggeredTasks(List.of(task));

        ArgumentCaptor<ChatConversation> conversationCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chatConversationRepository).save(conversationCaptor.capture());
        Long createdConversationId = conversationCaptor.getValue().getId();

        ArgumentCaptor<AutomationTask> taskCaptor = ArgumentCaptor.forClass(AutomationTask.class);
        verify(automationTaskRepository, times(2)).save(taskCaptor.capture());
        List<AutomationTask> savedTasks = taskCaptor.getAllValues();
        assertEquals(createdConversationId, savedTasks.get(0).getSourceConversationId());
        assertEquals(createdConversationId, savedTasks.get(1).getSourceConversationId());
        assertEquals("FAILED", savedTasks.get(1).getLastRunStatus());
    }

    private AutomationTask chatTask(Long taskId, Long userId, Long conversationId) {
        return AutomationTask.builder()
            .id(taskId)
            .userId(userId)
            .sourceType(AutomationTaskSourceType.CHAT)
            .sourceConversationId(conversationId)
            .name("AI 新闻")
            .prompt("推送 AI 新闻")
            .scheduleType(AutomationScheduleType.DAILY)
            .scheduleTime("12:00")
            .nextRunAt(LocalDateTime.of(2026, 6, 10, 12, 0))
            .lastRunAt(LocalDateTime.of(2026, 6, 9, 12, 0))
            .lastRunStatus("TRIGGERED")
            .enabled(true)
            .deleted(false)
            .build();
    }

    private AutomationTask manualTask(Long taskId, Long userId) {
        return chatTask(taskId, userId, null)
            .toBuilder()
            .sourceType(AutomationTaskSourceType.MANUAL)
            .sourceConversationId(null)
            .build();
    }
}
