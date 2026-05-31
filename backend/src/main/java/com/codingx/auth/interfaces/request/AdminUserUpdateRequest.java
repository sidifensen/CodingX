package com.codingx.auth.interfaces.request;

/**
 * 管理端编辑用户资料请求体。
 * @param displayName 展示名称，编辑时必填。
 * @param userType 用户类型，非空时覆盖原类型。
 * @param status 用户状态，非空时覆盖原状态。
 * @param email 邮箱，可为空但非空时需满足唯一性。
 * @param phone 手机号，可为空。
 * @param avatarUrl 头像地址，可为空。
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
