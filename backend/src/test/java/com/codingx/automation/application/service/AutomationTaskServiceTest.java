package com.codingx.automation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.automation.domain.model.AutomationScheduleType;
import com.codingx.automation.domain.model.AutomationTask;
import com.codingx.automation.domain.model.AutomationTaskSourceType;
import com.codingx.automation.domain.repository.AutomationTaskRepository;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.workspace.domain.model.AdminWorkspacePage;
import com.codingx.workspace.domain.model.AdminWorkspaceQuery;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 验证自动化定时任务应用服务的创建、归属过滤、计划校验和到期触发行为。
 */
class AutomationTaskServiceTest {

    /**
     * 手动创建任务时应补齐当前用户、默认启用状态、来源类型和下一次执行时间。
     */
    @Test
    void createManualTaskShouldPersistOwnerAndNextRunAt() {
        InMemoryAutomationTaskRepository repository = new InMemoryAutomationTaskRepository();
        AutomationTaskService service = new AutomationTaskService(repository, new InMemoryWorkspaceRepository(Set.of(2001L)));
        LocalDateTime now = LocalDateTime.of(2026, 6, 8, 17, 30);

        AutomationTask task = service.createManualTask(
            1001L,
            2001L,
            "每日项目总结",
            "总结项目状态并提醒风险",
            AutomationScheduleType.DAILY,
            "18:11",
            null,
            null,
            now
        );

        assertNotNull(task.getId());
        assertEquals(1001L, task.getUserId());
        assertEquals(2001L, task.getWorkspaceId());
        assertEquals(AutomationTaskSourceType.MANUAL, task.getSourceType());
        assertEquals("每日项目总结", task.getName());
        assertEquals("总结项目状态并提醒风险", task.getPrompt());
        assertTrue(task.isEnabled());
        assertEquals(LocalDateTime.of(2026, 6, 8, 18, 11), task.getNextRunAt());
    }

    /**
     * 手动创建任务时必须校验工作空间属于当前用户，避免用户把自动化绑定到他人工作空间。
     */
    @Test
    void createManualTaskShouldRejectForeignWorkspace() {
        AutomationTaskService service = new AutomationTaskService(
            new InMemoryAutomationTaskRepository(),
            new InMemoryWorkspaceRepository(Set.of(2001L))
        );
        LocalDateTime now = LocalDateTime.of(2026, 6, 8, 17, 30);

        NotFoundException exception = assertThrows(
            NotFoundException.class,
            () -> service.createManualTask(
                1001L,
                3001L,
                "越权任务",
                "总结他人项目",
                AutomationScheduleType.DAILY,
                "18:11",
                null,
                null,
                now
            )
        );

        assertEquals("NOT_FOUND", exception.getCode());
        assertEquals("工作空间不存在", exception.getMessage());
    }

    /**
     * 列表只返回当前用户未删除任务，并按启用状态和下一次执行时间排序。
     */
    @Test
    void listTasksShouldReturnOnlyCurrentUsersActiveTasks() {
        InMemoryAutomationTaskRepository repository = new InMemoryAutomationTaskRepository();
        AutomationTaskService service = new AutomationTaskService(repository, allowAnyWorkspace());
        LocalDateTime now = LocalDateTime.of(2026, 6, 8, 12, 0);
        AutomationTask later = service.createManualTask(1001L, null, "稍后任务", "稍后执行", AutomationScheduleType.DAILY, "20:00", null, null, now);
        AutomationTask earlier = service.createManualTask(1001L, null, "较早任务", "较早执行", AutomationScheduleType.DAILY, "18:00", null, null, now);
        service.createManualTask(2002L, null, "他人任务", "不可见", AutomationScheduleType.DAILY, "17:00", null, null, now);
        repository.save(later.toBuilder().deleted(true).build());

        List<AutomationTask> tasks = service.listTasks(1001L);

        assertEquals(1, tasks.size());
        assertEquals(earlier.getId(), tasks.get(0).getId());
        assertFalse(tasks.stream().anyMatch(task -> "他人任务".equals(task.getName())));
    }

    /**
     * 时间格式非法时必须抛出中文业务异常，避免脏计划进入调度器。
     */
    @Test
    void createManualTaskShouldRejectInvalidScheduleTime() {
        AutomationTaskService service = new AutomationTaskService(new InMemoryAutomationTaskRepository(), allowAnyWorkspace());
        LocalDateTime now = LocalDateTime.of(2026, 6, 8, 12, 0);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.createManualTask(1001L, null, "坏时间", "执行", AutomationScheduleType.DAILY, "25:99", null, null, now)
        );

        assertEquals("AUTOMATION_SCHEDULE_TIME_INVALID", exception.getCode());
        assertEquals("执行时间格式不正确", exception.getMessage());
    }

    /**
     * 一次性任务必须有明确的未来执行时间，不能因 scheduleTime 缺失走到空指针。
     */
    @Test
    void createManualOnceTaskShouldRejectMissingExecuteTime() {
        AutomationTaskService service = new AutomationTaskService(new InMemoryAutomationTaskRepository(), allowAnyWorkspace());
        LocalDateTime now = LocalDateTime.of(2026, 6, 8, 12, 0);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.createManualTask(1001L, null, "一次性提醒", "提醒我复盘", AutomationScheduleType.ONCE, null, null, null, now)
        );

        assertEquals("AUTOMATION_ONCE_TIME_REQUIRED", exception.getCode());
        assertEquals("一次性执行时间不能为空", exception.getMessage());
    }

    /**
     * 到期任务触发后应写入最近触发状态，并把每日任务的下一次执行时间推进到未来。
     */
    @Test
    void triggerDueTasksShouldMarkTriggeredAndAdvanceNextRunAt() {
        InMemoryAutomationTaskRepository repository = new InMemoryAutomationTaskRepository();
        AutomationTaskService service = new AutomationTaskService(repository, allowAnyWorkspace());
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 7, 12, 0);
        AutomationTask task = service.createManualTask(1001L, null, "到期任务", "执行", AutomationScheduleType.DAILY, "18:11", null, null, createdAt);
        repository.save(task.toBuilder().nextRunAt(LocalDateTime.of(2026, 6, 8, 18, 11)).build());

        List<AutomationTask> triggeredTasks = service.triggerDueTasks(LocalDateTime.of(2026, 6, 8, 18, 12));

        assertEquals(1, triggeredTasks.size());
        AutomationTask updatedTask = repository.findById(task.getId()).orElseThrow();
        assertEquals("TRIGGERED", updatedTask.getLastRunStatus());
        assertEquals(LocalDateTime.of(2026, 6, 8, 18, 12), updatedTask.getLastRunAt());
        assertEquals(LocalDateTime.of(2026, 6, 9, 18, 11), updatedTask.getNextRunAt());
    }

    /**
     * 调度器拿到重复到期快照时，应依赖仓储原子条件只认领一次，避免并发扫描重复触发同一任务。
     */
    @Test
    void triggerDueTasksShouldClaimSameDueTaskOnlyOnce() {
        DuplicatedDueTaskRepository repository = new DuplicatedDueTaskRepository();
        AutomationTaskService service = new AutomationTaskService(repository, allowAnyWorkspace());
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 7, 12, 0);
        AutomationTask task = service.createManualTask(1001L, null, "并发任务", "执行", AutomationScheduleType.DAILY, "18:11", null, null, createdAt);
        repository.save(task.toBuilder().nextRunAt(LocalDateTime.of(2026, 6, 8, 18, 11)).build());

        List<AutomationTask> triggeredTasks = service.triggerDueTasks(LocalDateTime.of(2026, 6, 8, 18, 12));

        assertEquals(1, triggeredTasks.size());
        assertEquals(1, repository.getSuccessfulClaimCount());
    }

    /**
     * 编辑当前用户自己的任务时，应复用创建校验并按新计划重新计算下一次运行时间。
     */
    @Test
    void updateTaskShouldModifyOwnedTaskAndRecalculateNextRunAt() {
        InMemoryAutomationTaskRepository repository = new InMemoryAutomationTaskRepository();
        AutomationTaskService service = new AutomationTaskService(repository, allowAnyWorkspace());
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 8, 12, 0);
        AutomationTask task = service.createManualTask(1001L, null, "旧任务", "旧需求", AutomationScheduleType.DAILY, "18:00", null, null, createdAt);

        AutomationTask updatedTask = service.updateTask(
            1001L,
            task.getId(),
            null,
            "AI 新闻",
            "推送今天 AI 新闻摘要",
            AutomationScheduleType.WEEKLY,
            "12:00",
            3,
            null,
            LocalDateTime.of(2026, 6, 9, 16, 0)
        );

        assertEquals(task.getId(), updatedTask.getId());
        assertEquals(AutomationTaskSourceType.MANUAL, updatedTask.getSourceType());
        assertEquals("AI 新闻", updatedTask.getName());
        assertEquals("推送今天 AI 新闻摘要", updatedTask.getPrompt());
        assertEquals(AutomationScheduleType.WEEKLY, updatedTask.getScheduleType());
        assertEquals(Integer.valueOf(3), updatedTask.getScheduleDayOfWeek());
        assertEquals(LocalDateTime.of(2026, 6, 10, 12, 0), updatedTask.getNextRunAt());
        assertTrue(updatedTask.isEnabled());
    }

    /**
     * 编辑任务必须按用户归属校验，不能通过任务 ID 修改其他用户的自动化配置。
     */
    @Test
    void updateTaskShouldRejectForeignTask() {
        InMemoryAutomationTaskRepository repository = new InMemoryAutomationTaskRepository();
        AutomationTaskService service = new AutomationTaskService(repository, allowAnyWorkspace());
        AutomationTask foreignTask = service.createManualTask(
            2002L,
            null,
            "他人任务",
            "不可修改",
            AutomationScheduleType.DAILY,
            "18:00",
            null,
            null,
            LocalDateTime.of(2026, 6, 8, 12, 0)
        );

        NotFoundException exception = assertThrows(
            NotFoundException.class,
            () -> service.updateTask(
                1001L,
                foreignTask.getId(),
                null,
                "越权修改",
                "不应该成功",
                AutomationScheduleType.DAILY,
                "19:00",
                null,
                null,
                LocalDateTime.of(2026, 6, 8, 13, 0)
            )
        );

        assertEquals("NOT_FOUND", exception.getCode());
        assertEquals("自动化任务不存在", exception.getMessage());
    }

    /**
     * 关闭任务时应停止后续调度，重新开启时按当前时间重新计算下一次运行时间。
     */
    @Test
    void updateTaskEnabledShouldPauseAndResumeWithNextRunAt() {
        InMemoryAutomationTaskRepository repository = new InMemoryAutomationTaskRepository();
        AutomationTaskService service = new AutomationTaskService(repository, allowAnyWorkspace());
        AutomationTask task = service.createManualTask(
            1001L,
            null,
            "每日任务",
            "执行",
            AutomationScheduleType.DAILY,
            "18:00",
            null,
            null,
            LocalDateTime.of(2026, 6, 8, 12, 0)
        );

        AutomationTask disabledTask = service.updateTaskEnabled(
            1001L,
            task.getId(),
            false,
            LocalDateTime.of(2026, 6, 8, 13, 0)
        );
        AutomationTask enabledTask = service.updateTaskEnabled(
            1001L,
            task.getId(),
            true,
            LocalDateTime.of(2026, 6, 8, 19, 0)
        );

        assertFalse(disabledTask.isEnabled());
        assertEquals(null, disabledTask.getNextRunAt());
        assertTrue(enabledTask.isEnabled());
        assertEquals(LocalDateTime.of(2026, 6, 9, 18, 0), enabledTask.getNextRunAt());
    }

    /**
     * 删除任务应写入逻辑删除状态，列表和后续单任务变更都不能再看到该任务。
     */
    @Test
    void deleteTaskShouldSoftDeleteAndHideFromList() {
        InMemoryAutomationTaskRepository repository = new InMemoryAutomationTaskRepository();
        AutomationTaskService service = new AutomationTaskService(repository, allowAnyWorkspace());
        AutomationTask task = service.createManualTask(
            1001L,
            null,
            "待删除任务",
            "执行",
            AutomationScheduleType.DAILY,
            "18:00",
            null,
            null,
            LocalDateTime.of(2026, 6, 8, 12, 0)
        );

        service.deleteTask(1001L, task.getId(), LocalDateTime.of(2026, 6, 8, 13, 0));

        assertTrue(service.listTasks(1001L).isEmpty());
        assertTrue(repository.findById(task.getId()).isEmpty());
        assertThrows(
            NotFoundException.class,
            () -> service.updateTaskEnabled(1001L, task.getId(), true, LocalDateTime.of(2026, 6, 8, 14, 0))
        );
    }

    /**
     * 测试用内存仓储，只模拟服务需要的持久化行为，避免单元测试依赖数据库。
     */
    private static class InMemoryAutomationTaskRepository implements AutomationTaskRepository {

        /**
         * 当前测试内保存的任务集合。
         */
        private final List<AutomationTask> tasks = new ArrayList<>();

        @Override
        public void save(AutomationTask task) {
            tasks.removeIf(existing -> existing.getId().equals(task.getId()));
            tasks.add(task);
        }

        @Override
        public Optional<AutomationTask> findById(Long taskId) {
            return tasks.stream()
                .filter(task -> task.getId().equals(taskId))
                .filter(task -> !task.isDeleted())
                .findFirst();
        }

        @Override
        public List<AutomationTask> findByUserId(Long userId) {
            return tasks.stream()
                .filter(task -> task.getUserId().equals(userId))
                .filter(task -> !task.isDeleted())
                .sorted(Comparator
                    .comparing((AutomationTask task) -> !task.isEnabled())
                    .thenComparing(AutomationTask::getNextRunAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(AutomationTask::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        }

        @Override
        public List<AutomationTask> findDueTasks(LocalDateTime now, int limit) {
            return tasks.stream()
                .filter(AutomationTask::isEnabled)
                .filter(task -> !task.isDeleted())
                .filter(task -> task.getNextRunAt() != null && !task.getNextRunAt().isAfter(now))
                .limit(limit)
                .toList();
        }

        @Override
        public boolean markTriggeredIfDue(AutomationTask task, LocalDateTime previousNextRunAt) {
            Optional<AutomationTask> currentTask = findById(task.getId());
            if (currentTask.isEmpty() || !previousNextRunAt.equals(currentTask.get().getNextRunAt())) {
                return false;
            }
            save(task);
            return true;
        }
    }

    /**
     * 默认测试不关心工作空间时使用的放行仓储，避免每个用例重复声明归属集合。
     */
    private static WorkspaceRepository allowAnyWorkspace() {
        return new InMemoryWorkspaceRepository(Set.of());
    }

    /**
     * 模拟两个调度线程读到同一到期任务快照的仓储，只有第一次原子认领可以成功。
     */
    private static class DuplicatedDueTaskRepository extends InMemoryAutomationTaskRepository {

        /** 成功认领同一到期任务的次数，用于断言幂等保护是否生效。 */
        private int successfulClaimCount;

        @Override
        public List<AutomationTask> findDueTasks(LocalDateTime now, int limit) {
            List<AutomationTask> dueTasks = super.findDueTasks(now, limit);
            if (dueTasks.isEmpty()) {
                return List.of();
            }
            return List.of(dueTasks.get(0), dueTasks.get(0));
        }

        @Override
        public boolean markTriggeredIfDue(AutomationTask task, LocalDateTime previousNextRunAt) {
            boolean claimed = super.markTriggeredIfDue(task, previousNextRunAt);
            if (claimed) {
                successfulClaimCount++;
            }
            return claimed;
        }

        int getSuccessfulClaimCount() {
            return successfulClaimCount;
        }
    }

    /**
     * 测试用工作空间仓储，只保留用户可访问工作空间集合，模拟归属校验。
     */
    private record InMemoryWorkspaceRepository(Set<Long> ownedWorkspaceIds) implements WorkspaceRepository {

        @Override
        public void ensureExists(Long workspaceId) {
            ensureOwnedByUser(workspaceId, 1001L);
        }

        @Override
        public void ensureOwnedByUser(Long workspaceId, Long userId) {
            if (workspaceId != null && !ownedWorkspaceIds.contains(workspaceId)) {
                throw new NotFoundException("工作空间不存在");
            }
        }

        @Override
        public AdminWorkspacePage pageForAdmin(AdminWorkspaceQuery query) {
            return new AdminWorkspacePage(List.of(), 0L, 10L, 1L, 1L);
        }
    }
}
