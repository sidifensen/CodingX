package com.codingx.automation.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.automation.application.service.AutomationTaskService;
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
}
