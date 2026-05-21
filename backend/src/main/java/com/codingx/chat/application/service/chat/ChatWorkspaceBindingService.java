package com.codingx.chat.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.common.exception.BusinessException;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 维护用户与本地仓库目录的绑定关系，供聊天工具执行链路复用。
 */
@Service
@RequiredArgsConstructor
public class ChatWorkspaceBindingService {

    private final Map<Long, Path> repositoryPathByUserId = new ConcurrentHashMap<>();
    private final WorkspaceMapper workspaceMapper;

    /**
     * 绑定当前登录用户的仓库目录。
     * @param repositoryPath 用户选择的仓库路径。
     * @return 规范化后的绝对路径。
     */
    public WorkspaceBindingResult bindRepositoryPathForCurrentUser(String repositoryPath) {
        Long userId = StpUtil.getLoginIdAsLong();
        Path normalizedPath = normalizeAndValidateRepositoryPath(repositoryPath);
        WorkspaceDO workspace = findOrCreateWorkspace(userId, normalizedPath);
        repositoryPathByUserId.put(userId, normalizedPath);
        return new WorkspaceBindingResult(
            workspace.getId(),
            normalizedPath.toString().replace('\\', '/'),
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
            throw new BusinessException("CHAT_WORKSPACE_PATH_REQUIRED", "仓库路径不能为空");
        }
        Path normalizedPath = Path.of(repositoryPath).toAbsolutePath().normalize();
        if (!Files.exists(normalizedPath)) {
            throw new BusinessException("CHAT_WORKSPACE_PATH_NOT_FOUND", "仓库路径不存在");
        }
        if (!Files.isDirectory(normalizedPath)) {
            throw new BusinessException("CHAT_WORKSPACE_PATH_INVALID", "仓库路径必须是目录");
        }
        return normalizedPath;
    }

    /**
     * 按用户与目录查重创建工作空间，避免重复生成同路径工作空间记录。
     * @param userId 当前用户标识。
     * @param normalizedPath 规范化目录路径。
     * @return 工作空间记录。
     */
    private WorkspaceDO findOrCreateWorkspace(Long userId, Path normalizedPath) {
        String workingDirectory = normalizedPath.toString().replace('\\', '/');
        // 本地目录绑定必须写入可复用的“本地空间”记录，后续会话按 workspaceId 稳定归属。
        WorkspaceDO existing = workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getCreatedBy, userId)
            .eq(WorkspaceDO::getWorkingDirectory, workingDirectory)
            .eq(WorkspaceDO::getWorkspaceType, "local")
            .eq(WorkspaceDO::getDeleted, 0)
            .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(IdUtil.getSnowflakeNextId());
        workspace.setName(normalizedPath.getFileName() == null ? workingDirectory : normalizedPath.getFileName().toString());
        workspace.setWorkingDirectory(workingDirectory);
        workspace.setRuntimeTarget(WorkspaceRepositoryImpl.RUNTIME_TARGET_LOCAL);
        workspace.setWorkspaceType("local");
        workspace.setCreatedBy(userId);
        workspace.setCreatedAt(LocalDateTime.now());
        workspace.setUpdatedAt(LocalDateTime.now());
        workspace.setDeleted(0);
        workspaceMapper.insert(workspace);
        return workspace;
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
