package com.codingx.chat.application.command;

/**
 * 定义 SendChatMessageCommand 使用的数据载体。
 */
public record SendChatMessageCommand(
    Long conversationId, // 关联会话标识。
    String content, // 主体内容。
    boolean deepThinking // 是否开启深度思考。
) {
}
