package com.codingx.chat.application.service;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责协调 ChatConversationApplicationService 的应用流程，串联领域对象与基础设施服务。
 */
@Service
@RequiredArgsConstructor
public class ChatConversationApplicationService {

    /**
     * ChatConversationRepository 依赖。
     */
    private final ChatConversationRepository chatConversationRepository;

    /**
     * ChatMessageRepository 依赖。
     */
    private final ChatMessageRepository chatMessageRepository;
    /**
     * WorkspaceRepository 依赖。
     */
    private final WorkspaceRepository workspaceRepository;
    /**
     * WorkspaceRepositoryImpl 依赖，负责默认云端空间与用户归属校验。
     */
    private final WorkspaceRepositoryImpl workspaceRepositoryImpl;

    /**
     * 创建 createConversation 所需数据并返回结果。
     * @param command 输入参数。
     * @param userId 输入参数。
     * @return 输入参数。
     */
    public ChatConversation createConversation(CreateConversationCommand command, Long userId) {
        Long actualWorkspaceId = resolveWorkspaceId(command.workspaceId(), userId);
        String title = StrUtil.blankToDefault(command.title(), ErrorMessageCatalog.CHAT_CONVERSATION_DEFAULT_TITLE);
        ChatConversation conversation = ChatConversation.create(
            IdUtil.getSnowflakeNextId(),
            title,
            userId,
            actualWorkspaceId,
            ChatConversationStatus.ACTIVE
        );
        chatConversationRepository.save(conversation);
        return conversation;
    }

    /**
     * 返回 listConversations 需要的结果集合。
     * @param userId 输入参数。
     * @return 输入参数。
     */
    public List<ChatConversation> listConversations(Long userId, Long workspaceId) {
        if (workspaceId != null) {
            workspaceRepositoryImpl.requireOwnedWorkspace(workspaceId, userId);
            return chatConversationRepository.findByCreatedByAndWorkspaceId(userId, workspaceId);
        }
        // 默认云端历史页只应展示“默认云端空间 + 历史遗留未归属记录”，避免本地工作空间会话串进 Web 历史。
        List<ChatConversation> conversations = new ArrayList<>();
        workspaceRepositoryImpl.findDefaultCloudWorkspaceByUserId(userId)
            .ifPresent(workspace -> conversations.addAll(chatConversationRepository.findByCreatedByAndWorkspaceId(userId, workspace.getId())));
        conversations.addAll(chatConversationRepository.findByCreatedByAndWorkspaceId(userId, null));
        return conversations.stream()
            .sorted(
                Comparator.comparing(ChatConversation::getPinned, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ChatConversation::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ChatConversation::getId, Comparator.nullsLast(Comparator.reverseOrder()))
            )
            .toList();
    }

    /**
     * 返回 listMessages 需要的结果集合。
     * @param conversationId 输入参数。
     * @param userId 输入参数。
     * @return 输入参数。
     */
    public List<ChatMessage> listMessages(Long conversationId, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        return chatMessageRepository.findByConversationId(conversationId);
    }

    /**
     * 更新指定会话的标题，用于自动生成标题后的持久化。
     * @param conversationId 会话标识。
     * @param title 新标题。
     * @param userId 当前用户标识。
     */
    public void updateConversationTitle(Long conversationId, String title, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        conversation.rename(title);
        chatConversationRepository.save(conversation);
    }

    /**
     * 删除指定会话，供前端历史列表菜单触发逻辑删除。
     * @param conversationId 会话标识。
     * @param userId 当前用户标识。
     */
    public void deleteConversation(Long conversationId, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        chatConversationRepository.deleteById(conversationId);
    }

    /**
     * 逻辑删除会话内指定消息；编辑重发会先删除旧消息段落，再写入新的用户问题和助手回答。
     * @param conversationId 会话标识。
     * @param messageIds 待删除消息标识列表。
     * @param userId 当前用户标识。
     */
    public void deleteConversationMessages(Long conversationId, List<Long> messageIds, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        List<Long> normalizedMessageIds = messageIds == null
            ? List.of()
            : messageIds.stream()
                .filter(messageId -> messageId != null)
                .distinct()
                .toList();
        if (normalizedMessageIds.isEmpty()) {
            return;
        }
        chatMessageRepository.softDeleteByConversationIdAndIds(conversationId, normalizedMessageIds);
        conversation.touch();
        chatConversationRepository.save(conversation);
    }

    /**
     * 切换会话置顶状态，供侧边栏快速固定高频会话。
     * @param conversationId 会话标识。
     * @param pinned 置顶状态。
     * @param userId 当前用户标识。
     */
    public void updateConversationPinnedState(Long conversationId, boolean pinned, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        conversation.setPinned(pinned);
        conversation.touch();
        chatConversationRepository.save(conversation);
    }

    /**
     * 为会话生成分享令牌，公开分享页仅依赖该令牌回放只读内容。
     * @param conversationId 会话标识。
     * @param userId 当前用户标识。
     * @return 分享令牌。
     */
    public String generateShareToken(Long conversationId, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        if (StrUtil.isBlank(conversation.getShareToken())) {
            conversation.setShareToken(buildShareToken());
            conversation.touch();
            chatConversationRepository.save(conversation);
        }
        return conversation.getShareToken();
    }

    /**
     * 公开分享页按分享令牌加载会话，只暴露只读的会话骨架。
     * @param shareToken 分享令牌。
     * @return 会话记录。
     */
    public ChatConversation requireSharedConversation(String shareToken) {
        return chatConversationRepository.findByShareToken(shareToken)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CHAT_CONVERSATION_SHARE_NOT_FOUND));
    }

    /**
     * 公开分享页按分享令牌加载会话消息，保持只读回放。
     * @param shareToken 分享令牌。
     * @return 会话消息列表。
     */
    public List<ChatMessage> listSharedMessages(String shareToken) {
        ChatConversation conversation = requireSharedConversation(shareToken);
        return chatMessageRepository.findByConversationId(conversation.getId());
    }

    /**
     * 公开分享页按分享令牌和可选消息范围加载回放，保留原会话顺序。
     * @param shareToken 分享令牌。
     * @param messageIds 前端选择的消息标识。
     * @return 会话消息列表。
     */
    public List<ChatMessage> listSharedMessages(String shareToken, List<Long> messageIds) {
        ChatConversation conversation = requireSharedConversation(shareToken);
        List<ChatMessage> messages = chatMessageRepository.findByConversationId(conversation.getId());
        if (messageIds == null || messageIds.isEmpty()) {
            return messages;
        }
        java.util.Set<Long> selectedMessageIds = new java.util.LinkedHashSet<>(
            messageIds.stream()
                .filter(messageId -> messageId != null && messageId > 0)
                .toList()
        );
        return messages.stream()
            .filter(message -> selectedMessageIds.contains(message.getId()))
            .toList();
    }

    /**
     * 批量更新会话置顶状态。
     * @param conversationIds 会话标识集合。
     * @param pinned 目标状态。
     * @param userId 当前用户标识。
     */
    public void batchUpdatePinnedState(List<Long> conversationIds, boolean pinned, Long userId) {
        List<Long> normalizedIds = normalizeConversationIds(conversationIds);
        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_CONVERSATION_BATCH_IDS_REQUIRED);
        }
        for (Long conversationId : normalizedIds) {
            updateConversationPinnedState(conversationId, pinned, userId);
        }
    }

    /**
     * 导出会话的 Markdown 预览内容，供下载接口使用。
     * @param conversationId 会话标识。
     * @param userId 当前用户标识。
     * @return Markdown 文本。
     */
    public String exportConversationAsMarkdown(Long conversationId, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        List<ChatMessage> messages = chatMessageRepository.findByConversationId(conversationId);
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(conversation.getTitle()).append('\n');
        for (ChatMessage message : messages) {
            builder.append('\n')
                .append("## ")
                .append(message.getRole() == ChatMessageRole.USER ? "用户" : "助手")
                .append('\n')
                .append(message.getContent())
                .append('\n');
        }
        return builder.toString().trim();
    }

    /**
     * 解析会话归属工作空间：未显式指定时自动绑定默认云端空间，显式指定时校验用户归属。
     * @param requestedWorkspaceId 请求中传入的工作空间标识。
     * @param userId 当前用户标识。
     * @return 会话最终归属的工作空间标识。
     */
    private Long resolveWorkspaceId(Long requestedWorkspaceId, Long userId) {
        if (requestedWorkspaceId != null) {
            WorkspaceDO workspace = workspaceRepositoryImpl.requireOwnedWorkspace(requestedWorkspaceId, userId);
            return workspace.getId();
        }
        WorkspaceDO cloudWorkspace = workspaceRepositoryImpl.ensureDefaultCloudWorkspace(userId, null);
        return cloudWorkspace.getId();
    }

    /**
     * 将批量会话主键标准化，过滤空值与重复值，避免批处理误操作。
     * @param conversationIds 原始主键集合。
     * @return 标准化后的主键集合。
     */
    private List<Long> normalizeConversationIds(List<Long> conversationIds) {
        if (conversationIds == null) {
            return List.of();
        }
        return conversationIds.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
    }

    /**
     * 生成短分享令牌，避免暴露会话 ID。
     * @return 分享令牌。
     */
    private String buildShareToken() {
        return "share_" + IdUtil.fastSimpleUUID() + RandomUtil.randomString(8);
    }
}
