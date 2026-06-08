package com.codingx.chat.interfaces.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户端长期记忆响应载体，额外补齐工作空间展示名，避免前端从本地历史快照反推错误名称。
 *
 * @param id 长期记忆主键，序列化为字符串避免前端 Long 精度丢失。
 * @param memoryScope 记忆范围，USER 表示跨项目用户偏好，PROJECT 表示项目约定。
 * @param userId 记忆所属用户标识。
 * @param workspaceId 项目记忆所属工作空间标识，用户记忆为空。
 * @param workspaceName 项目记忆所属工作空间展示名，空间不可见或不存在时为空。
 * @param memoryKey 记忆去重键。
 * @param content 可直接回注给模型的记忆正文。
 * @param status 记忆状态，ACTIVE 参与上下文回注，REJECTED 表示停用。
 * @param sourceType 记忆来源类型，例如 CHAT_EXCHANGE。
 * @param sourceConversationId 来源会话标识。
 * @param sourceMessageId 来源用户消息标识。
 * @param keywordJson 关键词 JSON 数组。
 * @param confidenceScore 记忆提取置信分数。
 * @param lastUsedAt 最近一次参与模型回注的时间。
 * @param createdAt 创建时间。
 * @param updatedAt 更新时间。
 */
public record ChatLongTermMemoryResponse(
    Long id, // 长期记忆主键，序列化为字符串避免前端 Long 精度丢失。
    String memoryScope, // 记忆范围，USER 表示跨项目用户偏好，PROJECT 表示项目约定。
    Long userId, // 记忆所属用户标识。
    Long workspaceId, // 项目记忆所属工作空间标识，用户记忆为空。
    String workspaceName, // 项目记忆所属工作空间展示名，空间不可见或不存在时为空。
    String memoryKey, // 记忆去重键。
    String content, // 可直接回注给模型的记忆正文。
    String status, // 记忆状态，ACTIVE 参与上下文回注，REJECTED 表示停用。
    String sourceType, // 记忆来源类型，例如 CHAT_EXCHANGE。
    Long sourceConversationId, // 来源会话标识。
    Long sourceMessageId, // 来源用户消息标识。
    String keywordJson, // 关键词 JSON 数组。
    BigDecimal confidenceScore, // 记忆提取置信分数。
    LocalDateTime lastUsedAt, // 最近一次参与模型回注的时间。
    LocalDateTime createdAt, // 创建时间。
    LocalDateTime updatedAt // 更新时间。
) {
}
