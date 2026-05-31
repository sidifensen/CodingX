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
     * 消息反馈仓储，用于按消息和用户维度查询、保存或更新点赞/点踩记录。
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
        // 步骤 1：统一生成更新时间，新增与更新记录共用同一时间点，便于审计。
        LocalDateTime now = LocalDateTime.now();
        // 步骤 2：同一用户对同一消息只能保留一条反馈；存在时更新，不存在时创建。
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
        // 步骤 3：保存反馈记录，仓储层负责 insert/update 细节。
        chatMessageFeedbackRepository.save(feedback);
    }
}
