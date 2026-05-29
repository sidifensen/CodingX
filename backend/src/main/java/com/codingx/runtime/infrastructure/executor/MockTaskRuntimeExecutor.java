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
 * 负责执行 MockTaskRuntimeExecutor 的运行时行为。
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
     * TaskRepository 依赖。
     */
    private final TaskRepository taskRepository;

    /**
     * TaskStreamPublisher 依赖。
     */
    private final TaskStreamPublisher taskStreamPublisher;

    /**
     * 执行 execute 定义的处理逻辑。
     * @param task 输入参数。
     */
    @Override
    public void execute(Task task) {
        CompletableFuture.runAsync(() -> runTask(task));
    }

    /**
     * 执行 runTask 定义的处理逻辑。
     * @param task 输入参数。
     */
    private void runTask(Task task) {
        try {
            appendEvent(task, "task-status", "Task started", "Mock runtime started task execution");
            taskStreamPublisher.publishStatus(task.getId(), task.getStatus().name(), "Task started", "Mock runtime started task execution");
            pause();
            appendEvent(task, "task-log", "Planning", "Analyzing task input and preparing execution steps");
            taskStreamPublisher.publishLog(task.getId(), "Planning", "Analyzing task input and preparing execution steps");
            pause();
            appendEvent(task, "task-log", "Executing", "Running mock backend workflow");
            taskStreamPublisher.publishLog(task.getId(), "Executing", "Running mock backend workflow");
            pause();
            if (task.getTitle().toLowerCase().contains(MOCK_FAIL_KEYWORD.toLowerCase())) {
                task.fail("Mock runtime detected fail keyword in task title");
                taskRepository.save(task);
                appendEvent(task, "task-error", "Task failed", task.getErrorMessage());
                taskStreamPublisher.publishError(task.getId(), task.getErrorMessage());
                taskStreamPublisher.publishCompleted(task.getId(), task.getStatus().name());
                return;
            }
            String summary = "Mock runtime completed task successfully.";
            task.complete(summary);
            taskRepository.save(task);
            appendEvent(task, "task-summary", "Task summary", summary);
            taskStreamPublisher.publishSummary(task.getId(), summary);
            taskStreamPublisher.publishCompleted(task.getId(), task.getStatus().name());
        } catch (Exception exception) {
            task.fail(exception.getMessage());
            taskRepository.save(task);
            appendEvent(task, "task-error", "Task failed", exception.getMessage());
            taskStreamPublisher.publishError(task.getId(), exception.getMessage());
            taskStreamPublisher.publishCompleted(task.getId(), task.getStatus().name());
        }
    }

    /**
     * 执行 appendEvent 定义的处理逻辑。
     * @param task 输入参数。
     * @param eventType 输入参数。
     * @param title 输入参数。
     * @param content 输入参数。
     */
    private void appendEvent(Task task, String eventType, String title, String content) {
        // task_event 已删除；Mock 运行过程只通过 SSE 推给前端，避免本地联调写入旧过程表。
    }

    /**
     * 执行 pause 定义的处理逻辑。
     */
    private void pause() {
        ThreadUtil.safeSleep(MOCK_STEP_DELAY_MS);
    }
}
