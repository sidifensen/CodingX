package com.codingx.task.domain.model;

/**
 * 定义 TaskStatus 可用的枚举取值。
 */
public enum TaskStatus {

    // 已创建但尚未开始。
    CREATED,

    // 执行中。
    RUNNING,

    // 执行成功。
    SUCCEEDED,

    // 执行失败。
    FAILED
}
