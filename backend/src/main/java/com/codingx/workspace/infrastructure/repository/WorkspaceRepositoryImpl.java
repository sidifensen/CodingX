package com.codingx.workspace.infrastructure.repository;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 WorkspaceRepository 的持久化校验逻辑。
 */
@Repository
@RequiredArgsConstructor
public class WorkspaceRepositoryImpl implements WorkspaceRepository {

    /**
     * 云端默认空间运行目标常量，前后端通过该值识别“云端会话”归属。
     */
    public static final String RUNTIME_TARGET_CLOUD = "cloud";

    /**
     * 本地空间运行目标常量，目录绑定场景使用。
     */
    public static final String RUNTIME_TARGET_LOCAL = "local";

    /**
     * 默认云端空间名称常量，保证多入口创建时展示语义一致。
     */
    public static final String DEFAULT_CLOUD_WORKSPACE_NAME = "历史记录";

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
        WorkspaceDO workspace = findActiveById(workspaceId).orElse(null);
        if (workspace == null) {
            throw new NotFoundException(ErrorMessageCatalog.WORKSPACE_NOT_FOUND);
        }
    }

    /**
     * 校验工作空间是否属于当前用户，避免跨用户越权绑定会话。
     * @param workspaceId 工作空间标识。
     * @param userId 当前用户标识。
     * @return 工作空间记录。
     */
    public WorkspaceDO requireOwnedWorkspace(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            throw new NotFoundException(ErrorMessageCatalog.WORKSPACE_NOT_FOUND);
        }
        WorkspaceDO workspace = workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getId, workspaceId)
            .eq(WorkspaceDO::getCreatedBy, userId)
            .eq(WorkspaceDO::getDeleted, 0)
            .last("limit 1"));
        if (workspace == null) {
            throw new NotFoundException(ErrorMessageCatalog.WORKSPACE_NOT_FOUND);
        }
        return workspace;
    }

    /**
     * 查询用户可访问的工作空间记录，供会话响应补齐归属信息。
     * @param workspaceId 工作空间标识。
     * @param userId 当前用户标识。
     * @return 工作空间记录（存在时）。
     */
    public Optional<WorkspaceDO> findOwnedWorkspaceById(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getId, workspaceId)
            .eq(WorkspaceDO::getCreatedBy, userId)
            .eq(WorkspaceDO::getDeleted, 0)
            .last("limit 1")));
    }

    /**
     * 确保用户存在默认云端空间，供“未指定 workspaceId”的会话稳定归档。
     * @param userId 用户标识。
     * @param preferredName 候选展示名，可为空。
     * @return 默认云端空间记录。
     */
    public WorkspaceDO ensureDefaultCloudWorkspace(Long userId, String preferredName) {
        WorkspaceDO existing = findDefaultCloudWorkspace(userId);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(IdUtil.getSnowflakeNextId());
        workspace.setName(resolveDefaultCloudWorkspaceName(preferredName));
        workspace.setRuntimeTarget(RUNTIME_TARGET_CLOUD);
        // 业务语义已统一由 runtime_target 承载，避免再依赖旧的 workspace_type 列。
        workspace.setCreatedBy(userId);
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        workspace.setDeleted(0);
        try {
            workspaceMapper.insert(workspace);
            return workspace;
        } catch (Exception ignored) {
            // 并发创建默认空间时回查复用已落库记录，避免同一用户出现多条默认云端空间。
            WorkspaceDO concurrentWorkspace = findDefaultCloudWorkspace(userId);
            if (concurrentWorkspace != null) {
                return concurrentWorkspace;
            }
            throw ignored;
        }
    }

    /**
     * 按主键读取有效工作空间。
     * @param workspaceId 工作空间标识。
     * @return 工作空间记录（存在时）。
     */
    private Optional<WorkspaceDO> findActiveById(Long workspaceId) {
        return Optional.ofNullable(workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getId, workspaceId)
            .eq(WorkspaceDO::getDeleted, 0)
            .last("limit 1")));
    }

    /**
     * 查询用户默认云端空间，约束为 cloud 且非本地目录空间。
     * @param userId 用户标识。
     * @return 默认云端空间，不存在返回 null。
     */
    private WorkspaceDO findDefaultCloudWorkspace(Long userId) {
        if (userId == null) {
            return null;
        }
        return workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getCreatedBy, userId)
            .eq(WorkspaceDO::getRuntimeTarget, RUNTIME_TARGET_CLOUD)
            .isNull(WorkspaceDO::getWorkingDirectory)
            .eq(WorkspaceDO::getDeleted, 0)
            .last("limit 1"));
    }

    /**
     * 组装默认云端空间展示名称，优先使用固定中文语义，避免用户昵称变化导致名称漂移。
     * @param preferredName 候选名称。
     * @return 工作空间名称。
     */
    private String resolveDefaultCloudWorkspaceName(String preferredName) {
        return DEFAULT_CLOUD_WORKSPACE_NAME;
    }
}
