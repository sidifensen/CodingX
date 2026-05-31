package com.codingx.chat.application.command;

/**
 * 创建会话的应用层命令，承接 HTTP 请求和流式入口传入的会话初始化参数。
 *
 * @param title 用户输入或前端预填的会话标题，可为空；为空时应用服务回退默认标题。
 * @param workspaceId 目标工作空间主键，可为空；为空时按 runtimeTarget 绑定默认空间。
 * @param runtimeTarget 运行目标编码，可为空；用于在未传 workspaceId 时区分云端或本地默认空间。
 */
public record CreateConversationCommand(
    String title, // 用户输入或前端预填的会话标题，可为空；为空时应用服务回退默认标题。
    Long workspaceId, // 目标工作空间主键，可为空；为空时按 runtimeTarget 绑定默认空间。
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
