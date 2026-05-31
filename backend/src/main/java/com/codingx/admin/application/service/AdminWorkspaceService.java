package com.codingx.admin.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.interfaces.response.AdminChatConversationListItemResponse;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.workspace.domain.model.AdminWorkspacePage;
import com.codingx.workspace.domain.model.AdminWorkspaceQuery;
import com.codingx.workspace.domain.model.AdminWorkspaceRecord;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import com.codingx.workspace.interfaces.response.AdminWorkspaceListItemResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供管理端工作空间只读查询能力，避免管理页触发用户侧默认空间创建副作用。
 */
@Service
@RequiredArgsConstructor
public class AdminWorkspaceService {

    /** 工作空间仓储，用于管理端只读分页和存在性校验。 */
    private final WorkspaceRepository workspaceRepository;
    /** 会话仓储，用于按工作空间反查会话列表和会话状态。 */
    private final ChatConversationRepository chatConversationRepository;

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
     * 分页查询指定工作空间下的会话，供管理端从空间维度排查会话归属。
     * @param workspaceId 工作空间标识。
     * @param current 当前页码。
     * @param size 每页条数。
     * @param keyword 可选关键字。
     * @return 工作空间内会话分页结果。
     */
    public PageResult<AdminChatConversationListItemResponse> pageWorkspaceConversations(
        Long workspaceId,
        int current,
        int size,
        String keyword
    ) {
        workspaceRepository.ensureExists(workspaceId);
        int normalizedCurrent = Math.max(1, current);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<ChatConversation> allRecords = chatConversationRepository.findAllByWorkspaceId(
            workspaceId,
            StrUtil.trimToEmpty(keyword)
        );
        long total = allRecords.size();
        long pages = total == 0 ? 1 : (total + normalizedSize - 1L) / normalizedSize;
        int fromIndex = Math.min((normalizedCurrent - 1) * normalizedSize, allRecords.size());
        int toIndex = Math.min(fromIndex + normalizedSize, allRecords.size());
        return PageResult.<AdminChatConversationListItemResponse>builder()
            .records(allRecords.subList(fromIndex, toIndex).stream().map(this::toConversationResponse).toList())
            .total(total)
            .size((long) normalizedSize)
            .current((long) normalizedCurrent)
            .pages(pages)
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
     * 将会话领域对象转换为工作空间详情页复用的管理端会话列表行。
     * @param conversation 会话领域对象。
     * @return 管理端会话列表项。
     */
    private AdminChatConversationListItemResponse toConversationResponse(ChatConversation conversation) {
        return AdminChatConversationListItemResponse.builder()
            .id(conversation.getId())
            .title(conversation.getTitle())
            .createdBy(conversation.getCreatedBy())
            .status(conversation.getStatus())
            .statusLabel(resolveConversationStatusLabel(conversation.getStatus()))
            .lastMessageAt(conversation.getLastMessageAt())
            .lastRunId(conversation.getLastRunId())
            .createdAt(conversation.getCreatedAt())
            .updatedAt(conversation.getUpdatedAt())
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

    /**
     * 会话状态中文化保持与会话管理页一致，避免同一状态在不同入口显示不一致。
     * @param status 会话状态枚举。
     * @return 中文文案。
     */
    private String resolveConversationStatusLabel(ChatConversationStatus status) {
        if (status == ChatConversationStatus.ACTIVE) {
            return "活跃";
        }
        if (status == ChatConversationStatus.ARCHIVED) {
            return "已归档";
        }
        return "未知";
    }
}
