package com.codingx.workspace.domain.model;

import java.time.LocalDateTime;

/**
 * 管理端工作空间列表记录，包含空间上下文与关联会话数量。
 * @param id 工作空间主键。
 * @param name 工作空间名称。
 * @param repositoryUrl 关联代码仓库地址。
 * @param branchName 工作空间分支。
 * @param workingDirectory 本地工作目录。
 * @param runtimeTarget 运行目标类型。
 * @param createdBy 创建人用户标识。
 * @param conversationCount 未删除会话数量。
 * @param createdAt 创建时间。
 * @param updatedAt 更新时间。
 */
public record AdminWorkspaceRecord(
    Long id,
    String name,
    String repositoryUrl,
    String branchName,
    String workingDirectory,
    String runtimeTarget,
    Long createdBy,
    Long conversationCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
