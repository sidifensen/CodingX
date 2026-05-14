package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatMessageFeedback;
import java.util.Optional;

/**
 * 定义消息反馈仓储需要提供的最小持久化能力。
 */
public interface ChatMessageFeedbackRepository {

    /**
     * 保存或更新消息反馈。
     * @param feedback 反馈记录。
     */
    void save(ChatMessageFeedback feedback);

    /**
     * 根据消息与用户查询反馈记录。
     * @param messageId 消息标识。
     * @param userId 用户标识。
     * @return 反馈记录。
     */
    Optional<ChatMessageFeedback> findByMessageIdAndUserId(Long messageId, Long userId);
}
