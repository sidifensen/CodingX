package com.codingx.chat.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatConversationViewService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.chat.interfaces.request.ChatMessageFeedbackRequest;
import com.codingx.common.model.ApiResponse;
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
     * 运行保护服务，满足控制器构造依赖，本测试不触发取消逻辑。
     */
    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    /**
     * 消息反馈服务，提交接口应把投票和原因转发到该服务。
     */
    @Mock
    private ChatReactionService chatReactionService;

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
            assertEquals(ErrorMessageCatalog.CHAT_FEEDBACK_SUBMITTED, response.message());
            verify(chatReactionService).submitReaction(101L, 201L, 1001L, 1, "good", "clear");
        }
    }
}
