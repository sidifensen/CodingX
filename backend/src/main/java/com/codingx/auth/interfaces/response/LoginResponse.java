package com.codingx.auth.interfaces.response;

import com.codingx.auth.domain.model.UserType;

/**
 * 登录接口响应体，返回已登录用户身份和访问令牌。
 *
 * @param userId 登录用户主键，序列化为字符串避免前端 Long 精度丢失。
 * @param username 登录用户名。
 * @param displayName 用户展示名称。
 * @param userType 用户类型，决定普通端或管理端入口能力。
 * @param token 访问令牌，前端后续请求需要携带。
 */
public record LoginResponse(
    Long userId, // 登录用户主键，序列化为字符串避免前端 Long 精度丢失。
    String username, // 登录用户名。
    String displayName, // 用户展示名称。
    UserType userType, // 用户类型，决定普通端或管理端入口能力。
    String token // 访问令牌，前端后续请求需要携带。
) {
}
