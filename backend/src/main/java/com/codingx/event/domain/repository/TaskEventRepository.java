package com.codingx.event.domain.repository;
import com.codingx.event.domain.model.TaskEvent;
import java.util.List;

/**
 * 定义 TaskEventRepository 的仓储契约。
 */
public interface TaskEventRepository {

    /**
     * 持久化 save 处理的状态。
     * @param taskEvent 输入参数。
     */
    void save(TaskEvent taskEvent);

    /**
     * 查询 findByTaskId 需要的数据。
     * @param taskId 输入参数。
     * @return 输入参数。
     */
    List<TaskEvent> findByTaskId(Long taskId);

    /**
     * 执行 nextSequence 定义的处理逻辑。
     * @param taskId 输入参数。
     * @return 输入参数。
     */
    long nextSequence(Long taskId);
}
