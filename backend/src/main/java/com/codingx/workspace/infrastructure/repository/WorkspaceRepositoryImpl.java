package com.codingx.workspace.infrastructure.repository;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatConversationMapper;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import com.codingx.workspace.domain.model.AdminWorkspacePage;
import com.codingx.workspace.domain.model.AdminWorkspaceQuery;
import com.codingx.workspace.domain.model.AdminWorkspaceRecord;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import java.time.LocalDateTime;
import java.util.List;
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
    public static final String DEFAULT_CLOUD_WORKSPACE_NAME = "云端历史记录";

    /**
     * 默认本地空间名称常量，用于本地运行但尚未选择目录的历史归档。
     */
    public static final String DEFAULT_LOCAL_WORKSPACE_NAME = "本地历史记录";

    /** 工作空间 Mapper，用于读写 workspace 表并支持管理端分页。 */
    private final WorkspaceMapper workspaceMapper;
    /** 会话 Mapper，用于按工作空间统计未删除会话数量。 */
    private final ChatConversationMapper chatConversationMapper;

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
     * 管理端分页查询工作空间，并按空间补齐未删除会话数量。
     * @param query 查询条件。
     * @return 工作空间分页结果。
     */
    @Override
    public AdminWorkspacePage pageForAdmin(AdminWorkspaceQuery query) {
        int normalizedCurrent = Math.max(1, query == null ? 1 : query.current());
        int normalizedSize = Math.max(1, Math.min(query == null ? 10 : query.size(), 100));
        String normalizedKeyword = StrUtil.trimToEmpty(query == null ? null : query.keyword());
        String normalizedRuntimeTarget = normalizeRuntimeTarget(query == null ? null : query.runtimeTarget());

        LambdaQueryWrapper<WorkspaceDO> wrapper = new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getDeleted, 0)
            .eq(StrUtil.isNotBlank(normalizedRuntimeTarget), WorkspaceDO::getRuntimeTarget, normalizedRuntimeTarget)
            .and(StrUtil.isNotBlank(normalizedKeyword), condition -> {
                Long workspaceId = parseWorkspaceId(normalizedKeyword);
                condition.like(WorkspaceDO::getName, normalizedKeyword)
                    .or()
                    .like(WorkspaceDO::getRepositoryUrl, normalizedKeyword)
                    .or()
                    .like(WorkspaceDO::getBranchName, normalizedKeyword)
                    .or()
                    .like(WorkspaceDO::getWorkingDirectory, normalizedKeyword);
                if (workspaceId != null) {
                    condition.or().eq(WorkspaceDO::getId, workspaceId);
                }
            })
            .orderByDesc(WorkspaceDO::getUpdatedAt)
            .orderByDesc(WorkspaceDO::getId);

        List<WorkspaceDO> allRecords = workspaceMapper.selectList(wrapper);
        long total = allRecords.size();
        long pages = total == 0 ? 1 : (total + normalizedSize - 1L) / normalizedSize;
        int fromIndex = Math.min((normalizedCurrent - 1) * normalizedSize, allRecords.size());
        int toIndex = Math.min(fromIndex + normalizedSize, allRecords.size());
        List<AdminWorkspaceRecord> records = allRecords.subList(fromIndex, toIndex).stream()
            .map(this::toAdminRecord)
            .toList();

        return new AdminWorkspacePage(records, total, (long) normalizedSize, (long) normalizedCurrent, pages);
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
        // 步骤 1：先查找用户默认云端空间，已存在时直接复用，避免重复创建历史归档空间。
        WorkspaceDO existing = findDefaultCloudWorkspace(userId);
        if (existing != null) {
            return existing;
        }
        // 步骤 2：构造默认云端 workspace 数据，名称优先使用候选展示名兜底。
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
            // 步骤 3：尝试插入新空间；并发唯一约束冲突时回查并复用已落库记录。
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
     * 确保用户存在默认本地历史空间，供本地运行但未选择目录的请求归档。
     * @param userId 用户标识。
     * @return 默认本地空间记录。
     */
    public WorkspaceDO ensureDefaultLocalWorkspace(Long userId) {
        // 步骤 1：先查找用户默认本地历史空间，存在时不再创建新记录。
        WorkspaceDO existing = findDefaultLocalWorkspace(userId);
        if (existing != null) {
            return existing;
        }
        // 步骤 2：构造默认本地 workspace，运行目标固定为 local 且不绑定具体目录。
        LocalDateTime now = LocalDateTime.now();
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(IdUtil.getSnowflakeNextId());
        workspace.setName(DEFAULT_LOCAL_WORKSPACE_NAME);
        workspace.setRuntimeTarget(RUNTIME_TARGET_LOCAL);
        workspace.setCreatedBy(userId);
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        workspace.setDeleted(0);
        try {
            // 步骤 3：插入失败时按并发创建处理，回查可复用记录后再决定是否抛出原异常。
            workspaceMapper.insert(workspace);
            return workspace;
        } catch (Exception ignored) {
            // 并发创建默认本地空间时回查复用，避免同一用户出现多个“本地历史记录”。
            WorkspaceDO concurrentWorkspace = findDefaultLocalWorkspace(userId);
            if (concurrentWorkspace != null) {
                return concurrentWorkspace;
            }
            throw ignored;
        }
    }

    /**
     * 确保用户指定本地目录存在 workspace 记录，按规范化路径复用，避免同一路径重复建空间。
     * @param userId 用户标识。
     * @param normalizedWorkingDirectory 规范化后的本地目录路径。
     * @param workspaceName 工作空间展示名。
     * @return 本地目录工作空间记录。
     */
    public WorkspaceDO ensureLocalWorkspace(Long userId, String normalizedWorkingDirectory, String workspaceName) {
        // 步骤 1：先统一目录分隔符并按用户+路径查找既有本地 workspace。
        String normalizedPath = StrUtil.trimToEmpty(normalizedWorkingDirectory).replace('\\', '/');
        WorkspaceDO existing = findLocalWorkspaceByPath(userId, normalizedPath);
        if (existing != null) {
            return existing;
        }
        // 步骤 2：没有既有记录时创建新的本地目录 workspace，名称为空则使用默认本地名称。
        LocalDateTime now = LocalDateTime.now();
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(IdUtil.getSnowflakeNextId());
        workspace.setName(StrUtil.blankToDefault(workspaceName, DEFAULT_LOCAL_WORKSPACE_NAME));
        workspace.setWorkingDirectory(normalizedPath);
        workspace.setRuntimeTarget(RUNTIME_TARGET_LOCAL);
        workspace.setCreatedBy(userId);
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        workspace.setDeleted(0);
        try {
            // 步骤 3：插入时如遇并发创建同一路径，回查复用并避免重复项目。
            workspaceMapper.insert(workspace);
            return workspace;
        } catch (Exception ignored) {
            // 并发绑定同一路径时回查复用，避免前端重复点击生成重复项目。
            WorkspaceDO concurrentWorkspace = findLocalWorkspaceByPath(userId, normalizedPath);
            if (concurrentWorkspace != null) {
                return concurrentWorkspace;
            }
            throw ignored;
        }
    }

    /**
     * 读取用户默认云端工作空间，不创建新记录。
     * 列表查询会用它区分“默认云端历史”与“本地工作空间历史”，避免把本地会话混进 Web 侧历史页。
     * @param userId 用户标识。
     * @return 默认云端工作空间记录（存在时）。
     */
    public Optional<WorkspaceDO> findDefaultCloudWorkspaceByUserId(Long userId) {
        return Optional.ofNullable(findDefaultCloudWorkspace(userId));
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
     * 查询用户默认本地历史空间，约束为 local 且无本地目录。
     * @param userId 用户标识。
     * @return 默认本地空间，不存在返回 null。
     */
    private WorkspaceDO findDefaultLocalWorkspace(Long userId) {
        if (userId == null) {
            return null;
        }
        return workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getCreatedBy, userId)
            .eq(WorkspaceDO::getRuntimeTarget, RUNTIME_TARGET_LOCAL)
            .isNull(WorkspaceDO::getWorkingDirectory)
            .eq(WorkspaceDO::getDeleted, 0)
            .last("limit 1"));
    }

    /**
     * 按本地目录查询用户工作空间。
     * @param userId 用户标识。
     * @param normalizedWorkingDirectory 规范化后的本地目录。
     * @return 本地工作空间，不存在返回 null。
     */
    private WorkspaceDO findLocalWorkspaceByPath(Long userId, String normalizedWorkingDirectory) {
        if (userId == null || StrUtil.isBlank(normalizedWorkingDirectory)) {
            return null;
        }
        return workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getCreatedBy, userId)
            .eq(WorkspaceDO::getRuntimeTarget, RUNTIME_TARGET_LOCAL)
            .eq(WorkspaceDO::getWorkingDirectory, normalizedWorkingDirectory)
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

    /**
     * 转换管理端工作空间记录，并按工作空间统计有效会话数量。
     * @param workspace 工作空间持久化对象。
     * @return 管理端列表记录。
     */
    private AdminWorkspaceRecord toAdminRecord(WorkspaceDO workspace) {
        return new AdminWorkspaceRecord(
            workspace.getId(),
            workspace.getName(),
            workspace.getRepositoryUrl(),
            workspace.getBranchName(),
            workspace.getWorkingDirectory(),
            workspace.getRuntimeTarget(),
            workspace.getCreatedBy(),
            countActiveConversations(workspace.getId()),
            workspace.getCreatedAt(),
            workspace.getUpdatedAt()
        );
    }

    /**
     * 统计工作空间下未删除会话，供管理端快速判断空间活跃度。
     * @param workspaceId 工作空间标识。
     * @return 未删除会话数量。
     */
    private long countActiveConversations(Long workspaceId) {
        if (workspaceId == null) {
            return 0L;
        }
        return chatConversationMapper.selectCount(new LambdaQueryWrapper<ChatConversationDO>()
            .eq(ChatConversationDO::getWorkspaceId, workspaceId)
            .eq(ChatConversationDO::getDeleted, 0));
    }

    /**
     * 归一化运行目标筛选值，ALL 或空值表示不过滤。
     * @param runtimeTarget 原始运行目标筛选。
     * @return 可用于数据库过滤的运行目标。
     */
    private String normalizeRuntimeTarget(String runtimeTarget) {
        String normalized = StrUtil.trimToEmpty(runtimeTarget).toLowerCase();
        if (StrUtil.isBlank(normalized) || "all".equals(normalized)) {
            return "";
        }
        if (RUNTIME_TARGET_CLOUD.equals(normalized) || RUNTIME_TARGET_LOCAL.equals(normalized)) {
            return normalized;
        }
        return "";
    }

    /**
     * 尝试按数字关键字匹配工作空间 ID，非数字时忽略 ID 条件。
     * @param keyword 关键字。
     * @return 工作空间 ID 或 null。
     */
    private Long parseWorkspaceId(String keyword) {
        try {
            return Long.valueOf(keyword);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
