package com.codingx.workspace.infrastructure.repository;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import org.springframework.stereotype.Repository;

/**
 * 定义 NoopWorkspaceRepository 的职责边界。
 */
@Repository
public class NoopWorkspaceRepository implements WorkspaceRepository {

    /**
     * 校验 ensureExists 需要的前置条件。
     * @param workspaceId 输入参数。
     */
    @Override
    public void ensureExists(Long workspaceId) {
    }
}
