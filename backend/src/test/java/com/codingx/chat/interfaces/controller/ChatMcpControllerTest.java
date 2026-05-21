package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.config.GlobalExceptionHandler;
import com.codingx.mcp.application.service.ChatMcpQueryService;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.interfaces.controller.ChatMcpController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证用户侧 MCP 查询接口契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatMcpControllerTest {

    @Mock
    private ChatMcpQueryService chatMcpQueryService;

    @InjectMocks
    private ChatMcpController chatMcpController;

    /**
     * 用户侧 MCP 标准路径应返回启用列表。
     */
    @Test
    void listEnabledMcpsReturnsRows() throws Exception {
        when(chatMcpQueryService.listEnabledMcps()).thenReturn(List.of(
            ChatMcp.builder()
                .id(8101L)
                .mcpCode("weather_query")
                .displayName("天气查询")
                .category("天气")
                .enabled(1)
                .available(true)
                .sortNo(1)
                .build()
        ));

        mockMvc().perform(get("/api/mcps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].mcpCode").value("weather_query"))
            .andExpect(jsonPath("$.data[0].displayName").value("天气查询"))
            .andExpect(jsonPath("$.data[0].available").value(true));
    }

    /**
     * 聊天工作区 MCP 兼容路径应返回与标准路径一致的数据结构。
     */
    @Test
    void listEnabledMcpsReturnsRowsForChatPath() throws Exception {
        when(chatMcpQueryService.listEnabledMcps()).thenReturn(List.of(
            ChatMcp.builder()
                .id(8101L)
                .mcpCode("weather_query")
                .displayName("天气查询")
                .category("天气")
                .enabled(1)
                .available(true)
                .sortNo(1)
                .build()
        ));

        mockMvc().perform(get("/api/chat/mcps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].mcpCode").value("weather_query"))
            .andExpect(jsonPath("$.data[0].displayName").value("天气查询"))
            .andExpect(jsonPath("$.data[0].available").value(true));
    }

    /**
     * 构造测试用 MockMvc 并启用全局异常处理。
     * @return MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatMcpController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
