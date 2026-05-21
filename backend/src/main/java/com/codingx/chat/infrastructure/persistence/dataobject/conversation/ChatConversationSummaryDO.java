package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义会话摘要表的数据对象映射。
 */
@Data
@TableName("chat_conversation_summary")
public class ChatConversationSummaryDO {
    @TableId("id") private Long id;
    @TableField("conversation_id") private Long conversationId;
    @TableField("user_id") private Long userId;
    @TableField("last_message_id") private Long lastMessageId;
    @TableField("content") private String content;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("deleted") private Integer deleted;
}
