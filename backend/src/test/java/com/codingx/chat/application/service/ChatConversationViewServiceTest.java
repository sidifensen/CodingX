package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageFeedback;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatMessageFeedbackRepository;
import com.codingx.chat.interfaces.response.ChatConversationResponse;
import com.codingx.chat.interfaces.response.ChatMessageResponse;
import com.codingx.chat.interfaces.response.SharedConversationResponse;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证聊天会话视图服务的响应投影规则。
 */
@ExtendWith(MockitoExtension.class)
class ChatConversationViewServiceTest {

    @Mock
    private ChatAttachmentService chatAttachmentService;

    @Mock
    private ChatMessageFeedbackRepository chatMessageFeedbackRepository;

    @Mock
    private WorkspaceRepositoryImpl workspaceRepositoryImpl;

    @Mock
    private ChatExecutionRunRepository chatExecutionRunRepository;

    @InjectMocks
    private ChatConversationViewService chatConversationViewService;

    /**
     * 会话响应应从聊天 run 投影运行中的兼容任务字段，供侧栏恢复运行态。
     */
    @Test
    void toConversationResponseReturnsWorkspaceAndRunningRunProjection() {
        Long runId = 2054964195115945984L;
        ChatConversation conversation = ChatConversation.create(2001L, "后台任务会话", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 25, 10, 0, 0), runId);
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(3001L);
        workspace.setName("CodingX");
        workspace.setRuntimeTarget(WorkspaceRepositoryImpl.RUNTIME_TARGET_LOCAL);
        when(workspaceRepositoryImpl.findOwnedWorkspaceById(3001L, 1002L)).thenReturn(Optional.of(workspace));
        when(chatExecutionRunRepository.findByConversationId(2001L)).thenReturn(List.of(
            ChatExecutionRun.builder()
                .id(runId)
                .conversationId(2001L)
                .status("RUNNING")
                .queueStatus("ACQUIRED")
                .build()
        ));

        ChatConversationResponse response = chatConversationViewService.toConversationResponse(conversation, 1002L);

        assertEquals("CodingX", response.workspaceName());
        assertEquals(ChatConversationResponse.WorkspaceType.LOCAL, response.workspaceType());
        assertEquals(runId, response.activeTaskId());
        assertEquals("RUNNING", response.activeTaskStatus());
        assertEquals(runId, response.lastTaskId());
        assertEquals("RUNNING", response.lastTaskStatus());
    }

    /**
     * 已完成聊天 run 应直接投影为成功终态，历史队列状态不能覆盖 run 终态。
     */
    @Test
    void toConversationResponseReturnsCompletedRunProjection() {
        Long runId = 2054964195115945984L;
        LocalDateTime finishedAt = LocalDateTime.of(2026, 5, 25, 10, 2, 0);
        ChatConversation conversation = ChatConversation.create(2001L, "已完成后台任务会话", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 25, 10, 0, 0), runId);
        when(workspaceRepositoryImpl.findOwnedWorkspaceById(3001L, 1002L)).thenReturn(Optional.empty());
        when(chatExecutionRunRepository.findByConversationId(2001L)).thenReturn(List.of(
            ChatExecutionRun.builder()
                .id(runId)
                .conversationId(2001L)
                .status("COMPLETED")
                .queueStatus("ACQUIRED")
                .finishedAt(finishedAt)
                .build()
        ));

        ChatConversationResponse response = chatConversationViewService.toConversationResponse(conversation, 1002L);

        assertNull(response.activeTaskId());
        assertNull(response.activeTaskStatus());
        assertEquals(runId, response.lastTaskId());
        assertEquals("SUCCEEDED", response.lastTaskStatus());
        assertEquals(finishedAt, response.lastTaskFinishedAt());
    }

    /**
     * 失败或拒绝的聊天 run 应投影为失败终态，并保留 run 完成时间供提醒展示。
     */
    @Test
    void toConversationResponseReturnsFailedRunProjectionForErrorAndRejectedRuns() {
        Long errorRunId = 2054964195115945984L;
        Long rejectedRunId = 2054964195115945985L;
        LocalDateTime errorFinishedAt = LocalDateTime.of(2026, 5, 25, 10, 3, 0);
        LocalDateTime rejectedFinishedAt = LocalDateTime.of(2026, 5, 25, 10, 4, 0);
        ChatConversation errorConversation = ChatConversation.create(2001L, "失败后台任务会话", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        errorConversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 25, 10, 0, 0), errorRunId);
        ChatConversation rejectedConversation = ChatConversation.create(2002L, "拒绝后台任务会话", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        rejectedConversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 25, 10, 1, 0), rejectedRunId);
        when(workspaceRepositoryImpl.findOwnedWorkspaceById(3001L, 1002L)).thenReturn(Optional.empty());
        when(chatExecutionRunRepository.findByConversationId(2001L)).thenReturn(List.of(
            ChatExecutionRun.builder()
                .id(errorRunId)
                .conversationId(2001L)
                .status("ERROR")
                .finishedAt(errorFinishedAt)
                .build()
        ));
        when(chatExecutionRunRepository.findByConversationId(2002L)).thenReturn(List.of(
            ChatExecutionRun.builder()
                .id(rejectedRunId)
                .conversationId(2002L)
                .status("REJECTED")
                .finishedAt(rejectedFinishedAt)
                .build()
        ));

        ChatConversationResponse errorResponse = chatConversationViewService.toConversationResponse(errorConversation, 1002L);
        ChatConversationResponse rejectedResponse = chatConversationViewService.toConversationResponse(rejectedConversation, 1002L);

        assertNull(errorResponse.activeTaskId());
        assertNull(errorResponse.activeTaskStatus());
        assertEquals(errorRunId, errorResponse.lastTaskId());
        assertEquals("FAILED", errorResponse.lastTaskStatus());
        assertEquals(errorFinishedAt, errorResponse.lastTaskFinishedAt());
        assertNull(rejectedResponse.activeTaskId());
        assertNull(rejectedResponse.activeTaskStatus());
        assertEquals(rejectedRunId, rejectedResponse.lastTaskId());
        assertEquals("FAILED", rejectedResponse.lastTaskStatus());
        assertEquals(rejectedFinishedAt, rejectedResponse.lastTaskFinishedAt());
    }

    /**
     * 消息响应应补齐附件、技能编码和当前用户投票状态。
     */
    @Test
    void toMessageResponseReturnsAttachmentsSkillCodesAndUserVote() {
        ChatMessage message = ChatMessage.create(
            101L,
            2001L,
            ChatMessageRole.USER,
            "@weather_query 历史问题",
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        );
        when(chatAttachmentService.listByMessageId(101L)).thenReturn(List.of(
            ChatAttachment.builder()
                .id(301L)
                .conversationId(2001L)
                .messageId(101L)
                .attachmentType("image")
                .fileName("demo.png")
                .fileExt("png")
                .mimeType("image/png")
                .fileSize(2048L)
                .previewUrl("/api/chat/attachments/301/content")
                .status("UPLOADED")
                .createdAt(LocalDateTime.of(2026, 5, 31, 10, 0, 0))
                .build()
        ));
        when(chatMessageFeedbackRepository.findByMessageIdAndUserId(101L, 1002L)).thenReturn(Optional.of(
            ChatMessageFeedback.builder()
                .messageId(101L)
                .conversationId(2001L)
                .userId(1002L)
                .vote(1)
                .build()
        ));

        ChatMessageResponse response = chatConversationViewService.toMessageResponse(message, 1002L);

        assertEquals(301L, response.attachments().getFirst().id());
        assertEquals("weather_query", response.skillCodes().getFirst());
        assertEquals(1, response.userVote());
    }

    /**
     * 公开分享页没有登录态，消息响应不得查询当前用户投票状态。
     */
    @Test
    void toSharedConversationResponseDoesNotQueryUserVote() {
        ChatConversation conversation = ChatConversation.create(2001L, "公开分享", 1002L, ChatConversationStatus.ACTIVE);
        conversation.restoreSharingState(false, "share-token");
        ChatMessage message = ChatMessage.create(
            101L,
            2001L,
            ChatMessageRole.ASSISTANT,
            "第一答",
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        );
        when(workspaceRepositoryImpl.findOwnedWorkspaceById(null, 1002L)).thenReturn(Optional.empty());
        when(chatAttachmentService.listByMessageId(101L)).thenReturn(List.of());

        SharedConversationResponse response = chatConversationViewService.toSharedConversationResponse(conversation, List.of(message));

        assertEquals(2001L, response.conversation().id());
        assertEquals(null, response.messages().getFirst().userVote());
        verify(chatMessageFeedbackRepository, never()).findByMessageIdAndUserId(anyLong(), anyLong());
    }
}
