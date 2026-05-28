package com.codingx.admin.application.service;

/**
 * Dashboard 核心指标视图。
 *
 * @param activeUserCount 当前窗口活跃用户数
 * @param conversationCount 当前窗口会话数
 * @param messageCount 当前窗口消息数
 * @param workspaceCount 当前有效工作空间数
 * @param traceCount 当前窗口链路数
 * @param runningTraceCount 当前窗口运行中链路数
 */
public record AdminChatDashboardKpiView(
    int activeUserCount,
    int conversationCount,
    int messageCount,
    int workspaceCount,
    int traceCount,
    int runningTraceCount
) {
}
