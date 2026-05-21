package com.codingx.chat.interfaces.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.mcp.application.service.AdminChatMcpService;
import com.codingx.mcp.application.service.AdminChatMcpService.McpToolHealthView;
import com.codingx.admin.interfaces.controller.AdminChatMcpController;
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
 * 验证管理端 MCP 工具页面所需 HTTP 契约。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatMcpControllerTest {

    @Mock
    private AdminChatMcpService adminChatMcpService;

    @InjectMocks
    private AdminChatMcpController adminChatMcpController;

    /**
     * MCP 工具列表接口应返回可直接渲染管理表格的字段。
     */
    @Test
    void listMcpToolsReturnsToolRows() throws Exception {
        when(adminChatMcpService.listTools()).thenReturn(List.of(
            McpToolHealthView.builder()
                .toolId("weather_query")
                .displayName("天气查询")
                .category("天气")
                .source("内置后端")
                .status("healthy")
                .statusLabel("可用")
                .description("查询当前天气与未来预报")
                .sampleQuestion("北京今天天气怎么样")
                .build(),
            McpToolHealthView.builder()
                .toolId("code_search")
                .displayName("代码检索")
                .category("研发")
                .source("内置后端")
                .status("healthy")
                .statusLabel("可用")
                .description("按关键词检索代码文件与行号")
                .sampleQuestion("请查找 ChatController 中 sendMessage 的实现")
                .build()
        ));

        mockMvc().perform(get("/api/admin/mcps/tools"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].toolId").value("weather_query"))
            .andExpect(jsonPath("$.data[0].displayName").value("天气查询"))
            .andExpect(jsonPath("$.data[0].status").value("healthy"))
            .andExpect(jsonPath("$.data[0].statusLabel").value("可用"))
            .andExpect(jsonPath("$.data[1].toolId").value("code_search"));
    }

    /**
     * 探测接口应返回当前工具可用性及耗时。
     */
    @Test
    void pingMcpToolReturnsRuntimeStatus() throws Exception {
        when(adminChatMcpService.pingTool(eq("weather_query"))).thenReturn(
            McpToolHealthView.builder()
                .toolId("weather_query")
                .displayName("天气查询")
                .category("天气")
                .source("内置后端")
                .status("healthy")
                .statusLabel("可用")
                .message("weather_query 可用")
                .ok(true)
                .durationMs(18L)
                .checkedAt("2026-05-16 16:10:00")
                .build()
        );

        mockMvc().perform(get("/api/admin/mcps/tools/weather_query/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.toolId").value("weather_query"))
            .andExpect(jsonPath("$.data.ok").value(true))
            .andExpect(jsonPath("$.data.message").value("weather_query 可用"))
            .andExpect(jsonPath("$.data.durationMs").value(18));
    }

    /**
     * 创建 MockMvc 并挂载全局异常处理器，保证错误响应契约一致。
     *
     * @return 测试用 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminChatMcpController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
