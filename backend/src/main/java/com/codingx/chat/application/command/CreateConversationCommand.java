package com.codingx.chat.application.command;

/**
 * 定义 CreateConversationCommand 使用的数据载体。
 */
public record CreateConversationCommand(
    String title, // 展示标题。
    Long workspaceId, // 工作空间标识。
    String runtimeTarget // 运行目标，未传 workspaceId 时用于选择默认云端或本地历史空间。
) {

    /**
     * 兼容旧调用方，仅传标题和工作空间时默认按云端历史空间兜底。
     * @param title 会话标题。
     * @param workspaceId 工作空间标识。
     */
    public CreateConversationCommand(String title, Long workspaceId) {
        this(title, workspaceId, null);
    }

    /**
     * 兼容旧调用方，仅传标题时默认无工作空间绑定。
     * @param title 会话标题。
     */
    public CreateConversationCommand(String title) {
        this(title, null, null);
    }
}
