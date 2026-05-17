package com.codingx.auth.domain.model;

/**
 * 定义 UserStatus 可用的枚举取值。
 */
public enum UserStatus {

    // 当前可用。
    ACTIVE,

    // 当前禁用。
    DISABLED,

    // 待管理员审核。
    PENDING
}
