package com.codingx.chat.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.domain.repository.ChatSkillRepository;
import com.codingx.common.model.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证会话重命名与删除接口的最小控制器契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerConversationMutationTest {

    @Mock
    private ChatConversationApplicationService chatConversationApplicationService;

    @Mock
    private ChatApplicationService chatApplicationService;

    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    @Mock
    private ChatReactionService chatReactionService;

    @Mock
    private ChatSkillRepository chatSkillRepository;

    @InjectMocks
    private ChatController chatController;

    /**
     * 重命名接口应转发新标题并返回成功响应。
     */
    @Test
    void renameConversationDelegatesToApplicationService() {
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<Void> response = chatController.renameConversation(2001L, new com.codingx.chat.interfaces.request.RenameConversationRequest("新的标题"));

            assertEquals(true, response.success());
            assertEquals("conversation renamed", response.message());
            verify(chatConversationApplicationService).updateConversationTitle(2001L, "新的标题", 1002L);
        }
    }

    /**
     * 删除接口应转发会话标识并返回成功响应。
     */
    @Test
    void deleteConversationDelegatesToApplicationService() {
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<Void> response = chatController.deleteConversation(2001L);

            assertEquals(true, response.success());
            assertEquals("conversation deleted", response.message());
            verify(chatConversationApplicationService).deleteConversation(2001L, 1002L);
        }
    }
}
