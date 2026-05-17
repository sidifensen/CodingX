package com.codingx.task.interfaces.controller;
import cn.dev33.satoken.stp.StpUtil;
import com.codingx.task.application.service.TaskQueryApplicationService;
import com.codingx.task.infrastructure.stream.TaskSseRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 负责处理 TaskStreamController 的 HTTP 请求并调用应用服务。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskStreamController {

    /**
     * TaskSseRegistry 依赖。
     */
    private final TaskSseRegistry taskSseRegistry;
    private final TaskQueryApplicationService taskQueryApplicationService;

    /**
     * 以流式方式处理 stream 的结果。
     * @param taskId 输入参数。
     * @return 输入参数。
     */
    @GetMapping("/{taskId}/stream")
    public SseEmitter stream(@PathVariable Long taskId) {
        // 步骤：注册任务流前先校验 owner，避免越权订阅他人任务执行过程。
        taskQueryApplicationService.getTask(taskId, StpUtil.getLoginIdAsLong());
        return taskSseRegistry.register(taskId);
    }
}
