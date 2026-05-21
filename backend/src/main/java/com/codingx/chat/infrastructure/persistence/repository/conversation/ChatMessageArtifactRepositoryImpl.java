package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatMessageArtifact;
import com.codingx.chat.domain.repository.ChatMessageArtifactRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageArtifactDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatMessageArtifactMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现聊天产物仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageArtifactRepositoryImpl implements ChatMessageArtifactRepository {

    private final ChatMessageArtifactMapper chatMessageArtifactMapper;

    @Override
    public void save(ChatMessageArtifact artifact) {
        ChatMessageArtifactDO dataObject = toDataObject(artifact);
        if (chatMessageArtifactMapper.selectById(artifact.getId()) == null) {
            chatMessageArtifactMapper.insert(dataObject);
        } else {
            chatMessageArtifactMapper.updateById(dataObject);
        }
    }

    @Override
    public List<ChatMessageArtifact> findByRunId(Long runId) {
        return chatMessageArtifactMapper.selectList(new LambdaQueryWrapper<ChatMessageArtifactDO>()
                .eq(ChatMessageArtifactDO::getRunId, runId)
                .orderByAsc(ChatMessageArtifactDO::getCreatedAt))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private ChatMessageArtifactDO toDataObject(ChatMessageArtifact artifact) {
        ChatMessageArtifactDO dataObject = new ChatMessageArtifactDO();
        dataObject.setId(artifact.getId());
        dataObject.setRunId(artifact.getRunId());
        dataObject.setMessageId(artifact.getMessageId());
        dataObject.setConversationId(artifact.getConversationId());
        dataObject.setArtifactType(artifact.getArtifactType());
        dataObject.setName(artifact.getName());
        dataObject.setMimeType(artifact.getMimeType());
        dataObject.setStoragePath(artifact.getStoragePath());
        dataObject.setContentPreview(artifact.getContentPreview());
        dataObject.setMetadataJson(artifact.getMetadataJson());
        dataObject.setCreatedAt(artifact.getCreatedAt());
        return dataObject;
    }

    private ChatMessageArtifact toDomain(ChatMessageArtifactDO dataObject) {
        return ChatMessageArtifact.builder()
            .id(dataObject.getId())
            .runId(dataObject.getRunId())
            .messageId(dataObject.getMessageId())
            .conversationId(dataObject.getConversationId())
            .artifactType(dataObject.getArtifactType())
            .name(dataObject.getName())
            .mimeType(dataObject.getMimeType())
            .storagePath(dataObject.getStoragePath())
            .contentPreview(dataObject.getContentPreview())
            .metadataJson(dataObject.getMetadataJson())
            .createdAt(dataObject.getCreatedAt())
            .build();
    }
}
