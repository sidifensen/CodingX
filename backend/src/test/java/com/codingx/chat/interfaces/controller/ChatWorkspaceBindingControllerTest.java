package com.codingx.chat.interfaces.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.chat.application.service.ChatWorkspaceBindingService;
import com.codingx.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证用户工作区路径绑定接口契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatWorkspaceBindingControllerTest {

    @Mock
    private ChatWorkspaceBindingService chatWorkspaceBindingService;

    @InjectMocks
    private ChatWorkspaceBindingController chatWorkspaceBindingController;

    /**
     * 绑定路径接口应回传规范路径并调用服务层。
     *
     * @throws Exception 断言失败时抛出。
     */
    @Test
    void bindRepositoryPathShouldReturnBoundPath() throws Exception {
        mockMvc().perform(post("/api/chat/workspace/bind-repository")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "repositoryPath":"D:/code/codingx"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.repositoryPath").value("D:/code/codingx"));

        verify(chatWorkspaceBindingService).bindRepositoryPathForCurrentUser(eq("D:/code/codingx"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatWorkspaceBindingController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}

