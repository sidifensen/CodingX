package com.codingx.task.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.task.application.service.TaskQueryApplicationService;
import com.codingx.task.domain.model.RuntimeType;
import com.codingx.task.domain.model.Task;
import com.codingx.task.infrastructure.stream.TaskSseRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 验证任务 SSE 订阅入口在注册前会校验任务 owner。
 */
@ExtendWith(MockitoExtension.class)
class TaskStreamControllerTest {

    @Mock
    private TaskSseRegistry taskSseRegistry;

    @Mock
    private TaskQueryApplicationService taskQueryApplicationService;

    @InjectMocks
    private TaskStreamController taskStreamController;

    /**
     * 订阅任务流时应先校验当前用户是否有权访问该任务。
     */
    @Test
    void streamChecksTaskOwnershipBeforeRegister() {
        SseEmitter emitter = new SseEmitter(0L);
        Task task = Task.create(5001L, "Demo Task", "phase", RuntimeType.MOCK, null, 1002L);
        when(taskQueryApplicationService.getTask(5001L, 1002L)).thenReturn(task);
        when(taskSseRegistry.register(5001L)).thenReturn(emitter);

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            SseEmitter actual = taskStreamController.stream(5001L);

            assertEquals(emitter, actual);
            verify(taskQueryApplicationService).getTask(5001L, 1002L);
            verify(taskSseRegistry).register(5001L);
        }
    }
}
