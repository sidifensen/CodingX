package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageFeedback;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatMessageFeedbackRepository;
import com.codingx.chat.application.service.ChatConversationApplicationService.SharedConversationContent;
import com.codingx.chat.interfaces.response.ChatAttachmentResponse;
import com.codingx.chat.interfaces.response.ChatConversationResponse;
import com.codingx.chat.interfaces.response.ChatMessageResponse;
import com.codingx.chat.interfaces.response.SharedConversationResponse;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责把聊天领域对象投影为接口响应对象。
 * 该服务集中承接工作空间、聊天 run 兼容状态、附件与用户反馈查询，避免 Controller 编排业务查询。
 */
@Service
@RequiredArgsConstructor
public class ChatConversationViewService {

    /** 附件应用服务，用于将消息关联附件补齐到响应模型。 */
    private final ChatAttachmentService chatAttachmentService;

    /** 消息反馈仓储，用于查询当前用户对消息的点赞或点踩状态。 */
    private final ChatMessageFeedbackRepository chatMessageFeedbackRepository;

    /** 工作空间仓储实现，用于补齐会话所属空间名称和云端/本地类型。 */
    private final WorkspaceRepositoryImpl workspaceRepositoryImpl;

    /** 执行记录仓储，用于从最近 run 还原后台任务投影。 */
    private final ChatExecutionRunRepository chatExecutionRunRepository;

    /**
     * 将当前登录用户可见的会话列表转换为接口响应。
     * @param conversations 会话领域对象列表。
     * @param currentUserId 当前登录用户标识。
     * @return 会话响应列表。
     */
    public List<ChatConversationResponse> toConversationResponses(List<ChatConversation> conversations, Long currentUserId) {
        return conversations.stream()
            .map(conversation -> toConversationResponse(conversation, currentUserId))
            .toList();
    }

    /**
     * 将当前登录用户可见的消息列表转换为接口响应。
     * @param messages 消息领域对象列表。
     * @param currentUserId 当前登录用户标识。
     * @return 消息响应列表。
     */
    public List<ChatMessageResponse> toMessageResponses(List<ChatMessage> messages, Long currentUserId) {
        return messages.stream()
            .map(message -> toMessageResponse(message, currentUserId))
            .toList();
    }

    /**
     * 构建公开分享页的完整回放响应。
     * @param conversation 公开分享会话。
     * @param messages 分享范围内的消息列表。
     * @return 公开分享页响应。
     */
    public SharedConversationResponse toSharedConversationResponse(ChatConversation conversation, List<ChatMessage> messages) {
        // 步骤 1：公开页没有登录态，按会话创建人读取空间元信息，避免访问当前用户上下文。
        ChatConversationResponse conversationResponse = toConversationResponse(conversation, null);

        // 步骤 2：消息转换显式传入 null 用户，确保不会查询匿名访问者的投票状态。
        List<ChatMessageResponse> messageResponses = messages.stream()
            .map(message -> toMessageResponse(message, null))
            .toList();

        // 步骤 3：返回会话元信息与消息回放，前端只负责展示，不再补业务字段。
        return new SharedConversationResponse(conversationResponse, messageResponses);
    }

    /**
     * 将应用层已完成分享令牌校验和消息过滤的载体转换为公开分享响应。
     * @param content 公开分享会话与消息载体。
     * @return 公开分享页响应。
     */
    public SharedConversationResponse toSharedConversationResponse(SharedConversationContent content) {
        // 步骤 1：应用层负责加载和过滤，视图层只负责统一投影为接口响应。
        return toSharedConversationResponse(content.conversation(), content.messages());
    }

    /**
     * 将会话领域对象转换为接口响应。
     * @param conversation 会话领域对象。
     * @param currentUserId 当前用户标识，公开分享页为空。
     * @return 会话响应对象。
     */
    public ChatConversationResponse toConversationResponse(ChatConversation conversation, Long currentUserId) {
        // 步骤 1：按当前用户或会话创建人读取工作空间，公开分享场景不能读取登录态。
        Optional<WorkspaceDO> workspaceOptional = currentUserId == null
            ? workspaceRepositoryImpl.findOwnedWorkspaceById(conversation.getWorkspaceId(), conversation.getCreatedBy())
            : workspaceRepositoryImpl.findOwnedWorkspaceById(conversation.getWorkspaceId(), currentUserId);
        WorkspaceDO workspace = workspaceOptional.orElse(null);

        // 步骤 2：从最近聊天 run 计算兼容任务字段，字段名保留 Task 语义但不再读取任务表。
        ConversationTaskProjection taskProjection = resolveTaskProjection(conversation);

        // 步骤 3：合并会话基础字段、工作空间展示字段和任务投影字段，输出前端列表模型。
        return new ChatConversationResponse(
            conversation.getId(),
            conversation.getTitle(),
            conversation.getStatus(),
            conversation.getLastMessageAt(),
            conversation.getLastRunId(),
            conversation.getPinned(),
            conversation.getShareToken(),
            conversation.getTaskCompletionRead(),
            conversation.getWorkspaceId(),
            workspace == null ? null : workspace.getName(),
            resolveWorkspaceType(workspace),
            taskProjection.activeTaskId(),
            taskProjection.activeTaskStatus(),
            taskProjection.lastTaskId(),
            taskProjection.lastTaskStatus(),
            taskProjection.lastTaskFinishedAt()
        );
    }

    /**
     * 将消息领域对象转换为接口响应。
     * @param message 消息领域对象。
     * @param currentUserId 当前用户标识，公开分享页为空。
     * @return 消息响应对象。
     */
    public ChatMessageResponse toMessageResponse(ChatMessage message, Long currentUserId) {
        // 步骤 1：读取消息绑定附件，避免 Controller 了解附件查询规则。
        List<ChatAttachmentResponse> attachments = chatAttachmentService.listByMessageId(message.getId()).stream()
            .map(this::toAttachmentResponse)
            .toList();

        // 步骤 2：从消息正文解析技能标记，兼容已移除 task_skill 表后的历史回放。
        List<String> skillCodes = ChatCapabilityMentionSupport.parseSkillCodes(message.getContent());

        // 步骤 3：登录用户补齐投票状态；公开分享页没有用户标识时保持 null。
        Integer userVote = currentUserId == null
            ? null
            : chatMessageFeedbackRepository.findByMessageIdAndUserId(message.getId(), currentUserId)
                .map(ChatMessageFeedback::getVote)
                .orElse(null);

        return new ChatMessageResponse(
            message.getId(),
            message.getConversationId(),
            message.getRunId(),
            message.getRole(),
            message.getContent(),
            message.getThinkingContent(),
            message.getThinkingDuration(),
            message.getStatus(),
            message.getProvider(),
            message.getModel(),
            message.getErrorMessage(),
            message.getDeleted(),
            message.getCreatedAt(),
            message.getUpdatedAt(),
            attachments,
            skillCodes,
            userVote
        );
    }

    /**
     * 从最新聊天 run 投影会话级兼容任务状态。
     * @param conversation 会话领域对象。
     * @return 来源于聊天 run 的会话后台任务投影。
     */
    private ConversationTaskProjection resolveTaskProjection(ChatConversation conversation) {
        if (conversation.getLastRunId() == null) {
            return ConversationTaskProjection.empty();
        }

        // 步骤 1：优先用会话最近 run 主键匹配执行记录，chat_execution_run 现在只保留 run.id。
        ChatExecutionRun latestRun = chatExecutionRunRepository.findByConversationId(conversation.getId()).stream()
            .filter(run -> conversation.getLastRunId().equals(run.getId()))
            .findFirst()
            .orElse(null);
        if (latestRun == null) {
            return ConversationTaskProjection.empty();
        }

        // 步骤 2：投影标识直接来自聊天 run 主键，不再依赖已删除的 task_id 列。
        Long runProjectionId = latestRun.getId();
        String runStatus = normalizeRunStatus(latestRun.getStatus(), latestRun.getQueueStatus());
        java.time.LocalDateTime finishedAt = latestRun.getFinishedAt();

        // 步骤 3：运行中 run 同时填充 activeTask 与 lastTask；终态 run 只保留 lastTask 兼容字段。
        if (isActiveRunProjectionStatus(runStatus)) {
            return new ConversationTaskProjection(runProjectionId, "RUNNING", runProjectionId, "RUNNING", finishedAt);
        }
        if (isTerminalRunProjectionStatus(runStatus)) {
            return new ConversationTaskProjection(null, null, runProjectionId, runStatus, finishedAt);
        }
        return new ConversationTaskProjection(null, null, runProjectionId, runStatus, finishedAt);
    }

    /**
     * 将执行记录状态折算为前端可理解的后台任务状态。
     * @param runStatus 执行 run 状态。
     * @param queueStatus 排队状态。
     * @return 标准任务状态。
     */
    private String normalizeRunStatus(String runStatus, String queueStatus) {
        if (StrUtil.equalsAnyIgnoreCase(runStatus, "COMPLETED", "SUCCESS")) {
            return "SUCCEEDED";
        }
        if (StrUtil.equalsAnyIgnoreCase(runStatus, "ERROR", "FAILED", "REJECTED", "CANCELLED")) {
            return "FAILED";
        }
        if (StrUtil.equalsAnyIgnoreCase(runStatus, "RUNNING") || StrUtil.equalsAnyIgnoreCase(queueStatus, "WAITING", "ACQUIRED")) {
            return "RUNNING";
        }
        return null;
    }

    /**
     * 判断聊天 run 投影状态是否仍代表后台执行中。
     * @param runStatus run 投影后的兼容任务状态。
     * @return 是否运行中。
     */
    private boolean isActiveRunProjectionStatus(String runStatus) {
        return StrUtil.equalsAnyIgnoreCase(runStatus, "RUNNING");
    }

    /**
     * 判断聊天 run 投影状态是否已经终结。
     * @param runStatus run 投影后的兼容任务状态。
     * @return 是否终态。
     */
    private boolean isTerminalRunProjectionStatus(String runStatus) {
        return StrUtil.equalsAnyIgnoreCase(runStatus, "SUCCEEDED", "FAILED");
    }

    /**
     * 解析会话空间类型，缺失空间时按云端历史兼容展示。
     * @param workspace 工作空间记录。
     * @return 空间类型。
     */
    private ChatConversationResponse.WorkspaceType resolveWorkspaceType(WorkspaceDO workspace) {
        if (workspace == null) {
            return ChatConversationResponse.WorkspaceType.CLOUD;
        }
        if (WorkspaceRepositoryImpl.RUNTIME_TARGET_CLOUD.equalsIgnoreCase(StrUtil.blankToDefault(workspace.getRuntimeTarget(), ""))) {
            return ChatConversationResponse.WorkspaceType.CLOUD;
        }
        return ChatConversationResponse.WorkspaceType.LOCAL;
    }

    /**
     * 将附件领域对象转换为接口响应对象。
     * @param attachment 附件领域对象。
     * @return 附件响应对象。
     */
    private ChatAttachmentResponse toAttachmentResponse(ChatAttachment attachment) {
        return new ChatAttachmentResponse(
            attachment.getId(),
            attachment.getConversationId(),
            attachment.getMessageId(),
            attachment.getAttachmentType(),
            attachment.getFileName(),
            attachment.getFileExt(),
            attachment.getMimeType(),
            attachment.getFileSize(),
            attachment.getPreviewUrl(),
            attachment.getContentSummary(),
            attachment.getStatus(),
            attachment.getCreatedAt()
        );
    }

    /**
     * 会话列表中的后台运行投影，字段名保留 Task 是为了兼容前端现有协议。
     * @param activeTaskId 当前运行聊天 run 的兼容任务标识。
     * @param activeTaskStatus 当前运行聊天 run 的兼容任务状态。
     * @param lastTaskId 最近聊天 run 的兼容任务标识。
     * @param lastTaskStatus 最近聊天 run 的兼容任务状态。
     * @param lastTaskFinishedAt 最近聊天 run 的完成时间。
     */
    private record ConversationTaskProjection(
        Long activeTaskId,
        String activeTaskStatus,
        Long lastTaskId,
        String lastTaskStatus,
        java.time.LocalDateTime lastTaskFinishedAt
    ) {
        /**
         * 构造空投影，表示会话当前没有可展示的后台任务状态。
         * @return 空任务投影。
         */
        private static ConversationTaskProjection empty() {
            return new ConversationTaskProjection(null, null, null, null, null);
        }
    }
}
