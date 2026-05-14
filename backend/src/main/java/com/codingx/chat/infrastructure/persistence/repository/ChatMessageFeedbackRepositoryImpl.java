package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatMessageFeedback;
import com.codingx.chat.domain.repository.ChatMessageFeedbackRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageFeedbackDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatMessageFeedbackMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现消息反馈仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageFeedbackRepositoryImpl implements ChatMessageFeedbackRepository {

    private final ChatMessageFeedbackMapper chatMessageFeedbackMapper;

    @Override
    public void save(ChatMessageFeedback feedback) {
        ChatMessageFeedbackDO dataObject = toDataObject(feedback);
        if (chatMessageFeedbackMapper.selectById(feedback.getId()) == null) {
            chatMessageFeedbackMapper.insert(dataObject);
        } else {
            chatMessageFeedbackMapper.updateById(dataObject);
        }
    }

    @Override
    public Optional<ChatMessageFeedback> findByMessageIdAndUserId(Long messageId, Long userId) {
        return Optional.ofNullable(chatMessageFeedbackMapper.selectOne(new LambdaQueryWrapper<ChatMessageFeedbackDO>()
            .eq(ChatMessageFeedbackDO::getMessageId, messageId)
            .eq(ChatMessageFeedbackDO::getUserId, userId)
            .eq(ChatMessageFeedbackDO::getDeleted, 0)
            .last("LIMIT 1"))).map(this::toDomain);
    }

    private ChatMessageFeedbackDO toDataObject(ChatMessageFeedback feedback) {
        ChatMessageFeedbackDO dataObject = new ChatMessageFeedbackDO();
        dataObject.setId(feedback.getId());
        dataObject.setMessageId(feedback.getMessageId());
        dataObject.setConversationId(feedback.getConversationId());
        dataObject.setUserId(feedback.getUserId());
        dataObject.setVote(feedback.getVote());
        dataObject.setReason(feedback.getReason());
        dataObject.setComment(feedback.getComment());
        dataObject.setCreatedAt(feedback.getCreatedAt());
        dataObject.setUpdatedAt(feedback.getUpdatedAt());
        dataObject.setDeleted(feedback.getDeleted());
        return dataObject;
    }

    private ChatMessageFeedback toDomain(ChatMessageFeedbackDO dataObject) {
        return ChatMessageFeedback.builder()
            .id(dataObject.getId())
            .messageId(dataObject.getMessageId())
            .conversationId(dataObject.getConversationId())
            .userId(dataObject.getUserId())
            .vote(dataObject.getVote())
            .reason(dataObject.getReason())
            .comment(dataObject.getComment())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
