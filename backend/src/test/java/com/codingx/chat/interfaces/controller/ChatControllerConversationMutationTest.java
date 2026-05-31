package com.codingx.chat.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatConversationViewService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.interfaces.request.SendChatMessageRequest;
import com.codingx.chat.interfaces.response.ChatConversationResponse;
import com.codingx.chat.interfaces.response.ChatMessageResponse;
import com.codingx.chat.interfaces.response.ConversationShareResponse;
import com.codingx.chat.interfaces.response.SharedConversationResponse;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.model.ApiResponse;
import java.util.Arrays;
import java.util.List;
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
    private ChatConversationViewService chatConversationViewService;

    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    @Mock
    private ChatReactionService chatReactionService;

    @InjectMocks
    private ChatController chatController;

    /**
     * Controller 只能做 HTTP 协议适配，业务查询与持久化依赖必须下沉到应用服务。
     */
    @Test
    void controllerDoesNotDependOnRepositoriesOrPersistenceInfrastructure() {
        List<String> illegalFields = Arrays.stream(ChatController.class.getDeclaredFields())
            .filter(field -> {
                String typeName = field.getType().getName();
                return typeName.contains(".domain.repository.")
                    || typeName.contains(".infrastructure.repository.")
                    || typeName.contains(".infrastructure.persistence.");
            })
            .map(field -> field.getName() + ":" + field.getType().getName())
            .toList();

        assertEquals(List.of(), illegalFields);
    }

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
        ChatConversationResponse conversationResponse = new ChatConversationResponse(
            2001L,
            "新会话",
            ChatConversationStatus.ACTIVE,
            null,
            null,
            false,
            null,
            true,
            3001L,
            null,
            ChatConversationResponse.WorkspaceType.CLOUD,
            null,
            null,
            null,
            null,
            null
        );
        when(chatConversationApplicationService.createConversation(
            new com.codingx.chat.application.command.CreateConversationCommand("新会话", 3001L),
            1002L
        )).thenReturn(conversation);
        when(chatConversationViewService.toConversationResponse(conversation, 1002L)).thenReturn(conversationResponse);
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
            verify(chatConversationViewService).toConversationResponse(conversation, 1002L);
        }
    }

    /**
     * 同步发送消息应把 HTTP 请求参数转发给聊天主流程服务，业务编排由 service 负责。
     */
    @Test
    void sendMessageDelegatesToApplicationService() {
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<Void> response = chatController.sendMessage(
                2001L,
                new SendChatMessageRequest("请搜索最新 Java 版本", List.of(), List.of())
            );

            assertEquals(true, response.success());
            assertEquals(ErrorMessageCatalog.CHAT_MESSAGE_PROCESSED, response.message());
            verify(chatApplicationService).sendSynchronousMessage(2001L, "请搜索最新 Java 版本", List.of(), List.of(), 1002L);
        }
    }

    /**
     * 同步发送消息异常应原样向上抛出，由全局异常处理器统一返回 ApiResponse。
     */
    @Test
    void sendMessagePropagatesApplicationServiceFailure() {
        doThrow(new IllegalStateException("boom")).when(chatApplicationService)
            .sendSynchronousMessage(2001L, "请搜索最新 Java 版本", List.of(), List.of(), 1002L);
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            assertThrows(
                IllegalStateException.class,
                () -> chatController.sendMessage(2001L, new SendChatMessageRequest("请搜索最新 Java 版本", List.of(), List.of()))
            );

            verify(chatApplicationService).sendSynchronousMessage(2001L, "请搜索最新 Java 版本", List.of(), List.of(), 1002L);
        }
    }

    /**
     * 同步发送消息时应透传前端选中的技能列表，避免后端把“全量启用技能”误注入模型上下文。
     */
    @Test
    void sendMessageUsesExplicitSkillCodesFromRequest() {
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<Void> response = chatController.sendMessage(
                2001L,
                new SendChatMessageRequest("请用已选技能帮我查一下网页", List.of("web-access", "code_search"), List.of())
            );

            assertEquals(true, response.success());
            verify(chatApplicationService).sendSynchronousMessage(
                2001L,
                "请用已选技能帮我查一下网页",
                List.of("web-access", "code_search"),
                List.of(),
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
        when(chatConversationApplicationService.shareConversation(2001L, 1002L, List.of(101L, 102L)))
            .thenReturn(new ConversationShareResponse("share-token", "/share/chat/share-token?messages=101%2C102"));
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ApiResponse<ConversationShareResponse> response = chatController.shareConversation(
                2001L,
                new com.codingx.chat.interfaces.request.ShareConversationRequest(List.of(101L, 102L))
            );

            assertEquals(true, response.success());
            assertEquals("share-token", response.data().shareToken());
            assertEquals("/share/chat/share-token?messages=101%2C102", response.data().shareUrl());
            verify(chatConversationApplicationService).shareConversation(2001L, 1002L, List.of(101L, 102L));
        }
    }

    /**
     * 公开分享页不携带登录态，消息转换不能读取当前用户投票状态。
     */
    @Test
    void getSharedConversationDoesNotRequireLoginWhenMappingMessages() {
        ChatConversation conversation = ChatConversation.create(2001L, "公开分享", 1002L, ChatConversationStatus.ACTIVE);
        conversation.restoreSharingState(false, "share-token");
        List<ChatMessage> sharedMessages = List.of(
            ChatMessage.create(101L, 2001L, ChatMessageRole.USER, "第一问", ChatMessageStatus.COMPLETED, null, null, null),
            ChatMessage.create(102L, 2001L, ChatMessageRole.ASSISTANT, "第一答", ChatMessageStatus.COMPLETED, null, null, null)
        );
        ChatConversationApplicationService.SharedConversationContent content =
            new ChatConversationApplicationService.SharedConversationContent(conversation, sharedMessages);
        SharedConversationResponse sharedResponse = new SharedConversationResponse(
            new ChatConversationResponse(2001L, "公开分享", ChatConversationStatus.ACTIVE, null, null, false, "share-token", true, null, null, ChatConversationResponse.WorkspaceType.CLOUD, null, null, null, null, null),
            List.of(
                new ChatMessageResponse(101L, 2001L, ChatMessageRole.USER, "第一问", null, null, ChatMessageStatus.COMPLETED, null, null, null, null, List.of(), List.of(), null),
                new ChatMessageResponse(102L, 2001L, ChatMessageRole.ASSISTANT, "第一答", null, null, ChatMessageStatus.COMPLETED, null, null, null, null, List.of(), List.of(), null)
            )
        );
        when(chatConversationApplicationService.loadSharedConversation("share-token", "101,102")).thenReturn(content);
        when(chatConversationViewService.toSharedConversationResponse(content)).thenReturn(sharedResponse);

        ApiResponse<?> response = chatController.getSharedConversation("share-token", "101,102");

        assertEquals(true, response.success());
        verify(chatConversationApplicationService).loadSharedConversation("share-token", "101,102");
        verify(chatConversationViewService).toSharedConversationResponse(content);
    }
}
