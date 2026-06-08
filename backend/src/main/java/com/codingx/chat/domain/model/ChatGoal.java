package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 聊天会话级目标实体，一个会话同一时刻最多存在一个 ACTIVE 目标。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatGoal {

    /** 目标主键，创建时由服务使用 Snowflake 生成。 */
    private Long id;
    /** 所属会话 ID，所有工具查询和页面查询都必须携带该上下文。 */
    private Long conversationId;
    /** 目标归属用户 ID，用于防止跨用户读取或更新会话目标。 */
    private Long userId;
    /** 模型传入的稳定目标键，未指定时使用 default。 */
    private String goalKey;
    /** 目标标题，作为右侧目标浮窗的主标题。 */
    private String title;
    /** 目标说明，可为空，用于模型记录较长目标背景。 */
    private String description;
    /** 目标当前状态，active 查询只返回 ACTIVE。 */
    private ChatGoalStatus status;
    /** 模型更新的当前进度摘要，可为空。 */
    private String progressSummary;
    /** 创建目标的聊天运行 ID，可为空。 */
    private Long createdRunId;
    /** 最近一次更新目标的聊天运行 ID，可为空。 */
    private Long updatedRunId;
    /** 目标创建时间。 */
    private LocalDateTime createdAt;
    /** 目标最近更新时间。 */
    private LocalDateTime updatedAt;
    /** 目标进入终态的时间，ACTIVE 状态为空。 */
    private LocalDateTime completedAt;
    /** 逻辑删除标记，0 表示正常，1 表示删除。 */
    private Integer deleted;
}
