package com.codingx.chat.interfaces.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.chat.application.service.ChatSampleQuestionService;
import com.codingx.chat.domain.model.ChatSampleQuestion;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证示例问题接口会返回欢迎区需要的最小字段集。
 */
@ExtendWith(MockitoExtension.class)
class ChatSampleQuestionControllerTest {

    @Mock
    private ChatSampleQuestionService chatSampleQuestionService;

    @InjectMocks
    private ChatSampleQuestionController chatSampleQuestionController;

    /**
     * 列表接口应返回 id、questionText 和 category。
     */
    @Test
    void listSampleQuestionsReturnsEnabledQuestions() throws Exception {
        org.mockito.Mockito.when(chatSampleQuestionService.listEnabledQuestions()).thenReturn(List.of(
            ChatSampleQuestion.builder().id(6001L).questionText("请介绍一下 OA 系统的主要功能").category("业务系统").build()
        ));

        mockMvc().perform(get("/api/chat/sample-questions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(6001))
            .andExpect(jsonPath("$.data[0].questionText").value("请介绍一下 OA 系统的主要功能"))
            .andExpect(jsonPath("$.data[0].category").value("业务系统"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatSampleQuestionController).build();
    }
}
