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
    double successRate, // 当前窗口链路成功率，单位百分比，保留 1 位小数。
    double failureRate, // 当前窗口链路失败率，单位百分比，保留 1 位小数。
    double runningRate, // 当前窗口链路运行中占比，单位百分比，保留 1 位小数。
    long avgTraceDurationMs, // 当前窗口已记录耗时链路的平均耗时，单位毫秒。
    long p95TraceDurationMs // 当前窗口已记录耗时链路的 P95 耗时，单位毫秒。
) {
}
