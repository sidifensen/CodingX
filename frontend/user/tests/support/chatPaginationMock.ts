/**
 * 判断当前请求是否为聊天会话列表接口；分页改造后首屏会带 pageSize，旧测试仍可复用数组响应。
 */
export function isConversationListRequest(url: string) {
  return url === '/api/chat/conversations' || url.startsWith('/api/chat/conversations?');
}

/**
 * 判断当前请求是否为指定工作空间的会话分页接口，避免测试和 query 参数顺序耦合。
 */
export function isWorkspaceConversationListRequest(url: string, workspaceId: string) {
  return isConversationListRequest(url) && conversationListSearchParams(url).get('workspaceId') === workspaceId;
}

/**
 * 判断当前请求是否为某个会话的消息列表接口；分页改造后打开会话会带 pageSize。
 */
export function isConversationMessageListRequest(url: string, conversationId: string) {
  const prefix = `/api/chat/conversations/${conversationId}/messages`;
  return url === prefix || url.startsWith(`${prefix}?`);
}

/**
 * 判断当前请求是否为任意会话的消息列表接口，供不关心会话 ID 的旧测试桩使用。
 */
export function isAnyConversationMessageListRequest(url: string) {
  return /^\/api\/chat\/conversations\/[^/]+\/messages(?:\?|$)/.test(url);
}

/**
 * 读取会话列表分页请求的查询参数，便于测试验证 workspaceId 或 cursor 是否透传。
 */
export function conversationListSearchParams(url: string) {
  return new URLSearchParams(url.split('?')[1] ?? '');
}
