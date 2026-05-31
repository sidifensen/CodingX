package com.codingx.task.interfaces.controller;
import cn.dev33.satoken.stp.StpUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.model.ApiResponse;
import com.codingx.task.application.command.CreateTaskCommand;
import com.codingx.task.application.command.StartTaskCommand;
import com.codingx.task.application.service.TaskCommandApplicationService;
import com.codingx.task.application.service.TaskQueryApplicationService;
import com.codingx.task.application.service.TaskViewService;
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
 * 任务 HTTP 控制器，只负责协议适配和应用服务调用。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    /**
     * 任务命令应用服务，承接创建和启动等会修改任务状态的业务入口。
     */
    private final TaskCommandApplicationService taskCommandApplicationService;

    /**
     * 任务查询应用服务，承接任务列表与详情的归属校验和读取。
     */
    private final TaskQueryApplicationService taskQueryApplicationService;

    /**
     * 任务视图服务，负责把领域对象转换为接口响应，避免 Controller 手写字段映射。
     */
    private final TaskViewService taskViewService;

    /**
     * 创建任务并返回前端展示模型。
     * @param request 创建任务请求。
     * @return 新任务响应。
     */
    @PostMapping
    public ApiResponse<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        // 步骤 1：读取当前登录用户并把 HTTP 请求体转换为应用层 command。
        Long userId = StpUtil.getLoginIdAsLong();
        Task task = taskCommandApplicationService.createTask(
            new CreateTaskCommand(request.title(), request.description(), request.runtimeType(), request.workspaceId(), request.skillCodes()),
            userId
        );
        // 步骤 2：由视图服务投影响应，Controller 不关心任务字段映射细节。
        return ApiResponse.success(taskViewService.toResponse(task));
    }

    /**
     * 启动指定任务。
     * @param taskId 任务标识。
     * @return 启动结果。
     */
    @PostMapping("/{taskId}/start")
    public ApiResponse<Void> startTask(@PathVariable Long taskId) {
        // 步骤 1：把路径任务标识和当前用户标识交给命令服务做归属校验和状态流转。
        taskCommandApplicationService.startTask(new StartTaskCommand(taskId, StpUtil.getLoginIdAsLong()));
        // 步骤 2：启动成功后只返回统一中文提示，任务进度由 SSE 接口继续推送。
        return ApiResponse.successMessage(ErrorMessageCatalog.TASK_STARTED);
    }

    /**
     * 查询当前用户创建的任务列表。
     * @return 任务响应列表。
     */
    @GetMapping
    public ApiResponse<List<TaskResponse>> listTasks() {
        // 步骤 1：查询服务按当前用户过滤任务，避免 Controller 直接接触仓储条件。
        Long userId = StpUtil.getLoginIdAsLong();
        List<Task> tasks = taskQueryApplicationService.listTasks(userId);
        // 步骤 2：视图服务保持查询顺序并转换响应字段。
        return ApiResponse.success(taskViewService.toResponses(tasks));
    }

    /**
     * 查询指定任务详情。
     * @param taskId 任务标识。
     * @return 任务响应。
     */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskResponse> getTask(@PathVariable Long taskId) {
        // 步骤 1：查询服务负责校验当前用户是否有权访问任务。
        Task task = taskQueryApplicationService.getTask(taskId, StpUtil.getLoginIdAsLong());
        // 步骤 2：详情响应仍统一走视图服务，避免列表和详情字段映射分叉。
        return ApiResponse.success(taskViewService.toResponse(task));
    }
}
