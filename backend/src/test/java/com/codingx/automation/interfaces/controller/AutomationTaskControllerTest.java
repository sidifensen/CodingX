package com.codingx.automation.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.automation.application.service.AutomationTaskService;
import com.codingx.automation.domain.model.AutomationScheduleType;
import com.codingx.automation.domain.model.AutomationTask;
import com.codingx.automation.domain.model.AutomationTaskSourceType;
import com.codingx.automation.interfaces.request.AutomationTaskEnabledRequest;
import com.codingx.automation.interfaces.request.AutomationTaskCreateRequest;
import com.codingx.automation.interfaces.request.AutomationTaskUpdateRequest;
import com.codingx.common.model.ApiResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证用户端自动化任务控制器只做协议适配，业务创建与列表查询下沉到应用服务。
 */
@ExtendWith(MockitoExtension.class)
class AutomationTaskControllerTest {

    /** 自动化任务应用服务。 */
    @Mock
    private AutomationTaskService automationTaskService;

    /** 被测控制器。 */
    @InjectMocks
    private AutomationTaskController automationTaskController;

    /**
     * 创建接口应读取当前登录用户并返回手动来源的任务响应。
     */
    @Test
    void createTaskShouldDelegateToServiceWithLoginUser() {
        AutomationTask task = AutomationTask.builder()
            .id(7001L)
            .userId(1002L)
            .workspaceId(3001L)
            .sourceType(AutomationTaskSourceType.MANUAL)
            .name("每日项目总结")
            .prompt("总结项目状态")
            .scheduleType(AutomationScheduleType.DAILY)
            .scheduleTime("18:11")
            .nextRunAt(LocalDateTime.of(2026, 6, 8, 18, 11))
            .enabled(true)
            .deleted(false)
            .build();
        when(automationTaskService.parseScheduleType("DAILY")).thenReturn(AutomationScheduleType.DAILY);
        when(automationTaskService.createManualTask(
            org.mockito.ArgumentMatchers.eq(1002L),
            org.mockito.ArgumentMatchers.eq(3001L),
            org.mockito.ArgumentMatchers.eq("每日项目总结"),
            org.mockito.ArgumentMatchers.eq("总结项目状态"),
            org.mockito.ArgumentMatchers.eq(AutomationScheduleType.DAILY),
            org.mockito.ArgumentMatchers.eq("18:11"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(task);

        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<?> response = automationTaskController.createTask(new AutomationTaskCreateRequest(
                "每日项目总结",
                "总结项目状态",
                "DAILY",
                "18:11",
                null,
                null,
                3001L
            ));

            assertEquals(true, response.success());
            assertEquals("OK", response.code());
            verify(automationTaskService).createManualTask(
                org.mockito.ArgumentMatchers.eq(1002L),
                org.mockito.ArgumentMatchers.eq(3001L),
                org.mockito.ArgumentMatchers.eq("每日项目总结"),
                org.mockito.ArgumentMatchers.eq("总结项目状态"),
                org.mockito.ArgumentMatchers.eq(AutomationScheduleType.DAILY),
                org.mockito.ArgumentMatchers.eq("18:11"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()
            );
        }
    }

    /**
     * 列表接口只应按当前登录用户查询任务。
     */
    @Test
    void listTasksShouldUseLoginUserScope() {
        when(automationTaskService.listTasks(1002L)).thenReturn(List.of());
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<?> response = automationTaskController.listTasks();

            assertEquals(true, response.success());
            verify(automationTaskService).listTasks(1002L);
        }
    }

    /**
     * 编辑接口应读取登录用户、路径任务 ID 和请求字段，并把计划类型解析交给服务层。
     */
    @Test
    void updateTaskShouldDelegateToServiceWithLoginUserAndTaskId() {
        AutomationTask task = AutomationTask.builder()
            .id(7001L)
            .userId(1002L)
            .workspaceId(null)
            .sourceType(AutomationTaskSourceType.MANUAL)
            .name("AI 新闻")
            .prompt("推送 AI 新闻摘要")
            .scheduleType(AutomationScheduleType.WEEKLY)
            .scheduleTime("12:00")
            .scheduleDayOfWeek(3)
            .nextRunAt(LocalDateTime.of(2026, 6, 10, 12, 0))
            .enabled(true)
            .deleted(false)
            .build();
        when(automationTaskService.parseScheduleType("WEEKLY")).thenReturn(AutomationScheduleType.WEEKLY);
        when(automationTaskService.updateTask(
            org.mockito.ArgumentMatchers.eq(1002L),
            org.mockito.ArgumentMatchers.eq(7001L),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("AI 新闻"),
            org.mockito.ArgumentMatchers.eq("推送 AI 新闻摘要"),
            org.mockito.ArgumentMatchers.eq(AutomationScheduleType.WEEKLY),
            org.mockito.ArgumentMatchers.eq("12:00"),
            org.mockito.ArgumentMatchers.eq(3),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(task);

        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<?> response = automationTaskController.updateTask(7001L, new AutomationTaskUpdateRequest(
                "AI 新闻",
                "推送 AI 新闻摘要",
                "WEEKLY",
                "12:00",
                3,
                null,
                null
            ));

            assertEquals(true, response.success());
            verify(automationTaskService).updateTask(
                org.mockito.ArgumentMatchers.eq(1002L),
                org.mockito.ArgumentMatchers.eq(7001L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("AI 新闻"),
                org.mockito.ArgumentMatchers.eq("推送 AI 新闻摘要"),
                org.mockito.ArgumentMatchers.eq(AutomationScheduleType.WEEKLY),
                org.mockito.ArgumentMatchers.eq("12:00"),
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()
            );
        }
    }

    /**
     * 启停接口只接收 enabled 状态，由服务层负责归属校验和下一次运行时间计算。
     */
    @Test
    void updateTaskEnabledShouldDelegateToServiceWithLoginUser() {
        AutomationTask task = AutomationTask.builder()
            .id(7001L)
            .userId(1002L)
            .sourceType(AutomationTaskSourceType.MANUAL)
            .name("AI 新闻")
            .prompt("推送 AI 新闻摘要")
            .scheduleType(AutomationScheduleType.DAILY)
            .scheduleTime("12:00")
            .enabled(false)
            .deleted(false)
            .build();
        when(automationTaskService.updateTaskEnabled(
            org.mockito.ArgumentMatchers.eq(1002L),
            org.mockito.ArgumentMatchers.eq(7001L),
            org.mockito.ArgumentMatchers.eq(false),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(task);

        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<?> response = automationTaskController.updateTaskEnabled(
                7001L,
                new AutomationTaskEnabledRequest(false)
            );

            assertEquals(true, response.success());
            verify(automationTaskService).updateTaskEnabled(
                org.mockito.ArgumentMatchers.eq(1002L),
                org.mockito.ArgumentMatchers.eq(7001L),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.any()
            );
        }
    }

    /**
     * 删除接口只暴露逻辑删除语义，不返回任务快照，避免前端误用已删除数据。
     */
    @Test
    void deleteTaskShouldDelegateToServiceWithLoginUser() {
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<?> response = automationTaskController.deleteTask(7001L);

            assertEquals(true, response.success());
            verify(automationTaskService).deleteTask(
                org.mockito.ArgumentMatchers.eq(1002L),
                org.mockito.ArgumentMatchers.eq(7001L),
                org.mockito.ArgumentMatchers.any()
            );
        }
    }
}
