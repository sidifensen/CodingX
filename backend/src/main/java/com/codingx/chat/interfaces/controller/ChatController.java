package com.codingx.chat.interfaces.controller;
import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.interfaces.request.CreateConversationRequest;
import com.codingx.chat.interfaces.request.SendChatMessageRequest;
import com.codingx.chat.interfaces.response.ChatConversationResponse;
import com.codingx.chat.interfaces.response.ChatMessageResponse;
import com.codingx.common.model.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * 创建 createConversation 所需数据并返回结果。
     * @param request 输入参数。
     * @return 输入参数。
     */
    @PostMapping
    public ApiResponse<ChatConversationResponse> createConversation(@RequestBody CreateConversationRequest request) {
        ChatConversation conversation = chatConversationApplicationService.createConversation(
            new CreateConversationCommand(request.title()),
            StpUtil.getLoginIdAsLong()
        );
        return ApiResponse.success(toConversationResponse(conversation));
    }

    /**
     * 返回 listConversations 需要的结果集合。
     * @return 输入参数。
     */
    @GetMapping
    public ApiResponse<List<ChatConversationResponse>> listConversations() {
        return ApiResponse.success(chatConversationApplicationService.listConversations(StpUtil.getLoginIdAsLong())
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
        chatApplicationService.sendMessage(new SendChatMessageCommand(conversationId, request.content()), StpUtil.getLoginIdAsLong());
        return ApiResponse.successMessage("message processed");
    }

    /**
     * 执行 toConversationResponse 定义的处理逻辑。
     * @param conversation 输入参数。
     * @return 输入参数。
     */
    private ChatConversationResponse toConversationResponse(ChatConversation conversation) {
        return new ChatConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getStatus(), conversation.getLastMessageAt());
    }

    /**
     * 执行 toMessageResponse 定义的处理逻辑。
     * @param message 输入参数。
     * @return 输入参数。
     */
    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
            message.getId(), message.getConversationId(), message.getRole(), message.getContent(),
            message.getStatus(), message.getProvider(), message.getModel(), message.getErrorMessage(), message.getCreatedAt()
        );
    }
}
