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
import com.codingx.chat.interfaces.response.ConversationShareResponse;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责协调 ChatConversationApplicationService 的应用流程，串联领域对象与基础设施服务。
 */
@Service
@RequiredArgsConstructor
public class ChatConversationApplicationService {

    /**
     * 会话仓储端口，用于保存会话聚合、查询历史列表、加载分享会话和更新置顶/提醒状态。
     */
    private final ChatConversationRepository chatConversationRepository;

    /**
     * 消息仓储端口，用于查询会话消息、逻辑删除消息和导出分享/Markdown 内容。
     */
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 工作空间仓储端口，保留给会话归属存在性校验扩展；当前默认空间创建由实现类承接。
     */
    private final WorkspaceRepository workspaceRepository;

    /**
     * 工作空间仓储实现，负责默认云端/本地空间创建与用户归属校验。
     */
    private final WorkspaceRepositoryImpl workspaceRepositoryImpl;

    /**
     * 创建新会话并绑定最终工作空间。
     * @param command 创建会话命令，包含标题、目标工作空间和运行目标。
     * @param userId 当前用户标识。
     * @return 已持久化的会话领域对象。
     */
    public ChatConversation createConversation(CreateConversationCommand command, Long userId) {
        // 步骤 1：解析最终归属工作空间，显式工作空间需校验用户归属，未指定时按运行目标创建默认空间。
        Long actualWorkspaceId = resolveWorkspaceId(command.workspaceId(), command.runtimeTarget(), userId);
        // 步骤 2：标题为空时使用统一默认标题，后续首轮回复完成后可自动重命名。
        String title = StrUtil.blankToDefault(command.title(), ErrorMessageCatalog.CHAT_CONVERSATION_DEFAULT_TITLE);
        // 步骤 3：创建 ACTIVE 会话领域对象，默认置顶为 false、任务提醒为已读。
        ChatConversation conversation = ChatConversation.create(
            IdUtil.getSnowflakeNextId(),
            title,
            userId,
            actualWorkspaceId,
            ChatConversationStatus.ACTIVE
        );
        // 步骤 4：保存会话并返回领域对象，Controller 只负责响应投影。
        chatConversationRepository.save(conversation);
        return conversation;
    }

    /**
     * 查询当前用户会话列表。
     * @param userId 当前用户标识。
     * @param workspaceId 工作空间标识，可为空；为空时查询默认云端和历史未归属会话。
     * @return 按置顶、更新时间和主键倒序排列的会话列表。
     */
    public List<ChatConversation> listConversations(Long userId, Long workspaceId) {
        if (workspaceId != null) {
            // 步骤 1：显式工作空间必须先校验归属，避免用户跨空间读取会话。
            workspaceRepositoryImpl.requireOwnedWorkspace(workspaceId, userId);
            return chatConversationRepository.findByCreatedByAndWorkspaceId(userId, workspaceId);
        }
        // 默认云端历史页只应展示“默认云端空间 + 历史遗留未归属记录”，避免本地工作空间会话串进 Web 历史。
        List<ChatConversation> conversations = new ArrayList<>();
        // 步骤 2：优先读取默认云端空间内的会话；默认云端空间不存在时不主动创建，保持列表查询只读。
        workspaceRepositoryImpl.findDefaultCloudWorkspaceByUserId(userId)
            .ifPresent(workspace -> conversations.addAll(chatConversationRepository.findByCreatedByAndWorkspaceId(userId, workspace.getId())));
        // 步骤 3：追加历史未绑定工作空间的旧会话，兼容工作空间上线前的数据。
        conversations.addAll(chatConversationRepository.findByCreatedByAndWorkspaceId(userId, null));
        // 步骤 4：合并结果后重新排序，确保默认云端与历史记录混合时顺序稳定。
        return conversations.stream()
            .sorted(
                Comparator.comparing(ChatConversation::getPinned, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ChatConversation::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ChatConversation::getId, Comparator.nullsLast(Comparator.reverseOrder()))
            )
            .toList();
    }

    /**
     * 查询指定会话的消息列表。
     * @param conversationId 会话标识。
     * @param userId 当前用户标识。
     * @return 会话内未删除消息列表。
     */
    public List<ChatMessage> listMessages(Long conversationId, Long userId) {
        // 步骤 1：先加载会话并校验归属，防止用户通过会话 ID 枚举其他人的消息。
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        // 步骤 2：归属校验通过后交给消息仓储按会话读取历史消息。
        return chatMessageRepository.findByConversationId(conversationId);
    }

    /**
     * 更新指定会话的标题，用于自动生成标题后的持久化。
     * @param conversationId 会话标识。
     * @param title 新标题。
     * @param userId 当前用户标识。
     */
    public void updateConversationTitle(Long conversationId, String title, Long userId) {
        // 步骤 1：加载会话并校验归属，标题更新只能由创建人触发。
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        // 步骤 2：领域对象负责忽略空标题，仓储只保存有效状态。
        conversation.rename(title);
        chatConversationRepository.save(conversation);
    }

    /**
     * 删除指定会话，供前端历史列表菜单触发逻辑删除。
     * @param conversationId 会话标识。
     * @param userId 当前用户标识。
     */
    public void deleteConversation(Long conversationId, Long userId) {
        // 步骤 1：删除前校验会话归属，避免跨用户逻辑删除。
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        // 步骤 2：仓储执行逻辑删除，不物理清理消息和运行记录。
        chatConversationRepository.deleteById(conversationId);
    }

    /**
     * 逻辑删除会话内指定消息；编辑重发会先删除旧消息段落，再写入新的用户问题和助手回答。
     * @param conversationId 会话标识。
     * @param messageIds 待删除消息标识列表。
     * @param userId 当前用户标识。
     */
    public void deleteConversationMessages(Long conversationId, List<Long> messageIds, Long userId) {
        // 步骤 1：先校验会话归属，消息删除必须限定在当前用户自己的会话内。
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        // 步骤 2：过滤空消息 ID 并去重，避免批量更新产生无效条件。
        List<Long> normalizedMessageIds = messageIds == null
            ? List.of()
            : messageIds.stream()
                .filter(messageId -> messageId != null)
                .distinct()
                .toList();
        if (normalizedMessageIds.isEmpty()) {
            // 没有有效消息 ID 时直接返回，不刷新会话时间。
            return;
        }
        // 步骤 3：按会话范围逻辑删除消息，再刷新会话最近更新时间。
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
        // 步骤 1：加载会话并校验归属，置顶状态只能由会话创建人修改。
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        // 步骤 2：置顶状态只影响列表排序，同时刷新更新时间让排序变化可见。
        conversation.setPinned(pinned);
        conversation.touch();
        chatConversationRepository.save(conversation);
    }

    /**
     * 将任务完成提醒标记为已读；该状态独立于置顶与分享字段，供前端刷新后恢复提醒状态。
     * @param conversationId 会话标识。
     * @param userId 当前用户标识。
     */
    public void markTaskCompletionRead(Long conversationId, Long userId) {
        // 步骤 1：校验会话归属，避免用户修改其他会话的任务提醒状态。
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        // 步骤 2：任务完成提醒状态与置顶/分享字段独立保存，刷新页面后仍以数据库为准。
        conversation.markTaskCompletionRead();
        chatConversationRepository.save(conversation);
    }

    /**
     * 为会话生成分享令牌，公开分享页仅依赖该令牌回放只读内容。
     * @param conversationId 会话标识。
     * @param userId 当前用户标识。
     * @return 分享令牌。
     */
    public String generateShareToken(Long conversationId, Long userId) {
        // 步骤 1：加载会话并校验创建人，公开分享只能由会话拥有者开启。
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        if (StrUtil.isBlank(conversation.getShareToken())) {
            // 步骤 2：首次分享时生成令牌并持久化；已有令牌时复用，保证分享链接稳定。
            conversation.setShareToken(buildShareToken());
            conversation.touch();
            chatConversationRepository.save(conversation);
        }
        // 步骤 3：返回分享令牌，分享响应入口会继续组装对外 URL。
        return conversation.getShareToken();
    }

    /**
     * 生成公开分享令牌和前端分享路径，消息范围过滤规则由应用层统一维护。
     * @param conversationId 会话标识。
     * @param userId 当前用户标识。
     * @param messageIds 前端选择公开分享的消息标识列表，可为空。
     * @return 分享响应，包含令牌和前端路径。
     */
    public ConversationShareResponse shareConversation(Long conversationId, Long userId, List<Long> messageIds) {
        // 步骤 1：复用令牌生成逻辑完成归属校验，已有令牌保持稳定不重复生成。
        String shareToken = generateShareToken(conversationId, userId);
        // 步骤 2：按应用层规则过滤消息范围并生成 URL，Controller 不再拼接业务参数。
        String shareUrl = buildConversationShareUrl(shareToken, messageIds);
        return new ConversationShareResponse(shareToken, shareUrl);
    }

    /**
     * 公开分享页按分享令牌加载会话，只暴露只读的会话骨架。
     * @param shareToken 分享令牌。
     * @return 会话记录。
     */
    public ChatConversation requireSharedConversation(String shareToken) {
        // 步骤 1：按分享令牌查找未删除会话，找不到时返回统一分享不存在文案。
        return chatConversationRepository.findByShareToken(shareToken)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CHAT_CONVERSATION_SHARE_NOT_FOUND));
    }

    /**
     * 公开分享页加载会话与消息回放，查询参数解析和非法值兜底均在应用层处理。
     * @param shareToken 分享令牌。
     * @param rawMessageIds 逗号分隔消息标识，可为空。
     * @return 公开分享会话和消息回放载体。
     */
    public SharedConversationContent loadSharedConversation(String shareToken, String rawMessageIds) {
        // 步骤 1：先校验分享令牌并加载只读会话骨架。
        ChatConversation conversation = requireSharedConversation(shareToken);
        // 步骤 2：解析 URL 查询参数中的消息范围，非法值直接忽略，避免公开接口因脏参数失败。
        List<Long> selectedMessageIds = parseSharedMessageIds(rawMessageIds);
        // 步骤 3：按选择范围过滤消息，过滤后仍保持原会话消息顺序。
        return new SharedConversationContent(conversation, listSharedMessages(conversation, selectedMessageIds));
    }

    /**
     * 公开分享页按分享令牌加载会话消息，保持只读回放。
     * @param shareToken 分享令牌。
     * @return 会话消息列表。
     */
    public List<ChatMessage> listSharedMessages(String shareToken) {
        // 步骤 1：先按分享令牌解析会话，再按会话 ID 读取完整只读回放消息。
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
        // 步骤 1：加载分享会话并读取完整消息，后续只在内存中按选择范围过滤。
        ChatConversation conversation = requireSharedConversation(shareToken);
        return listSharedMessages(conversation, messageIds);
    }

    private List<ChatMessage> listSharedMessages(ChatConversation conversation, List<Long> messageIds) {
        // 步骤 1：读取完整消息回放；无筛选条件时直接返回完整分享内容。
        List<ChatMessage> messages = chatMessageRepository.findByConversationId(conversation.getId());
        if (messageIds == null || messageIds.isEmpty()) {
            return messages;
        }
        // 步骤 2：只保留正数消息 ID，使用 LinkedHashSet 保持前端选择顺序且去重。
        java.util.Set<Long> selectedMessageIds = new java.util.LinkedHashSet<>(
            messageIds.stream()
                .filter(messageId -> messageId != null && messageId > 0)
                .toList()
        );
        // 步骤 3：过滤结果仍按原会话消息顺序返回，避免分享回放乱序。
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
        // 步骤 1：批量操作先标准化 ID，空列表视为非法请求。
        List<Long> normalizedIds = normalizeConversationIds(conversationIds);
        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_CONVERSATION_BATCH_IDS_REQUIRED);
        }
        // 步骤 2：逐条复用单会话置顶逻辑，保证每条记录都执行归属校验。
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
        // 步骤 1：导出前校验会话归属，避免用户导出他人历史。
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        // 步骤 2：读取会话消息并按用户/助手角色组装 Markdown 章节。
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
        // 步骤 3：裁掉首尾空白，避免下载文件首尾出现额外空行。
        return builder.toString().trim();
    }

    /**
     * 解析会话归属工作空间：未显式指定时自动绑定默认云端空间，显式指定时校验用户归属。
     * @param requestedWorkspaceId 请求中传入的工作空间标识。
     * @param userId 当前用户标识。
     * @return 会话最终归属的工作空间标识。
     */
    private Long resolveWorkspaceId(Long requestedWorkspaceId, String runtimeTarget, Long userId) {
        if (requestedWorkspaceId != null) {
            // 步骤 1：显式传入工作空间时必须校验归属，校验通过后使用该空间。
            WorkspaceDO workspace = workspaceRepositoryImpl.requireOwnedWorkspace(requestedWorkspaceId, userId);
            return workspace.getId();
        }
        if (WorkspaceRepositoryImpl.RUNTIME_TARGET_LOCAL.equalsIgnoreCase(StrUtil.trimToEmpty(runtimeTarget))) {
            // 步骤 2：本地运行目标自动绑定默认本地空间，避免本地会话串入云端历史。
            WorkspaceDO localWorkspace = workspaceRepositoryImpl.ensureDefaultLocalWorkspace(userId);
            return localWorkspace.getId();
        }
        // 步骤 3：默认 Web 云端会话绑定默认云端空间，没有时自动创建。
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
            // 空集合直接返回空列表，由调用方决定是否报错。
            return List.of();
        }
        // 步骤 1：批量会话 ID 只保留正数并去重，避免空值或负数进入更新链路。
        return conversationIds.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
    }

    /**
     * 解析公开分享页传入的消息过滤参数，非法值直接忽略，避免公开接口因 URL 脏参数失败。
     * @param rawMessageIds 逗号分隔的消息标识。
     * @return 规范化消息标识列表。
     */
    private List<Long> parseSharedMessageIds(String rawMessageIds) {
        // 步骤 1：缺少 messages 参数时表示公开整段会话。
        if (StrUtil.isBlank(rawMessageIds)) {
            return List.of();
        }
        // 步骤 2：逐个解析正数消息 ID，非法数字忽略并去重，保证公开接口稳定可访问。
        return StrUtil.splitTrim(rawMessageIds, ',').stream()
            .map(this::parsePositiveLongOrNull)
            .filter(messageId -> messageId != null && messageId > 0)
            .distinct()
            .toList();
    }

    private Long parsePositiveLongOrNull(String value) {
        // 步骤 1：单个消息 ID 解析失败时返回 null，由上层过滤，不抛异常中断公开访问。
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 构造前端公开分享页地址，选中的消息范围以查询参数保留给公开页过滤。
     * @param shareToken 分享令牌。
     * @param messageIds 选中的消息标识。
     * @return 前端公开分享页路径。
     */
    private String buildConversationShareUrl(String shareToken, List<Long> messageIds) {
        // 步骤 1：分享链接只接受正数消息 ID，避免脏请求参数污染公开访问地址。
        List<Long> normalizedMessageIds = normalizePositiveIds(messageIds);
        if (normalizedMessageIds.isEmpty()) {
            return "/share/chat/" + shareToken;
        }

        // 步骤 2：多消息 ID 统一 URL 编码，公开页再按 messages 参数恢复筛选范围。
        String joinedMessageIds = normalizedMessageIds.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        return "/share/chat/" + shareToken + "?messages=" + URLEncoder.encode(joinedMessageIds, StandardCharsets.UTF_8);
    }

    private List<Long> normalizePositiveIds(List<Long> ids) {
        // 步骤 1：过滤空值、非正数和重复值，保留前端选择顺序用于生成可预测 URL。
        return ids == null
            ? List.of()
            : ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    /**
     * 生成短分享令牌，避免暴露会话 ID。
     * @return 分享令牌。
     */
    private String buildShareToken() {
        // 步骤 1：分享令牌不暴露会话 ID，UUID 加短随机串降低碰撞概率。
        return "share_" + IdUtil.fastSimpleUUID() + RandomUtil.randomString(8);
    }

    /**
     * 公开分享会话与消息的应用层载体，视图服务负责转换为接口响应。
     * @param conversation 分享令牌对应的会话记录。
     * @param messages 分享范围内的消息回放，已按原会话顺序过滤。
     */
    public record SharedConversationContent(
        ChatConversation conversation,
        List<ChatMessage> messages
    ) {
    }
}
