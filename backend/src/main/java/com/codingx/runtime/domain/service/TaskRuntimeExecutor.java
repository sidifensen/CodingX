package com.codingx.runtime.domain.service;
import com.codingx.task.domain.model.Task;

/**
 * 任务运行时执行器契约，屏蔽本地、云端或 Mock 执行环境差异。
 */
public interface TaskRuntimeExecutor {

    /**
     * 执行已经启动的任务。
     * @param task 已完成归属校验并切换为 RUNNING 状态的任务聚合。
     */
    void execute(Task task);
}
