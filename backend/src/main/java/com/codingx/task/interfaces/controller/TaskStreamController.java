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
 * 任务流控制器，负责建立任务 SSE 订阅。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskStreamController {

    /**
     * 任务 SSE 注册表，负责维护任务与浏览器连接的映射。
     */
    private final TaskSseRegistry taskSseRegistry;

    /**
     * 任务查询应用服务，用于在订阅前校验当前用户是否有权访问任务。
     */
    private final TaskQueryApplicationService taskQueryApplicationService;

    /**
     * 订阅指定任务的执行事件流。
     * @param taskId 任务标识。
     * @return SSE 连接。
     */
    @GetMapping("/{taskId}/stream")
    public SseEmitter stream(@PathVariable Long taskId) {
        // 步骤 1：注册任务流前先校验 owner，避免越权订阅他人任务执行过程。
        taskQueryApplicationService.getTask(taskId, StpUtil.getLoginIdAsLong());
        // 步骤 2：归属校验通过后再注册 SSE 连接，后续事件由任务运行时按 taskId 推送。
        return taskSseRegistry.register(taskId);
    }
}
