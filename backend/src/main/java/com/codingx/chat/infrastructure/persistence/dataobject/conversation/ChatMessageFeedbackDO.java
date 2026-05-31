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
    @TableId("id") private Long id; // 消息反馈主键。
    @TableField("message_id") private Long messageId; // 被反馈的消息主键。
    @TableField("conversation_id") private Long conversationId; // 反馈所属会话主键。
    @TableField("user_id") private Long userId; // 提交反馈的用户标识。
    @TableField("vote") private Integer vote; // 反馈方向，通常 1 表示点赞，-1 表示点踩。
    @TableField("reason") private String reason; // 反馈原因编码，可为空。
    @TableField("comment") private String comment; // 用户补充说明，可为空。
    @TableField("created_at") private LocalDateTime createdAt; // 反馈创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 反馈最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
