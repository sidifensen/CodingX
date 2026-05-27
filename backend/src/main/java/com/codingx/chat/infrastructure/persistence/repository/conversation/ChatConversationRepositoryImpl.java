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
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 ChatConversationRepositoryImpl 的持久化行为。
 */
@Repository
@RequiredArgsConstructor
public class ChatConversationRepositoryImpl implements ChatConversationRepository {

    /**
     * ChatConversationMapper 依赖。
     */
    private final ChatConversationMapper chatConversationMapper;

    /**
     * 加载 requireById 所需数据，不存在时抛出异常。
     * @param conversationId 输入参数。
     * @return 输入参数。
     */
    @Override
    public ChatConversation requireById(Long conversationId) {
        ChatConversationDO dataObject = chatConversationMapper.selectById(conversationId);
        if (dataObject == null || Integer.valueOf(1).equals(dataObject.getDeleted())) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_CONVERSATION_NOT_FOUND);
        }
        return toDomain(dataObject);
    }

    /**
     * 持久化 save 处理的状态。
     * @param conversation 输入参数。
     */
    @Override
    public void save(ChatConversation conversation) {
        ChatConversationDO dataObject = toDataObject(conversation);
        if (chatConversationMapper.selectById(conversation.getId()) == null) {
            chatConversationMapper.insert(dataObject);
        } else {
            chatConversationMapper.updateById(dataObject);
        }
    }

    /**
     * 执行逻辑删除，避免误删历史数据。
     * @param conversationId 会话标识。
     */
    @Override
    public void deleteById(Long conversationId) {
        chatConversationMapper.update(
            null,
            new LambdaUpdateWrapper<ChatConversationDO>()
                .eq(ChatConversationDO::getId, conversationId)
                .set(ChatConversationDO::getDeleted, 1)
        );
    }

    /**
     * 查询 findByCreatedBy 需要的数据。
     * @param userId 输入参数。
     * @return 输入参数。
     */
    @Override
    public List<ChatConversation> findByCreatedByAndWorkspaceId(Long userId, Long workspaceId) {
        LambdaQueryWrapper<ChatConversationDO> queryWrapper = new LambdaQueryWrapper<ChatConversationDO>()
            .eq(ChatConversationDO::getCreatedBy, userId)
            .eq(ChatConversationDO::getDeleted, 0)
            .orderByDesc(ChatConversationDO::getPinned)
            .orderByDesc(ChatConversationDO::getUpdatedAt)
            .orderByDesc(ChatConversationDO::getId);
        if (workspaceId != null) {
            queryWrapper.eq(ChatConversationDO::getWorkspaceId, workspaceId);
        } else {
            // 默认查询需要兼容历史遗留的未归属云端会话，避免旧数据在迁移前后出现“消失”。
            queryWrapper.isNull(ChatConversationDO::getWorkspaceId);
        }
        return chatConversationMapper.selectList(queryWrapper)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 供管理端按关键字查询会话列表，支持标题模糊匹配或 ID 精确匹配。
     * @param keyword 可选关键字。
     * @return 会话列表。
     */
    @Override
    public List<ChatConversation> findAll(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<ChatConversationDO> wrapper = new LambdaQueryWrapper<ChatConversationDO>()
            .eq(ChatConversationDO::getDeleted, 0)
            .orderByDesc(ChatConversationDO::getPinned)
            .orderByDesc(ChatConversationDO::getUpdatedAt)
            .orderByDesc(ChatConversationDO::getId);
        if (!normalizedKeyword.isBlank()) {
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
            return List.of();
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<ChatConversationDO> wrapper = new LambdaQueryWrapper<ChatConversationDO>()
            .eq(ChatConversationDO::getWorkspaceId, workspaceId)
            .eq(ChatConversationDO::getDeleted, 0)
            .orderByDesc(ChatConversationDO::getPinned)
            .orderByDesc(ChatConversationDO::getUpdatedAt)
            .orderByDesc(ChatConversationDO::getId);
        if (!normalizedKeyword.isBlank()) {
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
            return Optional.empty();
        }
        ChatConversationDO dataObject = chatConversationMapper.selectById(conversationId);
        if (dataObject == null || Integer.valueOf(1).equals(dataObject.getDeleted())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(dataObject));
    }

    @Override
    public Optional<ChatConversation> findByShareToken(String shareToken) {
        if (shareToken == null || shareToken.isBlank()) {
            return Optional.empty();
        }
        ChatConversationDO dataObject = chatConversationMapper.selectOne(
            new LambdaQueryWrapper<ChatConversationDO>()
                .eq(ChatConversationDO::getShareToken, shareToken)
                .eq(ChatConversationDO::getDeleted, 0)
                .last("limit 1")
        );
        if (dataObject == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(dataObject));
    }

    /**
     * 执行 toDomain 定义的处理逻辑。
     * @param dataObject 输入参数。
     * @return 输入参数。
     */
    private ChatConversation toDomain(ChatConversationDO dataObject) {
        ChatConversation conversation = ChatConversation.create(
            dataObject.getId(),
            dataObject.getTitle(),
            dataObject.getCreatedBy(),
            dataObject.getWorkspaceId(),
            ChatConversationStatus.valueOf(dataObject.getStatus())
        );
        conversation.restoreRuntimeState(dataObject.getLastMessageAt(), dataObject.getLastRunId());
        conversation.restorePersistenceState(dataObject.getCreatedAt(), dataObject.getUpdatedAt());
        conversation.restoreSharingState(
            Integer.valueOf(1).equals(dataObject.getPinned()),
            dataObject.getShareToken()
        );
        conversation.restoreTaskCompletionReadState(!Integer.valueOf(0).equals(dataObject.getTaskCompletionRead()));
        return conversation;
    }

    /**
     * 执行 toDataObject 定义的处理逻辑。
     * @param conversation 输入参数。
     * @return 输入参数。
     */
    private ChatConversationDO toDataObject(ChatConversation conversation) {
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
        return dataObject;
    }

    /**
     * 尝试将关键字解析为会话 ID，便于支持“按 ID 精确查找”。
     * @param keyword 关键字。
     * @return 可解析时返回会话 ID，否则返回 null。
     */
    private Long parseConversationId(String keyword) {
        try {
            return Long.valueOf(keyword);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
