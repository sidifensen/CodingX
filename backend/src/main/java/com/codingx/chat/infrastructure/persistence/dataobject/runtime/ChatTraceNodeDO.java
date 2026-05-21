package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义 Trace 节点表的数据对象映射。
 */
@Data
@TableName("chat_trace_node")
public class ChatTraceNodeDO {
    @TableId("id") private Long id;
    @TableField("trace_id") private String traceId;
    @TableField("node_id") private String nodeId;
    @TableField("parent_node_id") private String parentNodeId;
    @TableField("depth") private Integer depth;
    @TableField("node_type") private String nodeType;
    @TableField("node_name") private String nodeName;
    @TableField("class_name") private String className;
    @TableField("method_name") private String methodName;
    @TableField("status") private String status;
    @TableField("error_message") private String errorMessage;
    @TableField("duration_ms") private Long durationMs;
    @TableField("extra_data_json") private String extraDataJson;
    @TableField("started_at") private LocalDateTime startedAt;
    @TableField("finished_at") private LocalDateTime finishedAt;
    @TableField("created_at") private LocalDateTime createdAt;
}
