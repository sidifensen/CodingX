package com.codingx.workspace.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.common.exception.NotFoundException;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 WorkspaceRepository 的持久化校验逻辑。
 */
@Repository
@RequiredArgsConstructor
public class WorkspaceRepositoryImpl implements WorkspaceRepository {

    private final WorkspaceMapper workspaceMapper;

    /**
     * 校验工作空间是否存在且未删除。
     * @param workspaceId 工作空间标识。
     */
    @Override
    public void ensureExists(Long workspaceId) {
        if (workspaceId == null) {
            return;
        }
        WorkspaceDO workspace = workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getId, workspaceId)
            .eq(WorkspaceDO::getDeleted, 0)
            .last("limit 1"));
        if (workspace == null) {
            throw new NotFoundException("Workspace not found");
        }
    }
}
