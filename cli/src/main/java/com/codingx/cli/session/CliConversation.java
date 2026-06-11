package com.codingx.cli.session;

/**
 * CLI 侧会话摘要快照，字段直接来自 Web 会话列表响应但统一转成安全字符串。
 *
 * @param id 会话主键，必须是后端 Long 的字符串形式。
 * @param title 会话标题，可为空字符串。
 * @param status 会话生命周期状态，可为空字符串。
 * @param updatedAt 最近更新时间或最后消息时间，可为空字符串。
 * @param workspaceId 工作空间标识，可为空字符串。
 * @param workspaceType 工作空间类型或运行目标，可为空字符串。
 */
public record CliConversation(
    String id,
    String title,
    String status,
    String updatedAt,
    String workspaceId,
    String workspaceType
) {
}
