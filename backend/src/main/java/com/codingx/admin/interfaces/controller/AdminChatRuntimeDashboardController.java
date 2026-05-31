package com.codingx.admin.interfaces.controller;

import com.codingx.admin.application.service.AdminChatRuntimeDashboardService;
import com.codingx.admin.application.service.AdminChatRuntimeDashboardView;
import com.codingx.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供聊天运行时观测接口，输出队列与线程池关键指标。
 */
@RestController
@RequestMapping("/api/admin/chat/runtime")
@RequiredArgsConstructor
public class AdminChatRuntimeDashboardController {

    /** 运行时观测服务，负责聚合队列和线程池指标快照。 */
    private final AdminChatRuntimeDashboardService adminChatRuntimeDashboardService;

    /**
     * 查询聊天运行时观测快照。
     * @return 运行时指标。
     */
    @GetMapping
    public ApiResponse<AdminChatRuntimeDashboardView> getRuntimeDashboard() {
        return ApiResponse.success(adminChatRuntimeDashboardService.getRuntimeDashboard());
    }
}
