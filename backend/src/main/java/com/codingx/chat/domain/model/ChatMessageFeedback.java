package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示助手消息反馈记录，供后续统计与质量回收使用。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessageFeedback {

    /** 反馈主键，数据库生成，新建反馈时为空。 */
    private Long id;
    /** 被评价的助手消息 ID，来源于 chat_message.id。 */
    private Long messageId;
    /** 消息所属会话 ID，用于按会话聚合反馈。 */
    private Long conversationId;
    /** 提交反馈的用户 ID，用于权限隔离和质量分析。 */
    private Long userId;
    /** 投票值，通常 1 表示赞，-1 表示踩，空值表示仅提交文字反馈。 */
    private Integer vote;
    /** 反馈原因枚举或短文本，来自前端预设选项。 */
    private String reason;
    /** 用户补充说明，可为空。 */
    private String comment;
    /** 反馈创建时间，由仓储写入时生成。 */
    private LocalDateTime createdAt;
    /** 反馈更新时间，用户修改反馈时同步更新。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，0 表示有效，1 表示已删除。 */
    private Integer deleted;
}
