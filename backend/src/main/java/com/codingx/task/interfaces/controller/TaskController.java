package com.codingx.task.interfaces.controller;
import cn.dev33.satoken.stp.StpUtil;
import com.codingx.common.model.ApiResponse;
import com.codingx.task.application.command.CreateTaskCommand;
import com.codingx.task.application.command.StartTaskCommand;
import com.codingx.task.application.service.TaskCommandApplicationService;
import com.codingx.task.application.service.TaskQueryApplicationService;
import com.codingx.task.domain.model.Task;
import com.codingx.task.interfaces.request.CreateTaskRequest;
import com.codingx.task.interfaces.response.TaskResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 负责处理 TaskController 的 HTTP 请求并调用应用服务。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    /**
     * TaskCommandApplicationService 依赖。
     */
    private final TaskCommandApplicationService taskCommandApplicationService;

    /**
     * TaskQueryApplicationService 依赖。
     */
    private final TaskQueryApplicationService taskQueryApplicationService;

    /**
     * 创建 createTask 所需数据并返回结果。
     * @param request 输入参数。
     * @return 输入参数。
     */
    @PostMapping
    public ApiResponse<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        Task task = taskCommandApplicationService.createTask(
            new CreateTaskCommand(request.title(), request.description(), request.runtimeType(), request.workspaceId(), request.skillCodes()),
            userId
        );
        return ApiResponse.success(toResponse(task));
    }

    /**
     * 启动 startTask 处理的流程。
     * @param taskId 输入参数。
     * @return 输入参数。
     */
    @PostMapping("/{taskId}/start")
    public ApiResponse<Void> startTask(@PathVariable Long taskId) {
        taskCommandApplicationService.startTask(new StartTaskCommand(taskId, StpUtil.getLoginIdAsLong()));
        return ApiResponse.successMessage("task started");
    }

    /**
     * 返回 listTasks 需要的结果集合。
     * @return 输入参数。
     */
    @GetMapping
    public ApiResponse<List<TaskResponse>> listTasks() {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(taskQueryApplicationService.listTasks(userId).stream().map(this::toResponse).toList());
    }

    /**
     * 获取 getTask 对应的结果。
     * @param taskId 输入参数。
     * @return 输入参数。
     */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskResponse> getTask(@PathVariable Long taskId) {
        return ApiResponse.success(toResponse(taskQueryApplicationService.getTask(taskId, StpUtil.getLoginIdAsLong())));
    }

    /**
     * 执行 toResponse 定义的处理逻辑。
     * @param task 输入参数。
     * @return 输入参数。
     */
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
