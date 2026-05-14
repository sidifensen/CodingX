package com.codingx.workspace.domain.repository;

/**
 * 定义 WorkspaceRepository 的仓储契约。
 */
public interface WorkspaceRepository {

    /**
     * 校验 ensureExists 需要的前置条件。
     * @param workspaceId 输入参数。
     */
    void ensureExists(Long workspaceId);
}
