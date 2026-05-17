package com.codingx.auth.interfaces.request;

/**
 * 定义管理端编辑用户请求字段。
 * @param displayName 展示名称。
 * @param userType 用户类型。
 * @param status 用户状态。
 * @param email 邮箱。
 * @param phone 手机号。
 * @param avatarUrl 头像地址。
 */
public record AdminUserUpdateRequest(
    String displayName,
    String userType,
    String status,
    String email,
    String phone,
    String avatarUrl
) {
}
