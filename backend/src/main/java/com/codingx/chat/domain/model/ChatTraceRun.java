package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一次聊天链路的根 Trace 记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatTraceRun {

    /**
     * Trace 运行记录主键，首次创建前为空，持久化时由仓储或应用服务补齐。
     */
    private Long id;

    /**
     * 全链路追踪标识，串联根运行记录与节点记录，业务上要求同一次调用内稳定。
     */
    private String traceId;

    /**
     * Trace 展示名称，可为空；用于管理端列表快速识别本次链路来源。
     */
    private String traceName;

    /**
     * 入口方法名称，例如聊天流式入口；用于区分不同业务入口产生的 Trace。
     */
    private String entryMethod;

    /**
     * 关联聊天会话 ID，可为空；非会话入口产生的 Trace 不强制绑定会话。
     */
    private Long conversationId;

    /**
     * 关联后台任务 ID，可为空；异步任务完成后用于回查任务执行链路。
     */
    private Long taskId;

    /**
     * 触发用户 ID，可为空；系统任务或历史数据可能缺失用户上下文。
     */
    private Long userId;

    /**
     * Trace 状态，常见值为 RUNNING、SUCCESS、FAILED，由入口和收口阶段流转。
     */
    private String status;

    /**
     * 失败原因摘要，仅失败时有值；详细堆栈保留在日志，避免领域对象泄露内部实现。
     */
    private String errorMessage;

    /**
     * 链路耗时毫秒数，运行中可为空，收口时由开始和结束时间计算后写入。
     */
    private Long durationMs;

    /**
     * 扩展元数据 JSON，可为空；用于保留不适合拆列的调试上下文。
     */
    private String extraDataJson;

    /**
     * Trace 开始时间，创建运行记录时写入，用于耗时计算和列表排序。
     */
    private LocalDateTime startedAt;

    /**
     * Trace 结束时间，运行中为空，成功或失败收口时写入。
     */
    private LocalDateTime finishedAt;

    /**
     * 数据创建时间，由持久化入口补齐；历史恢复时保持数据库原值。
     */
    private LocalDateTime createdAt;

    /**
     * 数据更新时间，状态收口时同步刷新为结束时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 软删除标记，0 表示有效，1 表示已删除；Trace 查询默认只展示有效记录。
     */
    private Integer deleted;

    /**
     * 创建运行中的根 Trace，避免聊天入口依赖 Lombok 生成的 builder 内部类。
     * @param traceId 链路标识。
     * @param traceName 链路名称。
     * @param conversationId 会话标识。
     * @param userId 用户标识。
     * @return 运行中的根 Trace。
     */
    public static ChatTraceRun createRunning(String traceId, String traceName, Long conversationId, Long userId) {
        // 步骤 1：构造最小运行态 Trace，主键和审计字段留给持久化入口补齐。
        ChatTraceRun traceRun = new ChatTraceRun();
        traceRun.traceId = traceId;
        traceRun.traceName = traceName;
        traceRun.conversationId = conversationId;
        traceRun.userId = userId;
        traceRun.status = "RUNNING";
        return traceRun;
    }

    /**
     * 从持久化数据恢复领域对象，保持仓储层不依赖 Lombok 生成代码。
     * @param id 主键。
     * @param traceId 链路追踪标识。
     * @param traceName Trace 展示名称。
     * @param entryMethod 入口方法名称。
     * @param conversationId 关联会话 ID。
     * @param taskId 关联任务 ID。
     * @param userId 触发用户 ID。
     * @param status Trace 状态。
     * @param errorMessage 失败原因摘要。
     * @param durationMs 链路耗时毫秒数。
     * @param extraDataJson 扩展元数据 JSON。
     * @param startedAt 开始时间。
     * @param finishedAt 结束时间。
     * @param createdAt 创建时间。
     * @param updatedAt 更新时间。
     * @param deleted 软删除标记。
     * @return 从数据库字段恢复出的领域对象。
     */
    public static ChatTraceRun restore(
        Long id,
        String traceId,
        String traceName,
        String entryMethod,
        Long conversationId,
        Long taskId,
        Long userId,
        String status,
        String errorMessage,
        Long durationMs,
        String extraDataJson,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer deleted
    ) {
        // 步骤 1：逐字段恢复数据库快照，避免 builder 默认值改变历史记录状态。
        ChatTraceRun traceRun = new ChatTraceRun();
        traceRun.id = id;
        traceRun.traceId = traceId;
        traceRun.traceName = traceName;
        traceRun.entryMethod = entryMethod;
        traceRun.conversationId = conversationId;
        traceRun.taskId = taskId;
        traceRun.userId = userId;
        traceRun.status = status;
        traceRun.errorMessage = errorMessage;
        traceRun.durationMs = durationMs;
        traceRun.extraDataJson = extraDataJson;
        traceRun.startedAt = startedAt;
        traceRun.finishedAt = finishedAt;
        traceRun.createdAt = createdAt;
        traceRun.updatedAt = updatedAt;
        traceRun.deleted = deleted;
        return traceRun;
    }

    /**
     * 补齐新建 Trace 的持久化元信息。
     * @param id 主键。
     * @param entryMethod 入口方法名称。
     * @param startedAt 开始时间。
     * @param createdAt 创建时间。
     * @param updatedAt 更新时间。
     * @param deleted 软删除标记。
     * @return 带持久化元信息的 Trace 副本。
     */
    public ChatTraceRun withStartMetadata(
        Long id,
        String entryMethod,
        LocalDateTime startedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer deleted
    ) {
        // 步骤 1：先复制原始运行态信息，避免直接修改调用方持有的对象。
        ChatTraceRun traceRun = copy();
        // 步骤 2：补齐主键、入口方法和审计字段，形成可入库的新建记录。
        traceRun.id = id;
        traceRun.entryMethod = entryMethod;
        traceRun.startedAt = startedAt;
        traceRun.createdAt = createdAt;
        traceRun.updatedAt = updatedAt;
        traceRun.deleted = deleted;
        return traceRun;
    }

    /**
     * 生成收口后的 Trace 副本，保留原始链路信息并更新终态字段。
     * @param taskId 关联任务 ID，可为空。
     * @param status 收口状态，通常为 SUCCESS 或 FAILED。
     * @param errorMessage 失败原因摘要，成功时可为空。
     * @param finishedAt 结束时间。
     * @param durationMs 链路耗时毫秒数。
     * @return 已写入终态字段的 Trace 副本。
     */
    public ChatTraceRun finish(Long taskId, String status, String errorMessage, LocalDateTime finishedAt, Long durationMs) {
        // 步骤 1：复制当前 Trace，保证收口操作不改变原对象引用。
        ChatTraceRun traceRun = copy();
        // 步骤 2：写入任务关联、终态、错误摘要和耗时，供管理端 Trace 列表展示。
        traceRun.taskId = taskId;
        traceRun.status = status;
        traceRun.errorMessage = errorMessage;
        traceRun.finishedAt = finishedAt;
        traceRun.updatedAt = finishedAt;
        traceRun.durationMs = durationMs;
        return traceRun;
    }

    private ChatTraceRun copy() {
        // 步骤 1：复用 restore 的逐字段恢复逻辑，确保新增字段时复制语义集中维护。
        return restore(
            id,
            traceId,
            traceName,
            entryMethod,
            conversationId,
            taskId,
            userId,
            status,
            errorMessage,
            durationMs,
            extraDataJson,
            startedAt,
            finishedAt,
            createdAt,
            updatedAt,
            deleted
        );
    }
}
