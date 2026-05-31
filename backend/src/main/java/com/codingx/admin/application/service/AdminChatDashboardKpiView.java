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
    int activeUserCount, // 当前窗口内发生会话或链路活动的用户数。
    int conversationCount, // 当前窗口内创建的会话数。
    int messageCount, // 当前窗口内创建的消息数。
    int workspaceCount, // 当前未删除工作空间总数，不受窗口限制。
    int traceCount, // 当前窗口内创建或启动的链路数。
    int runningTraceCount // 当前窗口内仍处于运行、等待或已获取资源状态的链路数。
) {
}
