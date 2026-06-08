package com.codingx.chat.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.interfaces.response.ChatWorkspaceResponse;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责读取当前用户可见的工作区库存，供用户端侧栏展示空工作区分组。
 */
@Service
@RequiredArgsConstructor
public class ChatWorkspaceInventoryService {

    /** 工作空间仓储，用于按当前登录用户读取未删除工作区。 */
    private final WorkspaceRepositoryImpl workspaceRepositoryImpl;

    /**
     * 查询当前登录用户拥有的全部有效工作区。
     * @return 用户侧工作区响应列表。
     */
    public List<ChatWorkspaceResponse> listCurrentUserWorkspaces() {
        // 步骤 1：从登录态读取当前用户，确保库存接口不会越权读取管理端全量工作区。
        Long userId = StpUtil.getLoginIdAsLong();
        // 步骤 2：仓储只返回 created_by 命中的未删除工作区，服务层只做响应投影。
        return workspaceRepositoryImpl.listActiveWorkspacesByUser(userId).stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * 将数据库工作区记录投影为前端安全响应，Long ID 统一转字符串避免精度丢失。
     * @param workspace 工作区数据库记录。
     * @return 用户侧工作区响应。
     */
    private ChatWorkspaceResponse toResponse(WorkspaceDO workspace) {
        String runtimeTarget = StrUtil.blankToDefault(
            workspace.getRuntimeTarget(),
            WorkspaceRepositoryImpl.RUNTIME_TARGET_CLOUD
        );
        return new ChatWorkspaceResponse(
            String.valueOf(workspace.getId()),
            StrUtil.blankToDefault(workspace.getName(), ""),
            runtimeTarget,
            normalizeOptionalText(workspace.getWorkingDirectory()),
            normalizeOptionalText(workspace.getRepositoryUrl()),
            normalizeOptionalText(workspace.getBranchName())
        );
    }

    /**
     * 将可选文本统一归一化为空值或去除首尾空白后的值，避免前端把空白字符串误判为有效目录。
     * @param value 原始可选文本。
     * @return 有效文本或 null。
     */
    private String normalizeOptionalText(String value) {
        return StrUtil.isBlank(value) ? null : StrUtil.trim(value);
    }
}
