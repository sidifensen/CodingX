package com.codingx.auth.application.service;
import com.codingx.auth.domain.model.UserType;

/**
 * 登录用例结果，承载认证成功后的用户身份与会话令牌。
 */
public record LoginResult(
    Long userId, // 登录成功用户的主键标识。
    String username, // 登录用户名，用于前端展示和会话排查。
    String displayName, // 用户展示名称。
    UserType userType, // 用户类型，决定前端入口和后台权限展示。
    String token // Sa-Token 生成的访问令牌，前端后续请求需要携带。
) {
}
