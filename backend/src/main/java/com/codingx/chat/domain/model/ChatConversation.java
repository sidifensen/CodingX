package com.codingx.chat.domain.model;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 定义 ChatConversation 的核心领域状态与行为。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatConversation {

    /**
     * 主键标识。
     */
    private Long id;

    /**
     * 展示标题。
     */
    private String title;

    /**
     * 创建人用户标识。
     */
    private Long createdBy;

    /**
     * 所属工作空间标识。
     */
    private Long workspaceId;

    /**
     * 当前状态值。
     */
    private ChatConversationStatus status;

    /**
     * 最后消息时间。
     */
    private LocalDateTime lastMessageAt;

    /**
     * 最近一次执行记录标识。
     */
    private Long lastRunId;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 创建 create 所需数据并返回结果。
     * @param id 输入参数。
     * @param title 输入参数。
     * @param createdBy 输入参数。
     * @param status 输入参数。
     * @return 输入参数。
     */
    public static ChatConversation create(Long id, String title, Long createdBy, Long workspaceId, ChatConversationStatus status) {
        if (id == null || createdBy == null || status == null || StrUtil.isBlank(title)) {
            throw new IllegalArgumentException("Conversation fields are required");
        }
        return ChatConversation.builder()
            .id(id)
            .title(title)
            .createdBy(createdBy)
            .workspaceId(workspaceId)
            .status(status)
            .build();
    }

    /**
     * 兼容旧调用方，默认不绑定工作空间。
     * @param id 输入参数。
     * @param title 输入参数。
     * @param createdBy 输入参数。
     * @param status 输入参数。
     * @return 输入参数。
     */
    public static ChatConversation create(Long id, String title, Long createdBy, ChatConversationStatus status) {
        return create(id, title, createdBy, null, status);
    }

    /**
     * 刷新 touch 处理的时间或状态。
     */
    public void touch() {
        this.lastMessageAt = LocalDateTime.now();
    }

    /**
     * 更新会话标题，用于首轮回答完成后的自动命名。
     * @param title 新标题。
     */
    public void rename(String title) {
        if (StrUtil.isBlank(title)) {
            return;
        }
        this.title = title;
    }

    /**
     * 将当前会话标记为已删除，避免历史列表继续展示。
     */
    public void markDeleted() {
        this.status = ChatConversationStatus.ARCHIVED;
    }

    /**
     * 记录当前会话关联的最新执行记录 ID。
     * @param lastRunId 最新执行记录标识。
     */
    public void recordLastRunId(Long lastRunId) {
        this.lastRunId = lastRunId;
    }

    /**
     * 恢复持久化层中的运行时扩展字段，避免历史回放时丢失链路信息。
     * @param lastMessageAt 最近消息时间。
     * @param lastRunId 最近一次执行记录标识。
     */
    public void restoreRuntimeState(LocalDateTime lastMessageAt, Long lastRunId) {
        this.lastMessageAt = lastMessageAt;
        this.lastRunId = lastRunId;
    }

    /**
     * 恢复持久化层记录的创建/更新时间，供管理端展示与排序。
     * @param createdAt 创建时间。
     * @param updatedAt 更新时间。
     */
    public void restorePersistenceState(LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
