package com.codingx.auth.interfaces.request;

/**
 * 定义管理端新增用户请求字段。
 * @param username 用户名。
 * @param displayName 展示名称。
 * @param password 初始密码。
 * @param userType 用户类型。
 * @param status 用户状态。
 * @param email 邮箱。
 * @param phone 手机号。
 * @param avatarUrl 头像地址。
 */
public record AdminUserCreateRequest(
    String username,
    String displayName,
    String password,
    String userType,
    String status,
    String email,
    String phone,
    String avatarUrl
) {
}
