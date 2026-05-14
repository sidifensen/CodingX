package com.codingx.task.domain.repository;
import com.codingx.task.domain.model.Task;
import java.util.List;
import java.util.Optional;

/**
 * 定义 TaskRepository 的仓储契约。
 */
public interface TaskRepository {

    /**
     * 持久化 save 处理的状态。
     * @param task 输入参数。
     */
    void save(Task task);

    /**
     * 查询 findById 需要的数据。
     * @param taskId 输入参数。
     * @return 输入参数。
     */
    Optional<Task> findById(Long taskId);

    /**
     * 加载 requireById 所需数据，不存在时抛出异常。
     * @param taskId 输入参数。
     * @return 输入参数。
     */
    Task requireById(Long taskId);

    /**
     * 查询 findByCreatedBy 需要的数据。
     * @param createdBy 输入参数。
     * @return 输入参数。
     */
    List<Task> findByCreatedBy(Long createdBy);
}
