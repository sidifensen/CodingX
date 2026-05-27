package com.codingx.chat.infrastructure.persistence.dataobject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义 ChatConversationDO 的数据库字段映射。
 */
@Data
@TableName("chat_conversation")
public class ChatConversationDO {
    @TableId("id") private Long id;
    @TableField("title") private String title;
    @TableField("created_by") private Long createdBy;
    @TableField("workspace_id") private Long workspaceId;
    @TableField("status") private String status;
    @TableField("last_message_at") private LocalDateTime lastMessageAt;
    @TableField("last_run_id") private Long lastRunId;
    @TableField("pinned") private Integer pinned;
    @TableField("share_token") private String shareToken;
    @TableField("task_completion_read") private Integer taskCompletionRead;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("deleted") private Integer deleted;
}
