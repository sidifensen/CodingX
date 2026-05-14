package com.codingx.auth.application.service;
import com.codingx.auth.domain.model.UserType;

/**
 * 定义 LoginResult 使用的数据载体。
 */
public record LoginResult(
    Long userId, // userId 字段。
    String username, // 登录用户名。
    String displayName, // 展示名称。
    UserType userType, // 用户类型。
    String token // 访问令牌。
) {
}
