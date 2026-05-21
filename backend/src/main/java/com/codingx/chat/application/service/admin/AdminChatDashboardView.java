package com.codingx.chat.application.service;

/**
 * 定义管理端 Dashboard 聚合视图。
 * @param traceCount Trace 总数。
 * @param runningTraceCount 运行中 Trace 数。
 * @param intentNodeCount 意图节点数。
 * @param skillCount 技能数。
 * @param mappingCount 关键词映射数。
 * @param sampleQuestionCount 示例问题数。
 */
public record AdminChatDashboardView(
    int traceCount,
    int runningTraceCount,
    int intentNodeCount,
    int skillCount,
    int mappingCount,
    int sampleQuestionCount
) {
}
