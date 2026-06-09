package com.codingx.automation.infrastructure.scheduler;

import com.codingx.automation.application.service.AutomationTaskExecutionService;
import com.codingx.automation.application.service.AutomationTaskService;
import com.codingx.automation.domain.model.AutomationTask;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 自动化任务定时扫描入口，定期查找到期任务并把状态推进委派给应用服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationTaskScheduler {

    /** 自动化任务应用服务，负责到期任务查询、幂等状态推进和下一次执行时间计算。 */
    private final AutomationTaskService automationTaskService;

    /** 自动化任务执行服务，负责把已认领任务交给聊天执行链路真正产出结果。 */
    private final AutomationTaskExecutionService automationTaskExecutionService;

    /**
     * 按固定间隔扫描到期任务；真实执行链路后续可从服务层返回的触发快照继续编排。
     */
    @Scheduled(fixedDelayString = "${automation.scheduler.fixed-delay-ms:60000}")
    public void scanDueTasks() {
        // 步骤 1：读取当前调度时间，保证同一批扫描使用一致时间戳。
        LocalDateTime now = LocalDateTime.now();
        // 步骤 2：委派服务层筛选并推进到期任务，调度层不重复实现归属、启用和删除判断。
        List<AutomationTask> triggeredTasks = automationTaskService.triggerDueTasks(now);
        // 步骤 3：仅在有任务触发时打印摘要日志，避免空扫描持续刷屏。
        if (!triggeredTasks.isEmpty()) {
            log.info("自动化调度器已触发到期任务: 数量={}, 扫描时间={}", triggeredTasks.size(), now);
            // 步骤 4：状态认领成功后继续派发真实执行，避免任务只显示触发但没有结果。
            automationTaskExecutionService.executeTriggeredTasks(triggeredTasks);
        }
    }
}
