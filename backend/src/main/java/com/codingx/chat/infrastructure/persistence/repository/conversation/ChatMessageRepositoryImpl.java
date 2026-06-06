package com.codingx.chat.infrastructure.persistence.repository;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
 * 聊天消息仓储实现，负责在领域消息对象与 chat_message 表数据对象之间转换。
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    /**
     * 聊天消息 MyBatis Mapper，用于执行 chat_message 表的增删改查。
     */
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 保存消息聚合，已存在时更新，不存在时新增。
     * @param message 待持久化的聊天消息领域对象。
     */
    @Override
    public void save(ChatMessage message) {
        // 步骤 1：先把领域对象转换为数据对象，保持数据库字段写入集中在仓储层。
        ChatMessageDO dataObject = toDataObject(message);
        if (chatMessageMapper.selectById(message.getId()) == null) {
            // 步骤 2：主键不存在时插入新消息，通常对应用户消息或刚生成的助手消息。
            chatMessageMapper.insert(dataObject);
        } else {
            // 步骤 3：主键已存在时更新运行态字段，通常对应流式回复完成后补齐终态。
            chatMessageMapper.updateById(dataObject);
        }
    }

    /**
     * 查询会话下未删除消息。
     * @param conversationId 会话标识。
     * @return 按创建时间升序排列的消息领域对象列表。
     */
    @Override
    public List<ChatMessage> findByConversationId(Long conversationId) {
        // 步骤 1：只读取未逻辑删除的消息，并按创建时间升序保证上下文顺序稳定。
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getConversationId, conversationId)
                .eq(ChatMessageDO::getDeleted, 0)
                .orderByAsc(ChatMessageDO::getCreatedAt))
            .stream()
            // 步骤 2：查询结果统一还原为领域对象，避免应用层直接依赖 DO 字段。
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
            // 会话或消息列表缺失时没有可删除范围，直接返回避免生成无条件 update。
            return;
        }
        // 步骤 1：逻辑删除字段受 MyBatis-Plus 全局逻辑删除规则影响，必须在 wrapper 里显式 SET，避免接口返回成功但 deleted 未落库。
        chatMessageMapper.update(
            null,
            new UpdateWrapper<ChatMessageDO>()
                .set("deleted", 1)
                .set("updated_at", LocalDateTime.now())
                .eq("conversation_id", conversationId)
                .in("id", messageIds)
                .eq("deleted", 0)
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
            // 消息主键缺失时返回空，调用方按无记录处理。
            return Optional.empty();
        }
        // 步骤 1：按主键读取 DO，存在时还原领域对象，不存在时返回 Optional.empty。
        return Optional.ofNullable(chatMessageMapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 将数据库消息记录还原为领域对象。
     * @param dataObject chat_message 表数据对象。
     * @return 聊天消息领域对象。
     */
    private ChatMessage toDomain(ChatMessageDO dataObject) {
        // 步骤 1：先恢复领域必填字段，枚举字段在仓储层完成字符串到枚举的转换。
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
        // 步骤 2：再恢复运行态与审计字段，保持领域构造逻辑与持久化恢复逻辑分离。
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
     * 将领域消息对象转换为数据库数据对象。
     * @param message 聊天消息领域对象。
     * @return chat_message 表数据对象。
     */
    private ChatMessageDO toDataObject(ChatMessage message) {
        // 步骤 1：逐项映射领域字段到表字段，枚举统一保存为 name 以保持数据库可读。
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
        // 步骤 2：返回 DO 给 save 方法执行 insert 或 update，不在转换方法里触发数据库写入。
        return dataObject;
    }
}
