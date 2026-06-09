package com.codingx.admin.application.service;

/**
 * Dashboard 运行健康摘要视图。
 *
 * @param successRate 成功率
 * @param failureRate 失败率
 * @param runningRate 运行中占比
 * @param avgTraceDurationMs 平均链路耗时
 * @param p95TraceDurationMs P95 链路耗时
 * @param timedTraceCount 已记录耗时的链路数
 * @param p95TraceRank P95 在升序耗时样本中的位置
 * @param slowTraceThresholdMs 慢链路阈值
 * @param slowTraceCount 慢链路数量
 */
public record AdminChatDashboardPerformanceView(
    double successRate, // 当前窗口链路成功率，单位百分比，保留 1 位小数。
    double failureRate, // 当前窗口链路失败率，单位百分比，保留 1 位小数。
    double runningRate, // 当前窗口链路运行中占比，单位百分比，保留 1 位小数。
    long avgTraceDurationMs, // 当前窗口已记录耗时链路的平均耗时，单位毫秒。
    long p95TraceDurationMs, // 当前窗口已记录耗时链路的 P95 耗时，单位毫秒。
    int timedTraceCount, // 当前窗口内 durationMs 为正数的链路数量，是 P95 排名解释的样本总数。
    int p95TraceRank, // P95 样本在升序耗时列表中的 1 基排名，无样本时为 0。
    long slowTraceThresholdMs, // 慢链路阈值，单位毫秒，当前用于识别超过 60 秒的链路。
    int slowTraceCount // 当前窗口内耗时严格大于 slowTraceThresholdMs 的链路数量。
) {
}
