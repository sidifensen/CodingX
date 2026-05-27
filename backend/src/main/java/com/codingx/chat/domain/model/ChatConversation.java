package com.codingx.chat.domain.model;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
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
     * 置顶标识，供会话列表在同更新时间下优先展示。
     */
    private Boolean pinned;

    /**
     * 会话分享令牌，用于生成只读分享链接。
     */
    private String shareToken;

    /**
     * 最近任务完成提醒是否已读；false 表示侧栏需要展示提醒圆点。
     */
    private Boolean taskCompletionRead;

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
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_CONVERSATION_FIELDS_REQUIRED);
        }
        return ChatConversation.builder()
            .id(id)
            .title(title)
            .createdBy(createdBy)
            .workspaceId(workspaceId)
            .status(status)
            .pinned(Boolean.FALSE)
            .taskCompletionRead(Boolean.TRUE)
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
     * 切换会话置顶状态，供侧栏快速固定高频会话。
     * @param pinned 是否置顶。
     */
    public void setPinned(Boolean pinned) {
        this.pinned = pinned;
    }

    /**
     * 为会话生成或复用分享令牌，确保公开只读链接稳定可回放。
     * @param shareToken 分享令牌。
     */
    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    /**
     * 后台任务进入终态时重置为未读，提示用户回来查看执行结果。
     */
    public void markTaskCompletionUnread() {
        this.taskCompletionRead = Boolean.FALSE;
    }

    /**
     * 用户打开会话后标记任务完成提醒已读，避免刷新后重复提示。
     */
    public void markTaskCompletionRead() {
        this.taskCompletionRead = Boolean.TRUE;
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
     * 恢复持久化层记录的分享与置顶状态，保证列表排序和分享链接回放一致。
     * @param pinned 置顶状态。
     * @param shareToken 分享令牌。
     */
    public void restoreSharingState(Boolean pinned, String shareToken) {
        this.pinned = pinned;
        this.shareToken = shareToken;
    }

    /**
     * 恢复持久化层记录的任务完成提醒已读状态，兼容旧数据默认已读。
     * @param taskCompletionRead 任务完成提醒是否已读。
     */
    public void restoreTaskCompletionReadState(Boolean taskCompletionRead) {
        this.taskCompletionRead = taskCompletionRead == null ? Boolean.TRUE : taskCompletionRead;
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
