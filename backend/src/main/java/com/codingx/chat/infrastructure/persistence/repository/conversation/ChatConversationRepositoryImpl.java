package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatConversationMapper;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 聊天会话仓储实现，负责在会话领域对象与 chat_conversation 表数据对象之间转换。
 */
@Repository
@RequiredArgsConstructor
public class ChatConversationRepositoryImpl implements ChatConversationRepository {

    /**
     * 聊天会话 MyBatis Mapper，用于执行 chat_conversation 表的查询、插入和更新。
     */
    private final ChatConversationMapper chatConversationMapper;

    /**
     * 按主键加载未删除会话，不存在时抛出业务异常。
     * @param conversationId 会话标识。
     * @return 会话领域对象。
     */
    @Override
    public ChatConversation requireById(Long conversationId) {
        // 步骤 1：按主键读取会话数据对象，仓储层统一处理逻辑删除状态。
        ChatConversationDO dataObject = chatConversationMapper.selectById(conversationId);
        if (dataObject == null || Integer.valueOf(1).equals(dataObject.getDeleted())) {
            // 步骤 2：不存在或已逻辑删除都按“会话不存在”处理，避免向上层泄露存储细节。
            throw new NotFoundException(ErrorMessageCatalog.CHAT_CONVERSATION_NOT_FOUND);
        }
        // 步骤 3：命中记录后还原为领域对象，应用层不直接依赖 DO 字段。
        return toDomain(dataObject);
    }

    /**
     * 保存会话聚合，已存在时更新，不存在时新增。
     * @param conversation 待持久化的会话领域对象。
     */
    @Override
    public void save(ChatConversation conversation) {
        // 步骤 1：先把领域对象转换为数据对象，保持表字段映射集中在仓储层。
        ChatConversationDO dataObject = toDataObject(conversation);
        if (chatConversationMapper.selectById(conversation.getId()) == null) {
            // 步骤 2：主键不存在时插入新会话。
            chatConversationMapper.insert(dataObject);
        } else {
            // 步骤 3：主键存在时更新会话状态、置顶、分享令牌和任务完成已读等字段。
            chatConversationMapper.updateById(dataObject);
        }
    }

    /**
     * 执行逻辑删除，避免误删历史数据。
     * @param conversationId 会话标识。
     */
    @Override
    public void deleteById(Long conversationId) {
        // 步骤 1：只更新 deleted 标记，不物理删除会话，保留历史消息和审计线索。
        chatConversationMapper.update(
            null,
            new LambdaUpdateWrapper<ChatConversationDO>()
                .eq(ChatConversationDO::getId, conversationId)
                .set(ChatConversationDO::getDeleted, 1)
        );
    }

    /**
     * 查询用户在指定工作空间下的会话列表。
     * @param userId 用户标识。
     * @param workspaceId 工作空间标识，可为空；为空时查询历史未归属云端会话。
     * @return 按置顶、更新时间和主键倒序排列的会话领域对象列表。
     */
    @Override
    public List<ChatConversation> findByCreatedByAndWorkspaceId(Long userId, Long workspaceId) {
        // 步骤 1：基础条件限定创建人和未删除状态，排序保证置顶会话和最近更新优先展示。
        LambdaQueryWrapper<ChatConversationDO> queryWrapper = new LambdaQueryWrapper<ChatConversationDO>()
            .eq(ChatConversationDO::getCreatedBy, userId)
            .eq(ChatConversationDO::getDeleted, 0)
            .orderByDesc(ChatConversationDO::getPinned)
            .orderByDesc(ChatConversationDO::getUpdatedAt)
            .orderByDesc(ChatConversationDO::getId);
        if (workspaceId != null) {
            // 步骤 2：工作空间明确时只查询该空间会话。
            queryWrapper.eq(ChatConversationDO::getWorkspaceId, workspaceId);
        } else {
            // 默认查询需要兼容历史遗留的未归属云端会话，避免旧数据在迁移前后出现“消失”。
            queryWrapper.isNull(ChatConversationDO::getWorkspaceId);
        }
        // 步骤 3：查询结果统一转换为领域对象，保持仓储端口返回领域模型。
        return chatConversationMapper.selectList(queryWrapper)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 按用户与单个工作空间读取 cursor 页。
     * @param userId 用户标识。
     * @param workspaceId 工作空间标识，可为空。
     * @param limit 最大读取条数。
     * @param cursorPinned 上一页末尾会话置顶状态。
     * @param cursorUpdatedAt 上一页末尾会话更新时间。
     * @param cursorId 上一页末尾会话 ID。
     * @return 会话分页结果，多取一条供应用层判断 hasMore。
     */
    @Override
    public List<ChatConversation> findPageByCreatedByAndWorkspaceId(
        Long userId,
        Long workspaceId,
        int limit,
        Boolean cursorPinned,
        LocalDateTime cursorUpdatedAt,
        Long cursorId
    ) {
        return findPageByCreatedByAndWorkspaceScope(
            userId,
            workspaceId == null ? List.of() : List.of(workspaceId),
            workspaceId == null,
            limit,
            cursorPinned,
            cursorUpdatedAt,
            cursorId
        );
    }

    /**
     * 按用户与工作空间范围读取 cursor 页，支持默认云端空间与历史空 workspace 合并。
     * @param userId 用户标识。
     * @param workspaceIds 工作空间白名单。
     * @param includeNullWorkspace 是否包含历史空工作空间。
     * @param limit 最大读取条数。
     * @param cursorPinned 上一页末尾会话置顶状态。
     * @param cursorUpdatedAt 上一页末尾会话更新时间。
     * @param cursorId 上一页末尾会话 ID。
     * @return 会话分页结果，多取一条供应用层判断 hasMore。
     */
    @Override
    public List<ChatConversation> findPageByCreatedByAndWorkspaceScope(
        Long userId,
        List<Long> workspaceIds,
        boolean includeNullWorkspace,
        int limit,
        Boolean cursorPinned,
        LocalDateTime cursorUpdatedAt,
        Long cursorId
    ) {
        LambdaQueryWrapper<ChatConversationDO> queryWrapper = new LambdaQueryWrapper<ChatConversationDO>()
            .eq(ChatConversationDO::getCreatedBy, userId)
            .eq(ChatConversationDO::getDeleted, 0);
        applyWorkspaceScope(queryWrapper, workspaceIds, includeNullWorkspace);
        applyConversationCursor(queryWrapper, cursorPinned, cursorUpdatedAt, cursorId);
        queryWrapper
            .orderByDesc(ChatConversationDO::getPinned)
            .orderByDesc(ChatConversationDO::getUpdatedAt)
            .orderByDesc(ChatConversationDO::getId)
            .last("LIMIT " + Math.max(1, limit));
        return chatConversationMapper.selectList(queryWrapper)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 拼接工作空间范围条件，支持默认云端空间与历史未归属会话合并分页。
     * @param queryWrapper 当前查询包装器。
     * @param workspaceIds 可见工作空间标识集合。
     * @param includeNullWorkspace 是否包含 workspace_id 为空的历史会话。
     */
    private void applyWorkspaceScope(
        LambdaQueryWrapper<ChatConversationDO> queryWrapper,
        List<Long> workspaceIds,
        boolean includeNullWorkspace
    ) {
        List<Long> normalizedWorkspaceIds = workspaceIds == null
            ? List.of()
            : workspaceIds.stream()
                .filter(workspaceId -> workspaceId != null && workspaceId > 0)
                .distinct()
                .toList();
        if (normalizedWorkspaceIds.isEmpty() && !includeNullWorkspace) {
            // 步骤 1：没有任何合法工作空间范围时强制空结果，避免漏条件导致读取全量会话。
            queryWrapper.apply("1 = 0");
            return;
        }
        if (!normalizedWorkspaceIds.isEmpty() && includeNullWorkspace) {
            // 步骤 2：默认云端历史需要同时包含默认云端空间和旧版未归属会话。
            queryWrapper.and(scope -> scope
                .in(ChatConversationDO::getWorkspaceId, normalizedWorkspaceIds)
                .or()
                .isNull(ChatConversationDO::getWorkspaceId));
            return;
        }
        if (!normalizedWorkspaceIds.isEmpty()) {
            // 步骤 3：显式工作空间只读取指定空间会话。
            queryWrapper.in(ChatConversationDO::getWorkspaceId, normalizedWorkspaceIds);
            return;
        }
        // 步骤 4：只有历史兼容范围时读取 workspace_id 为空的旧会话。
        queryWrapper.isNull(ChatConversationDO::getWorkspaceId);
    }

    /**
     * 拼接会话分页游标条件，严格匹配 pinned desc、updated_at desc、id desc 排序。
     * @param queryWrapper 当前查询包装器。
     * @param cursorPinned 上一页末尾会话置顶状态。
     * @param cursorUpdatedAt 上一页末尾会话更新时间。
     * @param cursorId 上一页末尾会话 ID。
     */
    private void applyConversationCursor(
        LambdaQueryWrapper<ChatConversationDO> queryWrapper,
        Boolean cursorPinned,
        LocalDateTime cursorUpdatedAt,
        Long cursorId
    ) {
        if (cursorUpdatedAt == null || cursorId == null) {
            // 游标不完整时按首页处理，避免半截游标造成跳项。
            return;
        }
        int cursorPinnedValue = Boolean.TRUE.equals(cursorPinned) ? 1 : 0;
        queryWrapper.and(cursor -> cursor
            .lt(ChatConversationDO::getPinned, cursorPinnedValue)
            .or(branch -> branch
                .eq(ChatConversationDO::getPinned, cursorPinnedValue)
                .lt(ChatConversationDO::getUpdatedAt, cursorUpdatedAt))
            .or(branch -> branch
                .eq(ChatConversationDO::getPinned, cursorPinnedValue)
                .eq(ChatConversationDO::getUpdatedAt, cursorUpdatedAt)
                .lt(ChatConversationDO::getId, cursorId)));
    }

    /**
     * 供管理端按关键字查询会话列表，支持标题模糊匹配或 ID 精确匹配。
     * @param keyword 可选关键字。
     * @return 会话列表。
     */
    @Override
    public List<ChatConversation> findAll(String keyword) {
        // 步骤 1：管理端列表默认排除逻辑删除记录，并按置顶与更新时间排序。
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<ChatConversationDO> wrapper = new LambdaQueryWrapper<ChatConversationDO>()
            .eq(ChatConversationDO::getDeleted, 0)
            .orderByDesc(ChatConversationDO::getPinned)
            .orderByDesc(ChatConversationDO::getUpdatedAt)
            .orderByDesc(ChatConversationDO::getId);
        if (!normalizedKeyword.isBlank()) {
            // 步骤 2：关键字可同时支持标题模糊查找和会话 ID 精确查找。
            Long conversationId = parseConversationId(normalizedKeyword);
            if (conversationId != null) {
                wrapper.and(query -> query
                    .like(ChatConversationDO::getTitle, normalizedKeyword)
                    .or()
                    .eq(ChatConversationDO::getId, conversationId));
            } else {
                wrapper.like(ChatConversationDO::getTitle, normalizedKeyword);
            }
        }
        // 步骤 3：返回领域对象列表，管理端视图层再负责响应字段投影。
        return chatConversationMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    /**
     * 管理端按工作空间查询会话列表，支持标题模糊匹配或 ID 精确匹配。
     * @param workspaceId 工作空间标识。
     * @param keyword 可选关键字。
     * @return 工作空间内会话列表。
     */
    @Override
    public List<ChatConversation> findAllByWorkspaceId(Long workspaceId, String keyword) {
        if (workspaceId == null) {
            // 工作空间 ID 缺失时没有合法查询范围，直接返回空列表。
            return List.of();
        }
        // 步骤 1：限定工作空间与未删除状态，避免跨空间展示会话。
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<ChatConversationDO> wrapper = new LambdaQueryWrapper<ChatConversationDO>()
            .eq(ChatConversationDO::getWorkspaceId, workspaceId)
            .eq(ChatConversationDO::getDeleted, 0)
            .orderByDesc(ChatConversationDO::getPinned)
            .orderByDesc(ChatConversationDO::getUpdatedAt)
            .orderByDesc(ChatConversationDO::getId);
        if (!normalizedKeyword.isBlank()) {
            // 步骤 2：关键字同时支持标题模糊查找和 ID 精确查找。
            Long conversationId = parseConversationId(normalizedKeyword);
            if (conversationId != null) {
                wrapper.and(query -> query
                    .like(ChatConversationDO::getTitle, normalizedKeyword)
                    .or()
                    .eq(ChatConversationDO::getId, conversationId));
            } else {
                wrapper.like(ChatConversationDO::getTitle, normalizedKeyword);
            }
        }
        // 步骤 3：转换为领域对象后返回，避免管理端直接依赖持久化对象。
        return chatConversationMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    /**
     * 按会话主键查询记录，供反馈详情页展示会话标题与上下文信息。
     * @param conversationId 会话标识。
     * @return 会话记录。
     */
    @Override
    public Optional<ChatConversation> findById(Long conversationId) {
        if (conversationId == null) {
            // 会话 ID 缺失时直接返回空，调用方按无记录处理。
            return Optional.empty();
        }
        // 步骤 1：按主键读取记录，并过滤已逻辑删除会话。
        ChatConversationDO dataObject = chatConversationMapper.selectById(conversationId);
        if (dataObject == null || Integer.valueOf(1).equals(dataObject.getDeleted())) {
            return Optional.empty();
        }
        // 步骤 2：命中后转换为领域对象。
        return Optional.of(toDomain(dataObject));
    }

    @Override
    public Optional<ChatConversation> findByShareToken(String shareToken) {
        if (shareToken == null || shareToken.isBlank()) {
            // 分享令牌为空时不查询数据库，避免误扫无效公开入口。
            return Optional.empty();
        }
        // 步骤 1：公开分享只允许命中未删除会话，shareToken 与 pinned、taskCompletionRead 语义独立。
        ChatConversationDO dataObject = chatConversationMapper.selectOne(
            new LambdaQueryWrapper<ChatConversationDO>()
                .eq(ChatConversationDO::getShareToken, shareToken)
                .eq(ChatConversationDO::getDeleted, 0)
                .last("limit 1")
        );
        if (dataObject == null) {
            return Optional.empty();
        }
        // 步骤 2：返回领域对象，由上层负责公开视图字段裁剪。
        return Optional.of(toDomain(dataObject));
    }

    /**
     * 将数据库会话记录还原为领域对象。
     * @param dataObject chat_conversation 表数据对象。
     * @return 会话领域对象。
     */
    private ChatConversation toDomain(ChatConversationDO dataObject) {
        // 步骤 1：先恢复聚合基础字段，状态字符串在仓储层转换为领域枚举。
        ChatConversation conversation = ChatConversation.create(
            dataObject.getId(),
            dataObject.getTitle(),
            dataObject.getCreatedBy(),
            dataObject.getWorkspaceId(),
            ChatConversationStatus.valueOf(dataObject.getStatus())
        );
        // 步骤 2：恢复最近消息时间、最近运行编号和审计时间，供列表排序和运行态回放使用。
        conversation.restoreRuntimeState(dataObject.getLastMessageAt(), dataObject.getLastRunId());
        conversation.restorePersistenceState(dataObject.getCreatedAt(), dataObject.getUpdatedAt());
        // 步骤 3：置顶和分享状态独立恢复，避免 shareToken 被误用为置顶或提醒状态。
        conversation.restoreSharingState(
            Integer.valueOf(1).equals(dataObject.getPinned()),
            dataObject.getShareToken()
        );
        // 步骤 4：taskCompletionRead 数据库 0 表示未读，其他值或空值按已读兼容处理。
        conversation.restoreTaskCompletionReadState(!Integer.valueOf(0).equals(dataObject.getTaskCompletionRead()));
        return conversation;
    }

    /**
     * 将会话领域对象转换为数据库数据对象。
     * @param conversation 会话领域对象。
     * @return chat_conversation 表数据对象。
     */
    private ChatConversationDO toDataObject(ChatConversation conversation) {
        // 步骤 1：逐项映射领域字段到表字段，枚举统一保存为 name 以保持数据库可读。
        ChatConversationDO dataObject = new ChatConversationDO();
        dataObject.setId(conversation.getId());
        dataObject.setTitle(conversation.getTitle());
        dataObject.setCreatedBy(conversation.getCreatedBy());
        dataObject.setWorkspaceId(conversation.getWorkspaceId());
        dataObject.setStatus(conversation.getStatus().name());
        dataObject.setLastMessageAt(conversation.getLastMessageAt());
        dataObject.setLastRunId(conversation.getLastRunId());
        dataObject.setPinned(Boolean.TRUE.equals(conversation.getPinned()) ? 1 : 0);
        dataObject.setShareToken(conversation.getShareToken());
        dataObject.setTaskCompletionRead(Boolean.FALSE.equals(conversation.getTaskCompletionRead()) ? 0 : 1);
        dataObject.setDeleted(0);
        // 步骤 2：返回 DO 给 save 方法执行 insert 或 update，不在转换方法里触发数据库写入。
        return dataObject;
    }

    /**
     * 尝试将关键字解析为会话 ID，便于支持“按 ID 精确查找”。
     * @param keyword 关键字。
     * @return 可解析时返回会话 ID，否则返回 null。
     */
    private Long parseConversationId(String keyword) {
        try {
            // 步骤 1：纯数字关键字按会话 ID 参与精确匹配。
            return Long.valueOf(keyword);
        } catch (NumberFormatException ignored) {
            // 步骤 2：非数字关键字只参与标题模糊匹配。
            return null;
        }
    }
}
