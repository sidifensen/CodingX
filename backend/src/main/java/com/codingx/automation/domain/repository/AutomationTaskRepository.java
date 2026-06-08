package com.codingx.automation.domain.repository;

import com.codingx.automation.domain.model.AutomationTask;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 自动化任务仓储端口，隔离应用服务与具体数据库实现。
 */
public interface AutomationTaskRepository {

    /**
     * 保存自动化任务，存在时更新，不存在时新增。
     * @param task 自动化任务领域对象。
     */
    void save(AutomationTask task);

    /**
     * 按主键查询任务。
     * @param taskId 任务主键。
     * @return 命中的任务。
     */
    Optional<AutomationTask> findById(Long taskId);

    /**
     * 查询当前用户未删除任务。
     * @param userId 用户标识。
     * @return 用户任务列表。
     */
    List<AutomationTask> findByUserId(Long userId);

    /**
     * 查询已启用且到期的任务。
     * @param now 当前调度时间。
     * @param limit 最大任务数。
     * @return 到期任务列表。
     */
    List<AutomationTask> findDueTasks(LocalDateTime now, int limit);

    /**
     * 仅当任务仍处于指定到期快照时写入触发结果，用于调度器并发扫描的原子认领。
     * @param task 已计算好的触发后任务快照。
     * @param previousNextRunAt 扫描时读取到的旧下一次运行时间。
     * @return 是否成功认领并写入。
     */
    boolean markTriggeredIfDue(AutomationTask task, LocalDateTime previousNextRunAt);
}
