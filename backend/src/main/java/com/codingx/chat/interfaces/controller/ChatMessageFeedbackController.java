package com.codingx.chat.interfaces.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.interfaces.request.ChatMessageFeedbackRequest;
import com.codingx.common.model.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供兼容旧路径的消息反馈接口，避免前端迁移期出现 404。
 */
@RestController
@RequestMapping("/api/chat/messages")
@RequiredArgsConstructor
public class ChatMessageFeedbackController {

    private final ChatReactionService chatReactionService;

    /**
     * 提交消息反馈，用于兼容旧路径 `/api/chat/messages/{messageId}/feedback`。
     * @param messageId 消息标识。
     * @param request 请求载体。
     * @return 提交结果。
     */
    @PostMapping("/{messageId}/feedback")
    public ApiResponse<Void> submitReaction(@PathVariable Long messageId, @Valid @RequestBody ChatMessageFeedbackRequest request) {
        chatReactionService.submitReaction(messageId, request.conversationId(), StpUtil.getLoginIdAsLong(), request.vote(), request.reason(), request.comment());
        return ApiResponse.successMessage("feedback submitted");
    }
}
