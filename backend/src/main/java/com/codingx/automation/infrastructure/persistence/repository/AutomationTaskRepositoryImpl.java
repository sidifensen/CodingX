package com.codingx.automation.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codingx.automation.domain.model.AutomationScheduleType;
import com.codingx.automation.domain.model.AutomationTask;
import com.codingx.automation.domain.model.AutomationTaskSourceType;
import com.codingx.automation.domain.repository.AutomationTaskRepository;
import com.codingx.automation.infrastructure.persistence.dataobject.AutomationTaskDO;
import com.codingx.automation.infrastructure.persistence.mapper.AutomationTaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 自动化任务仓储实现，集中处理领域对象与 automation_task 表之间的字段映射。
 */
@Repository
@RequiredArgsConstructor
public class AutomationTaskRepositoryImpl implements AutomationTaskRepository {

    /** 自动化任务 Mapper，用于读写任务配置表。 */
    private final AutomationTaskMapper automationTaskMapper;

    @Override
    public void save(AutomationTask task) {
        AutomationTaskDO dataObject = toDataObject(task);
        if (automationTaskMapper.selectById(task.getId()) == null) {
            automationTaskMapper.insert(dataObject);
            return;
        }
        automationTaskMapper.updateById(dataObject);
    }

    @Override
    public Optional<AutomationTask> findById(Long taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        AutomationTaskDO dataObject = automationTaskMapper.selectById(taskId);
        if (dataObject == null || Integer.valueOf(1).equals(dataObject.getDeleted())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(dataObject));
    }

    @Override
    public List<AutomationTask> findByUserId(Long userId) {
        return automationTaskMapper.selectList(new LambdaQueryWrapper<AutomationTaskDO>()
                .eq(AutomationTaskDO::getUserId, userId)
                .eq(AutomationTaskDO::getDeleted, 0)
                .orderByDesc(AutomationTaskDO::getEnabled)
                .orderByAsc(AutomationTaskDO::getNextRunAt)
                .orderByDesc(AutomationTaskDO::getUpdatedAt))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<AutomationTask> findDueTasks(LocalDateTime now, int limit) {
        return automationTaskMapper.selectList(new LambdaQueryWrapper<AutomationTaskDO>()
                .eq(AutomationTaskDO::getEnabled, 1)
                .eq(AutomationTaskDO::getDeleted, 0)
                .isNotNull(AutomationTaskDO::getNextRunAt)
                .le(AutomationTaskDO::getNextRunAt, now)
                .orderByAsc(AutomationTaskDO::getNextRunAt)
                .last("LIMIT " + Math.max(1, limit)))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public boolean markTriggeredIfDue(AutomationTask task, LocalDateTime previousNextRunAt) {
        if (task == null || task.getId() == null || previousNextRunAt == null) {
            return false;
        }
        // 步骤 1：以扫描时读取到的 next_run_at 作为乐观条件，避免重叠调度重复认领同一到期任务。
        LambdaUpdateWrapper<AutomationTaskDO> wrapper = new LambdaUpdateWrapper<AutomationTaskDO>()
            .eq(AutomationTaskDO::getId, task.getId())
            .eq(AutomationTaskDO::getEnabled, 1)
            .eq(AutomationTaskDO::getDeleted, 0)
            .eq(AutomationTaskDO::getNextRunAt, previousNextRunAt)
            .set(AutomationTaskDO::getLastRunAt, task.getLastRunAt())
            .set(AutomationTaskDO::getLastRunStatus, task.getLastRunStatus())
            .set(AutomationTaskDO::getNextRunAt, task.getNextRunAt())
            .set(AutomationTaskDO::getEnabled, task.isEnabled() ? 1 : 0)
            .set(AutomationTaskDO::getUpdatedAt, task.getUpdatedAt());
        return automationTaskMapper.update(null, wrapper) > 0;
    }

    /**
     * 将领域对象转换为数据库数据对象。
     */
    private AutomationTaskDO toDataObject(AutomationTask task) {
        AutomationTaskDO dataObject = new AutomationTaskDO();
        dataObject.setId(task.getId());
        dataObject.setUserId(task.getUserId());
        dataObject.setWorkspaceId(task.getWorkspaceId());
        dataObject.setSourceType(task.getSourceType() == null ? null : task.getSourceType().name());
        dataObject.setSourceConversationId(task.getSourceConversationId());
        dataObject.setName(task.getName());
        dataObject.setPrompt(task.getPrompt());
        dataObject.setScheduleType(task.getScheduleType() == null ? null : task.getScheduleType().name());
        dataObject.setScheduleTime(task.getScheduleTime());
        dataObject.setScheduleDayOfWeek(task.getScheduleDayOfWeek());
        dataObject.setOnceExecuteAt(task.getOnceExecuteAt());
        dataObject.setNextRunAt(task.getNextRunAt());
        dataObject.setLastRunAt(task.getLastRunAt());
        dataObject.setLastRunStatus(task.getLastRunStatus());
        dataObject.setEnabled(task.isEnabled() ? 1 : 0);
        dataObject.setCreatedAt(task.getCreatedAt());
        dataObject.setUpdatedAt(task.getUpdatedAt());
        dataObject.setDeleted(task.isDeleted() ? 1 : 0);
        return dataObject;
    }

    /**
     * 将数据库数据对象还原为领域对象。
     */
    private AutomationTask toDomain(AutomationTaskDO dataObject) {
        return AutomationTask.builder()
            .id(dataObject.getId())
            .userId(dataObject.getUserId())
            .workspaceId(dataObject.getWorkspaceId())
            .sourceType(AutomationTaskSourceType.valueOf(dataObject.getSourceType()))
            .sourceConversationId(dataObject.getSourceConversationId())
            .name(dataObject.getName())
            .prompt(dataObject.getPrompt())
            .scheduleType(AutomationScheduleType.valueOf(dataObject.getScheduleType()))
            .scheduleTime(dataObject.getScheduleTime())
            .scheduleDayOfWeek(dataObject.getScheduleDayOfWeek())
            .onceExecuteAt(dataObject.getOnceExecuteAt())
            .nextRunAt(dataObject.getNextRunAt())
            .lastRunAt(dataObject.getLastRunAt())
            .lastRunStatus(dataObject.getLastRunStatus())
            .enabled(Integer.valueOf(1).equals(dataObject.getEnabled()))
            .deleted(Integer.valueOf(1).equals(dataObject.getDeleted()))
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .build();
    }
}
