package com.codingx.chat.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.mcp.application.service.ChatMcpQueryService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.interfaces.request.SendChatMessageRequest;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.chat.domain.repository.ChatMessageFeedbackRepository;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.common.model.ApiResponse;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.util.List;
import java.util.Optional;
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
    private ChatAttachmentService chatAttachmentService;

    @Mock
    private ChatReactionService chatReactionService;

    @Mock
    private ChatMessageFeedbackRepository chatMessageFeedbackRepository;

    @Mock
    private ChatSkillRepository chatSkillRepository;

    @Mock
    private ChatMcpRepository chatMcpRepository;
    @Mock
    private ChatMcpQueryService chatMcpQueryService;

    @Mock
    private WorkspaceRepositoryImpl workspaceRepositoryImpl;

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
            assertEquals(ErrorMessageCatalog.CHAT_CONVERSATION_RENAMED, response.message());
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
            assertEquals(ErrorMessageCatalog.CHAT_CONVERSATION_DELETED, response.message());
            verify(chatConversationApplicationService).deleteConversation(2001L, 1002L);
        }
    }

    /**
     * 打开会话时前端会调用该接口清除任务完成提醒，后端必须只更新独立的已读字段。
     */
    @Test
    void markTaskCompletionReadDelegatesToApplicationService() {
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<Void> response = chatController.markTaskCompletionRead(2001L);

            assertEquals(true, response.success());
            assertEquals(ErrorMessageCatalog.CHAT_CONVERSATION_TASK_COMPLETION_READ, response.message());
            verify(chatConversationApplicationService).markTaskCompletionRead(2001L, 1002L);
        }
    }

    /**
     * 创建会话接口应透传 workspaceId，保证会话和工作空间建立持久化关联。
     */
    @Test
    void createConversationDelegatesWorkspaceIdToApplicationService() {
        ChatConversation conversation = ChatConversation.create(2001L, "新会话", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        when(chatConversationApplicationService.createConversation(
            new com.codingx.chat.application.command.CreateConversationCommand("新会话", 3001L),
            1002L
        )).thenReturn(conversation);
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<com.codingx.chat.interfaces.response.ChatConversationResponse> response = chatController.createConversation(
                new com.codingx.chat.interfaces.request.CreateConversationRequest("新会话", 3001L)
            );

            assertEquals(true, response.success());
            assertEquals(2001L, response.data().id());
            verify(chatConversationApplicationService).createConversation(
                new com.codingx.chat.application.command.CreateConversationCommand("新会话", 3001L),
                1002L
            );
        }
    }

    /**
     * 同步发送消息完成后应释放会话门控，避免并发槽位被同步入口长期占用。
     */
    @Test
    void sendMessageReleasesConversationGuardAfterSyncProcessing() {
        when(chatMcpQueryService.listEnabledMcps()).thenReturn(java.util.List.of());
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<Void> response = chatController.sendMessage(2001L, new SendChatMessageRequest("请搜索最新 Java 版本", null));

            assertEquals(true, response.success());
            assertEquals(ErrorMessageCatalog.CHAT_MESSAGE_PROCESSED, response.message());
            verify(chatApplicationService).sendMessage(
                new com.codingx.chat.application.command.SendChatMessageCommand(
                    2001L,
                    "请搜索最新 Java 版本",
                    false,
                    java.util.List.of(),
                    java.util.List.of(),
                    null,
                    null,
                    java.util.List.of()
                ),
                1002L
            );
            verify(chatRuntimeGuardService).completeConversation(2001L);
        }
    }

    /**
     * 同步发送消息抛错时也应释放会话门控，避免异常路径造成后续请求被误判 busy。
     */
    @Test
    void sendMessageReleasesConversationGuardWhenSyncProcessingFails() {
        when(chatMcpQueryService.listEnabledMcps()).thenReturn(java.util.List.of());
        doThrow(new IllegalStateException("boom")).when(chatApplicationService).sendMessage(
            new com.codingx.chat.application.command.SendChatMessageCommand(
                2001L,
                "请搜索最新 Java 版本",
                false,
                java.util.List.of(),
                java.util.List.of(),
                null,
                null,
                java.util.List.of()
            ),
            1002L
        );
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            assertThrows(
                IllegalStateException.class,
                () -> chatController.sendMessage(2001L, new SendChatMessageRequest("请搜索最新 Java 版本", null))
            );

            verify(chatRuntimeGuardService).completeConversation(2001L);
        }
    }

    /**
     * 同步发送消息时应透传前端选中的技能列表，避免后端把“全量启用技能”误注入模型上下文。
     */
    @Test
    void sendMessageUsesExplicitSkillCodesFromRequest() {
        when(chatMcpQueryService.listEnabledMcps()).thenReturn(java.util.List.of());
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<Void> response = chatController.sendMessage(
                2001L,
                new SendChatMessageRequest("请用已选技能帮我查一下网页", List.of("web-access", "code_search"), List.of())
            );

            assertEquals(true, response.success());
            verify(chatApplicationService).sendMessage(
                new com.codingx.chat.application.command.SendChatMessageCommand(
                    2001L,
                    "请用已选技能帮我查一下网页",
                    false,
                    java.util.List.of(),
                    java.util.List.of("web-access", "code_search"),
                    null,
                    null,
                    java.util.List.of()
                ),
                1002L
            );
        }
    }

    /**
     * 重新生成接口应转发到聊天主流程服务，确保复用原始 run 上下文。
     */
    @Test
    void regenerateConversationDelegatesToChatApplicationService() {
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<Void> response = chatController.regenerateConversation(2001L);

            assertEquals(true, response.success());
            assertEquals(ErrorMessageCatalog.CHAT_CONVERSATION_REGENERATED, response.message());
            verify(chatApplicationService).regenerateLastAssistantMessage(2001L, 1002L);
        }
    }

    /**
     * 分享接口应把前端选中的消息 ID 编码进公开链接，供公开页按轮次过滤。
     */
    @Test
    void shareConversationBuildsUrlWithSelectedMessageIds() throws Exception {
        when(chatConversationApplicationService.generateShareToken(2001L, 1002L)).thenReturn("share-token");
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<?> response = chatController.shareConversation(
                2001L,
                new com.codingx.chat.interfaces.request.ShareConversationRequest(List.of(101L, 102L))
            );

            assertEquals(true, response.success());
            java.lang.reflect.Method shareUrlAccessor = response.data().getClass().getDeclaredMethod("shareUrl");
            shareUrlAccessor.setAccessible(true);
            assertEquals("/share/chat/share-token?messages=101%2C102", shareUrlAccessor.invoke(response.data()));
        }
    }

    /**
     * 公开分享页不携带登录态，消息转换不能读取当前用户投票状态。
     */
    @Test
    void getSharedConversationDoesNotRequireLoginWhenMappingMessages() {
        ChatConversation conversation = ChatConversation.create(2001L, "公开分享", 1002L, ChatConversationStatus.ACTIVE);
        conversation.restoreSharingState(false, "share-token");
        when(chatConversationApplicationService.requireSharedConversation("share-token")).thenReturn(conversation);
        when(chatConversationApplicationService.listSharedMessages("share-token", List.of(101L, 102L))).thenReturn(List.of(
            ChatMessage.create(101L, 2001L, ChatMessageRole.USER, "第一问", ChatMessageStatus.COMPLETED, null, null, null),
            ChatMessage.create(102L, 2001L, ChatMessageRole.ASSISTANT, "第一答", ChatMessageStatus.COMPLETED, null, null, null)
        ));
        when(chatAttachmentService.listByMessageId(101L)).thenReturn(List.of());
        when(chatAttachmentService.listByMessageId(102L)).thenReturn(List.of());
        when(workspaceRepositoryImpl.findOwnedWorkspaceById(null, 1002L)).thenReturn(Optional.empty());

        ApiResponse<?> response = chatController.getSharedConversation("share-token", "101,102");

        assertEquals(true, response.success());
        verify(chatMessageFeedbackRepository, Mockito.never()).findByMessageIdAndUserId(Mockito.anyLong(), Mockito.anyLong());
    }
}
