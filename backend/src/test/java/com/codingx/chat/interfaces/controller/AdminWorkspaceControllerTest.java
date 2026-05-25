package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.admin.application.service.AdminWorkspaceService;
import com.codingx.admin.interfaces.controller.AdminWorkspaceController;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.interfaces.response.AdminChatConversationListItemResponse;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.config.GlobalExceptionHandler;
import com.codingx.workspace.interfaces.response.AdminWorkspaceListItemResponse;
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
 * 验证管理端工作空间接口 HTTP 契约。
 */
@ExtendWith(MockitoExtension.class)
class AdminWorkspaceControllerTest {

    @Mock
    private AdminWorkspaceService adminWorkspaceService;

    @InjectMocks
    private AdminWorkspaceController adminWorkspaceController;

    /**
     * 工作空间列表接口应返回统一分页结构与运行目标标签。
     */
    @Test
    void listWorkspacesReturnsPagedPayload() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 0, 40, 0);
        when(adminWorkspaceService.pageWorkspaces(1, 10, "codingx", "local")).thenReturn(
            PageResult.<AdminWorkspaceListItemResponse>builder()
                .records(List.of(AdminWorkspaceListItemResponse.builder()
                    .id(3001L)
                    .name("本地项目")
                    .repositoryUrl("https://example.com/codingx.git")
                    .branchName("main")
                    .workingDirectory("D:/code/CodingX")
                    .runtimeTarget("local")
                    .runtimeTargetLabel("本地")
                    .createdBy(1001L)
                    .conversationCount(2L)
                    .createdAt(now)
                    .updatedAt(now)
                    .build()))
                .total(1L)
                .size(10L)
                .current(1L)
                .pages(1L)
                .build()
        );

        mockMvc().perform(get("/api/admin/workspaces")
                .param("current", "1")
                .param("size", "10")
                .param("keyword", "codingx")
                .param("runtimeTarget", "local"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.records[0].id").value(3001))
            .andExpect(jsonPath("$.data.records[0].name").value("本地项目"))
            .andExpect(jsonPath("$.data.records[0].runtimeTargetLabel").value("本地"))
            .andExpect(jsonPath("$.data.records[0].conversationCount").value(2));
    }

    /**
     * 工作空间会话子资源应透传分页和关键字参数，并返回会话分页结构。
     */
    @Test
    void listWorkspaceConversationsReturnsPagedPayload() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 10, 10, 0);
        when(adminWorkspaceService.pageWorkspaceConversations(3001L, 1, 10, "项目")).thenReturn(
            PageResult.<AdminChatConversationListItemResponse>builder()
                .records(List.of(AdminChatConversationListItemResponse.builder()
                    .id(2001L)
                    .title("本地项目会话")
                    .createdBy(1002L)
                    .status(ChatConversationStatus.ACTIVE)
                    .statusLabel("活跃")
                    .lastMessageAt(now)
                    .lastRunId(5001L)
                    .createdAt(now)
                    .updatedAt(now)
                    .build()))
                .total(1L)
                .size(10L)
                .current(1L)
                .pages(1L)
                .build()
        );

        mockMvc().perform(get("/api/admin/workspaces/3001/conversations")
                .param("current", "1")
                .param("size", "10")
                .param("keyword", "项目"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.records[0].id").value(2001))
            .andExpect(jsonPath("$.data.records[0].title").value("本地项目会话"))
            .andExpect(jsonPath("$.data.records[0].statusLabel").value("活跃"));
    }

    /**
     * 创建 MockMvc 并挂载全局异常处理器。
     * @return MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminWorkspaceController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
