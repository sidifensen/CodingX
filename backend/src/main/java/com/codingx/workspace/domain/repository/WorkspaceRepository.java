package com.codingx.workspace.domain.repository;

import com.codingx.workspace.domain.model.AdminWorkspacePage;
import com.codingx.workspace.domain.model.AdminWorkspaceQuery;

/**
 * 工作空间仓储端口，隔离业务层对工作空间存在性校验和管理端分页查询的持久化细节。
 */
public interface WorkspaceRepository {

    /**
     * 校验工作空间存在，不存在时由实现抛出业务异常。
     * @param workspaceId 工作空间标识。
     */
    void ensureExists(Long workspaceId);

    /**
     * 校验工作空间属于当前用户，不存在或不归属时由实现抛出业务异常。
     * @param workspaceId 工作空间标识，可为空；为空表示任务不绑定具体工作空间。
     * @param userId 当前用户标识。
     */
    void ensureOwnedByUser(Long workspaceId, Long userId);

    /**
     * 管理端分页查询工作空间，仅用于只读排查，不触发默认空间创建。
     * @param query 查询条件。
     * @return 工作空间分页结果。
     */
    AdminWorkspacePage pageForAdmin(AdminWorkspaceQuery query);
}
