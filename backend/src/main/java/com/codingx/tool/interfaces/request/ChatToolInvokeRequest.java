package com.codingx.tool.interfaces.request;

/**
 * 工具手工调用请求，供管理端测试单个 chat_tool 能力。
 * @param question 手工调用工具的输入内容，支持自然语言或 JSON 参数。
 * @param confirmHighRisk 是否确认执行高风险工具调用，true 表示用户已明确确认。
 * @param workspaceId 当前页面绑定的工作空间标识，可为空；用户态工具优先按该值定位目录。
 * @param repositoryPath 当前页面绑定的本地目录，可为空；仅用于用户级绑定兜底匹配。
 */
public record ChatToolInvokeRequest(
    String question, // 手工调用工具的输入内容，支持自然语言或 JSON 参数。
    Boolean confirmHighRisk, // 是否确认执行高风险工具调用，true 表示用户已明确确认。
    Long workspaceId, // 当前页面绑定的工作空间标识，可为空。
    String repositoryPath // 当前页面绑定的本地目录，可为空。
) {
}

