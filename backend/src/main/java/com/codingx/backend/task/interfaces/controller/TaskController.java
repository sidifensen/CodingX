package com.codingx.backend.task.interfaces.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.backend.common.model.ApiResponse;
import com.codingx.backend.task.application.command.CreateTaskCommand;
import com.codingx.backend.task.application.command.StartTaskCommand;
import com.codingx.backend.task.application.service.TaskCommandApplicationService;
import com.codingx.backend.task.application.service.TaskQueryApplicationService;
import com.codingx.backend.task.domain.model.Task;
import com.codingx.backend.task.interfaces.request.CreateTaskRequest;
import com.codingx.backend.task.interfaces.response.TaskResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskCommandApplicationService taskCommandApplicationService;
    private final TaskQueryApplicationService taskQueryApplicationService;

    @PostMapping
    public ApiResponse<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        Task task = taskCommandApplicationService.createTask(
            new CreateTaskCommand(request.title(), request.description(), request.runtimeType(), request.workspaceId()),
            userId
        );
        return ApiResponse.success(toResponse(task));
    }

    @PostMapping("/{taskId}/start")
    public ApiResponse<Void> startTask(@PathVariable Long taskId) {
        taskCommandApplicationService.startTask(new StartTaskCommand(taskId, StpUtil.getLoginIdAsLong()));
        return ApiResponse.successMessage("task started");
    }

    @GetMapping
    public ApiResponse<List<TaskResponse>> listTasks() {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(taskQueryApplicationService.listTasks(userId).stream().map(this::toResponse).toList());
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskResponse> getTask(@PathVariable Long taskId) {
        return ApiResponse.success(toResponse(taskQueryApplicationService.getTask(taskId)));
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
            task.getId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus(),
            task.getRuntimeType(),
            task.getWorkspaceId(),
            task.getCreatedBy(),
            task.getSummary(),
            task.getErrorMessage()
        );
    }
}