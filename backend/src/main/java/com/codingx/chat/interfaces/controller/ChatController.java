package com.codingx.chat.interfaces.controller;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageFeedback;
import com.codingx.chat.domain.repository.ChatMessageFeedbackRepository;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.chat.interfaces.request.ChatMessageFeedbackRequest;
import com.codingx.chat.interfaces.request.CreateConversationRequest;
import com.codingx.chat.interfaces.request.RenameConversationRequest;
import com.codingx.chat.interfaces.request.SendChatMessageRequest;
import com.codingx.chat.interfaces.response.ChatAttachmentResponse;
import com.codingx.chat.interfaces.response.ChatConversationResponse;
import com.codingx.chat.interfaces.response.ChatMessageResponse;
import com.codingx.common.model.ApiResponse;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final ChatMcpRepository chatMcpRepository;
    private final ChatSkillRepository chatSkillRepository;

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
     * 发送 sendMessage 处理的消息或请求。
     * @param conversationId 输入参数。
     * @param request 输入参数。
     * @return 输入参数。
     */
    @PostMapping("/{conversationId}/messages")
    public ApiResponse<Void> sendMessage(@PathVariable Long conversationId, @Valid @RequestBody SendChatMessageRequest request) {
        List<String> selectedSkillCodes = chatSkillRepository.findAllEnabled().stream()
            .map(ChatSkill::getSkillCode)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toList());
        List<String> selectedMcpCodes = chatMcpRepository.findAllEnabled().stream()
            .map(ChatMcp::getMcpCode)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toList());
        chatApplicationService.sendMessage(
            new SendChatMessageCommand(
                conversationId,
                request.content(),
                false,
                selectedMcpCodes,
                selectedSkillCodes,
                null,
                request.attachmentIds() == null ? List.of() : request.attachmentIds()
            ),
            StpUtil.getLoginIdAsLong()
        );
        return ApiResponse.successMessage("message processed");
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
        return ApiResponse.successMessage("conversation renamed");
    }

    /**
     * 删除指定会话。
     * @param conversationId 会话标识。
     * @return 操作结果。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{conversationId}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long conversationId) {
        chatConversationApplicationService.deleteConversation(conversationId, StpUtil.getLoginIdAsLong());
        return ApiResponse.successMessage("conversation deleted");
    }

    /**
     * 取消指定会话当前正在进行的聊天任务。
     * @param conversationId 会话标识。
     * @return 取消结果。
     */
    @PostMapping("/{conversationId}/cancel")
    public ApiResponse<Void> cancelConversation(@PathVariable Long conversationId) {
        chatRuntimeGuardService.cancelConversation(conversationId);
        return ApiResponse.successMessage("cancel requested");
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
        return ApiResponse.successMessage("feedback submitted");
    }

    /**
     * 执行 toConversationResponse 定义的处理逻辑。
     * @param conversation 输入参数。
     * @return 输入参数。
     */
    private ChatConversationResponse toConversationResponse(ChatConversation conversation) {
        return new ChatConversationResponse(
            conversation.getId(),
            conversation.getTitle(),
            conversation.getStatus(),
            conversation.getLastMessageAt(),
            conversation.getLastRunId()
        );
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
