package com.codingx.chat.interfaces.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.goal.ChatGoalService;
import com.codingx.chat.interfaces.response.ChatGoalResponse;
import com.codingx.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供会话真实目标查询接口，Controller 只负责 HTTP 协议适配和当前用户身份读取。
 */
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatGoalController {

    /** 目标应用服务，负责会话归属过滤和 active goal 查询。 */
    private final ChatGoalService chatGoalService;

    /**
     * 查询当前用户当前会话的 active goal；没有 active goal 时 data 返回 null。
     *
     * @param conversationId 会话 ID。
     * @return active goal 响应或 null。
     */
    @GetMapping("/{conversationId}/goal/active")
    public ApiResponse<ChatGoalResponse> getActiveGoal(@PathVariable Long conversationId) {
        // 步骤 1：从登录态读取用户 ID，目标归属校验由应用服务和仓储过滤完成。
        Long userId = StpUtil.getLoginIdAsLong();
        // 步骤 2：只查询 ACTIVE 状态目标，已完成或取消目标不在刷新后常驻右侧浮窗。
        ChatGoalResponse response = chatGoalService.getActiveGoal(conversationId, userId)
            .map(ChatGoalResponse::from)
            .orElse(null);
        // 步骤 3：无 active goal 仍返回成功响应，前端据此清空 activeGoal 状态。
        return ApiResponse.success(response);
    }
}
