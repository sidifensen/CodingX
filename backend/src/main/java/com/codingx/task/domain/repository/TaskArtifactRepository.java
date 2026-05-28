package com.codingx.task.domain.repository;
import com.codingx.task.domain.model.TaskArtifact;
import java.util.List;

/**
 * 定义任务产物的仓储契约。
 */
public interface TaskArtifactRepository {

    /**
     * 持久化 save 处理的状态。
     * @param taskArtifact 输入参数。
     */
    void save(TaskArtifact taskArtifact);

    /**
     * 查询 findByTaskId 需要的数据。
     * @param taskId 输入参数。
     * @return 输入参数。
     */
    List<TaskArtifact> findByTaskId(Long taskId);
}
