package com.codingx.admin.application.service;

import com.codingx.chat.application.service.ChatRuntimeExecutorDashboardView;
import com.codingx.chat.application.service.ChatRuntimeQueueDashboardView;

/**
 * 聊天运行时观测视图。
 * @param queue 队列观测数据。
 * @param executor 线程池观测数据。
 */
public record AdminChatRuntimeDashboardView(
    ChatRuntimeQueueDashboardView queue,
    ChatRuntimeExecutorDashboardView executor
) {
}
