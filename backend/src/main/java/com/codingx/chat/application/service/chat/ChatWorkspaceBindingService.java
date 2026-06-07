package com.codingx.chat.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.governance.application.service.LongTermMemoryService;
import com.codingx.governance.application.service.ProjectProfileService;
import com.codingx.governance.domain.model.GovernanceProjectProfile;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 维护用户与本地仓库目录的绑定关系，供聊天工具执行链路复用。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatWorkspaceBindingService {

    /** 用户到本地仓库路径的进程内绑定缓存，用于未显式传 workspaceId 的聊天请求快速回退。 */
    private final Map<Long, Path> repositoryPathByUserId = new ConcurrentHashMap<>();
    /** 工作空间 Mapper，用于查询用户已持久化的本地工作空间目录。 */
    private final WorkspaceMapper workspaceMapper;
    /** 工作空间仓储实现，用于创建或复用用户默认本地工作空间。 */
    private final WorkspaceRepositoryImpl workspaceRepositoryImpl;
    /** 项目画像服务，用于绑定本地仓库后生成 Agent 可消费的仓库画像。 */
    private final ProjectProfileService projectProfileService;
    /** 长期记忆服务，用于返回当前用户在该工作空间下的已生效记忆数量。 */
    private final LongTermMemoryService longTermMemoryService;

    /**
     * 绑定当前登录用户的仓库目录。
     * @param repositoryPath 用户选择的仓库路径。
     * @return 规范化后的绝对路径。
     */
    public WorkspaceBindingResult bindRepositoryPathForCurrentUser(String repositoryPath) {
        Long userId = StpUtil.getLoginIdAsLong();
        Path normalizedPath = normalizeAndValidateRepositoryPath(repositoryPath);
        repositoryPathByUserId.put(userId, normalizedPath);
        String normalizedPathText = normalizedPath.toString().replace('\\', '/');
        String workspaceName = normalizedPath.getFileName() == null ? normalizedPathText : normalizedPath.getFileName().toString();
        WorkspaceDO workspace = workspaceRepositoryImpl.ensureLocalWorkspace(userId, normalizedPathText, workspaceName);
        ProjectProfileView projectProfile = scanProjectProfile(workspace.getId(), normalizedPath);
        int activeMemoryCount = countActiveMemories(userId, workspace.getId());
        return new WorkspaceBindingResult(
            workspace.getId(),
            normalizedPathText,
            workspace.getName(),
            projectProfile,
            activeMemoryCount
        );
    }

    /**
     * 查询指定用户绑定的仓库目录。
     * @param userId 用户标识。
     * @return 目录路径。
     */
    public Optional<Path> findRepositoryPathByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(repositoryPathByUserId.get(userId))
            .filter(Files::exists)
            .filter(Files::isDirectory);
    }

    /**
     * 校验并规范化仓库路径，防止写入不存在路径或文件路径。
     * @param repositoryPath 原始路径字符串。
     * @return 规范化路径。
     */
    private Path normalizeAndValidateRepositoryPath(String repositoryPath) {
        if (StrUtil.isBlank(repositoryPath)) {
            throw new BusinessException("CHAT_WORKSPACE_PATH_REQUIRED", ErrorMessageCatalog.CHAT_WORKSPACE_PATH_REQUIRED);
        }
        Path normalizedPath = Path.of(repositoryPath).toAbsolutePath().normalize();
        if (!Files.exists(normalizedPath)) {
            throw new BusinessException("CHAT_WORKSPACE_PATH_NOT_FOUND", ErrorMessageCatalog.CHAT_WORKSPACE_PATH_NOT_FOUND);
        }
        if (!Files.isDirectory(normalizedPath)) {
            throw new BusinessException("CHAT_WORKSPACE_PATH_INVALID", ErrorMessageCatalog.CHAT_WORKSPACE_PATH_INVALID_DIRECTORY);
        }
        return normalizedPath;
    }

    /**
     * 绑定目录后刷新项目画像；扫描失败时降级读取最近画像，避免画像落库异常阻断目录绑定。
     * @param workspaceId 工作空间标识。
     * @param normalizedPath 已校验的本地仓库路径。
     * @return 项目画像视图，缺失时返回 null。
     */
    private ProjectProfileView scanProjectProfile(Long workspaceId, Path normalizedPath) {
        if (projectProfileService == null || workspaceId == null || normalizedPath == null) {
            return null;
        }
        try {
            return toProjectProfileView(projectProfileService.scanWorkspace(workspaceId, normalizedPath));
        } catch (RuntimeException exception) {
            log.warn("本地仓库项目画像扫描失败: workspaceId={}, path={}, message={}", workspaceId, normalizedPath, exception.getMessage());
            return toProjectProfileView(projectProfileService.findLatestByWorkspaceId(workspaceId));
        }
    }

    /**
     * 统计当前工作空间已生效长期记忆数量，统计失败时返回 0 保障绑定响应稳定。
     * @param userId 当前用户标识。
     * @param workspaceId 工作空间标识。
     * @return 已生效记忆数量。
     */
    private int countActiveMemories(Long userId, Long workspaceId) {
        if (longTermMemoryService == null) {
            return 0;
        }
        try {
            return longTermMemoryService.countActiveByUserAndWorkspace(userId, workspaceId);
        } catch (RuntimeException exception) {
            log.warn("已生效长期记忆统计失败: userId={}, workspaceId={}, message={}", userId, workspaceId, exception.getMessage());
            return 0;
        }
    }

    private ProjectProfileView toProjectProfileView(GovernanceProjectProfile profile) {
        if (profile == null) {
            return null;
        }
        return new ProjectProfileView(
            profile.getSummary(),
            profile.getModuleMapJson(),
            profile.getTestCommandsJson(),
            profile.getKeyEntrypointsJson(),
            profile.getRiskPointsJson(),
            profile.getAgentContext()
        );
    }

    /**
     * 按工作空间标识查询对应目录，供会话级执行上下文回放。
     * @param workspaceId 工作空间标识。
     * @return 目录路径。
     */
    public Optional<Path> findRepositoryPathByWorkspaceId(Long workspaceId) {
        if (workspaceId == null) {
            return Optional.empty();
        }
        WorkspaceDO workspace = workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getId, workspaceId)
            .eq(WorkspaceDO::getDeleted, 0)
            .last("LIMIT 1"));
        if (workspace == null || StrUtil.isBlank(workspace.getWorkingDirectory())) {
            return Optional.empty();
        }
        Path path = Path.of(workspace.getWorkingDirectory()).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    /**
     * 仓库路径绑定结果。
     * @param workspaceId 工作空间标识。
     * @param repositoryPath 规范化仓库路径。
     * @param workspaceName 工作空间名称。
     */
    public record WorkspaceBindingResult(
        Long workspaceId, // 工作空间标识，前端后续发送消息时使用。
        String repositoryPath, // 规范化仓库路径。
        String workspaceName, // 工作空间名称。
        ProjectProfileView projectProfile, // 最新项目画像摘要，可为空。
        int activeMemoryCount // 当前用户已生效长期记忆数量。
    ) {
        /**
         * 兼容旧调用方的最小构造器，默认不返回画像且已生效记忆数为 0。
         * @param workspaceId 工作空间标识。
         * @param repositoryPath 规范化仓库路径。
         * @param workspaceName 工作空间名称。
         */
        public WorkspaceBindingResult(Long workspaceId, String repositoryPath, String workspaceName) {
            this(workspaceId, repositoryPath, workspaceName, null, 0);
        }
    }

    /**
     * 工作空间绑定后返回给用户端的项目画像轻量视图。
     */
    public record ProjectProfileView(
        String summary, // 项目画像摘要。
        String moduleMapJson, // 模块地图 JSON。
        String testCommandsJson, // 测试命令 JSON。
        String keyEntrypointsJson, // 关键入口 JSON。
        String riskPointsJson, // 风险点 JSON。
        String agentContext // Agent 输入上下文摘要。
    ) {
    }
}
