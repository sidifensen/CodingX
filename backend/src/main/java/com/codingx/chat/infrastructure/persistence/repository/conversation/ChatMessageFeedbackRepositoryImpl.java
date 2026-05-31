package com.codingx.chat.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codingx.chat.domain.model.ChatMessageFeedback;
import com.codingx.chat.domain.repository.ChatMessageFeedbackRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageFeedbackDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatMessageFeedbackMapper;
import com.codingx.chat.interfaces.response.PageResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现消息反馈仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageFeedbackRepositoryImpl implements ChatMessageFeedbackRepository {

    /** 消息反馈 Mapper，用于读写 chat_message_feedback 表并支持管理端分页过滤。 */
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

    @Override
    public PageResult<ChatMessageFeedback> pageQuery(int current, int size, String keyword, Integer vote) {
        LambdaQueryWrapper<ChatMessageFeedbackDO> wrapper = new LambdaQueryWrapper<ChatMessageFeedbackDO>()
            .eq(ChatMessageFeedbackDO::getDeleted, 0)
            .eq(vote != null, ChatMessageFeedbackDO::getVote, vote)
            .and(StrUtil.isNotBlank(keyword), query -> query
                .like(ChatMessageFeedbackDO::getReason, keyword)
                .or()
                .like(ChatMessageFeedbackDO::getComment, keyword))
            .orderByDesc(ChatMessageFeedbackDO::getCreatedAt)
            .orderByDesc(ChatMessageFeedbackDO::getId);
        Page<ChatMessageFeedbackDO> page = chatMessageFeedbackMapper.selectPage(
            new Page<>(Math.max(1, current), Math.max(1, size)),
            wrapper
        );
        return PageResult.<ChatMessageFeedback>builder()
            .records(page.getRecords().stream().map(this::toDomain).toList())
            .total(page.getTotal())
            .size(page.getSize())
            .current(page.getCurrent())
            .pages(page.getPages())
            .build();
    }

    @Override
    public Optional<ChatMessageFeedback> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        ChatMessageFeedbackDO dataObject = chatMessageFeedbackMapper.selectById(id);
        if (dataObject == null || Integer.valueOf(1).equals(dataObject.getDeleted())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(dataObject));
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
