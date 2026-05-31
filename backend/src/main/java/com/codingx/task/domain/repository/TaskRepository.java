package com.codingx.task.domain.repository;
import com.codingx.task.domain.model.Task;
import java.util.List;
import java.util.Optional;

/**
 * 任务聚合仓储契约，屏蔽任务表读写细节。
 */
public interface TaskRepository {

    /**
     * 保存任务聚合的当前状态。
     * @param task 待保存任务聚合。
     */
    void save(Task task);

    /**
     * 按任务主键查询任务。
     * @param taskId 任务标识。
     * @return 任务聚合，未找到时为空。
     */
    Optional<Task> findById(Long taskId);

    /**
     * 加载 requireById 所需数据，不存在时抛出异常。
     * @param taskId 任务标识。
     * @return 任务聚合。
     */
    Task requireById(Long taskId);

    /**
     * 查询指定用户创建的任务列表。
     * @param createdBy 创建人用户标识。
     * @return 任务列表。
     */
    List<Task> findByCreatedBy(Long createdBy);
}
