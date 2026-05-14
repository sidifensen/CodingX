package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证旧反馈路径兼容控制器的 HTTP 契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageFeedbackControllerTest {

    @Mock
    private ChatReactionService chatReactionService;

    @InjectMocks
    private ChatMessageFeedbackController chatMessageFeedbackController;

    /**
     * 兼容反馈路径应正常消费 JSON 请求并转发给反馈服务。
     */
    @Test
    void submitReactionSupportsLegacyFeedbackPath() throws Exception {
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(post("/api/chat/messages/101/feedback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "conversationId": 2001,
                          "vote": 1,
                          "reason": "helpful",
                          "comment": "good"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("feedback submitted"))
                .andExpect(jsonPath("$.data").doesNotExist());

            verify(chatReactionService).submitReaction(101L, 2001L, 1002L, 1, "helpful", "good");
        }
    }

    /**
     * 缺失关键字段时应返回统一校验错误，保证前端兼容旧路径时有稳定失败信号。
     */
    @Test
    void submitReactionRejectsInvalidLegacyPayload() throws Exception {
        mockMvc().perform(post("/api/chat/messages/101/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "vote": 2
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * 延迟创建 MockMvc，确保 Mockito 已完成控制器依赖注入。
     * @return 可执行兼容路径请求的 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatMessageFeedbackController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
