package com.codingx.chat.interfaces.controller;
import com.codingx.admin.interfaces.controller.AdminChatConversationController;
import com.codingx.admin.interfaces.controller.AdminChatRuntimeDashboardController;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.admin.application.service.AdminChatConversationService;
import com.codingx.admin.application.service.AdminChatDashboardKpiView;
import com.codingx.admin.application.service.AdminChatDashboardPerformanceView;
import com.codingx.admin.application.service.AdminChatDashboardResourceView;
import com.codingx.admin.application.service.AdminChatRuntimeDashboardService;
import com.codingx.admin.application.service.AdminChatRuntimeDashboardView;
import com.codingx.admin.application.service.AdminChatDashboardService;
import com.codingx.admin.application.service.AdminChatDashboardTrendBucketView;
import com.codingx.admin.application.service.AdminChatDashboardView;
import com.codingx.admin.interfaces.controller.AdminChatDashboardController;
import com.codingx.chat.application.service.ChatRuntimeExecutorDashboardView;
import com.codingx.chat.application.service.ChatRuntimeQueueDashboardView;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.interfaces.response.AdminChatConversationDetailResponse;
import com.codingx.chat.interfaces.response.AdminChatConversationDetailResponse.AdminChatGoalEventResponse;
import com.codingx.chat.interfaces.response.AdminChatConversationDetailResponse.AdminChatGoalRecordResponse;
import com.codingx.chat.interfaces.response.AdminChatConversationDetailResponse.AdminChatGoalStepResponse;
import com.codingx.chat.interfaces.response.AdminChatConversationListItemResponse;
import com.codingx.chat.interfaces.response.ChatMessageResponse;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.config.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证管理端会话管理接口契约。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatConversationControllerTest {

    @Mock
    private AdminChatConversationService adminChatConversationService;

    @InjectMocks
    private AdminChatConversationController adminChatConversationController;

    @Mock
    private AdminChatRuntimeDashboardService adminChatRuntimeDashboardService;

    @InjectMocks
    private AdminChatRuntimeDashboardController adminChatRuntimeDashboardController;

    @Mock
    private AdminChatDashboardService adminChatDashboardService;

    @InjectMocks
    private AdminChatDashboardController adminChatDashboardController;

    /**
     * 分页列表接口应返回 records/total/current/size/pages 结构。
     */
    @Test
    void listConversationsReturnsPagedPayload() throws Exception {
        when(adminChatConversationService.pageConversations(1, 10, null)).thenReturn(
            PageResult.<AdminChatConversationListItemResponse>builder()
                .records(List.of(AdminChatConversationListItemResponse.builder()
                    .id(2001L)
                    .title("差旅报销说明")
                    .createdBy(1002L)
                    .status(ChatConversationStatus.ACTIVE)
                    .statusLabel("活跃")
                    .lastMessageAt(LocalDateTime.of(2026, 5, 16, 10, 2, 0))
                    .lastRunId(5001L)
                    .createdAt(LocalDateTime.of(2026, 5, 16, 10, 0, 0))
                    .updatedAt(LocalDateTime.of(2026, 5, 16, 10, 5, 0))
                    .build()))
                .total(1L)
                .size(10L)
                .current(1L)
                .pages(1L)
                .build()
        );

        mockMvc().perform(get("/api/admin/chat/conversations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.records[0].id").value(2001))
            .andExpect(jsonPath("$.data.records[0].title").value("差旅报销说明"));
    }

    /**
     * 详情接口应返回会话元信息与消息列表。
     */
    @Test
    void getConversationReturnsAggregatedPayload() throws Exception {
        when(adminChatConversationService.getConversationDetail(2001L)).thenReturn(
            AdminChatConversationDetailResponse.builder()
                .id(2001L)
                .title("差旅报销说明")
                .createdBy(1002L)
                .status(ChatConversationStatus.ACTIVE)
                .statusLabel("活跃")
                .lastMessageAt(LocalDateTime.of(2026, 5, 16, 10, 2, 0))
                .lastRunId(5001L)
                .createdAt(LocalDateTime.of(2026, 5, 16, 10, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 5, 16, 10, 5, 0))
                .messages(List.of(new ChatMessageResponse(
                    3001L,
                    2001L,
                    null,
                    ChatMessageRole.USER,
                    "怎么报销？",
                    null,
                    null,
                    ChatMessageStatus.COMPLETED,
                    null,
                    null,
                    null,
                    0,
                    LocalDateTime.of(2026, 5, 16, 10, 2, 0),
                    LocalDateTime.of(2026, 5, 16, 10, 3, 0),
                    List.of(),
                    List.of(),
                    null
                )))
                .goals(List.of(new AdminChatGoalRecordResponse(
                    "7001",
                    "2001",
                    "1002",
                    "default",
                    "修复聊天目标展示",
                    "管理端展示目标三表",
                    "ACTIVE",
                    "已进入实现阶段",
                    "5001",
                    "5002",
                    LocalDateTime.of(2026, 6, 9, 10, 1, 0),
                    LocalDateTime.of(2026, 6, 9, 10, 4, 0),
                    null,
                    List.of(new AdminChatGoalStepResponse(
                        "8001",
                        "7001",
                        "step-1",
                        "补充管理端测试",
                        "COMPLETED",
                        0,
                        "先证明目标三表还未返回",
                        LocalDateTime.of(2026, 6, 9, 10, 2, 0),
                        LocalDateTime.of(2026, 6, 9, 10, 3, 0),
                        LocalDateTime.of(2026, 6, 9, 10, 3, 0)
                    )),
                    List.of(new AdminChatGoalEventResponse(
                        "9001",
                        "7001",
                        "2001",
                        "5002",
                        "GOAL_UPDATED",
                        "{\"goal\":{\"title\":\"修复聊天目标展示\"}}",
                        LocalDateTime.of(2026, 6, 9, 10, 4, 0)
                    ))
                )))
                .build()
        );

        mockMvc().perform(get("/api/admin/chat/conversations/2001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(2001))
            .andExpect(jsonPath("$.data.messages[0].content").value("怎么报销？"))
            .andExpect(jsonPath("$.data.goals[0].id").value("7001"))
            .andExpect(jsonPath("$.data.goals[0].steps[0].title").value("补充管理端测试"))
            .andExpect(jsonPath("$.data.goals[0].events[0].eventType").value("GOAL_UPDATED"))
            .andExpect(jsonPath("$.data.goals[0].events[0].payloadJson").value("{\"goal\":{\"title\":\"修复聊天目标展示\"}}"));
    }

    /**
     * 运行时观测接口应返回队列与线程池双视图。
     */
    @Test
    void getRuntimeDashboardReturnsQueueAndExecutorPayload() throws Exception {
        when(adminChatRuntimeDashboardService.getRuntimeDashboard()).thenReturn(
            new AdminChatRuntimeDashboardView(
                new ChatRuntimeQueueDashboardView("redis", 2, 1, 3, 1),
                new ChatRuntimeExecutorDashboardView(1, 2, 3, 253, 2, 4, 0, 256)
            )
        );

        MockMvcBuilders.standaloneSetup(adminChatRuntimeDashboardController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build()
            .perform(get("/api/admin/chat/runtime"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.queue.mode").value("redis"))
            .andExpect(jsonPath("$.data.queue.waitingCount").value(3))
            .andExpect(jsonPath("$.data.executor.streamQueueSize").value(3))
            .andExpect(jsonPath("$.data.executor.searchPoolSize").value(4));
    }

    /**
     * Dashboard 接口应支持窗口参数，并返回控制台聚合结构。
     */
    @Test
    void getDashboardReturnsWindowedConsolePayload() throws Exception {
        when(adminChatDashboardService.getDashboard("7d")).thenReturn(
            new AdminChatDashboardView(
                "7d",
                "2026-05-28T15:52:32",
                new AdminChatDashboardKpiView(12, 18, 86, 6, 42, 5),
                new AdminChatDashboardResourceView(9, 11, 3, 4, 22, 7, 5),
                new AdminChatDashboardPerformanceView(83.3, 8.3, 8.4, 9200L, 15000L, 62, 59, 60000L, 4),
                List.of(
                    new AdminChatDashboardTrendBucketView(
                        "05-22",
                        "2026-05-22T00:00:00",
                        2,
                        10,
                        2,
                        3,
                        2,
                        1,
                        8400L
                    )
                )
            )
        );

        MockMvcBuilders.standaloneSetup(adminChatDashboardController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build()
            .perform(get("/api/admin/chat/dashboard").param("window", "7d"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.window").value("7d"))
            .andExpect(jsonPath("$.data.kpis.activeUserCount").value(12))
            .andExpect(jsonPath("$.data.resources.skillCount").value(9))
            .andExpect(jsonPath("$.data.performance.successRate").value(83.3))
            .andExpect(jsonPath("$.data.performance.timedTraceCount").value(62))
            .andExpect(jsonPath("$.data.performance.p95TraceRank").value(59))
            .andExpect(jsonPath("$.data.performance.slowTraceThresholdMs").value(60000))
            .andExpect(jsonPath("$.data.performance.slowTraceCount").value(4))
            .andExpect(jsonPath("$.data.trendBuckets[0].label").value("05-22"))
            .andExpect(jsonPath("$.data.trendBuckets[0].messageCount").value(10));
    }

    /**
     * 创建 MockMvc 并挂载全局异常处理器。
     * @return MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminChatConversationController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
