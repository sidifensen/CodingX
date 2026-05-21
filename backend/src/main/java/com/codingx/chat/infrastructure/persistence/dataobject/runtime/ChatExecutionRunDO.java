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
    @TableId("id") private Long id;
    @TableField("conversation_id") private Long conversationId;
    @TableField("request_message_id") private Long requestMessageId;
    @TableField("response_message_id") private Long responseMessageId;
    @TableField("task_id") private Long taskId;
    @TableField("intent_code") private String intentCode;
    @TableField("status") private String status;
    @TableField("queue_status") private String queueStatus;
    @TableField("search_enabled") private Boolean searchEnabled;
    @TableField("artifact_enabled") private Boolean artifactEnabled;
    @TableField("error_message") private String errorMessage;
    @TableField("started_at") private LocalDateTime startedAt;
    @TableField("finished_at") private LocalDateTime finishedAt;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
}
