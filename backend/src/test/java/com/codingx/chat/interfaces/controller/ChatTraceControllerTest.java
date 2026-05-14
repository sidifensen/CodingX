package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.chat.application.service.ConversationTraceQueryService;
import com.codingx.chat.application.service.ConversationTraceView;
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
 * 验证 Trace 查询接口的 HTTP 契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatTraceControllerTest {

    @Mock
    private ConversationTraceQueryService conversationTraceQueryService;

    @InjectMocks
    private ChatTraceController chatTraceController;

    /**
     * Trace 查询接口应返回根记录和节点列表。
     */
    @Test
    void getTraceReturnsAggregatedPayload() throws Exception {
        when(conversationTraceQueryService.getTrace("trace-1")).thenReturn(
            new ConversationTraceView(
                ChatTraceRun.builder().traceId("trace-1").traceName("chat-entry").build(),
                List.of(ChatTraceNode.builder().traceId("trace-1").nodeName("chat-entry").build())
            )
        );

        mockMvc().perform(get("/api/chat/traces/trace-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.traceRun.traceId").value("trace-1"))
            .andExpect(jsonPath("$.data.nodes[0].nodeName").value("chat-entry"));
    }

    /**
     * 延迟创建 MockMvc，确保 Mockito 注入完成。
     * @return 用于 controller 断言的 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatTraceController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
