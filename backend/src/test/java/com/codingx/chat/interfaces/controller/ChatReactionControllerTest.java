package com.codingx.chat.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.domain.repository.ChatSkillRepository;
import com.codingx.chat.interfaces.request.ChatMessageFeedbackRequest;
import com.codingx.common.model.ApiResponse;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.dev33.satoken.stp.StpUtil;

/**
 * 验证消息反馈接口的最小控制器契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatReactionControllerTest {

    /**
     * 反馈服务依赖。
     */
    @Mock
    private ChatConversationApplicationService chatConversationApplicationService;

    @Mock
    private ChatApplicationService chatApplicationService;

    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    @Mock
    private ChatReactionService chatReactionService;

    @Mock
    private ChatMcpRepository chatMcpRepository;

    @Mock
    private ChatSkillRepository chatSkillRepository;

    /**
     * 被测控制器。
     */
    @InjectMocks
    private ChatController chatController;

    /**
     * 提交点赞/点踩时应转发给反馈服务并返回成功响应。
     */
    @Test
    void submitReactionDelegatesToReactionService() {
        ChatMessageFeedbackRequest request = new ChatMessageFeedbackRequest(201L, 1, "good", "clear");
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

            ApiResponse<Void> response = chatController.submitReaction(101L, request);

            assertEquals(true, response.success());
            assertEquals("feedback submitted", response.message());
            verify(chatReactionService).submitReaction(101L, 201L, 1001L, 1, "good", "clear");
        }
    }
}
