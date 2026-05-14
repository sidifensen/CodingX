package com.codingx.task.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * 实现 TaskRepositoryImpl 的持久化行为。
 */
@Repository
@RequiredArgsConstructor
public class TaskRepositoryImpl implements TaskRepository {

    /**
     * TaskMapper 依赖。
     */
    private final TaskMapper taskMapper;

    /**
     * 持久化 save 处理的状态。
     * @param task 输入参数。
     */
    @Override
    public void save(Task task) {
        TaskDO dataObject = toDataObject(task);
        if (taskMapper.selectById(task.getId()) == null) {
            taskMapper.insert(dataObject);
        } else {
            taskMapper.updateById(dataObject);
        }
    }

    /**
     * 查询 findById 需要的数据。
     * @param taskId 输入参数。
     * @return 输入参数。
     */
    @Override
    public Optional<Task> findById(Long taskId) {
        return Optional.ofNullable(taskMapper.selectById(taskId)).map(this::toDomain);
    }

    /**
     * 加载 requireById 所需数据，不存在时抛出异常。
     * @param taskId 输入参数。
     * @return 输入参数。
     */
    @Override
    public Task requireById(Long taskId) {
        return findById(taskId).orElseThrow(() -> new NotFoundException("Task not found"));
    }

    /**
     * 查询 findByCreatedBy 需要的数据。
     * @param createdBy 输入参数。
     * @return 输入参数。
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
     * 执行 toDomain 定义的处理逻辑。
     * @param dataObject 输入参数。
     * @return 输入参数。
     */
    private Task toDomain(TaskDO dataObject) {
        Task task = Task.create(
            dataObject.getId(),
            dataObject.getTitle(),
            dataObject.getDescription(),
            RuntimeType.valueOf(dataObject.getRuntimeType()),
            dataObject.getWorkspaceId(),
            dataObject.getCreatedBy()
        );
        if (TaskStatus.RUNNING.name().equals(dataObject.getStatus())) {
            task.start();
        } else if (TaskStatus.SUCCEEDED.name().equals(dataObject.getStatus())) {
            task.start();
            task.complete(dataObject.getSummary());
        } else if (TaskStatus.FAILED.name().equals(dataObject.getStatus())) {
            task.start();
            task.fail(dataObject.getErrorMessage());
        }
        task.updateSummary(dataObject.getSummary());
        return task;
    }

    /**
     * 执行 toDataObject 定义的处理逻辑。
     * @param task 输入参数。
     * @return 输入参数。
     */
    private TaskDO toDataObject(Task task) {
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
        dataObject.setDeleted(0);
        return dataObject;
    }
}
