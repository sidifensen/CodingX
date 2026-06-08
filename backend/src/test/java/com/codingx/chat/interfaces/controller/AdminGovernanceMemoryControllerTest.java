package com.codingx.chat.interfaces.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.config.GlobalExceptionHandler;
import com.codingx.governance.application.service.HookRuleService;
import com.codingx.governance.application.service.LongTermMemoryService;
import com.codingx.governance.application.service.PermissionPolicyService;
import com.codingx.governance.application.service.SlashCommandService;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import com.codingx.governance.interfaces.controller.AdminGovernanceController;
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
 * 验证管理端治理中心长期记忆接口契约。
 */
@ExtendWith(MockitoExtension.class)
class AdminGovernanceMemoryControllerTest {

    @Mock private PermissionPolicyService permissionPolicyService;
    @Mock private HookRuleService hookRuleService;
    @Mock private SlashCommandService slashCommandService;
    @Mock private LongTermMemoryService longTermMemoryService;

    @InjectMocks
    private AdminGovernanceController adminGovernanceController;

    /**
     * 管理端长期记忆列表应支持状态和数量筛选。
     */
    @Test
    void listLongTermMemoriesReturnsRows() throws Exception {
        when(longTermMemoryService.listAdminMemories("ACTIVE", 50)).thenReturn(List.of(
            GovernanceLongTermMemory.builder()
                .id(9101L)
                .memoryScope("PROJECT")
                .userId(1002L)
                .workspaceId(3001L)
                .content("项目约定：后端接口错误文案必须使用中文")
                .status("ACTIVE")
                .build()
        ));

        mockMvc().perform(get("/api/admin/governance/long-term-memories")
                .param("status", "ACTIVE")
                .param("limit", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value("9101"))
            .andExpect(jsonPath("$.data[0].memoryScope").value("PROJECT"))
            .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    /**
     * 管理端状态更新应委托服务层，并返回更新后的记忆记录。
     */
    @Test
    void updateLongTermMemoryStatusDelegatesToService() throws Exception {
        when(longTermMemoryService.updateAdminMemoryStatus(9101L, "ACTIVE")).thenReturn(
            GovernanceLongTermMemory.builder()
                .id(9101L)
                .memoryScope("PROJECT")
                .userId(1002L)
                .workspaceId(3001L)
                .content("项目约定：后端接口错误文案必须使用中文")
                .status("ACTIVE")
                .build()
        );

        mockMvc().perform(patch("/api/admin/governance/long-term-memories/9101/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(longTermMemoryService).updateAdminMemoryStatus(eq(9101L), eq("ACTIVE"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminGovernanceController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
