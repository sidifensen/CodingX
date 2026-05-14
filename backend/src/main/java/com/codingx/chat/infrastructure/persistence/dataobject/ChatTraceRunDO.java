package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义 Trace 根链路表的数据对象映射。
 */
@Data
@TableName("chat_trace_run")
public class ChatTraceRunDO {
    @TableId("id") private Long id;
    @TableField("trace_id") private String traceId;
    @TableField("trace_name") private String traceName;
    @TableField("entry_method") private String entryMethod;
    @TableField("conversation_id") private Long conversationId;
    @TableField("task_id") private Long taskId;
    @TableField("user_id") private Long userId;
    @TableField("status") private String status;
    @TableField("error_message") private String errorMessage;
    @TableField("duration_ms") private Long durationMs;
    @TableField("extra_data_json") private String extraDataJson;
    @TableField("started_at") private LocalDateTime startedAt;
    @TableField("finished_at") private LocalDateTime finishedAt;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("deleted") private Integer deleted;
}
