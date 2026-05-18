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

import com.codingx.tool.application.service.AdminChatToolService;
import com.codingx.tool.application.service.AdminChatToolService.ToolHealthView;
import com.codingx.tool.application.service.AdminChatToolService.ToolInvokeView;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.interfaces.controller.AdminChatToolController;
import com.codingx.config.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证管理端工具管理接口的 HTTP 契约。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatToolControllerTest {

    @Mock
    private AdminChatToolService adminChatToolService;

    @InjectMocks
    private AdminChatToolController adminChatToolController;

    /**
     * 列表接口应返回工具基础字段。
     */
    @Test
    void listToolsReturnsToolRows() throws Exception {
        when(adminChatToolService.listAll()).thenReturn(List.of(
            ChatTool.builder()
                .id(9101L)
                .toolCode("shell_command")
                .displayName("Shell 命令执行")
                .category("终端")
                .enabled(1)
                .sortNo(1)
                .build()
        ));

        mockMvc().perform(get("/api/admin/tools"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].toolCode").value("shell_command"))
            .andExpect(jsonPath("$.data[0].displayName").value("Shell 命令执行"));
    }

    /**
     * 创建接口应返回新增后的工具记录。
     */
    @Test
    void createToolReturnsCreatedTool() throws Exception {
        when(adminChatToolService.create(any(ChatTool.class))).thenReturn(
            ChatTool.builder()
                .id(9130L)
                .toolCode("my_tool")
                .displayName("自定义工具")
                .category("扩展")
                .enabled(1)
                .sortNo(30)
                .build()
        );

        mockMvc().perform(post("/api/admin/tools")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "toolCode":"my_tool",
                      "displayName":"自定义工具",
                      "category":"扩展",
                      "enabled":1,
                      "sortNo":30
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(9130))
            .andExpect(jsonPath("$.data.toolCode").value("my_tool"));
    }

    /**
     * 更新接口应按路径主键更新并返回结果。
     */
    @Test
    void updateToolReturnsUpdatedTool() throws Exception {
        when(adminChatToolService.update(eq(9101L), any(ChatTool.class))).thenReturn(
            ChatTool.builder()
                .id(9101L)
                .toolCode("shell_command")
                .displayName("Shell 命令执行升级")
                .category("终端")
                .enabled(1)
                .sortNo(1)
                .build()
        );

        mockMvc().perform(put("/api/admin/tools/9101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "toolCode":"shell_command",
                      "displayName":"Shell 命令执行升级",
                      "category":"终端",
                      "enabled":1,
                      "sortNo":1
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(9101))
            .andExpect(jsonPath("$.data.displayName").value("Shell 命令执行升级"));
    }

    /**
     * 删除接口应返回统一成功文案。
     */
    @Test
    void deleteToolReturnsSuccessMessage() throws Exception {
        mockMvc().perform(delete("/api/admin/tools/9101"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("删除成功"));

        verify(adminChatToolService).delete(9101L);
    }

    /**
     * 健康列表接口应返回配置态+执行器态字段。
     */
    @Test
    void listToolHealthViewsReturnsRows() throws Exception {
        when(adminChatToolService.listToolHealthViews()).thenReturn(List.of(
            ToolHealthView.builder()
                .toolCode("shell_command")
                .displayName("Shell 命令执行")
                .category("终端")
                .source("codex-cli")
                .status("healthy")
                .statusLabel("可用")
                .ok(true)
                .message("shell_command 已接入内置执行器")
                .sampleQuestion("command=date")
                .checkedAt("2026-05-18 10:00:00")
                .durationMs(0L)
                .build()
        ));

        mockMvc().perform(get("/api/admin/tools/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].toolCode").value("shell_command"))
            .andExpect(jsonPath("$.data[0].status").value("healthy"))
            .andExpect(jsonPath("$.data[0].statusLabel").value("可用"));
    }

    /**
     * 探测接口应返回单工具探测结果。
     */
    @Test
    void pingToolReturnsHealthView() throws Exception {
        when(adminChatToolService.pingTool("shell_command")).thenReturn(
            ToolHealthView.builder()
                .toolCode("shell_command")
                .displayName("Shell 命令执行")
                .status("healthy")
                .statusLabel("可用")
                .ok(true)
                .message("shell_command 调用成功")
                .checkedAt("2026-05-18 10:01:00")
                .durationMs(8L)
                .build()
        );

        mockMvc().perform(get("/api/admin/tools/shell_command/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.toolCode").value("shell_command"))
            .andExpect(jsonPath("$.data.ok").value(true))
            .andExpect(jsonPath("$.data.durationMs").value(8));
    }

    /**
     * 调用接口应返回执行结果与元数据。
     */
    @Test
    void invokeToolReturnsInvokeResult() throws Exception {
        when(adminChatToolService.invokeTool(eq("shell_command"), eq("command=date"))).thenReturn(
            ToolInvokeView.builder()
                .toolCode("shell_command")
                .displayName("Shell 命令执行")
                .ok(true)
                .status("success")
                .statusLabel("成功")
                .message("工具调用成功")
                .requestQuestion("command=date")
                .content("exitCode: 0")
                .metadata(Map.of("exitCode", 0))
                .checkedAt("2026-05-18 10:02:00")
                .durationMs(12L)
                .build()
        );

        mockMvc().perform(post("/api/admin/tools/shell_command/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "question":"command=date"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.toolCode").value("shell_command"))
            .andExpect(jsonPath("$.data.status").value("success"))
            .andExpect(jsonPath("$.data.content").value("exitCode: 0"));
    }

    /**
     * 创建 MockMvc 并挂载全局异常处理器，保证错误响应格式一致。
     * @return 测试用 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminChatToolController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
