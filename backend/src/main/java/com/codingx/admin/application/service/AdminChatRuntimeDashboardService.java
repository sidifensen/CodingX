package com.codingx.admin.application.service;

import com.codingx.chat.application.service.ChatExecutorMetricsService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.application.service.ChatRuntimeQueueDashboardView;
import com.codingx.chat.infrastructure.runtime.ConversationQueueSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理端聊天运行时观测服务，聚合队列与线程池指标。
 */
@Service
@RequiredArgsConstructor
public class AdminChatRuntimeDashboardService {

    /**
     * 聊天运行态守卫服务，用于读取当前会话队列快照。
     */
    private final ChatRuntimeGuardService chatRuntimeGuardService;

    /**
     * 聊天执行器指标服务，用于读取线程池运行状态。
     */
    private final ChatExecutorMetricsService chatExecutorMetricsService;

    /**
     * 读取运行时观测快照。
     * @return 运行时视图。
     */
    public AdminChatRuntimeDashboardView getRuntimeDashboard() {
        // 步骤 1：先读取会话队列快照，包含并发限制、运行数和等待数。
        ConversationQueueSnapshot queueSnapshot = chatRuntimeGuardService.snapshot();
        // 步骤 2：把队列快照和线程池快照组合成管理端运行时观测视图。
        return new AdminChatRuntimeDashboardView(
            new ChatRuntimeQueueDashboardView(
                queueSnapshot.mode(),
                queueSnapshot.maxConcurrent(),
                queueSnapshot.activeCount(),
                queueSnapshot.waitingCount(),
                queueSnapshot.availablePermits()
            ),
            chatExecutorMetricsService.snapshot()
        );
    }
}
