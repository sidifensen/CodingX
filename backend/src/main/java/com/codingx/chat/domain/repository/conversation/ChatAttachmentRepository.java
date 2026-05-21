package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatAttachment;
import java.util.List;
import java.util.Optional;

/**
 * 定义聊天附件仓储契约，负责附件元数据的持久化与检索。
 */
public interface ChatAttachmentRepository {

    /**
     * 保存或更新附件记录。
     * @param attachment 附件记录。
     */
    void save(ChatAttachment attachment);

    /**
     * 批量查询指定主键的附件列表。
     * @param attachmentIds 附件主键集合。
     * @return 附件列表。
     */
    List<ChatAttachment> findByIds(List<Long> attachmentIds);

    /**
     * 查询指定消息绑定的附件列表。
     * @param messageId 消息主键。
     * @return 附件列表。
     */
    List<ChatAttachment> findByMessageId(Long messageId);

    /**
     * 按主键查询单个附件。
     * @param id 附件主键。
     * @return 附件记录。
     */
    Optional<ChatAttachment> findById(Long id);
}
