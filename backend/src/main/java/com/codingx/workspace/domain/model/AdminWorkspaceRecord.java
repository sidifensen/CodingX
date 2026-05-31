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
    Long id, // 工作空间主键，管理端详情、筛选和跳转使用。
    String name, // 工作空间展示名称。
    String repositoryUrl, // 关联代码仓库地址，可为空。
    String branchName, // 工作空间当前关联分支，可为空。
    String workingDirectory, // 本地工作空间目录，可为空；仅 local 运行目标通常有值。
    String runtimeTarget, // 运行目标编码，区分 cloud、local 等执行环境。
    Long createdBy, // 工作空间创建人用户标识。
    Long conversationCount, // 该工作空间下未删除会话数量。
    LocalDateTime createdAt, // 工作空间创建时间。
    LocalDateTime updatedAt // 工作空间最近更新时间。
) {
}
