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
    @TableId("id") private Long id; // 摘要主键。
    @TableField("conversation_id") private Long conversationId; // 摘要所属会话主键。
    @TableField("user_id") private Long userId; // 摘要所属用户标识。
    @TableField("last_message_id") private Long lastMessageId; // 生成摘要时覆盖到的最后一条消息主键。
    @TableField("content") private String content; // 会话摘要正文。
    @TableField("created_at") private LocalDateTime createdAt; // 摘要创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 摘要最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
