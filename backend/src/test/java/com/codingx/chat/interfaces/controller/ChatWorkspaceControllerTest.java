package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.chat.application.service.ChatWorkspaceQueryService;
import com.codingx.chat.domain.model.ChatExecutionStep;
import com.codingx.chat.domain.model.ChatMessageArtifact;
import com.codingx.chat.domain.model.ChatMessageReference;
import com.codingx.chat.domain.model.ChatSkill;
import com.codingx.mcp.domain.model.ChatMcp;
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
 * 验证聊天工作区回放接口的 HTTP 契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatWorkspaceControllerTest {

    @Mock
    private ChatWorkspaceQueryService chatWorkspaceQueryService;

    @InjectMocks
    private ChatWorkspaceController chatWorkspaceController;

    /**
     * 步骤回放接口应返回成功响应与步骤列表。
     */
    @Test
    void listStepsReturnsWorkspaceStepsPayload() throws Exception {
        when(chatWorkspaceQueryService.listSteps(2001L)).thenReturn(List.of(
            ChatExecutionStep.builder().id(1L).runId(5002L).stepTitle("搜索资料").stepStatus("completed").build()
        ));

        mockMvc().perform(get("/api/chat/conversations/2001/steps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("success"))
            .andExpect(jsonPath("$.data[0].id").value(1L))
            .andExpect(jsonPath("$.data[0].stepTitle").value("搜索资料"))
            .andExpect(jsonPath("$.data[0].stepStatus").value("completed"));
    }

    /**
     * 来源回放接口应返回真实来源字段，供右栏恢复展示。
     */
    @Test
    void listReferencesReturnsWorkspaceReferencesPayload() throws Exception {
        when(chatWorkspaceQueryService.listReferences(2001L)).thenReturn(List.of(
            ChatMessageReference.builder().id(11L).runId(5002L).title("Spring Boot SSE 指南").url("https://example.com/sse").build()
        ));

        mockMvc().perform(get("/api/chat/conversations/2001/references"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(11L))
            .andExpect(jsonPath("$.data[0].title").value("Spring Boot SSE 指南"))
            .andExpect(jsonPath("$.data[0].url").value("https://example.com/sse"));
    }

    /**
     * 产物回放接口应返回产物名称与存储路径，供前端右栏联动下载。
     */
    @Test
    void listArtifactsReturnsWorkspaceArtifactsPayload() throws Exception {
        when(chatWorkspaceQueryService.listArtifacts(2001L)).thenReturn(List.of(
            ChatMessageArtifact.builder().id(21L).runId(5002L).name("search-report.docx").storagePath("artifacts/2001/search-report.docx").build()
        ));

        mockMvc().perform(get("/api/chat/conversations/2001/artifacts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(21L))
            .andExpect(jsonPath("$.data[0].name").value("search-report.docx"))
            .andExpect(jsonPath("$.data[0].storagePath").value("artifacts/2001/search-report.docx"));
    }

    /**
     * 当前技能接口应返回任务绑定技能，供工作台显示“当前技能”区块。
     */
    @Test
    void listCurrentSkillsReturnsWorkspaceSkillPayload() throws Exception {
        when(chatWorkspaceQueryService.listCurrentSkills(2001L)).thenReturn(List.of(
            ChatSkill.builder().id(31L).skillCode("conversation-core").displayName("会话核心").category("核心能力").enabled(1).sortNo(1).build()
        ));

        mockMvc().perform(get("/api/chat/conversations/2001/current-skills"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].skillCode").value("conversation-core"))
            .andExpect(jsonPath("$.data[0].displayName").value("会话核心"))
            .andExpect(jsonPath("$.data[0].category").value("核心能力"));
    }

    /**
     * 当前 MCP 接口应返回任务绑定 MCP，供工作台显示“当前 MCP”区块。
     */
    @Test
    void listCurrentMcpsReturnsWorkspaceMcpPayload() throws Exception {
        when(chatWorkspaceQueryService.listCurrentMcps(2001L)).thenReturn(List.of(
            ChatMcp.builder().id(41L).mcpCode("sales_query").displayName("销售查询").category("销售").enabled(1).sortNo(1).build()
        ));

        mockMvc().perform(get("/api/chat/conversations/2001/current-mcps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].mcpCode").value("sales_query"))
            .andExpect(jsonPath("$.data[0].displayName").value("销售查询"))
            .andExpect(jsonPath("$.data[0].category").value("销售"));
    }

    /**
     * 延迟创建 MockMvc，确保 Mockito 已完成被测控制器注入。
     * @return 可执行 HTTP 契约断言的 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatWorkspaceController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
