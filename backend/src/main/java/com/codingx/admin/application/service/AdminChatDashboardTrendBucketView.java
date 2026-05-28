package com.codingx.admin.application.service;

/**
 * Dashboard 时间分桶趋势视图。
 *
 * @param label 展示标签
 * @param bucketStart 分桶起始时间
 * @param conversationCount 会话数
 * @param messageCount 消息数
 * @param activeUserCount 活跃用户数
 * @param traceCount 链路数
 * @param successCount 成功链路数
 * @param failedCount 失败链路数
 * @param avgDurationMs 平均耗时
 */
public record AdminChatDashboardTrendBucketView(
    String label,
    String bucketStart,
    int conversationCount,
    int messageCount,
    int activeUserCount,
    int traceCount,
    int successCount,
    int failedCount,
    long avgDurationMs
) {
}
