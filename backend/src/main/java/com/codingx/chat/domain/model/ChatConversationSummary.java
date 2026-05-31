package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示会话摘要快照，供上下文压缩与历史回放复用。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatConversationSummary {

    /** 摘要记录主键，数据库生成，新建摘要时为空。 */
    private Long id;
    /** 所属会话 ID，来源于 chat_conversation.id，不允许为空。 */
    private Long conversationId;
    /** 会话所属用户 ID，用于隔离不同用户的摘要数据。 */
    private Long userId;
    /** 摘要覆盖到的最后一条消息 ID，用于判断是否需要增量摘要。 */
    private Long lastMessageId;
    /** 摘要正文，保存压缩后的上下文内容。 */
    private String content;
    /** 摘要创建时间，由仓储写入时生成。 */
    private LocalDateTime createdAt;
    /** 摘要最近更新时间，摘要刷新时同步更新。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，0 表示有效，1 表示已删除。 */
    private Integer deleted;
}
