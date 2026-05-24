package com.codingx.chat.interfaces.controller;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.mcp.application.service.ChatMcpQueryService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageFeedback;
import com.codingx.chat.domain.repository.ChatMessageFeedbackRepository;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.chat.interfaces.request.ChatMessageFeedbackRequest;
import com.codingx.chat.interfaces.request.BatchUpdateConversationRequest;
import com.codingx.chat.interfaces.request.ConversationPinRequest;
import com.codingx.chat.interfaces.request.CreateConversationRequest;
import com.codingx.chat.interfaces.request.RenameConversationRequest;
import com.codingx.chat.interfaces.request.SendChatMessageRequest;
import com.codingx.chat.interfaces.response.ChatAttachmentResponse;
import com.codingx.chat.interfaces.response.ChatConversationResponse;
import com.codingx.chat.interfaces.response.ChatMessageResponse;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.model.ApiResponse;
import com.codingx.common.idempotent.IdempotentSubmit;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 负责处理 ChatController 的 HTTP 请求并调用应用服务。
 */
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatController {

    /**
     * ChatConversationApplicationService 依赖。
     */
    private final ChatConversationApplicationService chatConversationApplicationService;

    /**
     * ChatApplicationService 依赖。
     */
    private final ChatApplicationService chatApplicationService;

    /**
     * ChatRuntimeGuardService 依赖。
     */
    private final ChatRuntimeGuardService chatRuntimeGuardService;
    private final ChatAttachmentService chatAttachmentService;

    /**
     * ChatReactionService 依赖。
     */
    private final ChatReactionService chatReactionService;
    private final ChatMessageFeedbackRepository chatMessageFeedbackRepository;
    private final ChatMcpQueryService chatMcpQueryService;
    private final ChatSkillRepository chatSkillRepository;
    private final WorkspaceRepositoryImpl workspaceRepositoryImpl;

    /**
     * 创建 createConversation 所需数据并返回结果。
     * @param request 输入参数。
     * @return 输入参数。
     */
    @PostMapping
    public ApiResponse<ChatConversationResponse> createConversation(@RequestBody CreateConversationRequest request) {
        ChatConversation conversation = chatConversationApplicationService.createConversation(
            new CreateConversationCommand(request.title(), request.workspaceId()),
            StpUtil.getLoginIdAsLong()
        );
        return ApiResponse.success(toConversationResponse(conversation));
    }

    /**
     * 返回 listConversations 需要的结果集合。
     * @return 输入参数。
     */
    @GetMapping
    public ApiResponse<List<ChatConversationResponse>> listConversations(@RequestParam(required = false) Long workspaceId) {
        return ApiResponse.success(chatConversationApplicationService.listConversations(StpUtil.getLoginIdAsLong(), workspaceId)
            .stream().map(this::toConversationResponse).toList());
    }

    /**
     * 返回 listMessages 需要的结果集合。
     * @param conversationId 输入参数。
     * @return 输入参数。
     */
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(@PathVariable Long conversationId) {
        return ApiResponse.success(chatConversationApplicationService.listMessages(conversationId, StpUtil.getLoginIdAsLong())
            .stream().map(this::toMessageResponse).toList());
    }

    /**
     * 公开分享页按分享令牌回放消息，不要求登录。
     * @param shareToken 分享令牌。
     * @return 会话与消息回放数据。
     */
    @GetMapping("/shared/{shareToken}")
    public ApiResponse<SharedConversationResponse> getSharedConversation(@PathVariable String shareToken) {
        ChatConversation conversation = chatConversationApplicationService.requireSharedConversation(shareToken);
        List<ChatMessageResponse> messages = chatConversationApplicationService.listSharedMessages(shareToken)
            .stream()
            .map(this::toMessageResponse)
            .toList();
        return ApiResponse.success(new SharedConversationResponse(toSharedConversationResponse(conversation), messages));
    }

    /**
     * 发送 sendMessage 处理的消息或请求。
     * @param conversationId 输入参数。
     * @param request 输入参数。
     * @return 输入参数。
     */
    @PostMapping("/{conversationId}/messages")
    @IdempotentSubmit(
        key = "T(cn.dev33.satoken.stp.StpUtil).getLoginIdAsLong() + ':' + #conversationId",
        message = "当前会话处理中，请稍后再发送",
        code = "CHAT_MESSAGE_DUPLICATE",
        waitTimeMs = 0,
        leaseTimeMs = 30000
    )
    public ApiResponse<Void> sendMessage(@PathVariable Long conversationId, @Valid @RequestBody SendChatMessageRequest request) {
        List<String> selectedSkillCodes = chatSkillRepository.findAllEnabled().stream()
            .map(ChatSkill::getSkillCode)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toList());
        List<String> selectedMcpCodes = chatMcpQueryService.listEnabledMcps().stream()
            .filter(mcp -> mcp.getAvailable() == null || Boolean.TRUE.equals(mcp.getAvailable()))
            .map(ChatMcp::getMcpCode)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toList());
        // 同步入口需要显式收口门控，避免异常或成功路径遗漏释放导致后续请求被误判 busy。
        try {
            chatApplicationService.sendMessage(
                new SendChatMessageCommand(
                    conversationId,
                    request.content(),
                    false,
                    selectedMcpCodes,
                    selectedSkillCodes,
                    null,
                    null,
                    request.attachmentIds() == null ? List.of() : request.attachmentIds()
                ),
                StpUtil.getLoginIdAsLong()
            );
        } finally {
            chatRuntimeGuardService.completeConversation(conversationId);
        }
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_MESSAGE_PROCESSED);
    }

    /**
     * 重命名指定会话。
     * @param conversationId 会话标识。
     * @param request 新标题请求。
     * @return 操作结果。
     */
    @org.springframework.web.bind.annotation.PatchMapping("/{conversationId}")
    public ApiResponse<Void> renameConversation(@PathVariable Long conversationId, @Valid @RequestBody RenameConversationRequest request) {
        chatConversationApplicationService.updateConversationTitle(conversationId, request.title(), StpUtil.getLoginIdAsLong());
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_CONVERSATION_RENAMED);
    }

    /**
     * 删除指定会话。
     * @param conversationId 会话标识。
     * @return 操作结果。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{conversationId}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long conversationId) {
        chatConversationApplicationService.deleteConversation(conversationId, StpUtil.getLoginIdAsLong());
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_CONVERSATION_DELETED);
    }

    /**
     * 切换会话置顶状态，供侧边栏菜单快速固定会话。
     * @param conversationId 会话标识。
     * @param request 置顶状态。
     * @return 操作结果。
     */
    @PatchMapping("/{conversationId}/pin")
    public ApiResponse<Void> updateConversationPinnedState(@PathVariable Long conversationId, @Valid @RequestBody ConversationPinRequest request) {
        chatConversationApplicationService.updateConversationPinnedState(conversationId, request.pinned(), StpUtil.getLoginIdAsLong());
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_CONVERSATION_PIN_UPDATED);
    }

    /**
     * 批量设置会话置顶状态。
     * @param request 批量请求。
     * @return 操作结果。
     */
    @PostMapping("/batch/pin")
    public ApiResponse<Void> batchUpdateConversationPinnedState(@Valid @RequestBody BatchUpdateConversationRequest request) {
        chatConversationApplicationService.batchUpdatePinnedState(request.conversationIds(), request.pinned(), StpUtil.getLoginIdAsLong());
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_CONVERSATION_BATCH_UPDATED);
    }

    /**
     * 为会话生成分享链接令牌，前端只需拼接当前站点地址即可跳转分享页。
     * @param conversationId 会话标识。
     * @return 分享令牌与分享路径。
     */
    @PostMapping("/{conversationId}/share")
    public ApiResponse<ConversationShareResponse> shareConversation(@PathVariable Long conversationId) {
        String shareToken = chatConversationApplicationService.generateShareToken(conversationId, StpUtil.getLoginIdAsLong());
        return ApiResponse.success(new ConversationShareResponse(
            shareToken,
            "/api/chat/conversations/shared/" + shareToken
        ));
    }

    /**
     * 导出会话为 Markdown 文件，供前端下载。
     * @param conversationId 会话标识。
     * @return Markdown 下载响应。
     */
    @GetMapping("/{conversationId}/export")
    public ResponseEntity<byte[]> exportConversation(@PathVariable Long conversationId) {
        String markdown = chatConversationApplicationService.exportConversationAsMarkdown(conversationId, StpUtil.getLoginIdAsLong());
        byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
        String fileName = "conversation-" + conversationId + ".md";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_MARKDOWN_VALUE + ";charset=UTF-8")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
            .body(bytes);
    }

    /**
     * 重新生成最后一条助手回复，保持会话历史不被重复写入新的用户消息。
     * @param conversationId 会话标识。
     * @return 操作结果。
     */
    @PostMapping("/{conversationId}/regenerate")
    public ApiResponse<Void> regenerateConversation(@PathVariable Long conversationId) {
        chatApplicationService.regenerateLastAssistantMessage(conversationId, StpUtil.getLoginIdAsLong());
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_CONVERSATION_REGENERATED);
    }

    /**
     * 取消指定会话当前正在进行的聊天任务。
     * @param conversationId 会话标识。
     * @return 取消结果。
     */
    @PostMapping("/{conversationId}/cancel")
    public ApiResponse<Void> cancelConversation(@PathVariable Long conversationId) {
        chatRuntimeGuardService.cancelConversation(conversationId);
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_CANCEL_REQUESTED);
    }

    /**
     * 提交消息反馈，用于点赞、点踩和原因补充。
     * @param messageId 消息标识。
     * @param request 请求载体。
     * @return 提交结果。
     */
    @PostMapping("/messages/{messageId}/feedback")
    public ApiResponse<Void> submitReaction(@PathVariable Long messageId, @Valid @RequestBody ChatMessageFeedbackRequest request) {
        chatReactionService.submitReaction(messageId, request.conversationId(), StpUtil.getLoginIdAsLong(), request.vote(), request.reason(), request.comment());
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_FEEDBACK_SUBMITTED);
    }

    /**
     * 执行 toConversationResponse 定义的处理逻辑。
     * @param conversation 输入参数。
     * @return 输入参数。
     */
    private ChatConversationResponse toConversationResponse(ChatConversation conversation) {
        return toConversationResponse(StpUtil.getLoginIdAsLong(), conversation);
    }

    /**
     * 转换会话响应，允许公开分享页跳过 workspace 权限校验。
     * @param currentUserId 当前用户标识，可为空。
     * @param conversation 会话对象。
     * @return 响应对象。
     */
    private ChatConversationResponse toConversationResponse(Long currentUserId, ChatConversation conversation) {
        Optional<WorkspaceDO> workspaceOptional = currentUserId == null
            ? workspaceRepositoryImpl.findOwnedWorkspaceById(conversation.getWorkspaceId(), conversation.getCreatedBy())
            : workspaceRepositoryImpl.findOwnedWorkspaceById(conversation.getWorkspaceId(), currentUserId);
        WorkspaceDO workspace = workspaceOptional.orElse(null);
        return new ChatConversationResponse(
            conversation.getId(),
            conversation.getTitle(),
            conversation.getStatus(),
            conversation.getLastMessageAt(),
            conversation.getLastRunId(),
            conversation.getPinned(),
            conversation.getShareToken(),
            conversation.getWorkspaceId(),
            workspace == null ? null : workspace.getName(),
            resolveWorkspaceType(workspace)
        );
    }

    /**
     * 公开分享页专用会话响应，避免依赖当前登录态。
     * @param conversation 会话对象。
     * @return 响应对象。
     */
    private ChatConversationResponse toSharedConversationResponse(ChatConversation conversation) {
        Optional<WorkspaceDO> workspaceOptional = workspaceRepositoryImpl.findOwnedWorkspaceById(
            conversation.getWorkspaceId(),
            conversation.getCreatedBy()
        );
        WorkspaceDO workspace = workspaceOptional.orElse(null);
        return new ChatConversationResponse(
            conversation.getId(),
            conversation.getTitle(),
            conversation.getStatus(),
            conversation.getLastMessageAt(),
            conversation.getLastRunId(),
            conversation.getPinned(),
            conversation.getShareToken(),
            conversation.getWorkspaceId(),
            workspace == null ? null : workspace.getName(),
            resolveWorkspaceType(workspace)
        );
    }

    /**
     * 解析会话空间类型，前端据此区分云端与本地会话展示分组。
     * @param workspace 工作空间记录。
     * @return 空间类型，缺失时默认 CLOUD。
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
     * 执行 toMessageResponse 定义的处理逻辑。
     * @param message 输入参数。
     * @return 输入参数。
     */
    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        List<ChatAttachmentResponse> attachments = chatAttachmentService.listByMessageId(message.getId()).stream()
            .map(this::toAttachmentResponse)
            .toList();
        // 业务约束：消息气泡回放依赖技能编码，按 runId 读取当次绑定技能避免生成完成后丢失。
        List<String> skillCodes = message.getRunId() == null
            ? List.of()
            : chatSkillRepository.findByTaskId(message.getRunId()).stream()
                .map(ChatSkill::getSkillCode)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        // 查询当前用户对该消息的投票状态，用于前端渲染已点赞/点踩状态。
        Integer userVote = chatMessageFeedbackRepository.findByMessageIdAndUserId(message.getId(), StpUtil.getLoginIdAsLong())
            .map(ChatMessageFeedback::getVote)
            .orElse(null);
        return new ChatMessageResponse(
            message.getId(),
            message.getConversationId(),
            message.getRole(),
            message.getContent(),
            message.getThinkingContent(),
            message.getThinkingDuration(),
            message.getStatus(),
            message.getProvider(),
            message.getModel(),
            message.getErrorMessage(),
            message.getCreatedAt(),
            attachments,
            skillCodes,
            userVote
        );
    }

    /**
     * 公开分享页返回会话元信息与消息列表。
     * @param conversation 会话响应。
     * @param messages 消息列表。
     */
    private record SharedConversationResponse(
        ChatConversationResponse conversation,
        List<ChatMessageResponse> messages
    ) {
    }

    /**
     * 分享会话响应，只暴露生成链接所需的令牌与路径。
     * @param shareToken 分享令牌。
     * @param shareUrl 分享地址。
     */
    private record ConversationShareResponse(
        String shareToken,
        String shareUrl
    ) {
    }

    /**
     * 将附件领域对象转换为接口响应对象。
     * @param attachment 附件领域对象。
     * @return 附件响应。
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
}
