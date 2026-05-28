package com.codingx.admin.application.service;

/**
 * Dashboard 运行健康摘要视图。
 *
 * @param successRate 成功率
 * @param failureRate 失败率
 * @param runningRate 运行中占比
 * @param avgTraceDurationMs 平均链路耗时
 * @param p95TraceDurationMs P95 链路耗时
 */
public record AdminChatDashboardPerformanceView(
    double successRate,
    double failureRate,
    double runningRate,
    long avgTraceDurationMs,
    long p95TraceDurationMs
) {
}
