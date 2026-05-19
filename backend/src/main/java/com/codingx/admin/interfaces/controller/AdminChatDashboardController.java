package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.AdminChatDashboardService;
import com.codingx.chat.application.service.AdminChatDashboardView;
import com.codingx.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供聊天运行时 Dashboard 聚合接口。
 */
@RestController
@RequestMapping("/api/admin/chat/dashboard")
@RequiredArgsConstructor
public class AdminChatDashboardController {

    private final AdminChatDashboardService adminChatDashboardService;

    @GetMapping
    public ApiResponse<AdminChatDashboardView> getDashboard() {
        return ApiResponse.success(adminChatDashboardService.getDashboard());
    }
}
