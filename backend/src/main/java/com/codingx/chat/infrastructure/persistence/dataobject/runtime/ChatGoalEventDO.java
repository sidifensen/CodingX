package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 聊天目标事件数据对象，记录目标创建、更新和终态变化的追加事件。
 */
@Data
@TableName("chat_goal_event")
public class ChatGoalEventDO {
    @TableId("id") private Long id; // 事件主键 ID。
    @TableField("goal_id") private Long goalId; // 所属目标 ID。
    @TableField("conversation_id") private Long conversationId; // 冗余会话 ID，便于按会话审计。
    @TableField("run_id") private Long runId; // 触发事件的聊天运行 ID，可为空。
    @TableField("event_type") private String eventType; // 目标事件类型。
    @TableField("payload_json") private String payloadJson; // 工具输入和目标快照 JSON，可为空。
    @TableField("created_at") private LocalDateTime createdAt; // 事件创建时间。
}
