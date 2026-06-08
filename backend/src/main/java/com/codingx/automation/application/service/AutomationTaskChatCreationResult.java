package com.codingx.automation.application.service;

import com.codingx.automation.domain.model.AutomationTask;

/**
 * 聊天内自动化创建结果，包含已创建任务和要写回当前会话的助手摘要。
 *
 * @param task 已创建的自动化任务。
 * @param assistantContent 当前会话内展示给用户的创建成功摘要。
 */
public record AutomationTaskChatCreationResult(
    AutomationTask task,
    String assistantContent
) {
}
