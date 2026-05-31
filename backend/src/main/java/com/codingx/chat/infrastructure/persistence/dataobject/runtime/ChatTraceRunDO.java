package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Trace 根链路数据对象，对应 chat_trace_run 表，记录一次聊天链路的入口、状态和耗时。
 */
@Data
@TableName("chat_trace_run")
public class ChatTraceRunDO {
    @TableId("id") private Long id; // Trace 根链路主键。
    @TableField("trace_id") private String traceId; // Trace 根链路唯一标识。
    @TableField("trace_name") private String traceName; // Trace 展示名称。
    @TableField("entry_method") private String entryMethod; // Trace 入口方法或入口场景。
    @TableField("conversation_id") private Long conversationId; // 关联会话主键，可为空。
    @TableField("task_id") private Long taskId; // 关联后台任务主键，可为空。
    @TableField("user_id") private Long userId; // 触发 Trace 的用户标识，可为空。
    @TableField("status") private String status; // Trace 状态，例如 RUNNING、COMPLETED、FAILED。
    @TableField("error_message") private String errorMessage; // Trace 失败时的错误摘要。
    @TableField("duration_ms") private Long durationMs; // Trace 总耗时，单位毫秒。
    @TableField("extra_data_json") private String extraDataJson; // Trace 扩展数据 JSON。
    @TableField("started_at") private LocalDateTime startedAt; // Trace 开始时间。
    @TableField("finished_at") private LocalDateTime finishedAt; // Trace 结束时间，未结束时为空。
    @TableField("created_at") private LocalDateTime createdAt; // Trace 记录创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // Trace 记录最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
