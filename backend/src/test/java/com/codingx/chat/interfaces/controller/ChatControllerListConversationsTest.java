package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.mcp.application.service.ChatMcpQueryService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.task.domain.model.RuntimeType;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.repository.TaskRepository;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.config.GlobalExceptionHandler;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
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
 * 验证会话列表接口会返回前端真实回放所需的 lastRunId 字段。
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerListConversationsTest {

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
    private ChatSkillRepository chatSkillRepository;

    @Mock
    private ChatMcpRepository chatMcpRepository;
    @Mock
    private ChatMcpQueryService chatMcpQueryService;

    @Mock
    private ChatExecutionRunRepository chatExecutionRunRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private WorkspaceRepositoryImpl workspaceRepositoryImpl;

    @InjectMocks
    private ChatController chatController;

    /**
     * 会话列表应包含 lastRunId，供前端判断右栏是否已有回放数据。
     */
    @Test
    void listConversationsReturnsLastRunId() throws Exception {
        ChatConversation conversation = ChatConversation.create(2001L, "Default Demo Conversation", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 15, 0, 36, 58), 2054964195115945984L);
        when(chatConversationApplicationService.listConversations(1002L, 3001L)).thenReturn(List.of(conversation));
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(3001L);
        workspace.setName("CodingX");
        workspace.setRuntimeTarget(WorkspaceRepositoryImpl.RUNTIME_TARGET_LOCAL);
        when(workspaceRepositoryImpl.findOwnedWorkspaceById(3001L, 1002L)).thenReturn(java.util.Optional.of(workspace));

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
     * 会话列表中的超大 Long ID 应序列化为字符串，避免浏览器 Number 精度丢失后无法正确切换历史会话。
     */
    @Test
    void listConversationsSerializesLongIdentifiersAsStrings() throws Exception {
        ChatConversation conversation = ChatConversation.create(2055114974648864768L, "历史会话", 1002L, 7001L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 15, 10, 36, 58), 2055114974682419200L);
        when(chatConversationApplicationService.listConversations(1002L, 3001L)).thenReturn(List.of(conversation));

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
     * 会话列表应暴露最新后台任务状态，供侧栏运行图标与完成提醒使用。
     */
    @Test
    void listConversationsReturnsTaskStatusProjection() throws Exception {
        Long taskId = 2054964195115945984L;
        ChatConversation conversation = ChatConversation.create(2001L, "后台任务会话", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 25, 10, 0, 0), taskId);
        when(chatConversationApplicationService.listConversations(1002L, 3001L)).thenReturn(List.of(conversation));
        when(chatExecutionRunRepository.findByConversationId(2001L)).thenReturn(List.of(
            ChatExecutionRun.builder()
                .id(taskId)
                .conversationId(2001L)
                .taskId(taskId)
                .status("RUNNING")
                .queueStatus("ACQUIRED")
                .build()
        ));
        when(taskRepository.findById(taskId)).thenReturn(java.util.Optional.of(
            Task.create(taskId, "后台任务会话", "聊天任务", RuntimeType.LOCAL, 3001L, 1002L)
        ));
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(3001L);
        workspace.setName("CodingX");
        workspace.setRuntimeTarget(WorkspaceRepositoryImpl.RUNTIME_TARGET_LOCAL);
        when(workspaceRepositoryImpl.findOwnedWorkspaceById(3001L, 1002L)).thenReturn(java.util.Optional.of(workspace));

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(get("/api/chat/conversations").param("workspaceId", "3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].activeTaskId").value("2054964195115945984"))
                .andExpect(jsonPath("$.data[0].activeTaskStatus").value("RUNNING"))
                .andExpect(jsonPath("$.data[0].lastTaskId").value("2054964195115945984"))
                .andExpect(jsonPath("$.data[0].lastTaskStatus").value("RUNNING"));
        }
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
