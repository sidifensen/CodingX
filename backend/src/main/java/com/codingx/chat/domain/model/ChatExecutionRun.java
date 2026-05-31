package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 聊天请求执行主链路记录，聚合一次消息生成的 run 状态、队列状态和能力开关。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatExecutionRun {

    /**
     * 执行 run 主键，创建时由应用服务生成；执行步骤、Trace 和上下文记录都通过该值关联。
     */
    private Long id;

    /**
     * 所属会话主键，用于把一次执行链路归属到聊天历史。
     */
    private Long conversationId;

    /**
     * 触发本次执行的用户消息主键，可为空；恢复历史或异常中断场景可能尚未绑定。
     */
    private Long requestMessageId;

    /**
     * 本次执行生成的助手消息主键，可为空；模型还未产出或执行失败时为空。
     */
    private Long responseMessageId;

    /**
     * 关联后台任务主键，可为空；普通聊天没有后台任务时不写入。
     */
    private Long taskId;

    /**
     * 命中的意图编码，可为空；未命中明确意图或跳过意图识别时为空。
     */
    private String intentCode;

    /**
     * 执行状态，常见值为 RUNNING、COMPLETED、FAILED，决定前端历史链路展示。
     */
    private String status;

    /**
     * 队列状态，记录本次请求是否等待、已放行或被门控拒绝。
     */
    private String queueStatus;

    /**
     * 是否启用搜索能力，记录运行时真实决策结果而不是前端原始开关。
     */
    private Boolean searchEnabled;

    /**
     * 是否启用产物生成能力，供执行步骤和管理端排查模型输出链路。
     */
    private Boolean artifactEnabled;

    /**
     * 失败原因摘要，仅失败时写入；返回前端的错误文案由接口层统一处理。
     */
    private String errorMessage;

    /**
     * 执行开始时间，用于耗时统计和运行中任务排序。
     */
    private LocalDateTime startedAt;

    /**
     * 执行结束时间，运行中为空；成功、失败或取消时写入。
     */
    private LocalDateTime finishedAt;

    /**
     * 数据创建时间，由持久化入口写入；历史恢复时保持数据库原值。
     */
    private LocalDateTime createdAt;

    /**
     * 数据最近更新时间，状态变化、消息绑定或任务绑定时刷新。
     */
    private LocalDateTime updatedAt;
}
