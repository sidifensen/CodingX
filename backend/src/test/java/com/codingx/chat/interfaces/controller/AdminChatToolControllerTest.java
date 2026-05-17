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

import com.codingx.chat.application.service.AdminChatToolService;
import com.codingx.chat.domain.model.ChatTool;
import com.codingx.config.GlobalExceptionHandler;
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

        mockMvc().perform(get("/api/admin/chat/tools"))
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

        mockMvc().perform(post("/api/admin/chat/tools")
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

        mockMvc().perform(put("/api/admin/chat/tools/9101")
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
        mockMvc().perform(delete("/api/admin/chat/tools/9101"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("删除成功"));

        verify(adminChatToolService).delete(9101L);
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