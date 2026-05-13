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

@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatConversationApplicationService chatConversationApplicationService;
    private final ChatApplicationService chatApplicationService;

    @PostMapping
    public ApiResponse<ChatConversationResponse> createConversation(@RequestBody CreateConversationRequest request) {
        ChatConversation conversation = chatConversationApplicationService.createConversation(
            new CreateConversationCommand(request.title()),
            StpUtil.getLoginIdAsLong()
        );
        return ApiResponse.success(toConversationResponse(conversation));
    }

    @GetMapping
    public ApiResponse<List<ChatConversationResponse>> listConversations() {
        return ApiResponse.success(chatConversationApplicationService.listConversations(StpUtil.getLoginIdAsLong())
            .stream().map(this::toConversationResponse).toList());
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(@PathVariable Long conversationId) {
        return ApiResponse.success(chatConversationApplicationService.listMessages(conversationId)
            .stream().map(this::toMessageResponse).toList());
    }

    @PostMapping("/{conversationId}/messages")
    public ApiResponse<Void> sendMessage(@PathVariable Long conversationId, @Valid @RequestBody SendChatMessageRequest request) {
        chatApplicationService.sendMessage(new SendChatMessageCommand(conversationId, request.content()), StpUtil.getLoginIdAsLong());
        return ApiResponse.successMessage("message processed");
    }

    private ChatConversationResponse toConversationResponse(ChatConversation conversation) {
        return new ChatConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getStatus(), conversation.getLastMessageAt());
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
            message.getId(), message.getConversationId(), message.getRole(), message.getContent(),
            message.getStatus(), message.getProvider(), message.getModel(), message.getErrorMessage(), message.getCreatedAt()
        );
    }
}