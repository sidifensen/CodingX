package com.codingx.backend.task.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.backend.common.exception.NotFoundException;
import com.codingx.backend.task.domain.model.RuntimeType;
import com.codingx.backend.task.domain.model.Task;
import com.codingx.backend.task.domain.model.TaskStatus;
import com.codingx.backend.task.domain.repository.TaskRepository;
import com.codingx.backend.task.infrastructure.persistence.dataobject.TaskDO;
import com.codingx.backend.task.infrastructure.persistence.mapper.TaskMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Implements the persistence behavior required by TaskRepositoryImpl.
 */
@Repository
@RequiredArgsConstructor
public class TaskRepositoryImpl implements TaskRepository {

    /**
     * taskMapper value.
     */
    private final TaskMapper taskMapper;

    /**
     * Persists the state handled by save.
     * @param task input argument.
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
     * Finds the data required by findById.
     * @param taskId input argument.
     * @return processing result.
     */
    @Override
    public Optional<Task> findById(Long taskId) {
        return Optional.ofNullable(taskMapper.selectById(taskId)).map(this::toDomain);
    }

    /**
     * Resolves the required data for requireById or throws when it is missing.
     * @param taskId input argument.
     * @return processing result.
     */
    @Override
    public Task requireById(Long taskId) {
        return findById(taskId).orElseThrow(() -> new NotFoundException("Task not found"));
    }

    /**
     * Finds the data required by findByCreatedBy.
     * @param createdBy input argument.
     * @return processing result.
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
     * Executes the logic defined by toDomain.
     * @param dataObject input argument.
     * @return processing result.
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
     * Executes the logic defined by toDataObject.
     * @param task input argument.
     * @return processing result.
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
