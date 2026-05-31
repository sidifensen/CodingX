package com.codingx.chat.interfaces.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 定义用户仓库路径绑定请求体。
 * @param repositoryPath 本地仓库绝对路径。
 * @param workspaceId 需要绑定的本地工作空间标识；为空时服务层按仓库路径解析或创建默认本地空间。
 */
public record BindRepositoryPathRequest(
    @NotBlank(message = "repositoryPath 不能为空") String repositoryPath, // 用户在前端选择的本地仓库绝对路径，不能为空。
    Long workspaceId // 目标本地工作空间主键，可为空；为空时由服务层根据路径完成绑定决策。
) {
}
