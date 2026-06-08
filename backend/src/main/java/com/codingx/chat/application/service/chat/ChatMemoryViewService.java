package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.interfaces.response.ChatLongTermMemoryResponse;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责把长期记忆领域对象投影为用户端响应对象，并补齐页面展示所需的工作空间名称。
 */
@Service
@RequiredArgsConstructor
public class ChatMemoryViewService {

    /** 工作空间仓储实现，用于按当前用户校验并读取记忆所属空间名称。 */
    private final WorkspaceRepositoryImpl workspaceRepositoryImpl;

    /**
     * 批量转换用户可见的长期记忆响应。
     * @param memories 长期记忆领域对象列表。
     * @param currentUserId 当前登录用户标识。
     * @return 带工作空间展示名的响应列表。
     */
    public List<ChatLongTermMemoryResponse> toMemoryResponses(
        List<GovernanceLongTermMemory> memories,
        Long currentUserId
    ) {
        return (memories == null ? List.<GovernanceLongTermMemory>of() : memories).stream()
            .map(memory -> toMemoryResponse(memory, currentUserId))
            .toList();
    }

    /**
     * 转换单条长期记忆，PROJECT 记忆会按 workspaceId 补齐 workspace.name。
     * @param memory 长期记忆领域对象。
     * @param currentUserId 当前登录用户标识。
     * @return 带展示字段的长期记忆响应。
     */
    public ChatLongTermMemoryResponse toMemoryResponse(GovernanceLongTermMemory memory, Long currentUserId) {
        // 步骤 1：工作空间名称只从当前用户拥有的 workspace 读取，避免跨用户泄露空间信息。
        String workspaceName = resolveWorkspaceName(memory.getWorkspaceId(), currentUserId);

        // 步骤 2：保留原长期记忆字段，同时附加 workspaceName 给记忆管理页直接展示。
        return new ChatLongTermMemoryResponse(
            memory.getId(),
            memory.getMemoryScope(),
            memory.getUserId(),
            memory.getWorkspaceId(),
            workspaceName,
            memory.getMemoryKey(),
            memory.getContent(),
            memory.getStatus(),
            memory.getSourceType(),
            memory.getSourceConversationId(),
            memory.getSourceMessageId(),
            memory.getKeywordJson(),
            memory.getConfidenceScore(),
            memory.getLastUsedAt(),
            memory.getCreatedAt(),
            memory.getUpdatedAt()
        );
    }

    /**
     * 读取可展示的工作空间名称；查不到或名称为空时返回 null，由前端继续用 ID 兜底。
     * @param workspaceId 工作空间标识。
     * @param currentUserId 当前登录用户标识。
     * @return 工作空间展示名。
     */
    private String resolveWorkspaceName(Long workspaceId, Long currentUserId) {
        if (workspaceId == null || currentUserId == null) {
            return null;
        }
        return workspaceRepositoryImpl.findOwnedWorkspaceById(workspaceId, currentUserId)
            .map(WorkspaceDO::getName)
            .map(StrUtil::trimToNull)
            .orElse(null);
    }
}
