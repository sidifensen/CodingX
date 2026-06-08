package com.codingx.chat.interfaces.response;

/**
 * 用户侧工作区库存响应，供聊天侧栏展示当前用户全部有效工作区。
 *
 * @param id 工作区主键字符串，前端保持字符串避免 Long 精度丢失。
 * @param name 工作区展示名称，可为空字符串，前端会按目录名兜底。
 * @param runtimeTarget 运行目标编码，通常为 cloud 或 local。
 * @param workingDirectory 本地工作目录，可为空；local 目录工作区使用该值生成侧栏分区。
 * @param repositoryUrl 远程仓库地址，可为空；当前仅透传展示上下文。
 * @param branchName 分支名称，可为空；当前仅透传展示上下文。
 */
public record ChatWorkspaceResponse(
    String id,
    String name,
    String runtimeTarget,
    String workingDirectory,
    String repositoryUrl,
    String branchName
) {
}
