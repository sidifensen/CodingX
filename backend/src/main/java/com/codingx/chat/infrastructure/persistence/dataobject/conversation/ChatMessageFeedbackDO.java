package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义消息反馈表的数据对象映射。
 */
@Data
@TableName("chat_message_feedback")
public class ChatMessageFeedbackDO {
    @TableId("id") private Long id;
    @TableField("message_id") private Long messageId;
    @TableField("conversation_id") private Long conversationId;
    @TableField("user_id") private Long userId;
    @TableField("vote") private Integer vote;
    @TableField("reason") private String reason;
    @TableField("comment") private String comment;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("deleted") private Integer deleted;
}
