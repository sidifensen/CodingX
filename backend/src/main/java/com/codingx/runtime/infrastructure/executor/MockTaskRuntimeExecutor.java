package com.codingx.runtime.infrastructure.executor;
import cn.hutool.core.thread.ThreadUtil;
import com.codingx.runtime.domain.service.TaskRuntimeExecutor;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.repository.TaskRepository;
import com.codingx.task.domain.service.TaskStreamPublisher;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Mock 任务运行时执行器，用于本地联调任务状态、SSE 推送和失败分支。
 */
@Component
@Primary
@RequiredArgsConstructor
public class MockTaskRuntimeExecutor implements TaskRuntimeExecutor {

    /**
     * Mock 执行器仅用于本地联调，固定阶段间隔即可，不再暴露环境变量以免污染部署配置。
     */
    private static final long MOCK_STEP_DELAY_MS = 300L;

    /**
     * 任务标题命中该关键字时，直接走失败分支，便于手工验证失败态与通知链路。
     */
    private static final String MOCK_FAIL_KEYWORD = "fail";

    /**
     * 任务仓储，用于回写 Mock 执行后的成功或失败终态。
     */
    private final TaskRepository taskRepository;

    /**
     * 任务流发布器，用于把 Mock 执行过程推送给前端 SSE 订阅者。
     */
    private final TaskStreamPublisher taskStreamPublisher;

    /**
     * 异步启动 Mock 任务执行。
     * @param task 已切换为 RUNNING 状态的任务聚合。
     */
    @Override
    public void execute(Task task) {
        // 步骤 1：Mock 执行不阻塞接口线程，任务过程由后台线程通过 SSE 推送。
        CompletableFuture.runAsync(() -> runTask(task));
    }

    /**
     * 执行 Mock 任务主流程，并按阶段推送状态、日志、摘要或错误事件。
     * @param task 当前运行中的任务聚合。
     */
    private void runTask(Task task) {
        try {
            // 步骤 1：推送任务开始状态，模拟真实运行时接收到任务。
            appendEvent(task, "task-status", "Task started", "Mock runtime started task execution");
            taskStreamPublisher.publishStatus(task.getId(), task.getStatus().name(), "Task started", "Mock runtime started task execution");
            pause();
            // 步骤 2：推送规划日志，给前端进度流提供中间过程。
            appendEvent(task, "task-log", "Planning", "Analyzing task input and preparing execution steps");
            taskStreamPublisher.publishLog(task.getId(), "Planning", "Analyzing task input and preparing execution steps");
            pause();
            // 步骤 3：推送执行日志，模拟后端工作流进入执行阶段。
            appendEvent(task, "task-log", "Executing", "Running mock backend workflow");
            taskStreamPublisher.publishLog(task.getId(), "Executing", "Running mock backend workflow");
            pause();
            // 步骤 4：标题命中 fail 关键字时走失败分支，便于验证错误态和完成事件。
            if (task.getTitle().toLowerCase().contains(MOCK_FAIL_KEYWORD.toLowerCase())) {
                task.fail("Mock runtime detected fail keyword in task title");
                taskRepository.save(task);
                appendEvent(task, "task-error", "Task failed", task.getErrorMessage());
                taskStreamPublisher.publishError(task.getId(), task.getErrorMessage());
                taskStreamPublisher.publishCompleted(task.getId(), task.getStatus().name());
                return;
            }
            // 步骤 5：正常分支写入成功摘要并推送摘要、完成事件。
            String summary = "Mock runtime completed task successfully.";
            task.complete(summary);
            taskRepository.save(task);
            appendEvent(task, "task-summary", "Task summary", summary);
            taskStreamPublisher.publishSummary(task.getId(), summary);
            taskStreamPublisher.publishCompleted(task.getId(), task.getStatus().name());
        } catch (Exception exception) {
            // 步骤 6：任何未预期异常都转换为任务失败终态，并通过 SSE 通知前端。
            task.fail(exception.getMessage());
            taskRepository.save(task);
            appendEvent(task, "task-error", "Task failed", exception.getMessage());
            taskStreamPublisher.publishError(task.getId(), exception.getMessage());
            taskStreamPublisher.publishCompleted(task.getId(), task.getStatus().name());
        }
    }

    /**
     * 兼容旧任务事件表的扩展点。
     * @param task 当前任务聚合。
     * @param eventType 事件类型。
     * @param title 事件标题。
     * @param content 事件正文。
     */
    private void appendEvent(Task task, String eventType, String title, String content) {
        // task_event 已删除；Mock 运行过程只通过 SSE 推给前端，避免本地联调写入旧过程表。
    }

    /**
     * 暂停一小段时间，模拟任务阶段之间的真实耗时。
     */
    private void pause() {
        // 步骤 1：使用 Hutool 安全睡眠，避免手写 InterruptedException 样板代码。
        ThreadUtil.safeSleep(MOCK_STEP_DELAY_MS);
    }
}
