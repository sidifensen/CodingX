package com.codingx.admin.application.service;

import java.util.List;

/**
 * 定义管理端 Dashboard 聚合视图。
 *
 * @param window 当前窗口
 * @param generatedAt 快照生成时间
 * @param kpis 核心指标
 * @param resources 资产指标
 * @param performance 运行健康摘要
 * @param trendBuckets 时间分桶趋势
 */
public record AdminChatDashboardView(
    String window, // 当前统计窗口编码，例如 24h、7d、30d。
    String generatedAt, // 快照生成时间，精确到秒。
    AdminChatDashboardKpiView kpis, // 核心业务指标。
    AdminChatDashboardResourceView resources, // 配置资产指标。
    AdminChatDashboardPerformanceView performance, // 链路运行健康摘要。
    List<AdminChatDashboardTrendBucketView> trendBuckets // 时间分桶趋势，按窗口起点升序排列。
) {
}
