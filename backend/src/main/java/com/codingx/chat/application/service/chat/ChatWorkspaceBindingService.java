package com.codingx.chat.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 维护用户与本地仓库目录的绑定关系，供聊天工具执行链路复用。
 */
@Service
@RequiredArgsConstructor
public class ChatWorkspaceBindingService {

    /** 用户到本地仓库路径的进程内绑定缓存，用于未显式传 workspaceId 的聊天请求快速回退。 */
    private final Map<Long, Path> repositoryPathByUserId = new ConcurrentHashMap<>();
    /** 工作空间 Mapper，用于查询用户已持久化的本地工作空间目录。 */
    private final WorkspaceMapper workspaceMapper;
    /** 工作空间仓储实现，用于创建或复用用户默认本地工作空间。 */
    private final WorkspaceRepositoryImpl workspaceRepositoryImpl;

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
        return new WorkspaceBindingResult(
            workspace.getId(),
            normalizedPathText,
            workspace.getName()
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
    public record WorkspaceBindingResult(Long workspaceId, String repositoryPath, String workspaceName) {
    }
}
