package com.codingx.chat.application.command;

/**
 * 定义 CreateConversationCommand 使用的数据载体。
 */
public record CreateConversationCommand(
    String title, // 展示标题。
    Long workspaceId // 工作空间标识。
) {

    /**
     * 兼容旧调用方，仅传标题时默认无工作空间绑定。
     * @param title 会话标题。
     */
    public CreateConversationCommand(String title) {
        this(title, null);
    }
}
