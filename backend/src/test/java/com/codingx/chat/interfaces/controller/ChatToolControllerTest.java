package com.codingx.chat.interfaces.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.config.GlobalExceptionHandler;
import com.codingx.tool.application.service.ChatToolExecutionResult;
import com.codingx.tool.application.service.ChatToolUserService;
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
 * 验证用户态工具调用接口的 HTTP 契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatToolControllerTest {

    @Mock
    private ChatToolUserService chatToolUserService;

    @InjectMocks
    private ChatToolController chatToolController;

    /**
     * 合法调用应透传用户与问题并返回成功结果。
     *
     * @throws Exception 断言失败时抛出。
     */
    @Test
    void invokeShouldReturnExecutionResult() throws Exception {
        when(chatToolUserService.invokeForCurrentUser(eq("shell_command"), eq("{\"command\":\"git status\"}"), eq(false)))
            .thenReturn(new ChatToolExecutionResult(
                "shell_command",
                "exitCode: 0",
                Map.of("exitCode", 0)
            ));

        mockMvc().perform(post("/api/chat/tools/shell_command/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "question":"{\\"command\\":\\"git status\\"}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.toolCode").value("shell_command"))
            .andExpect(jsonPath("$.data.content").value("exitCode: 0"));

        verify(chatToolUserService).invokeForCurrentUser("shell_command", "{\"command\":\"git status\"}", false);
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatToolController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
