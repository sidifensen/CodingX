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
    int skillCount, // 当前未删除技能配置数量。
    int toolCount, // 当前未删除工具配置数量。
    int expertCount, // 当前未删除专家配置数量。
    int mcpCount, // 当前未删除 MCP 配置数量。
    int intentNodeCount, // 当前意图节点数量。
    int mappingCount, // 当前关键词映射规则数量。
    int sampleQuestionCount // 当前启用示例问题数量。
) {
}
