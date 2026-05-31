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
    @TableId("id") private Long id; // 执行步骤主键。
    @TableField("run_id") private Long runId; // 所属执行 run 主键。
    @TableField("step_type") private String stepType; // 步骤类型，例如 intent、mcp、tool、model。
    @TableField("step_title") private String stepTitle; // 步骤展示标题。
    @TableField("step_status") private String stepStatus; // 步骤状态，例如 RUNNING、COMPLETED、FAILED。
    @TableField("sequence_no") private Long sequenceNo; // 步骤顺序号，数值越小越靠前。
    @TableField("content") private String content; // 步骤输出内容或摘要。
    @TableField("metadata_json") private String metadataJson; // 步骤扩展元数据 JSON。
    @TableField("created_at") private LocalDateTime createdAt; // 步骤创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 步骤最近更新时间。
}
