package com.codingx.runtime.domain.service;
import com.codingx.task.domain.model.Task;

/**
 * 定义 TaskRuntimeExecutor 的领域服务契约。
 */
public interface TaskRuntimeExecutor {

    /**
     * 执行 execute 定义的处理逻辑。
     * @param task 输入参数。
     */
    void execute(Task task);
}
