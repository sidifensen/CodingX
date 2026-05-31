package com.codingx.chat.interfaces.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatConversationViewService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.interfaces.request.ChatMessageFeedbackRequest;
import com.codingx.chat.interfaces.request.BatchUpdateConversationRequest;
import com.codingx.chat.interfaces.request.ConversationPinRequest;
import com.codingx.chat.interfaces.request.CreateConversationRequest;
import com.codingx.chat.interfaces.request.DeleteChatMessagesRequest;
import com.codingx.chat.interfaces.request.RenameConversationRequest;
import com.codingx.chat.interfaces.request.SendChatMessageRequest;
import com.codingx.chat.interfaces.request.ShareConversationRequest;
import com.codingx.chat.interfaces.response.ChatConversationResponse;
import com.codingx.chat.interfaces.response.ChatMessageResponse;
import com.codingx.chat.interfaces.response.SharedConversationResponse;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.model.ApiResponse;
import com.codingx.common.idempotent.IdempotentSubmit;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
     * 会话应用服务，承接会话创建、列表读取、分享、删除和置顶等会话级业务操作。
     */
    private final ChatConversationApplicationService chatConversationApplicationService;

    /**
     * 聊天主流程服务，负责发送、重新生成等会话执行链路。
     */
    private final ChatApplicationService chatApplicationService;

    /**
     * 会话视图服务，负责把领域对象投影为接口响应，避免控制器直接查询仓储。
     */
    private final ChatConversationViewService chatConversationViewService;

    /**
     * 运行态守卫服务，负责取消当前会话执行。
     */
    private final ChatRuntimeGuardService chatRuntimeGuardService;

    /**
     * 消息反馈服务，负责点赞、点踩和反馈原因提交。
     */
    private final ChatReactionService chatReactionService;

    /**
     * 创建会话并返回前端可展示的会话摘要。
     * @param request 创建会话请求。
     * @return 新会话响应。
     */
    @PostMapping
    public ApiResponse<ChatConversationResponse> createConversation(@RequestBody CreateConversationRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        ChatConversation conversation = chatConversationApplicationService.createConversation(
            new CreateConversationCommand(request.title(), request.workspaceId()),
            userId
        );
        return ApiResponse.success(chatConversationViewService.toConversationResponse(conversation, userId));
    }

    /**
     * 查询当前用户的会话列表。
     * @param workspaceId 工作空间过滤条件，缺省时读取云端历史范围。
     * @return 会话响应列表。
     */
    @GetMapping
    public ApiResponse<List<ChatConversationResponse>> listConversations(@RequestParam(required = false) Long workspaceId) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<ChatConversation> conversations = chatConversationApplicationService.listConversations(userId, workspaceId);
        return ApiResponse.success(chatConversationViewService.toConversationResponses(conversations, userId));
    }

    /**
     * 查询会话消息列表。
     * @param conversationId 会话标识。
     * @return 消息响应列表。
     */
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(@PathVariable Long conversationId) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<ChatMessage> messages = chatConversationApplicationService.listMessages(conversationId, userId);
        return ApiResponse.success(chatConversationViewService.toMessageResponses(messages, userId));
    }

    /**
     * 公开分享页按分享令牌回放消息，不要求登录。
     * @param shareToken 分享令牌。
     * @return 会话与消息回放数据。
     */
    @GetMapping("/shared/{shareToken}")
    public ApiResponse<SharedConversationResponse> getSharedConversation(
        @PathVariable String shareToken,
        @RequestParam(required = false) String messages
    ) {
        ChatConversation conversation = chatConversationApplicationService.requireSharedConversation(shareToken);
        List<ChatMessage> sharedMessages = chatConversationApplicationService.listSharedMessages(shareToken, parseSharedMessageIds(messages));
        return ApiResponse.success(chatConversationViewService.toSharedConversationResponse(conversation, sharedMessages));
    }

    /**
     * 发送同步聊天消息。
     * @param conversationId 会话标识。
     * @param request 消息请求。
     * @return 发送结果。
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
        chatApplicationService.sendSynchronousMessage(
            conversationId,
            request.content(),
            request.skillCodes(),
            request.attachmentIds(),
            StpUtil.getLoginIdAsLong()
        );
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_MESSAGE_PROCESSED);
    }

    /**
     * 删除会话内指定消息；用于消息气泡删除和编辑重发的旧上下文清理。
     * @param conversationId 会话标识。
     * @param request 删除消息请求。
     * @return 删除结果。
     */
    @DeleteMapping("/{conversationId}/messages")
    public ApiResponse<Void> deleteMessages(
        @PathVariable Long conversationId,
        @RequestBody(required = false) DeleteChatMessagesRequest request
    ) {
        chatConversationApplicationService.deleteConversationMessages(
            conversationId,
            request == null ? List.of() : request.messageIds(),
            StpUtil.getLoginIdAsLong()
        );
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_MESSAGES_DELETED);
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
     * 标记会话任务完成提醒为已读，避免刷新后重复显示完成提醒。
     * @param conversationId 会话标识。
     * @return 操作结果。
     */
    @PatchMapping("/{conversationId}/task-completion-read")
    public ApiResponse<Void> markTaskCompletionRead(@PathVariable Long conversationId) {
        chatConversationApplicationService.markTaskCompletionRead(conversationId, StpUtil.getLoginIdAsLong());
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_CONVERSATION_TASK_COMPLETION_READ);
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
    public ApiResponse<ConversationShareResponse> shareConversation(
        @PathVariable Long conversationId,
        @org.springframework.web.bind.annotation.RequestBody(required = false) ShareConversationRequest request
    ) {
        String shareToken = chatConversationApplicationService.generateShareToken(conversationId, StpUtil.getLoginIdAsLong());
        String shareUrl = buildConversationShareUrl(shareToken, request == null ? List.of() : request.messageIds());
        return ApiResponse.success(new ConversationShareResponse(
            shareToken,
            shareUrl
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
     * 解析公开分享页传入的消息过滤参数，非法值直接忽略，避免公开接口因 URL 脏参数失败。
     * @param rawMessageIds 逗号分隔的消息标识。
     * @return 规范化消息标识列表。
     */
    private List<Long> parseSharedMessageIds(String rawMessageIds) {
        if (StrUtil.isBlank(rawMessageIds)) {
            return List.of();
        }
        return StrUtil.splitTrim(rawMessageIds, ',').stream()
            .map(value -> {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException exception) {
                    return null;
                }
            })
            .filter(messageId -> messageId != null && messageId > 0)
            .distinct()
            .toList();
    }

    /**
     * 构造前端公开分享页地址，选中的消息范围以查询参数保留给公开页过滤。
     * @param shareToken 分享令牌。
     * @param messageIds 选中的消息标识。
     * @return 前端公开分享页路径。
     */
    private String buildConversationShareUrl(String shareToken, List<Long> messageIds) {
        // 步骤 1：分享链接只接受正数消息 ID，避免脏请求参数污染公开访问地址。
        List<Long> normalizedMessageIds = messageIds == null ? List.of() : messageIds.stream()
            .filter(messageId -> messageId != null && messageId > 0)
            .distinct()
            .toList();
        if (normalizedMessageIds.isEmpty()) {
            return "/share/chat/" + shareToken;
        }

        // 步骤 2：多消息 ID 统一 URL 编码，公开页再按 messages 参数恢复筛选范围。
        String joinedMessageIds = normalizedMessageIds.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        return "/share/chat/" + shareToken + "?messages=" + URLEncoder.encode(joinedMessageIds, StandardCharsets.UTF_8);
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
}
