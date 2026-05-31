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
    String label, // 前端图表展示标签，小时窗口为 HH:mm，天窗口为 MM-dd。
    String bucketStart, // 分桶起始时间，精确到秒。
    int conversationCount, // 当前分桶内创建的会话数。
    int messageCount, // 当前分桶内创建的消息数。
    int activeUserCount, // 当前分桶内活跃用户数。
    int traceCount, // 当前分桶内链路数。
    int successCount, // 当前分桶内成功链路数。
    int failedCount, // 当前分桶内失败链路数。
    long avgDurationMs // 当前分桶内已记录耗时链路的平均耗时，单位毫秒。
) {
}
