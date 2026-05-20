package com.codingx.chat.interfaces.controller;
import com.codingx.admin.interfaces.controller.AdminChatFeedbackController;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.chat.application.service.AdminChatFeedbackService;
import com.codingx.chat.interfaces.response.AdminChatMessageFeedbackDetailResponse;
import com.codingx.chat.interfaces.response.AdminChatMessageFeedbackListItemResponse;
import com.codingx.chat.interfaces.response.AdminChatMessageReferenceResponse;
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
 * 验证管理端反馈管理接口契约。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatFeedbackControllerTest {

    @Mock
    private AdminChatFeedbackService adminChatFeedbackService;

    @InjectMocks
    private AdminChatFeedbackController adminChatFeedbackController;

    /**
     * 分页接口应返回 records/total/current/size/pages 结构。
     */
    @Test
    void listFeedbacksReturnsPagedPayload() throws Exception {
        when(adminChatFeedbackService.pageFeedback(1, 10, "", null)).thenReturn(
            PageResult.<AdminChatMessageFeedbackListItemResponse>builder()
                .records(List.of(AdminChatMessageFeedbackListItemResponse.builder()
                    .id(9001L)
                    .messageId(102L)
                    .conversationId(2001L)
                    .userId(1002L)
                    .vote(1)
                    .reason("helpful")
                    .comment("good")
                    .createdAt(LocalDateTime.of(2026, 5, 17, 10, 0, 0))
                    .updatedAt(LocalDateTime.of(2026, 5, 17, 10, 1, 0))
                    .build()))
                .total(1L)
                .current(1L)
                .size(10L)
                .pages(1L)
                .build()
        );

        mockMvc().perform(get("/api/admin/chat/feedbacks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.records[0].id").value(9001))
            .andExpect(jsonPath("$.data.records[0].messageId").value(102))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.current").value(1))
            .andExpect(jsonPath("$.data.size").value(10))
            .andExpect(jsonPath("$.data.pages").value(1));
    }

    /**
     * 详情接口应返回反馈主体和关联消息信息。
     */
    @Test
    void getFeedbackDetailReturnsAggregatedPayload() throws Exception {
        when(adminChatFeedbackService.getFeedbackDetail(9001L)).thenReturn(
            AdminChatMessageFeedbackDetailResponse.builder()
                .id(9001L)
                .messageId(102L)
                .conversationId(2001L)
                .conversationTitle("差旅报销说明")
                .messageRole("ASSISTANT")
                .messageContent("请准备以下报销材料")
                .userId(1002L)
                .vote(1)
                .reason("helpful")
                .comment("good")
                .createdAt(LocalDateTime.of(2026, 5, 17, 10, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 5, 17, 10, 1, 0))
                .build()
        );

        mockMvc().perform(get("/api/admin/chat/feedbacks/9001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(9001))
            .andExpect(jsonPath("$.data.messageId").value(102))
            .andExpect(jsonPath("$.data.messageRole").value("ASSISTANT"))
            .andExpect(jsonPath("$.data.messageContent").value("请准备以下报销材料"));
    }

    /**
     * 引用接口应返回反馈关联消息的来源列表。
     */
    @Test
    void listFeedbackReferencesReturnsRows() throws Exception {
        when(adminChatFeedbackService.listReferencesByFeedback(9001L)).thenReturn(List.of(
            AdminChatMessageReferenceResponse.builder()
                .id(7001L)
                .runId(5002L)
                .messageId(102L)
                .conversationId(2001L)
                .sourceType("web")
                .title("报销制度说明")
                .url("https://example.com/policy")
                .siteName("内部门户")
                .snippet("差旅报销需提供票据")
                .rankNo(1)
                .createdAt(LocalDateTime.of(2026, 5, 17, 9, 55, 0))
                .build()
        ));

        mockMvc().perform(get("/api/admin/chat/feedbacks/9001/references"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(7001))
            .andExpect(jsonPath("$.data[0].title").value("报销制度说明"))
            .andExpect(jsonPath("$.data[0].url").value("https://example.com/policy"));
    }

    /**
     * 创建 MockMvc 并挂载全局异常处理器。
     * @return MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminChatFeedbackController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
