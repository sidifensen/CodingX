package com.codingx.chat.interfaces.controller;

import cn.dev33.satoken.stp.StpUtil;
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
import com.codingx.chat.interfaces.response.ConversationShareResponse;
import com.codingx.chat.interfaces.response.SharedConversationResponse;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.model.ApiResponse;
import com.codingx.common.idempotent.IdempotentSubmit;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
        // 步骤 1：从登录态读取用户标识，Controller 只负责协议身份入口。
        Long userId = StpUtil.getLoginIdAsLong();
        // 步骤 2：将请求转换为应用命令，创建与工作空间绑定规则交给应用服务。
        ChatConversation conversation = chatConversationApplicationService.createConversation(
            new CreateConversationCommand(request.title(), request.workspaceId()),
            userId
        );
        // 步骤 3：由视图服务投影响应字段，Controller 只封装统一 ApiResponse。
        return ApiResponse.success(chatConversationViewService.toConversationResponse(conversation, userId));
    }

    /**
     * 查询当前用户的会话列表。
     * @param workspaceId 工作空间过滤条件，缺省时读取云端历史范围。
     * @return 会话响应列表。
     */
    @GetMapping
    public ApiResponse<List<ChatConversationResponse>> listConversations(@RequestParam(required = false) Long workspaceId) {
        // 步骤 1：读取登录用户和可选工作空间参数，业务过滤由应用服务处理。
        Long userId = StpUtil.getLoginIdAsLong();
        List<ChatConversation> conversations = chatConversationApplicationService.listConversations(userId, workspaceId);
        // 步骤 2：会话列表响应统一交给视图服务补齐工作空间、任务状态和分享字段。
        return ApiResponse.success(chatConversationViewService.toConversationResponses(conversations, userId));
    }

    /**
     * 查询会话消息列表。
     * @param conversationId 会话标识。
     * @return 消息响应列表。
     */
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(@PathVariable Long conversationId) {
        // 步骤 1：Controller 只传递会话标识和登录用户，归属校验由应用服务完成。
        Long userId = StpUtil.getLoginIdAsLong();
        List<ChatMessage> messages = chatConversationApplicationService.listMessages(conversationId, userId);
        // 步骤 2：消息附件、技能标记和反馈状态由视图服务统一投影。
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
        // 步骤 1：公开入口不读取登录态，仅透传分享令牌和消息筛选参数给应用服务。
        return ApiResponse.success(chatConversationViewService.toSharedConversationResponse(
            chatConversationApplicationService.loadSharedConversation(shareToken, messages)
        ));
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
        // 步骤 1：同步消息入口只转发会话、消息内容、能力选择和附件标识。
        chatApplicationService.sendSynchronousMessage(
            conversationId,
            request.content(),
            request.skillCodes(),
            request.attachmentIds(),
            StpUtil.getLoginIdAsLong()
        );
        // 步骤 2：处理结果文案使用后端统一中文错误/提示目录。
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
        // 步骤 1：请求体为空时按空列表处理，具体是否跳过由应用服务决定。
        chatConversationApplicationService.deleteConversationMessages(
            conversationId,
            request == null ? List.of() : request.messageIds(),
            StpUtil.getLoginIdAsLong()
        );
        // 步骤 2：响应只返回统一删除成功文案，不暴露内部删除数量。
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
        // 步骤 1：Controller 传递路径 ID、新标题和登录用户，标题校验与归属校验由应用服务处理。
        chatConversationApplicationService.updateConversationTitle(conversationId, request.title(), StpUtil.getLoginIdAsLong());
        // 步骤 2：统一封装重命名成功提示。
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_CONVERSATION_RENAMED);
    }

    /**
     * 删除指定会话。
     * @param conversationId 会话标识。
     * @return 操作结果。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{conversationId}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long conversationId) {
        // 步骤 1：删除入口只传递会话 ID 和登录用户，逻辑删除与权限校验由应用服务完成。
        chatConversationApplicationService.deleteConversation(conversationId, StpUtil.getLoginIdAsLong());
        // 步骤 2：统一封装删除成功提示。
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
        // 步骤 1：置顶入口只接收目标状态，排序和更新时间刷新交给应用服务。
        chatConversationApplicationService.updateConversationPinnedState(conversationId, request.pinned(), StpUtil.getLoginIdAsLong());
        // 步骤 2：统一封装置顶状态更新提示。
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_CONVERSATION_PIN_UPDATED);
    }

    /**
     * 标记会话任务完成提醒为已读，避免刷新后重复显示完成提醒。
     * @param conversationId 会话标识。
     * @return 操作结果。
     */
    @PatchMapping("/{conversationId}/task-completion-read")
    public ApiResponse<Void> markTaskCompletionRead(@PathVariable Long conversationId) {
        // 步骤 1：提醒已读入口只传递会话和登录用户，状态独立性由应用服务维护。
        chatConversationApplicationService.markTaskCompletionRead(conversationId, StpUtil.getLoginIdAsLong());
        // 步骤 2：统一封装提醒已读提示。
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_CONVERSATION_TASK_COMPLETION_READ);
    }

    /**
     * 批量设置会话置顶状态。
     * @param request 批量请求。
     * @return 操作结果。
     */
    @PostMapping("/batch/pin")
    public ApiResponse<Void> batchUpdateConversationPinnedState(@Valid @RequestBody BatchUpdateConversationRequest request) {
        // 步骤 1：批量置顶入口只透传 ID 列表和目标状态，ID 规范化由应用服务完成。
        chatConversationApplicationService.batchUpdatePinnedState(request.conversationIds(), request.pinned(), StpUtil.getLoginIdAsLong());
        // 步骤 2：统一封装批量更新提示。
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
        // 步骤 1：分享入口只透传会话、登录用户和可选消息范围，令牌与 URL 规则由应用服务生成。
        return ApiResponse.success(chatConversationApplicationService.shareConversation(
            conversationId,
            StpUtil.getLoginIdAsLong(),
            request == null ? List.of() : request.messageIds()
        ));
    }

    /**
     * 导出会话为 Markdown 文件，供前端下载。
     * @param conversationId 会话标识。
     * @return Markdown 下载响应。
     */
    @GetMapping("/{conversationId}/export")
    public ResponseEntity<byte[]> exportConversation(@PathVariable Long conversationId) {
        // 步骤 1：Markdown 内容由应用服务按归属校验后生成，Controller 只处理下载协议。
        String markdown = chatConversationApplicationService.exportConversationAsMarkdown(conversationId, StpUtil.getLoginIdAsLong());
        // 步骤 2：按 UTF-8 输出字节，并构造稳定下载文件名。
        byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
        String fileName = "conversation-" + conversationId + ".md";
        // 步骤 3：封装 text/markdown 与 attachment 头，交给浏览器触发下载。
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
        // 步骤 1：重新生成入口只传递会话 ID 和登录用户，历史裁剪与任务调度由应用服务负责。
        chatApplicationService.regenerateLastAssistantMessage(conversationId, StpUtil.getLoginIdAsLong());
        // 步骤 2：统一封装重新生成已触发提示。
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_CONVERSATION_REGENERATED);
    }

    /**
     * 取消指定会话当前正在进行的聊天任务。
     * @param conversationId 会话标识。
     * @return 取消结果。
     */
    @PostMapping("/{conversationId}/cancel")
    public ApiResponse<Void> cancelConversation(@PathVariable Long conversationId) {
        // 步骤 1：取消入口只传递会话 ID，运行态取消规则由守卫服务处理。
        chatRuntimeGuardService.cancelConversation(conversationId);
        // 步骤 2：统一封装取消请求已接收提示。
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
        // 步骤 1：反馈入口透传消息、会话、投票和原因字段，幂等更新由反馈服务处理。
        chatReactionService.submitReaction(messageId, request.conversationId(), StpUtil.getLoginIdAsLong(), request.vote(), request.reason(), request.comment());
        // 步骤 2：统一封装反馈提交成功提示。
        return ApiResponse.successMessage(ErrorMessageCatalog.CHAT_FEEDBACK_SUBMITTED);
    }

}
