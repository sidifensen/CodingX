package com.codingx.governance.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 长期记忆领域对象，承载用户级或项目级记忆的确认状态与检索关键词。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GovernanceLongTermMemory {

    /** 长期记忆主键。 */
    private Long id;
    /** 记忆范围，USER 表示用户偏好，PROJECT 表示项目约定。 */
    private String memoryScope;
    /** 记忆所属用户，用户记忆必填，项目候选也记录提取用户。 */
    private Long userId;
    /** 记忆所属工作空间，项目记忆必填，用户记忆可为空。 */
    private Long workspaceId;
    /** 记忆去重键，按范围、归属和内容确定性生成。 */
    private String memoryKey;
    /** 记忆正文，必须是可直接回注给模型的简洁中文约束。 */
    private String content;
    /** 记忆状态，PENDING/ACTIVE/REJECTED。 */
    private String status;
    /** 来源类型，例如 CHAT_EXCHANGE。 */
    private String sourceType;
    /** 来源会话 ID。 */
    private Long sourceConversationId;
    /** 来源消息 ID，通常指用户消息。 */
    private Long sourceMessageId;
    /** 检索关键词 JSON 数组。 */
    private String keywordJson;
    /** 确定性提取置信分数。 */
    private BigDecimal confidenceScore;
    /** 最近一次被回注使用的时间。 */
    private LocalDateTime lastUsedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，1 表示已删除。 */
    private Integer deleted;
}
