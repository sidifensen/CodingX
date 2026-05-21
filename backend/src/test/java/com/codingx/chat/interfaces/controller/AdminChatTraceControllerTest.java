package com.codingx.chat.interfaces.controller;
import com.codingx.admin.interfaces.controller.AdminChatTraceController;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.admin.application.service.AdminChatTraceService;
import com.codingx.chat.application.service.ConversationTraceView;
import com.codingx.admin.application.service.AdminTraceRunListItemView;
import com.codingx.admin.application.service.AdminTraceRunPageResultView;
import com.codingx.chat.domain.model.ChatTraceNode;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.config.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证管理端 Trace 列表与详情接口契约。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatTraceControllerTest {

    @Mock
    private AdminChatTraceService adminChatTraceService;

    @InjectMocks
    private AdminChatTraceController adminChatTraceController;

    /**
     * 分页列表接口应返回 records/total/size/current/pages 字段，供前端列表页直接渲染。
     */
    @Test
    void listTracesReturnsPagedPayload() throws Exception {
        when(adminChatTraceService.pageTraces(1, 10, "trace-1")).thenReturn(new AdminTraceRunPageResultView(
            List.of(
                new AdminTraceRunListItemView(
                    "trace-1",
                    "chat-entry",
                    1001L,
                    2001L,
                    3001L,
                    "admin",
                    "SUCCESS",
                    null,
                    6124L,
                    java.time.LocalDateTime.of(2026, 5, 16, 18, 0, 0),
                    java.time.LocalDateTime.of(2026, 5, 16, 18, 0, 6)
                )
            ),
            1,
            10,
            1,
            1
        ));

        mockMvc().perform(get("/api/admin/chat/traces")
                .param("current", "1")
                .param("size", "10")
                .param("traceId", "trace-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.records[0].traceId").value("trace-1"))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.size").value(10))
            .andExpect(jsonPath("$.data.current").value(1))
            .andExpect(jsonPath("$.data.pages").value(1));
    }

    /**
     * 详情接口应返回根链路和节点集合，供独立详情页渲染时序数据。
     */
    @Test
    void getTraceReturnsAggregatedPayload() throws Exception {
        when(adminChatTraceService.getTrace("trace-1")).thenReturn(new ConversationTraceView(
            ChatTraceRun.builder().traceId("trace-1").traceName("chat-entry").build(),
            List.of(ChatTraceNode.builder().traceId("trace-1").nodeName("intent-resolve").build())
        ));

        mockMvc().perform(get("/api/admin/chat/traces/trace-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.traceRun.traceId").value("trace-1"))
            .andExpect(jsonPath("$.data.nodes[0].nodeName").value("intent-resolve"));
    }

    /**
     * 延迟创建 MockMvc，确保 Mockito 注入完成并复用全局异常处理契约。
     *
     * @return 控制器契约测试专用 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminChatTraceController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
