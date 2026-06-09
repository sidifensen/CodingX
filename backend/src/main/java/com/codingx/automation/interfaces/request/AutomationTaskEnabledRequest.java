package com.codingx.automation.interfaces.request;

/**
 * 用户端启停自动化任务请求。
 *
 * @param enabled true 表示恢复调度，false 表示暂停调度。
 */
public record AutomationTaskEnabledRequest(Boolean enabled) {
}
