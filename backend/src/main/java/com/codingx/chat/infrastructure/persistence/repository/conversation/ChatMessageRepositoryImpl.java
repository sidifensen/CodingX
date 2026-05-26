package com.codingx.chat.infrastructure.persistence.repository;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatMessageMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 ChatMessageRepositoryImpl 的持久化行为。
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    /**
     * ChatMessageMapper 依赖。
     */
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 持久化 save 处理的状态。
     * @param message 输入参数。
     */
    @Override
    public void save(ChatMessage message) {
        ChatMessageDO dataObject = toDataObject(message);
        if (chatMessageMapper.selectById(message.getId()) == null) {
            chatMessageMapper.insert(dataObject);
        } else {
            chatMessageMapper.updateById(dataObject);
        }
    }

    /**
     * 查询 findByConversationId 需要的数据。
     * @param conversationId 输入参数。
     * @return 输入参数。
     */
    @Override
    public List<ChatMessage> findByConversationId(Long conversationId) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getConversationId, conversationId)
                .eq(ChatMessageDO::getDeleted, 0)
                .orderByAsc(ChatMessageDO::getCreatedAt))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 逻辑删除指定会话内的消息；会话条件必须参与更新，避免跨会话误删。
     * @param conversationId 会话标识。
     * @param messageIds 消息主键列表。
     */
    @Override
    public void softDeleteByConversationIdAndIds(Long conversationId, List<Long> messageIds) {
        if (conversationId == null || CollUtil.isEmpty(messageIds)) {
            return;
        }
        ChatMessageDO dataObject = new ChatMessageDO();
        dataObject.setDeleted(1);
        dataObject.setUpdatedAt(LocalDateTime.now());
        chatMessageMapper.update(
            dataObject,
            new LambdaUpdateWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getConversationId, conversationId)
                .in(ChatMessageDO::getId, messageIds)
                .eq(ChatMessageDO::getDeleted, 0)
        );
    }

    /**
     * 按消息主键查询单条记录，供管理端反馈详情聚合消息上下文。
     * @param id 消息主键。
     * @return 消息记录。
     */
    @Override
    public Optional<ChatMessage> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(chatMessageMapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 执行 toDomain 定义的处理逻辑。
     * @param dataObject 输入参数。
     * @return 输入参数。
     */
    private ChatMessage toDomain(ChatMessageDO dataObject) {
        ChatMessage message = ChatMessage.create(
            dataObject.getId(),
            dataObject.getConversationId(),
            ChatMessageRole.valueOf(dataObject.getRole()),
            dataObject.getContent(),
            ChatMessageStatus.valueOf(dataObject.getStatus()),
            dataObject.getProvider(),
            dataObject.getModel(),
            dataObject.getErrorMessage()
        );
        message.restoreRuntimeState(
            dataObject.getRunId(),
            dataObject.getThinkingContent(),
            dataObject.getThinkingDuration(),
            dataObject.getCreatedAt(),
            dataObject.getUpdatedAt()
        );
        return message;
    }

    /**
     * 执行 toDataObject 定义的处理逻辑。
     * @param message 输入参数。
     * @return 输入参数。
     */
    private ChatMessageDO toDataObject(ChatMessage message) {
        ChatMessageDO dataObject = new ChatMessageDO();
        dataObject.setId(message.getId());
        dataObject.setConversationId(message.getConversationId());
        dataObject.setRunId(message.getRunId());
        dataObject.setRole(message.getRole().name());
        dataObject.setContent(message.getContent());
        dataObject.setThinkingContent(message.getThinkingContent());
        dataObject.setThinkingDuration(message.getThinkingDuration());
        dataObject.setStatus(message.getStatus().name());
        dataObject.setProvider(message.getProvider());
        dataObject.setModel(message.getModel());
        dataObject.setErrorMessage(message.getErrorMessage());
        dataObject.setDeleted(message.getDeleted() == null ? 0 : message.getDeleted());
        dataObject.setCreatedAt(message.getCreatedAt());
        dataObject.setUpdatedAt(message.getUpdatedAt());
        return dataObject;
    }
}
