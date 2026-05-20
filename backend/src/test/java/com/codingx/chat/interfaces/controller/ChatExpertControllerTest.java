package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.common.model.ApiResponse;
import com.codingx.config.GlobalExceptionHandler;
import com.codingx.expert.application.service.ChatExpertQueryService;
import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.interfaces.controller.ChatExpertController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证用户侧聊天专家查询接口契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatExpertControllerTest {

    @Mock
    private ChatExpertQueryService chatExpertQueryService;

    @InjectMocks
    private ChatExpertController chatExpertController;

    /**
     * 用户侧专家接口应返回启用专家清单。
     */
    @Test
    void listEnabledExpertsReturnsRows() throws Exception {
        when(chatExpertQueryService.listEnabledExperts()).thenReturn(List.of(
            ChatExpert.builder()
                .id(8301L)
                .expertCode("solution-architect")
                .displayName("解决方案架构师")
                .category("研发架构")
                .presetQuestion("请帮我把一个 SaaS 项目拆成可落地的系统架构方案")
                .enabled(1)
                .sortNo(1)
                .build()
        ));

        mockMvc().perform(get("/api/experts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].expertCode").value("solution-architect"))
            .andExpect(jsonPath("$.data[0].displayName").value("解决方案架构师"));
    }

    /**
     * 聊天工作区专家接口兼容路径应返回与标准路径一致的数据结构。
     */
    @Test
    void listEnabledExpertsReturnsRowsForChatPath() throws Exception {
        when(chatExpertQueryService.listEnabledExperts()).thenReturn(List.of(
            ChatExpert.builder()
                .id(8301L)
                .expertCode("solution-architect")
                .displayName("解决方案架构师")
                .category("研发架构")
                .enabled(1)
                .sortNo(1)
                .build()
        ));

        mockMvc().perform(get("/api/chat/experts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].expertCode").value("solution-architect"))
            .andExpect(jsonPath("$.data[0].displayName").value("解决方案架构师"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatExpertController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
