package com.codingx.task.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import com.codingx.task.domain.model.RuntimeType;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.model.TaskStatus;
import com.codingx.task.domain.repository.TaskRepository;
import com.codingx.task.infrastructure.persistence.dataobject.TaskDO;
import com.codingx.task.infrastructure.persistence.mapper.TaskMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 任务仓储实现，负责领域对象和任务表数据对象互转。
 */
@Repository
@RequiredArgsConstructor
public class TaskRepositoryImpl implements TaskRepository {

    /**
     * 任务表 Mapper，用于执行任务数据的增删改查。
     */
    private final TaskMapper taskMapper;

    /**
     * 保存任务聚合当前状态。
     * @param task 待保存任务聚合。
     */
    @Override
    public void save(Task task) {
        // 步骤 1：先把领域对象映射为 MyBatis-Plus 数据对象，确保枚举和时间字段落库格式一致。
        TaskDO dataObject = toDataObject(task);
        // 步骤 2：按主键判断新增或更新，避免上层应用服务关心持久化细节。
        if (taskMapper.selectById(task.getId()) == null) {
            taskMapper.insert(dataObject);
        } else {
            taskMapper.updateById(dataObject);
        }
    }

    /**
     * 按任务主键查询任务聚合。
     * @param taskId 任务标识。
     * @return 任务聚合，未找到时为空。
     */
    @Override
    public Optional<Task> findById(Long taskId) {
        return Optional.ofNullable(taskMapper.selectById(taskId)).map(this::toDomain);
    }

    /**
     * 加载 requireById 所需数据，不存在时抛出异常。
     * @param taskId 任务标识。
     * @return 任务聚合。
     */
    @Override
    public Task requireById(Long taskId) {
        return findById(taskId).orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.TASK_NOT_FOUND));
    }

    /**
     * 查询指定创建人的未删除任务列表。
     * @param createdBy 创建人用户标识。
     * @return 按创建时间倒序排列的任务列表。
     */
    @Override
    public List<Task> findByCreatedBy(Long createdBy) {
        return taskMapper.selectList(new LambdaQueryWrapper<TaskDO>()
                .eq(TaskDO::getCreatedBy, createdBy)
                .eq(TaskDO::getDeleted, 0)
                .orderByDesc(TaskDO::getCreatedAt))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 将数据库记录恢复为任务领域对象。
     * @param dataObject 任务表记录。
     * @return 任务领域对象。
     */
    private Task toDomain(TaskDO dataObject) {
        // 步骤 1：先按持久化字段创建 CREATED 状态任务，复用领域构造校验。
        Task task = Task.create(
            dataObject.getId(),
            dataObject.getTitle(),
            dataObject.getDescription(),
            RuntimeType.valueOf(dataObject.getRuntimeType()),
            dataObject.getWorkspaceId(),
            dataObject.getCreatedBy()
        );
        // 步骤 2：根据持久化状态重放领域状态流转，恢复运行中、成功或失败语义。
        if (TaskStatus.RUNNING.name().equals(dataObject.getStatus())) {
            task.start();
        } else if (TaskStatus.SUCCEEDED.name().equals(dataObject.getStatus())) {
            task.start();
            task.complete(dataObject.getSummary());
        } else if (TaskStatus.FAILED.name().equals(dataObject.getStatus())) {
            task.start();
            task.fail(dataObject.getErrorMessage());
        }
        // 步骤 3：摘要可能在运行中被增量更新，最终按数据库字段覆盖领域摘要。
        task.updateSummary(dataObject.getSummary());
        return task;
    }

    /**
     * 将任务领域对象转换为任务表记录。
     * @param task 任务领域对象。
     * @return 任务表记录。
     */
    private TaskDO toDataObject(Task task) {
        // 步骤 1：复制任务基础字段和生命周期字段，枚举统一以 name 落库。
        TaskDO dataObject = new TaskDO();
        dataObject.setId(task.getId());
        dataObject.setTitle(task.getTitle());
        dataObject.setDescription(task.getDescription());
        dataObject.setStatus(task.getStatus().name());
        dataObject.setRuntimeType(task.getRuntimeType().name());
        dataObject.setWorkspaceId(task.getWorkspaceId());
        dataObject.setCreatedBy(task.getCreatedBy());
        dataObject.setStartedAt(task.getStartedAt());
        dataObject.setFinishedAt(task.getFinishedAt());
        dataObject.setErrorMessage(task.getErrorMessage());
        dataObject.setSummary(task.getSummary());
        // 步骤 2：任务保存默认保持未删除，删除语义不由当前命令服务处理。
        dataObject.setDeleted(0);
        return dataObject;
    }
}
