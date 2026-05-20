package com.codingx.chat.interfaces.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.admin.interfaces.controller.AdminChatExpertController;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.config.GlobalExceptionHandler;
import com.codingx.expert.application.service.AdminChatExpertService;
import com.codingx.expert.domain.model.ChatExpert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证管理端专家管理接口的 HTTP 契约。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatExpertControllerTest {

    @Mock
    private AdminChatExpertService adminChatExpertService;

    @InjectMocks
    private AdminChatExpertController adminChatExpertController;

    /**
     * 列表接口应返回专家基础字段。
     */
    @Test
    void listExpertsReturnsExpertRows() throws Exception {
        when(adminChatExpertService.pageExperts(1, 10)).thenReturn(PageResult.<ChatExpert>builder()
            .records(List.of(
                ChatExpert.builder()
                    .id(8301L)
                    .expertCode("solution-architect")
                    .displayName("解决方案架构师")
                    .category("研发架构")
                    .enabled(1)
                    .sortNo(1)
                    .build()
            ))
            .total(1L)
            .size(10L)
            .current(1L)
            .pages(1L)
            .build());

        mockMvc().perform(get("/api/admin/experts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.records[0].expertCode").value("solution-architect"))
            .andExpect(jsonPath("$.data.records[0].displayName").value("解决方案架构师"))
            .andExpect(jsonPath("$.data.total").value(1));
    }

    /**
     * 创建接口应返回新增后的专家记录。
     */
    @Test
    void createExpertReturnsCreatedExpert() throws Exception {
        when(adminChatExpertService.create(any(ChatExpert.class))).thenReturn(
            ChatExpert.builder()
                .id(8399L)
                .expertCode("qa-lead")
                .displayName("测试负责人")
                .category("质量保障")
                .enabled(1)
                .sortNo(18)
                .build()
        );

        mockMvc().perform(post("/api/admin/experts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "expertCode":"qa-lead",
                      "displayName":"测试负责人",
                      "category":"质量保障",
                      "systemPrompt":"你是一名测试负责人",
                      "enabled":1,
                      "sortNo":18
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(8399))
            .andExpect(jsonPath("$.data.expertCode").value("qa-lead"));
    }

    /**
     * 更新接口应按路径主键更新并返回结果。
     */
    @Test
    void updateExpertReturnsUpdatedExpert() throws Exception {
        when(adminChatExpertService.update(eq(8301L), any(ChatExpert.class))).thenReturn(
            ChatExpert.builder()
                .id(8301L)
                .expertCode("solution-architect")
                .displayName("首席解决方案架构师")
                .category("研发架构")
                .enabled(1)
                .sortNo(1)
                .build()
        );

        mockMvc().perform(put("/api/admin/experts/8301")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "expertCode":"solution-architect",
                      "displayName":"首席解决方案架构师",
                      "category":"研发架构",
                      "systemPrompt":"你是一名企业级解决方案架构师",
                      "enabled":1,
                      "sortNo":1
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.displayName").value("首席解决方案架构师"));
    }

    /**
     * 删除接口应返回统一成功文案。
     */
    @Test
    void deleteExpertReturnsSuccessMessage() throws Exception {
        mockMvc().perform(delete("/api/admin/experts/8301"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("删除成功"));

        verify(adminChatExpertService).delete(8301L);
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminChatExpertController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
