package com.codingx.admin.interfaces.controller;

import com.codingx.admin.application.service.AdminChatFeedbackService;
import com.codingx.chat.interfaces.response.AdminChatMessageFeedbackDetailResponse;
import com.codingx.chat.interfaces.response.AdminChatMessageFeedbackListItemResponse;
import com.codingx.chat.interfaces.response.AdminChatMessageReferenceResponse;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供管理端反馈管理接口，支撑反馈分页、详情和来源回溯。
 */
@RestController
@RequestMapping("/api/admin/chat/feedbacks")
@RequiredArgsConstructor
public class AdminChatFeedbackController {

    /** 反馈管理服务，负责反馈分页、详情聚合和来源回溯。 */
    private final AdminChatFeedbackService adminChatFeedbackService;

    /**
     * 分页查询反馈列表，支持关键字和点赞/点踩过滤。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @param keyword 关键字。
     * @param vote 投票值过滤。
     * @return 反馈分页列表。
     */
    @GetMapping
    public ApiResponse<PageResult<AdminChatMessageFeedbackListItemResponse>> listFeedbacks(
        @RequestParam(defaultValue = "1") int current,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(required = false) Integer vote
    ) {
        return ApiResponse.success(adminChatFeedbackService.pageFeedback(current, size, keyword, vote));
    }

    /**
     * 查询单条反馈详情。
     * @param feedbackId 反馈主键。
     * @return 反馈详情。
     */
    @GetMapping("/{feedbackId}")
    public ApiResponse<AdminChatMessageFeedbackDetailResponse> getFeedbackDetail(@PathVariable Long feedbackId) {
        return ApiResponse.success(adminChatFeedbackService.getFeedbackDetail(feedbackId));
    }

    /**
     * 查询反馈关联消息的引用来源列表。
     * @param feedbackId 反馈主键。
     * @return 来源列表。
     */
    @GetMapping("/{feedbackId}/references")
    public ApiResponse<List<AdminChatMessageReferenceResponse>> listReferences(@PathVariable Long feedbackId) {
        return ApiResponse.success(adminChatFeedbackService.listReferencesByFeedback(feedbackId));
    }
}

