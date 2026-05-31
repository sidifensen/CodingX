package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatMessageReference;
import com.codingx.chat.domain.repository.ChatMessageReferenceRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageReferenceDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatMessageReferenceMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现参考来源仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageReferenceRepositoryImpl implements ChatMessageReferenceRepository {

    /** 消息引用 Mapper，用于读写搜索来源并按 run/message 维度回放。 */
    private final ChatMessageReferenceMapper chatMessageReferenceMapper;

    @Override
    public void save(ChatMessageReference reference) {
        ChatMessageReferenceDO dataObject = toDataObject(reference);
        if (chatMessageReferenceMapper.selectById(reference.getId()) == null) {
            chatMessageReferenceMapper.insert(dataObject);
        } else {
            chatMessageReferenceMapper.updateById(dataObject);
        }
    }

    @Override
    public List<ChatMessageReference> findByRunId(Long runId) {
        return chatMessageReferenceMapper.selectList(new LambdaQueryWrapper<ChatMessageReferenceDO>()
                .eq(ChatMessageReferenceDO::getRunId, runId)
                .orderByAsc(ChatMessageReferenceDO::getRankNo))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<ChatMessageReference> findByMessageId(Long messageId) {
        return chatMessageReferenceMapper.selectList(new LambdaQueryWrapper<ChatMessageReferenceDO>()
                .eq(ChatMessageReferenceDO::getMessageId, messageId)
                .orderByAsc(ChatMessageReferenceDO::getRankNo)
                .orderByAsc(ChatMessageReferenceDO::getId))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private ChatMessageReferenceDO toDataObject(ChatMessageReference reference) {
        ChatMessageReferenceDO dataObject = new ChatMessageReferenceDO();
        dataObject.setId(reference.getId());
        dataObject.setRunId(reference.getRunId());
        dataObject.setMessageId(reference.getMessageId());
        dataObject.setConversationId(reference.getConversationId());
        dataObject.setSourceType(reference.getSourceType());
        dataObject.setTitle(reference.getTitle());
        dataObject.setUrl(reference.getUrl());
        dataObject.setSiteName(reference.getSiteName());
        dataObject.setSnippet(reference.getSnippet());
        dataObject.setRankNo(reference.getRankNo());
        dataObject.setMetadataJson(reference.getMetadataJson());
        dataObject.setCreatedAt(reference.getCreatedAt());
        return dataObject;
    }

    private ChatMessageReference toDomain(ChatMessageReferenceDO dataObject) {
        return ChatMessageReference.builder()
            .id(dataObject.getId())
            .runId(dataObject.getRunId())
            .messageId(dataObject.getMessageId())
            .conversationId(dataObject.getConversationId())
            .sourceType(dataObject.getSourceType())
            .title(dataObject.getTitle())
            .url(dataObject.getUrl())
            .siteName(dataObject.getSiteName())
            .snippet(dataObject.getSnippet())
            .rankNo(dataObject.getRankNo())
            .metadataJson(dataObject.getMetadataJson())
            .createdAt(dataObject.getCreatedAt())
            .build();
    }
}
