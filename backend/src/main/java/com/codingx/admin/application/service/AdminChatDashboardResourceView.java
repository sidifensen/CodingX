package com.codingx.admin.application.service;

/**
 * Dashboard 资源资产视图。
 *
 * @param skillCount 技能数
 * @param toolCount 工具数
 * @param expertCount 专家数
 * @param mcpCount MCP 数
 * @param intentNodeCount 意图节点数
 * @param mappingCount 关键词映射数
 * @param sampleQuestionCount 示例问题数
 */
public record AdminChatDashboardResourceView(
    int skillCount,
    int toolCount,
    int expertCount,
    int mcpCount,
    int intentNodeCount,
    int mappingCount,
    int sampleQuestionCount
) {
}
