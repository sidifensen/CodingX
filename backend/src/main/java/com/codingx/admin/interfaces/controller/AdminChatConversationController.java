package com.codingx.admin.interfaces.controller;

import com.codingx.chat.application.service.AdminChatConversationService;
import com.codingx.chat.interfaces.response.AdminChatConversationDetailResponse;
import com.codingx.chat.interfaces.response.AdminChatConversationListItemResponse;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供管理端会话管理接口，支持会话列表与详情查看。
 */
@RestController
@RequestMapping("/api/admin/chat/conversations")
@RequiredArgsConstructor
public class AdminChatConversationController {

    private final AdminChatConversationService adminChatConversationService;

    /**
     * 分页查询会话列表。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @param keyword 可选关键字，匹配标题或会话 ID。
     * @return 分页结果。
     */
    @GetMapping
    public ApiResponse<PageResult<AdminChatConversationListItemResponse>> listConversations(
        @RequestParam(defaultValue = "1") int current,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(adminChatConversationService.pageConversations(current, size, keyword));
    }

    /**
     * 查询单条会话详情。
     * @param conversationId 会话标识。
     * @return 会话详情。
     */
    @GetMapping("/{conversationId}")
    public ApiResponse<AdminChatConversationDetailResponse> getConversation(@PathVariable Long conversationId) {
        return ApiResponse.success(adminChatConversationService.getConversationDetail(conversationId));
    }
}
