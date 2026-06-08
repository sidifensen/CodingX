package com.codingx.automation.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.automation.application.service.AutomationTaskExecutionService;
import com.codingx.automation.application.service.AutomationTaskService;
import com.codingx.automation.domain.model.AutomationScheduleType;
import com.codingx.automation.domain.model.AutomationTask;
import com.codingx.automation.domain.model.AutomationTaskSourceType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证自动化调度入口会把扫描职责委派给应用服务，避免调度层重复实现业务规则。
 */
@ExtendWith(MockitoExtension.class)
class AutomationTaskSchedulerTest {

    /** 自动化任务服务，负责查找到期任务并推进下一次执行时间。 */
    @Mock
    private AutomationTaskService automationTaskService;

    /** 自动化任务执行服务，负责把已认领任务派发到聊天执行链路。 */
    @Mock
    private AutomationTaskExecutionService automationTaskExecutionService;

    /** 被测调度入口，仅负责定时触发服务扫描。 */
    @InjectMocks
    private AutomationTaskScheduler automationTaskScheduler;

    /**
     * 每次定时扫描都应携带当前时间调用服务层，由服务层保证到期任务幂等推进。
     */
    @Test
    void scanDueTasksShouldDelegateToAutomationTaskService() {
        when(automationTaskService.triggerDueTasks(any())).thenReturn(List.of());

        automationTaskScheduler.scanDueTasks();

        verify(automationTaskService).triggerDueTasks(any());
    }

    /**
     * 扫描到到期任务后，调度入口必须继续委派执行服务，不能只推进状态。
     */
    @Test
    void scanDueTasksShouldDispatchTriggeredTasksToExecutionService() {
        AutomationTask task = AutomationTask.builder()
            .id(1001L)
            .userId(2001L)
            .sourceType(AutomationTaskSourceType.CHAT)
            .sourceConversationId(3001L)
            .name("新闻推送")
            .prompt("推送 AI 新闻")
            .scheduleType(AutomationScheduleType.DAILY)
            .scheduleTime("12:00")
            .nextRunAt(LocalDateTime.of(2026, 6, 9, 12, 0))
            .lastRunStatus("TRIGGERED")
            .enabled(true)
            .deleted(false)
            .build();
        when(automationTaskService.triggerDueTasks(any())).thenReturn(List.of(task));

        automationTaskScheduler.scanDueTasks();

        verify(automationTaskExecutionService).executeTriggeredTasks(List.of(task));
    }
}
