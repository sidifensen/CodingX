package com.codingx.backend.chat.interfaces.controller;
import cn.dev33.satoken.stp.StpUtil;
import com.codingx.backend.chat.application.command.CreateConversationCommand;
import com.codingx.backend.chat.application.command.SendChatMessageCommand;
import com.codingx.backend.chat.application.service.ChatApplicationService;
import com.codingx.backend.chat.application.service.ChatConversationApplicationService;
import com.codingx.backend.chat.domain.model.ChatConversation;
import com.codingx.backend.chat.domain.model.ChatMessage;
import com.codingx.backend.chat.interfaces.request.CreateConversationRequest;
import com.codingx.backend.chat.interfaces.request.SendChatMessageRequest;
import com.codingx.backend.chat.interfaces.response.ChatConversationResponse;
import com.codingx.backend.chat.interfaces.response.ChatMessageResponse;
import com.codingx.backend.common.model.ApiResponse;
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
 * Handles HTTP requests for ChatController and delegates work to application services.
 */
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatController {

    /**
     * ChatConversationApplicationService dependency.
     */
    private final ChatConversationApplicationService chatConversationApplicationService;

    /**
     * ChatApplicationService dependency.
     */
    private final ChatApplicationService chatApplicationService;

    /**
     * Creates the data required by createConversation and returns the result.
     * @param request input argument.
     * @return processing result.
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
     * Returns the collection required by listConversations.
     * @return processing result.
     */
    @GetMapping
    public ApiResponse<List<ChatConversationResponse>> listConversations() {
        return ApiResponse.success(chatConversationApplicationService.listConversations(StpUtil.getLoginIdAsLong())
            .stream().map(this::toConversationResponse).toList());
    }

    /**
     * Returns the collection required by listMessages.
     * @param conversationId input argument.
     * @return processing result.
     */
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(@PathVariable Long conversationId) {
        return ApiResponse.success(chatConversationApplicationService.listMessages(conversationId, StpUtil.getLoginIdAsLong())
            .stream().map(this::toMessageResponse).toList());
    }

    /**
     * Sends the message or payload handled by sendMessage.
     * @param conversationId input argument.
     * @param request input argument.
     * @return processing result.
     */
    @PostMapping("/{conversationId}/messages")
    public ApiResponse<Void> sendMessage(@PathVariable Long conversationId, @Valid @RequestBody SendChatMessageRequest request) {
        chatApplicationService.sendMessage(new SendChatMessageCommand(conversationId, request.content()), StpUtil.getLoginIdAsLong());
        return ApiResponse.successMessage("message processed");
    }

    /**
     * Executes the logic defined by toConversationResponse.
     * @param conversation input argument.
     * @return processing result.
     */
    private ChatConversationResponse toConversationResponse(ChatConversation conversation) {
        return new ChatConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getStatus(), conversation.getLastMessageAt());
    }

    /**
     * Executes the logic defined by toMessageResponse.
     * @param message input argument.
     * @return processing result.
     */
    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
            message.getId(), message.getConversationId(), message.getRole(), message.getContent(),
            message.getStatus(), message.getProvider(), message.getModel(), message.getErrorMessage(), message.getCreatedAt()
        );
    }
}
