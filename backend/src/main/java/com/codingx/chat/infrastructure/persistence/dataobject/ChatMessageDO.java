package com.codingx.chat.infrastructure.persistence.dataobject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义 ChatMessageDO 的数据库字段映射。
 */
@Data
@TableName("chat_message")
public class ChatMessageDO {
    @TableId("id") private Long id;
    @TableField("conversation_id") private Long conversationId;
    @TableField("role") private String role;
    @TableField("content") private String content;
    @TableField("status") private String status;
    @TableField("provider") private String provider;
    @TableField("model") private String model;
    @TableField("error_message") private String errorMessage;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
}
