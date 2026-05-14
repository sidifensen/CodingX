package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义执行步骤表的数据对象映射。
 */
@Data
@TableName("chat_execution_step")
public class ChatExecutionStepDO {
    @TableId("id") private Long id;
    @TableField("run_id") private Long runId;
    @TableField("step_type") private String stepType;
    @TableField("step_title") private String stepTitle;
    @TableField("step_status") private String stepStatus;
    @TableField("sequence_no") private Long sequenceNo;
    @TableField("content") private String content;
    @TableField("metadata_json") private String metadataJson;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
}
