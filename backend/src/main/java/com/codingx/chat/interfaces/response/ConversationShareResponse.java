package com.codingx.chat.interfaces.response;

/**
 * 分享会话响应，只暴露生成链接所需的令牌与路径。
 *
 * @param shareToken 公开分享令牌，由后端生成；前端可持久展示或复制。
 * @param shareUrl 前端公开分享页路径，已包含可选消息筛选参数。
 */
public record ConversationShareResponse(
    String shareToken,
    String shareUrl
) {
}
