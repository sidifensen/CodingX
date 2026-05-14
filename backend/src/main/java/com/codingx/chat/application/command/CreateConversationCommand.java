package com.codingx.chat.application.command;

/**
 * 定义 CreateConversationCommand 使用的数据载体。
 */
public record CreateConversationCommand(
    String title // 展示标题。
) {
}
