package com.codingx.chat.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.mcp.application.service.ChatMcpQueryService;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.common.model.ApiResponse;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
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
     * 会话应用服务依赖。
     */
    @Mock
    private ChatConversationApplicationService chatConversationApplicationService;

    /**
     * 聊天应用服务依赖。
     */
    @Mock
    private ChatApplicationService chatApplicationService;

    /**
     * 运行保护服务依赖。
     */
    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    @Mock
    private ChatAttachmentService chatAttachmentService;

    /**
     * 技能仓储依赖。
     */
    @Mock
    private ChatSkillRepository chatSkillRepository;

    @Mock
    private ChatMcpRepository chatMcpRepository;
    @Mock
    private ChatMcpQueryService chatMcpQueryService;

    @Mock
    private WorkspaceRepositoryImpl workspaceRepositoryImpl;

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
