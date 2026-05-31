package com.codingx.workspace.interfaces.response;

import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 管理端工作空间列表项响应，聚合空间上下文和关联会话数量。
 * @param id 工作空间主键。
 * @param name 工作空间名称。
 * @param repositoryUrl 关联仓库地址。
 * @param branchName 关联分支。
 * @param workingDirectory 本地工作目录。
 * @param runtimeTarget 运行目标编码。
 * @param runtimeTargetLabel 运行目标中文文案。
 * @param createdBy 创建人用户标识。
 * @param conversationCount 关联未删除会话数量。
 * @param createdAt 创建时间。
 * @param updatedAt 更新时间。
 */
@Builder
public record AdminWorkspaceListItemResponse(
    Long id, // 工作空间主键。
    String name, // 工作空间名称。
    String repositoryUrl, // 关联仓库地址，可为空。
    String branchName, // 关联分支名称，可为空。
    String workingDirectory, // 本地运行时工作目录，可为空。
    String runtimeTarget, // 运行目标编码，例如 cloud 或 local。
    String runtimeTargetLabel, // 运行目标中文展示文案。
    Long createdBy, // 创建人用户标识。
    Long conversationCount, // 关联未删除会话数量。
    LocalDateTime createdAt, // 创建时间。
    LocalDateTime updatedAt // 最近更新时间。
) {
}
