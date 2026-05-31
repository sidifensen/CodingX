package com.codingx.chat.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatConversationViewService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.model.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证聊天取消接口的最小控制器契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerCancelTest {

    /**
     * 会话应用服务，满足控制器构造依赖，本测试不触发会话业务。
     */
    @Mock
    private ChatConversationApplicationService chatConversationApplicationService;

    /**
     * 聊天主流程服务，满足控制器构造依赖，本测试不触发发送或重新生成。
     */
    @Mock
    private ChatApplicationService chatApplicationService;

    /**
     * 会话视图服务，满足控制器构造依赖，本测试不触发响应投影。
     */
    @Mock
    private ChatConversationViewService chatConversationViewService;

    /**
     * 运行保护服务，取消接口应把会话标识转发到该服务。
     */
    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    /**
     * 被测控制器。
     */
    @InjectMocks
    private ChatController chatController;

    /**
     * 调用取消接口时应转发到运行保护服务并返回成功响应。
     */
    @Test
    void cancelConversationDelegatesToRuntimeGuard() {
        ApiResponse<Void> response = chatController.cancelConversation(1001L);

        assertEquals(true, response.success());
        assertEquals(ErrorMessageCatalog.CHAT_CANCEL_REQUESTED, response.message());
        verify(chatRuntimeGuardService).cancelConversation(1001L);
    }
}
