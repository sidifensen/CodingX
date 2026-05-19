package com.codingx.chat.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.exception.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 维护用户与本地仓库目录的绑定关系，供聊天工具执行链路复用。
 */
@Service
public class ChatWorkspaceBindingService {

    private final Map<Long, Path> repositoryPathByUserId = new ConcurrentHashMap<>();

    /**
     * 绑定当前登录用户的仓库目录。
     * @param repositoryPath 用户选择的仓库路径。
     * @return 规范化后的绝对路径。
     */
    public String bindRepositoryPathForCurrentUser(String repositoryPath) {
        Long userId = StpUtil.getLoginIdAsLong();
        Path normalizedPath = normalizeAndValidateRepositoryPath(repositoryPath);
        repositoryPathByUserId.put(userId, normalizedPath);
        return normalizedPath.toString().replace('\\', '/');
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
}
