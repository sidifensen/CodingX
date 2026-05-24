package com.codingx.workspace.domain.repository;

import com.codingx.workspace.domain.model.AdminWorkspacePage;
import com.codingx.workspace.domain.model.AdminWorkspaceQuery;

/**
 * 定义 WorkspaceRepository 的仓储契约。
 */
public interface WorkspaceRepository {

    /**
     * 校验 ensureExists 需要的前置条件。
     * @param workspaceId 输入参数。
     */
    void ensureExists(Long workspaceId);

    /**
     * 管理端分页查询工作空间，仅用于只读排查，不触发默认空间创建。
     * @param query 查询条件。
     * @return 工作空间分页结果。
     */
    AdminWorkspacePage pageForAdmin(AdminWorkspaceQuery query);
}
