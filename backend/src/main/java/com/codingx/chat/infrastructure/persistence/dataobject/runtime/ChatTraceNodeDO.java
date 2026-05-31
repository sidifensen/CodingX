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
    @TableId("id") private Long id; // Trace 节点主键。
    @TableField("trace_id") private String traceId; // Trace 根链路标识。
    @TableField("node_id") private String nodeId; // 当前节点标识。
    @TableField("parent_node_id") private String parentNodeId; // 父节点标识，根节点可为空。
    @TableField("depth") private Integer depth; // 节点深度，根节点通常为 0。
    @TableField("node_type") private String nodeType; // 节点类型，例如 service、repository、tool。
    @TableField("node_name") private String nodeName; // 节点展示名称。
    @TableField("class_name") private String className; // 记录节点对应的 Java 类名。
    @TableField("method_name") private String methodName; // 记录节点对应的方法名。
    @TableField("status") private String status; // 节点执行状态，例如 RUNNING、COMPLETED、FAILED。
    @TableField("error_message") private String errorMessage; // 节点失败时的错误摘要。
    @TableField("duration_ms") private Long durationMs; // 节点耗时，单位毫秒。
    @TableField("extra_data_json") private String extraDataJson; // 节点扩展数据 JSON。
    @TableField("started_at") private LocalDateTime startedAt; // 节点开始时间。
    @TableField("finished_at") private LocalDateTime finishedAt; // 节点结束时间，未结束时为空。
    @TableField("created_at") private LocalDateTime createdAt; // 节点记录创建时间。
}
