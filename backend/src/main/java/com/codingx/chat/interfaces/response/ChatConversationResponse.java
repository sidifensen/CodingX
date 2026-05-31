package com.codingx.chat.interfaces.response;

import com.codingx.chat.domain.model.ChatConversationStatus;
import java.time.LocalDateTime;

/**
 * 会话列表与会话详情接口的响应载体。
 *
 * @param id 会话主键，序列化为字符串避免前端 Long 精度丢失。
 * @param title 会话展示标题，创建时可由用户指定，后续可由模型摘要刷新。
 * @param status 会话生命周期状态，决定是否可继续发送消息。
 * @param lastMessageAt 最近一条消息写入时间，用于侧栏排序。
 * @param lastRunId 最近一次聊天执行 run 或后台任务标识，用于恢复运行态。
 * @param pinned 会话置顶标记，只影响侧栏排序。
 * @param shareToken 公开分享令牌，未分享时为空。
 * @param taskCompletionRead 任务完成提醒已读状态，true 表示无需显示红点。
 * @param workspaceId 会话所属工作空间标识，历史云端会话可为空。
 * @param workspaceName 会话所属工作空间名称，空间不存在或不可见时为空。
 * @param workspaceType 会话所属工作空间类型，前端据此区分云端与本地分组。
 * @param activeTaskId 当前仍在运行的后台任务标识，无运行任务时为空。
 * @param activeTaskStatus 当前运行任务状态，运行中统一返回 RUNNING。
 * @param lastTaskId 最近一次后台任务标识，用于展示任务完成或失败结果。
 * @param lastTaskStatus 最近一次后台任务终态或运行态，取值来自任务表或 run 状态折算。
 * @param lastTaskFinishedAt 最近一次后台任务完成时间，任务未结束时为空。
 */
public record ChatConversationResponse(
    Long id, // 会话主键，序列化为字符串避免前端 Long 精度丢失。
    String title, // 会话展示标题，创建时可由用户指定，后续可由模型摘要刷新。
    ChatConversationStatus status, // 会话生命周期状态，决定是否可继续发送消息。
    LocalDateTime lastMessageAt, // 最近一条消息写入时间，用于侧栏排序。
    Long lastRunId, // 最近一次聊天执行 run 或后台任务标识，用于恢复运行态。
    Boolean pinned, // 会话置顶标记，只影响侧栏排序。
    String shareToken, // 公开分享令牌，未分享时为空。
    Boolean taskCompletionRead, // 任务完成提醒已读状态，true 表示无需显示红点。
    Long workspaceId, // 会话所属工作空间标识，历史云端会话可为空。
    String workspaceName, // 会话所属工作空间名称，空间不存在或不可见时为空。
    WorkspaceType workspaceType, // 会话所属工作空间类型，前端据此区分云端与本地分组。
    Long activeTaskId, // 当前仍在运行的后台任务标识，无运行任务时为空。
    String activeTaskStatus, // 当前运行任务状态，运行中统一返回 RUNNING。
    Long lastTaskId, // 最近一次后台任务标识，用于展示任务完成或失败结果。
    String lastTaskStatus, // 最近一次后台任务终态或运行态，取值来自任务表或 run 状态折算。
    LocalDateTime lastTaskFinishedAt // 最近一次后台任务完成时间，任务未结束时为空。
) {

    /**
     * 会话所属空间类型，供前端区分云端会话与本地会话展示。
     */
    public enum WorkspaceType {
        CLOUD,
        LOCAL
    }
}
