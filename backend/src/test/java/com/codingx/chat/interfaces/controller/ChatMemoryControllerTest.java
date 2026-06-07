package com.codingx.chat.interfaces.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.config.GlobalExceptionHandler;
import com.codingx.governance.application.service.LongTermMemoryService;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import java.util.List;
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
 * 验证用户侧长期记忆列表和状态更新接口契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatMemoryControllerTest {

    @Mock
    private LongTermMemoryService longTermMemoryService;

    @InjectMocks
    private ChatMemoryController chatMemoryController;

    /**
     * 用户记忆列表应按当前登录用户和 workspaceId 查询，并返回统一响应结构。
     */
    @Test
    void listMemoriesReturnsCurrentUserMemories() throws Exception {
        when(longTermMemoryService.listUserMemories(1002L, 3001L, "ACTIVE")).thenReturn(List.of(
            GovernanceLongTermMemory.builder()
                .id(9001L)
                .memoryScope("USER")
                .userId(1002L)
                .workspaceId(3001L)
                .content("代码风格偏好：业务注释")
                .status("ACTIVE")
                .keywordJson("[\"业务注释\"]")
                .build()
        ));

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(get("/api/chat/memories")
                    .param("workspaceId", "3001")
                    .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value("9001"))
                .andExpect(jsonPath("$.data[0].content").value("代码风格偏好：业务注释"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
        }
    }

    /**
     * 用户停用已有记忆时应把目标状态传给服务层，并回传更新后的记录。
     */
    @Test
    void updateMemoryStatusDelegatesToService() throws Exception {
        when(longTermMemoryService.updateUserMemoryStatus(9001L, 1002L, "REJECTED")).thenReturn(
            GovernanceLongTermMemory.builder()
                .id(9001L)
                .memoryScope("USER")
                .userId(1002L)
                .workspaceId(3001L)
                .content("代码风格偏好：业务注释")
                .status("REJECTED")
                .build()
        );

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(patch("/api/chat/memories/9001/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        }

        verify(longTermMemoryService).updateUserMemoryStatus(eq(9001L), eq(1002L), eq("REJECTED"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatMemoryController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
