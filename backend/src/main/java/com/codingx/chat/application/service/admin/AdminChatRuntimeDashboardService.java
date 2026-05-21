package com.codingx.chat.application.service;

import com.codingx.chat.infrastructure.runtime.ConversationQueueSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理端聊天运行时观测服务，聚合队列与线程池指标。
 */
@Service
@RequiredArgsConstructor
public class AdminChatRuntimeDashboardService {

    private final ChatRuntimeGuardService chatRuntimeGuardService;
    private final ChatExecutorMetricsService chatExecutorMetricsService;

    /**
     * 读取运行时观测快照。
     * @return 运行时视图。
     */
    public AdminChatRuntimeDashboardView getRuntimeDashboard() {
        ConversationQueueSnapshot queueSnapshot = chatRuntimeGuardService.snapshot();
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
