package com.codingx.automation.interfaces.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.automation.application.service.AutomationTaskService;
import com.codingx.automation.domain.model.AutomationTask;
import com.codingx.automation.interfaces.request.AutomationTaskCreateRequest;
import com.codingx.automation.interfaces.request.AutomationTaskEnabledRequest;
import com.codingx.automation.interfaces.request.AutomationTaskUpdateRequest;
import com.codingx.automation.interfaces.response.AutomationTaskResponse;
import com.codingx.common.model.ApiResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端自动化任务控制器，只负责协议适配、登录态读取和统一响应封装。
 */
@RestController
@RequestMapping("/api/automation/tasks")
@RequiredArgsConstructor
public class AutomationTaskController {

    /** 自动化任务应用服务，负责业务校验、归属隔离和持久化。 */
    private final AutomationTaskService automationTaskService;

    /**
     * 查询当前用户自动化任务列表。
     * @return 自动化任务响应列表。
     */
    @GetMapping
    public ApiResponse<List<AutomationTaskResponse>> listTasks() {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(automationTaskService.listTasks(userId).stream()
            .map(this::toResponse)
            .toList());
    }

    /**
     * 手动创建自动化任务。
     * @param request 创建请求。
     * @return 创建后的任务响应。
     */
    @PostMapping
    public ApiResponse<AutomationTaskResponse> createTask(@RequestBody AutomationTaskCreateRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        AutomationTask task = automationTaskService.createManualTask(
            userId,
            request.workspaceId(),
            request.name(),
            request.prompt(),
            automationTaskService.parseScheduleType(request.scheduleType()),
            request.scheduleTime(),
            request.scheduleDayOfWeek(),
            request.onceExecuteAt(),
            LocalDateTime.now()
        );
        return ApiResponse.success(toResponse(task));
    }

    /**
     * 编辑当前用户自己的自动化任务。
     * @param taskId 路径中的任务主键。
     * @param request 编辑请求。
     * @return 更新后的任务响应。
     */
    @PutMapping("/{taskId}")
    public ApiResponse<AutomationTaskResponse> updateTask(
        @PathVariable Long taskId,
        @RequestBody AutomationTaskUpdateRequest request
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        AutomationTask task = automationTaskService.updateTask(
            userId,
            taskId,
            request.workspaceId(),
            request.name(),
            request.prompt(),
            automationTaskService.parseScheduleType(request.scheduleType()),
            request.scheduleTime(),
            request.scheduleDayOfWeek(),
            request.onceExecuteAt(),
            LocalDateTime.now()
        );
        return ApiResponse.success(toResponse(task));
    }

    /**
     * 启用或停用当前用户自己的自动化任务。
     * @param taskId 路径中的任务主键。
     * @param request 启停请求。
     * @return 更新后的任务响应。
     */
    @PatchMapping("/{taskId}/enabled")
    public ApiResponse<AutomationTaskResponse> updateTaskEnabled(
        @PathVariable Long taskId,
        @RequestBody AutomationTaskEnabledRequest request
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        AutomationTask task = automationTaskService.updateTaskEnabled(
            userId,
            taskId,
            Boolean.TRUE.equals(request.enabled()),
            LocalDateTime.now()
        );
        return ApiResponse.success(toResponse(task));
    }

    /**
     * 逻辑删除当前用户自己的自动化任务。
     * @param taskId 路径中的任务主键。
     * @return 无数据成功响应。
     */
    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> deleteTask(@PathVariable Long taskId) {
        Long userId = StpUtil.getLoginIdAsLong();
        automationTaskService.deleteTask(userId, taskId, LocalDateTime.now());
        return ApiResponse.successMessage("自动化任务已删除");
    }

    /**
     * 将领域任务投影为前端响应。
     */
    private AutomationTaskResponse toResponse(AutomationTask task) {
        return new AutomationTaskResponse(
            task.getId(),
            task.getName(),
            task.getPrompt(),
            task.getSourceType() == null ? null : task.getSourceType().name(),
            task.getSourceConversationId(),
            task.getScheduleType() == null ? null : task.getScheduleType().name(),
            task.getScheduleTime(),
            task.getScheduleDayOfWeek(),
            task.getOnceExecuteAt(),
            task.getNextRunAt(),
            task.getLastRunAt(),
            task.getLastRunStatus(),
            task.isEnabled(),
            task.getWorkspaceId()
        );
    }
}
