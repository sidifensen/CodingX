package com.codingx.chat.interfaces.request;

/**
 * 定义 CreateConversationRequest 使用的数据载体。
 */
public record CreateConversationRequest(
    String title, // 展示标题。
    Long workspaceId // 工作空间标识。
) {
}
