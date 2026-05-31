package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.repository.ChatAttachmentRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatAttachmentDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatAttachmentMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现聊天附件仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatAttachmentRepositoryImpl implements ChatAttachmentRepository {

    /** 附件 Mapper，用于执行 chat_attachment 表的查询、插入和更新。 */
    private final ChatAttachmentMapper chatAttachmentMapper;

    @Override
    public void save(ChatAttachment attachment) {
        ChatAttachmentDO dataObject = toDataObject(attachment);
        if (chatAttachmentMapper.selectById(attachment.getId()) == null) {
            chatAttachmentMapper.insert(dataObject);
        } else {
            chatAttachmentMapper.updateById(dataObject);
        }
    }

    @Override
    public List<ChatAttachment> findByIds(List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        return chatAttachmentMapper.selectList(new LambdaQueryWrapper<ChatAttachmentDO>()
                .in(ChatAttachmentDO::getId, attachmentIds)
                .eq(ChatAttachmentDO::getDeleted, 0))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<ChatAttachment> findByMessageId(Long messageId) {
        if (messageId == null) {
            return List.of();
        }
        return chatAttachmentMapper.selectList(new LambdaQueryWrapper<ChatAttachmentDO>()
                .eq(ChatAttachmentDO::getMessageId, messageId)
                .eq(ChatAttachmentDO::getDeleted, 0)
                .orderByAsc(ChatAttachmentDO::getCreatedAt))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<ChatAttachment> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        ChatAttachmentDO dataObject = chatAttachmentMapper.selectById(id);
        if (dataObject == null || Integer.valueOf(1).equals(dataObject.getDeleted())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(dataObject));
    }

    private ChatAttachmentDO toDataObject(ChatAttachment attachment) {
        ChatAttachmentDO dataObject = new ChatAttachmentDO();
        dataObject.setId(attachment.getId());
        dataObject.setRunId(attachment.getRunId());
        dataObject.setConversationId(attachment.getConversationId());
        dataObject.setMessageId(attachment.getMessageId());
        dataObject.setUploadedBy(attachment.getUploadedBy());
        dataObject.setAttachmentType(attachment.getAttachmentType());
        dataObject.setFileName(attachment.getFileName());
        dataObject.setFileExt(attachment.getFileExt());
        dataObject.setMimeType(attachment.getMimeType());
        dataObject.setFileSize(attachment.getFileSize());
        dataObject.setStorageKey(attachment.getStorageKey());
        dataObject.setPreviewUrl(attachment.getPreviewUrl());
        dataObject.setContentSummary(attachment.getContentSummary());
        dataObject.setStatus(attachment.getStatus());
        dataObject.setCreatedAt(attachment.getCreatedAt());
        dataObject.setUpdatedAt(attachment.getUpdatedAt());
        dataObject.setDeleted(attachment.getDeleted() == null ? 0 : attachment.getDeleted());
        return dataObject;
    }

    private ChatAttachment toDomain(ChatAttachmentDO dataObject) {
        return ChatAttachment.builder()
            .id(dataObject.getId())
            .runId(dataObject.getRunId())
            .conversationId(dataObject.getConversationId())
            .messageId(dataObject.getMessageId())
            .uploadedBy(dataObject.getUploadedBy())
            .attachmentType(dataObject.getAttachmentType())
            .fileName(dataObject.getFileName())
            .fileExt(dataObject.getFileExt())
            .mimeType(dataObject.getMimeType())
            .fileSize(dataObject.getFileSize())
            .storageKey(dataObject.getStorageKey())
            .previewUrl(dataObject.getPreviewUrl())
            .contentSummary(dataObject.getContentSummary())
            .status(dataObject.getStatus())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
