package com.codingx.chat.domain.model;

/**
 * 定义 ChatMessageStatus 可用的枚举取值。
 */
public enum ChatMessageStatus {

    // 等待处理。
    PENDING,

    // 已完成状态。
    COMPLETED,

    // 已取消状态。
    CANCELLED,

    // 执行失败。
    FAILED
}
