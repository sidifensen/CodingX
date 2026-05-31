package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示面向用户展示的执行步骤快照。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatExecutionStep {

    /** 执行步骤主键，数据库生成，新建步骤时为空。 */
    private Long id;
    /** 所属执行运行 ID，来源于 chat_execution_run.id。 */
    private Long runId;
    /** 步骤类型，如 search、tool、mcp，用于前端选择展示样式。 */
    private String stepType;
    /** 步骤标题，面向用户展示当前阶段名称。 */
    private String stepTitle;
    /** 步骤状态，如 RUNNING、COMPLETED、FAILED，表示当前执行结果。 */
    private String stepStatus;
    /** 步骤序号，同一 run 内按该值排序展示。 */
    private Long sequenceNo;
    /** 步骤内容摘要，记录搜索词、工具输出或异常提示。 */
    private String content;
    /** 扩展元数据 JSON，保存工具参数、引用信息等非固定字段。 */
    private String metadataJson;
    /** 步骤创建时间，由仓储写入时生成。 */
    private LocalDateTime createdAt;
    /** 步骤更新时间，状态或内容变化时同步更新。 */
    private LocalDateTime updatedAt;
}
