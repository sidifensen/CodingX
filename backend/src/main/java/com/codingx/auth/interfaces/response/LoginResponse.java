package com.codingx.auth.interfaces.response;
import com.codingx.auth.domain.model.UserType;

/**
 * 定义 LoginResponse 使用的数据载体。
 */
public record LoginResponse(
    Long userId, // userId 字段。
    String username, // 登录用户名。
    String displayName, // 展示名称。
    UserType userType, // 用户类型。
    String token // 访问令牌。
) {
}
