package com.codingx.chat.interfaces.request;

/**
 * 创建会话接口请求体，前端在新建会话或首次发送前传入标题和可选工作空间。
 *
 * @param title 用户输入或前端预填的会话展示标题，可为空；为空时服务层回退默认标题。
 * @param workspaceId 会话归属工作空间标识，可为空；为空时服务层按运行目标绑定默认空间。
 */
public record CreateConversationRequest(
    String title, // 用户输入或前端预填的会话展示标题，可为空；为空时服务层回退默认标题。
    Long workspaceId // 会话归属工作空间标识，可为空；为空时服务层按运行目标绑定默认云端或本地空间。
) {
}
