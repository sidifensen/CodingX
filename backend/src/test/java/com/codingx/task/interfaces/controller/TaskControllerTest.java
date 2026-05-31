package com.codingx.task.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.model.ApiResponse;
import com.codingx.task.application.command.CreateTaskCommand;
import com.codingx.task.application.command.StartTaskCommand;
import com.codingx.task.application.service.TaskCommandApplicationService;
import com.codingx.task.application.service.TaskQueryApplicationService;
import com.codingx.task.application.service.TaskViewService;
import com.codingx.task.domain.model.RuntimeType;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.model.TaskStatus;
import com.codingx.task.interfaces.request.CreateTaskRequest;
import com.codingx.task.interfaces.response.TaskResponse;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证任务控制器只做 HTTP 协议适配并委托应用服务。
 */
@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private TaskCommandApplicationService taskCommandApplicationService;

    @Mock
    private TaskQueryApplicationService taskQueryApplicationService;

    @Mock
    private TaskViewService taskViewService;

    @InjectMocks
    private TaskController taskController;

    /**
     * Controller 不应直接持有仓储或持久化对象依赖，避免协议层编排业务查询。
     */
    @Test
    void controllerDoesNotDependOnRepositoriesOrPersistenceInfrastructure() {
        List<String> illegalFields = Arrays.stream(TaskController.class.getDeclaredFields())
            .filter(field -> {
                String typeName = field.getType().getName();
                return typeName.contains(".domain.repository.")
                    || typeName.contains(".infrastructure.repository.")
                    || typeName.contains(".infrastructure.persistence.");
            })
            .map(field -> field.getName() + ":" + field.getType().getName())
            .toList();

        assertEquals(List.of(), illegalFields);
    }

    /**
     * 创建任务接口应组装 command 并委托视图服务转换响应。
     */
    @Test
    void createTaskDelegatesToCommandAndViewServices() {
        Task task = Task.create(5001L, "生成报告", "整理聊天执行结果", RuntimeType.MOCK, 3001L, 1002L, List.of("report"));
        TaskResponse taskResponse = response(5001L, "生成报告");
        when(taskCommandApplicationService.createTask(
            new CreateTaskCommand("生成报告", "整理聊天执行结果", RuntimeType.MOCK, 3001L, List.of("report")),
            1002L
        )).thenReturn(task);
        when(taskViewService.toResponse(task)).thenReturn(taskResponse);
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<TaskResponse> apiResponse = taskController.createTask(
                new CreateTaskRequest("生成报告", "整理聊天执行结果", RuntimeType.MOCK, 3001L, List.of("report"))
            );

            assertEquals(taskResponse, apiResponse.data());
            verify(taskViewService).toResponse(task);
        }
    }

    /**
     * 列表接口应只传当前用户给查询服务，响应映射交给视图服务。
     */
    @Test
    void listTasksDelegatesToQueryAndViewServices() {
        List<Task> tasks = List.of(Task.create(5001L, "生成报告", null, RuntimeType.MOCK, null, 1002L));
        List<TaskResponse> responses = List.of(response(5001L, "生成报告"));
        when(taskQueryApplicationService.listTasks(1002L)).thenReturn(tasks);
        when(taskViewService.toResponses(tasks)).thenReturn(responses);
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<List<TaskResponse>> apiResponse = taskController.listTasks();

            assertEquals(responses, apiResponse.data());
            verify(taskViewService).toResponses(tasks);
        }
    }

    /**
     * 启动接口应转发任务标识和当前用户，不在 Controller 内修改任务状态。
     */
    @Test
    void startTaskDelegatesToCommandService() {
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<Void> response = taskController.startTask(5001L);

            assertEquals(true, response.success());
            assertEquals(ErrorMessageCatalog.TASK_STARTED, response.message());
            verify(taskCommandApplicationService).startTask(new StartTaskCommand(5001L, 1002L));
        }
    }

    private TaskResponse response(Long id, String title) {
        return new TaskResponse(id, title, null, TaskStatus.CREATED, RuntimeType.MOCK, null, 1002L, null, null);
    }
}
