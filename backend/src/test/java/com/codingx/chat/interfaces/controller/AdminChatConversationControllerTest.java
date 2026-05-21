package com.codingx.chat.interfaces.controller;
import com.codingx.admin.interfaces.controller.AdminChatConversationController;
import com.codingx.admin.interfaces.controller.AdminChatRuntimeDashboardController;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.chat.application.service.AdminChatConversationService;
import com.codingx.chat.application.service.AdminChatRuntimeDashboardService;
import com.codingx.chat.application.service.AdminChatRuntimeDashboardView;
import com.codingx.chat.application.service.ChatRuntimeExecutorDashboardView;
import com.codingx.chat.application.service.ChatRuntimeQueueDashboardView;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.interfaces.response.AdminChatConversationDetailResponse;
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
                    ChatMessageRole.USER,
                    "怎么报销？",
                    null,
                    null,
                    ChatMessageStatus.COMPLETED,
                    null,
                    null,
                    null,
                    LocalDateTime.of(2026, 5, 16, 10, 2, 0),
                    List.of(),
                    List.of(),
                    null
                )))
                .build()
        );

        mockMvc().perform(get("/api/admin/chat/conversations/2001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(2001))
            .andExpect(jsonPath("$.data.messages[0].content").value("怎么报销？"));
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
     * 创建 MockMvc 并挂载全局异常处理器。
     * @return MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminChatConversationController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
