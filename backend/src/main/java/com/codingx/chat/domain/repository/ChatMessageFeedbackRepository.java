package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatMessageFeedback;
import com.codingx.chat.interfaces.response.PageResult;
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

    /**
     * 按页查询反馈记录，支持关键字和投票类型过滤。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @param keyword 关键字，匹配反馈原因和评论。
     * @param vote 投票值过滤，null 表示不过滤。
     * @return 反馈分页结果。
     */
    PageResult<ChatMessageFeedback> pageQuery(int current, int size, String keyword, Integer vote);

    /**
     * 按反馈主键查询记录。
     * @param id 反馈主键。
     * @return 反馈记录。
     */
    Optional<ChatMessageFeedback> findById(Long id);
}
