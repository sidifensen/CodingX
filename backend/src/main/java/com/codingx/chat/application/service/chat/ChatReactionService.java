package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import com.codingx.chat.domain.model.ChatMessageFeedback;
import com.codingx.chat.domain.repository.ChatMessageFeedbackRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责处理助手消息点赞/点踩的保存与更新逻辑。
 */
@Service
@RequiredArgsConstructor
public class ChatReactionService {

    /**
     * 反馈仓储依赖。
     */
    private final ChatMessageFeedbackRepository chatMessageFeedbackRepository;

    /**
     * 提交或更新某条助手消息的反馈记录。
     * @param messageId 消息标识。
     * @param conversationId 会话标识。
     * @param userId 用户标识。
     * @param vote 投票值。
     * @param reason 原因。
     * @param comment 评论。
     */
    public void submitReaction(Long messageId, Long conversationId, Long userId, Integer vote, String reason, String comment) {
        LocalDateTime now = LocalDateTime.now();
        ChatMessageFeedback feedback = chatMessageFeedbackRepository.findByMessageIdAndUserId(messageId, userId)
            .map(existing -> existing.toBuilder()
                .vote(vote)
                .reason(reason)
                .comment(comment)
                .updatedAt(now)
                .build())
            .orElseGet(() -> ChatMessageFeedback.builder()
                .id(IdUtil.getSnowflakeNextId())
                .messageId(messageId)
                .conversationId(conversationId)
                .userId(userId)
                .vote(vote)
                .reason(reason)
                .comment(comment)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build());
        chatMessageFeedbackRepository.save(feedback);
    }
}
