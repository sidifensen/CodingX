package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.goal.ChatGoalService;
import com.codingx.chat.application.service.goal.ChatGoalView;
import com.codingx.config.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证会话 active goal 查询接口，确保 Controller 只做身份和协议适配。
 */
@ExtendWith(MockitoExtension.class)
class ChatGoalControllerTest {

    /** 目标应用服务，负责归属校验和 active goal 查询。 */
    @Mock
    private ChatGoalService chatGoalService;

    /** 被测目标控制器，暴露用户侧会话目标查询入口。 */
    @InjectMocks
    private ChatGoalController chatGoalController;

    /**
     * active goal 查询应把路径会话和当前用户透传给服务，并返回前端可消费的目标快照。
     *
     * @throws Exception MockMvc 断言失败时抛出。
     */
    @Test
    void getActiveGoalReturnsCurrentUserConversationGoal() throws Exception {
        ChatGoalView goal = new ChatGoalView(
            "9001",
            "1001",
            "default",
            "真实目标模式",
            "后端持久化目标",
            "ACTIVE",
            "正在建表",
            "GOAL_UPDATED",
            LocalDateTime.parse("2026-06-09T12:00:00"),
            LocalDateTime.parse("2026-06-09T12:01:00"),
            null,
            List.of(new ChatGoalView.StepView("9101", "schema", "建表", "COMPLETED", "已完成", 0))
        );
        when(chatGoalService.getActiveGoal(1001L, 2001L)).thenReturn(Optional.of(goal));

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(2001L);

            mockMvc().perform(get("/api/chat/conversations/1001/goal/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("9001"))
                .andExpect(jsonPath("$.data.conversationId").value("1001"))
                .andExpect(jsonPath("$.data.title").value("真实目标模式"))
                .andExpect(jsonPath("$.data.steps[0].id").value("9101"))
                .andExpect(jsonPath("$.data.steps[0].status").value("COMPLETED"));
        }

        verify(chatGoalService).getActiveGoal(1001L, 2001L);
    }

    /**
     * 会话没有 active goal 时仍返回成功响应，data 为 null，前端据此清空目标浮窗。
     *
     * @throws Exception MockMvc 断言失败时抛出。
     */
    @Test
    void getActiveGoalReturnsNullWhenConversationHasNoActiveGoal() throws Exception {
        when(chatGoalService.getActiveGoal(1001L, 2001L)).thenReturn(Optional.empty());

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(2001L);

            mockMvc().perform(get("/api/chat/conversations/1001/goal/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    /**
     * 延迟创建 MockMvc，确保 Mockito 已完成被测控制器依赖注入。
     *
     * @return 可执行 HTTP 契约断言的 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatGoalController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
