package com.codingx.admin.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.workspace.domain.model.AdminWorkspacePage;
import com.codingx.workspace.domain.model.AdminWorkspaceQuery;
import com.codingx.workspace.domain.model.AdminWorkspaceRecord;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import com.codingx.workspace.interfaces.response.AdminWorkspaceListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供管理端工作空间只读查询能力，避免管理页触发用户侧默认空间创建副作用。
 */
@Service
@RequiredArgsConstructor
public class AdminWorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    /**
     * 分页查询工作空间并映射为管理端响应。
     * @param current 当前页码。
     * @param size 每页条数。
     * @param keyword 可选关键字。
     * @param runtimeTarget 可选运行目标。
     * @return 管理端工作空间分页结果。
     */
    public PageResult<AdminWorkspaceListItemResponse> pageWorkspaces(
        int current,
        int size,
        String keyword,
        String runtimeTarget
    ) {
        int normalizedCurrent = Math.max(1, current);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        AdminWorkspacePage page = workspaceRepository.pageForAdmin(
            new AdminWorkspaceQuery(
                normalizedCurrent,
                normalizedSize,
                StrUtil.trimToEmpty(keyword),
                StrUtil.trimToEmpty(runtimeTarget)
            )
        );
        return PageResult.<AdminWorkspaceListItemResponse>builder()
            .records(page.records().stream().map(this::toResponse).toList())
            .total(page.total())
            .size(page.size())
            .current(page.current())
            .pages(page.pages())
            .build();
    }

    /**
     * 将仓储记录转换为 HTTP 响应，并补齐运行目标中文标签。
     * @param record 仓储查询记录。
     * @return 管理端列表项响应。
     */
    private AdminWorkspaceListItemResponse toResponse(AdminWorkspaceRecord record) {
        return AdminWorkspaceListItemResponse.builder()
            .id(record.id())
            .name(record.name())
            .repositoryUrl(record.repositoryUrl())
            .branchName(record.branchName())
            .workingDirectory(record.workingDirectory())
            .runtimeTarget(record.runtimeTarget())
            .runtimeTargetLabel(resolveRuntimeTargetLabel(record.runtimeTarget()))
            .createdBy(record.createdBy())
            .conversationCount(record.conversationCount())
            .createdAt(record.createdAt())
            .updatedAt(record.updatedAt())
            .build();
    }

    /**
     * 运行目标中文化统一放在后端，避免各管理端页面重复翻译。
     * @param runtimeTarget 运行目标编码。
     * @return 中文文案。
     */
    private String resolveRuntimeTargetLabel(String runtimeTarget) {
        if (WorkspaceRepositoryImpl.RUNTIME_TARGET_CLOUD.equals(runtimeTarget)) {
            return "云端";
        }
        if (WorkspaceRepositoryImpl.RUNTIME_TARGET_LOCAL.equals(runtimeTarget)) {
            return "本地";
        }
        return "未知";
    }
}
