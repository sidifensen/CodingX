package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义执行主链路表的数据对象映射。
 */
@Data
@TableName("chat_execution_run")
public class ChatExecutionRunDO {
    @TableId("id") private Long id; // 执行 run 主键。
    @TableField("conversation_id") private Long conversationId; // run 所属会话主键。
    @TableField("request_message_id") private Long requestMessageId; // 触发 run 的用户消息主键。
    @TableField("response_message_id") private Long responseMessageId; // run 生成的助手消息主键，可为空。
    @TableField("intent_code") private String intentCode; // 本次执行命中的意图编码。
    @TableField("status") private String status; // run 执行状态，例如 RUNNING、COMPLETED、FAILED。
    @TableField("queue_status") private String queueStatus; // 队列状态，例如 WAITING、ACQUIRED、REJECTED。
    @TableField("search_enabled") private Boolean searchEnabled; // 本次执行是否启用联网搜索。
    @TableField("artifact_enabled") private Boolean artifactEnabled; // 本次执行是否允许生成产物。
    @TableField("error_message") private String errorMessage; // 执行失败时返回给前端的错误文案。
    @TableField("started_at") private LocalDateTime startedAt; // run 开始执行时间。
    @TableField("finished_at") private LocalDateTime finishedAt; // run 结束时间，未结束时为空。
    @TableField("created_at") private LocalDateTime createdAt; // run 记录创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // run 记录最近更新时间。
}
