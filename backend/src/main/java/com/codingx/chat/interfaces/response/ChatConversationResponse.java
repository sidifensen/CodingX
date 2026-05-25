package com.codingx.chat.interfaces.response;
import com.codingx.chat.domain.model.ChatConversationStatus;
import java.time.LocalDateTime;

/**
 * 定义 ChatConversationResponse 使用的数据载体。
 */
public record ChatConversationResponse(
    Long id, // 主键标识。
    String title, // 展示标题。
    ChatConversationStatus status, // 当前状态值。
    LocalDateTime lastMessageAt, // 最后消息时间。
    Long lastRunId, // 最近一次执行记录标识。
    Boolean pinned, // 置顶状态。
    String shareToken, // 分享令牌。
    Long workspaceId, // 所属工作空间标识。
    String workspaceName, // 所属工作空间名称。
    WorkspaceType workspaceType, // 所属工作空间类型。
    Long activeTaskId, // 当前运行中的后台任务标识。
    String activeTaskStatus, // 当前运行中的后台任务状态。
    Long lastTaskId, // 最近后台任务标识。
    String lastTaskStatus, // 最近后台任务状态。
    LocalDateTime lastTaskFinishedAt // 最近后台任务完成时间。
) {

    /**
     * 会话所属空间类型，供前端区分云端会话与本地会话展示。
     */
    public enum WorkspaceType {
        CLOUD,
        LOCAL
    }
}
