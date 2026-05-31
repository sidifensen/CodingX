package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示 Trace 链路中的单个节点记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatTraceNode {

    /** Trace 节点主键，数据库生成，新建节点时为空。 */
    private Long id;
    /** Trace 链路 ID，同一次请求内所有节点共享该值。 */
    private String traceId;
    /** 当前节点 ID，用于构建调用树。 */
    private String nodeId;
    /** 父节点 ID，根节点为空。 */
    private String parentNodeId;
    /** 节点深度，根节点通常为 0，用于前端缩进展示。 */
    private Integer depth;
    /** 节点类型，如 controller、service、search，用于分类统计。 */
    private String nodeType;
    /** 节点名称，通常来自注解配置或方法语义。 */
    private String nodeName;
    /** 执行节点所在类名，便于定位代码入口。 */
    private String className;
    /** 执行节点所在方法名，便于定位具体调用点。 */
    private String methodName;
    /** 执行状态，如 RUNNING、SUCCESS、FAILED。 */
    private String status;
    /** 异常信息，成功节点为空，失败节点记录返回前端或日志的摘要。 */
    private String errorMessage;
    /** 节点耗时毫秒数，节点结束时写入。 */
    private Long durationMs;
    /** 扩展数据 JSON，保存入参摘要、返回摘要或诊断字段。 */
    private String extraDataJson;
    /** 节点开始时间，进入切面或执行器时写入。 */
    private LocalDateTime startedAt;
    /** 节点结束时间，成功或失败收口时写入。 */
    private LocalDateTime finishedAt;
    /** 节点创建时间，由仓储写入时生成。 */
    private LocalDateTime createdAt;
}
