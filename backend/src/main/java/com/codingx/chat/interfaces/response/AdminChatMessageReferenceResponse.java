package com.codingx.chat.interfaces.response;

import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 定义反馈来源响应结构，用于展示消息最终引用证据。
 * @param id 来源主键。
 * @param runId 关联执行链路主键。
 * @param messageId 关联消息主键。
 * @param conversationId 所属会话主键。
 * @param sourceType 来源类型。
 * @param title 来源标题。
 * @param url 来源链接。
 * @param siteName 来源站点名称。
 * @param snippet 引用摘要片段。
 * @param rankNo 展示顺序。
 * @param createdAt 创建时间。
 */
@Builder
public record AdminChatMessageReferenceResponse(
    Long id,
    Long runId,
    Long messageId,
    Long conversationId,
    String sourceType,
    String title,
    String url,
    String siteName,
    String snippet,
    Integer rankNo,
    LocalDateTime createdAt
) {
}

