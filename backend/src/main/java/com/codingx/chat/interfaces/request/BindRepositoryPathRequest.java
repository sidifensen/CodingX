package com.codingx.chat.interfaces.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 定义用户仓库路径绑定请求体。
 * @param repositoryPath 本地仓库绝对路径。
 */
public record BindRepositoryPathRequest(
    @NotBlank(message = "repositoryPath 不能为空") String repositoryPath
) {
}
