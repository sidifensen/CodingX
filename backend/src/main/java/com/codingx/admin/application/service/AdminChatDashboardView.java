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
    String window,
    String generatedAt,
    AdminChatDashboardKpiView kpis,
    AdminChatDashboardResourceView resources,
    AdminChatDashboardPerformanceView performance,
    List<AdminChatDashboardTrendBucketView> trendBuckets
) {
}
