package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.chat.application.service.ChatWorkspaceInventoryService;
import com.codingx.chat.interfaces.response.ChatWorkspaceResponse;
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
 * 验证用户侧工作区库存接口契约，确保空工作区也能进入前端侧栏分组。
 */
@ExtendWith(MockitoExtension.class)
class ChatWorkspaceInventoryControllerTest {

    /** 用户侧工作区库存服务，负责按当前登录用户读取有效工作区。 */
    @Mock
    private ChatWorkspaceInventoryService chatWorkspaceInventoryService;

    /** 被测控制器，仅做协议适配和统一响应封装。 */
    @InjectMocks
    private ChatWorkspaceInventoryController chatWorkspaceInventoryController;

    /**
     * 工作区库存接口应返回字符串 ID、本地目录和运行目标，供前端安全合并侧栏分组。
     *
     * @throws Exception 断言失败时抛出。
     */
    @Test
    void listWorkspacesReturnsCurrentUserWorkspaceInventory() throws Exception {
        when(chatWorkspaceInventoryService.listCurrentUserWorkspaces()).thenReturn(List.of(
            new ChatWorkspaceResponse(
                "206321530629967872",
                "CodingX",
                "local",
                "D:/code/CodingX",
                null,
                null
            )
        ));

        mockMvc().perform(get("/api/chat/workspaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("success"))
            .andExpect(jsonPath("$.data[0].id").value("206321530629967872"))
            .andExpect(jsonPath("$.data[0].name").value("CodingX"))
            .andExpect(jsonPath("$.data[0].runtimeTarget").value("local"))
            .andExpect(jsonPath("$.data[0].workingDirectory").value("D:/code/CodingX"));
    }

    /**
     * 延迟创建 MockMvc，确保 Mockito 已完成控制器依赖注入。
     *
     * @return 可执行 HTTP 契约断言的 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatWorkspaceInventoryController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
