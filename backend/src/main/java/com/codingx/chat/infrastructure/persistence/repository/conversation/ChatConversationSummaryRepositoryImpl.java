package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatConversationSummary;
import com.codingx.chat.domain.repository.ChatConversationSummaryRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationSummaryDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatConversationSummaryMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现会话摘要仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatConversationSummaryRepositoryImpl implements ChatConversationSummaryRepository {

    /** 会话摘要 Mapper，用于读写 chat_conversation_summary 表的摘要覆盖点。 */
    private final ChatConversationSummaryMapper chatConversationSummaryMapper;

    @Override
    public void save(ChatConversationSummary summary) {
        ChatConversationSummaryDO dataObject = toDataObject(summary);
        if (chatConversationSummaryMapper.selectById(summary.getId()) == null) {
            chatConversationSummaryMapper.insert(dataObject);
        } else {
            chatConversationSummaryMapper.updateById(dataObject);
        }
    }

    @Override
    public Optional<ChatConversationSummary> findLatestByConversationId(Long conversationId) {
        return Optional.ofNullable(chatConversationSummaryMapper.selectOne(new LambdaQueryWrapper<ChatConversationSummaryDO>()
            .eq(ChatConversationSummaryDO::getConversationId, conversationId)
            .eq(ChatConversationSummaryDO::getDeleted, 0)
            .orderByDesc(ChatConversationSummaryDO::getUpdatedAt)
            .last("LIMIT 1"))).map(this::toDomain);
    }

    private ChatConversationSummaryDO toDataObject(ChatConversationSummary summary) {
        ChatConversationSummaryDO dataObject = new ChatConversationSummaryDO();
        dataObject.setId(summary.getId());
        dataObject.setConversationId(summary.getConversationId());
        dataObject.setUserId(summary.getUserId());
        dataObject.setLastMessageId(summary.getLastMessageId());
        dataObject.setContent(summary.getContent());
        dataObject.setCreatedAt(summary.getCreatedAt());
        dataObject.setUpdatedAt(summary.getUpdatedAt());
        dataObject.setDeleted(summary.getDeleted());
        return dataObject;
    }

    private ChatConversationSummary toDomain(ChatConversationSummaryDO dataObject) {
        return ChatConversationSummary.builder()
            .id(dataObject.getId())
            .conversationId(dataObject.getConversationId())
            .userId(dataObject.getUserId())
            .lastMessageId(dataObject.getLastMessageId())
            .content(dataObject.getContent())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
