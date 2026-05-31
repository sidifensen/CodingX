package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatConversationViewService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.interfaces.response.ChatConversationResponse;
import com.codingx.config.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证会话列表控制器的序列化契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerListConversationsTest {

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
     * 会话列表应包含 lastRunId 和工作空间字段，供前端恢复右栏回放。
     */
    @Test
    void listConversationsReturnsLastRunId() throws Exception {
        ChatConversation conversation = ChatConversation.create(2001L, "Default Demo Conversation", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 15, 0, 36, 58), 2054964195115945984L);
        List<ChatConversation> conversations = List.of(conversation);
        when(chatConversationApplicationService.listConversations(1002L, 3001L)).thenReturn(conversations);
        when(chatConversationViewService.toConversationResponses(conversations, 1002L)).thenReturn(List.of(
            response(
                2001L,
                "Default Demo Conversation",
                3001L,
                2054964195115945984L,
                ChatConversationResponse.WorkspaceType.LOCAL,
                null,
                null,
                true
            )
        ));

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(get("/api/chat/conversations").param("workspaceId", "3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value("2001"))
                .andExpect(jsonPath("$.data[0].lastRunId").value("2054964195115945984"))
                .andExpect(jsonPath("$.data[0].workspaceId").value("3001"))
                .andExpect(jsonPath("$.data[0].workspaceType").value("LOCAL"));
        }
    }

    /**
     * 会话列表中的超大 Long ID 应序列化为字符串，避免浏览器 Number 精度丢失。
     */
    @Test
    void listConversationsSerializesLongIdentifiersAsStrings() throws Exception {
        ChatConversation conversation = ChatConversation.create(2055114974648864768L, "历史会话", 1002L, 7001L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 15, 10, 36, 58), 2055114974682419200L);
        List<ChatConversation> conversations = List.of(conversation);
        when(chatConversationApplicationService.listConversations(1002L, 3001L)).thenReturn(conversations);
        when(chatConversationViewService.toConversationResponses(conversations, 1002L)).thenReturn(List.of(
            response(
                2055114974648864768L,
                "历史会话",
                7001L,
                2055114974682419200L,
                ChatConversationResponse.WorkspaceType.CLOUD,
                null,
                null,
                true
            )
        ));

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(get("/api/chat/conversations").param("workspaceId", "3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("2055114974648864768"))
                .andExpect(jsonPath("$.data[0].lastRunId").value("2055114974682419200"))
                .andExpect(jsonPath("$.data[0].workspaceId").value("7001"));
        }
    }

    /**
     * 会话列表应透出视图服务投影好的任务状态与提醒已读状态。
     */
    @Test
    void listConversationsReturnsTaskProjectionFromViewService() throws Exception {
        Long taskId = 2054964195115945984L;
        ChatConversation conversation = ChatConversation.create(2001L, "后台任务会话", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        List<ChatConversation> conversations = List.of(conversation);
        when(chatConversationApplicationService.listConversations(1002L, 3001L)).thenReturn(conversations);
        when(chatConversationViewService.toConversationResponses(conversations, 1002L)).thenReturn(List.of(
            response(
                2001L,
                "后台任务会话",
                3001L,
                taskId,
                ChatConversationResponse.WorkspaceType.LOCAL,
                taskId,
                "RUNNING",
                false
            )
        ));

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(get("/api/chat/conversations").param("workspaceId", "3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].activeTaskId").value("2054964195115945984"))
                .andExpect(jsonPath("$.data[0].activeTaskStatus").value("RUNNING"))
                .andExpect(jsonPath("$.data[0].taskCompletionRead").value(false));
        }
    }

    /**
     * 构造会话响应，测试只关心控制器序列化，不重复验证视图服务业务投影。
     */
    private ChatConversationResponse response(
        Long id,
        String title,
        Long workspaceId,
        Long lastRunId,
        ChatConversationResponse.WorkspaceType workspaceType,
        Long activeTaskId,
        String activeTaskStatus,
        Boolean taskCompletionRead
    ) {
        return new ChatConversationResponse(
            id,
            title,
            ChatConversationStatus.ACTIVE,
            LocalDateTime.of(2026, 5, 15, 0, 36, 58),
            lastRunId,
            false,
            null,
            taskCompletionRead,
            workspaceId,
            "CodingX",
            workspaceType,
            activeTaskId,
            activeTaskStatus,
            activeTaskId,
            activeTaskStatus,
            null
        );
    }

    /**
     * 延迟创建 MockMvc，确保 Mockito 注入完成。
     * @return 用于接口契约断言的 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
