package com.codingx.task.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codingx.task.domain.model.RuntimeType;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.model.TaskStatus;
import com.codingx.task.interfaces.response.TaskResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证任务视图服务的响应投影规则。
 */
class TaskViewServiceTest {

    private final TaskViewService taskViewService = new TaskViewService();

    /**
     * 任务响应应完整保留前端列表和详情页需要展示的核心字段。
     */
    @Test
    void toResponseProjectsTaskFields() {
        Task task = Task.create(5001L, "生成报告", "整理聊天执行结果", RuntimeType.MOCK, 3001L, 1002L, List.of("report"));
        task.start();
        task.complete("任务完成");

        TaskResponse response = taskViewService.toResponse(task);

        assertEquals(5001L, response.id());
        assertEquals("生成报告", response.title());
        assertEquals("整理聊天执行结果", response.description());
        assertEquals(TaskStatus.SUCCEEDED, response.status());
        assertEquals(RuntimeType.MOCK, response.runtimeType());
        assertEquals(3001L, response.workspaceId());
        assertEquals(1002L, response.createdBy());
        assertEquals("任务完成", response.summary());
    }

    /**
     * 列表转换应保持仓储返回顺序，避免 Controller 自行编排响应映射。
     */
    @Test
    void toResponsesKeepsTaskOrder() {
        Task first = Task.create(5001L, "任务一", null, RuntimeType.MOCK, null, 1002L);
        Task second = Task.create(5002L, "任务二", null, RuntimeType.MOCK, null, 1002L);

        List<TaskResponse> responses = taskViewService.toResponses(List.of(first, second));

        assertEquals(List.of(5001L, 5002L), responses.stream().map(TaskResponse::id).toList());
    }
}
