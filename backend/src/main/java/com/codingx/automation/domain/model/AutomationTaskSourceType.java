package com.codingx.automation.domain.model;

/**
 * 自动化任务创建来源，用于区分页面手动创建和聊天会话内自动创建。
 */
public enum AutomationTaskSourceType {
    /** 自动化页面手动创建。 */
    MANUAL,
    /** 聊天会话内识别自动化创建意图后直接创建。 */
    CHAT
}
