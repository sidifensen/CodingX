package com.codingx.admin.application.service;

import com.codingx.chat.application.service.ChatRuntimeExecutorDashboardView;
import com.codingx.chat.application.service.ChatRuntimeQueueDashboardView;

/**
 * 聊天运行时观测视图。
 * @param queue 队列观测数据。
 * @param executor 线程池观测数据。
 */
public record AdminChatRuntimeDashboardView(
    ChatRuntimeQueueDashboardView queue, // 会话队列观测数据，包含并发上限、运行数和等待数。
    ChatRuntimeExecutorDashboardView executor // 聊天线程池观测数据，包含活跃线程、队列和拒绝等指标。
) {
}
