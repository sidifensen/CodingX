package com.codingx.chat.infrastructure.persistence.dataobject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 聊天会话表的数据对象映射。
 */
@Data
@TableName("chat_conversation")
public class ChatConversationDO {
    @TableId("id") private Long id; // 会话主键。
    @TableField("title") private String title; // 会话展示标题。
    @TableField("created_by") private Long createdBy; // 会话创建人用户标识。
    @TableField("workspace_id") private Long workspaceId; // 会话所属工作空间标识，历史遗留数据可为空。
    @TableField("status") private String status; // 会话状态，映射 ChatConversationStatus。
    @TableField("last_message_at") private LocalDateTime lastMessageAt; // 最近一条消息写入时间。
    @TableField("last_run_id") private Long lastRunId; // 最近一次聊天执行 run 或后台任务标识。
    @TableField("pinned") private Integer pinned; // 置顶标记，1 表示置顶，0 表示普通排序。
    @TableField("share_token") private String shareToken; // 公开分享令牌，仅用于只读分享链接。
    @TableField("task_completion_read") private Integer taskCompletionRead; // 任务完成提醒已读状态，1 已读，0 未读。
    @TableField("created_at") private LocalDateTime createdAt; // 数据创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 数据最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
