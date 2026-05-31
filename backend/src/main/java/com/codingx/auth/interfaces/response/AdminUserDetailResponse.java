package com.codingx.auth.interfaces.response;

import java.time.LocalDateTime;

/**
 * 管理端用户详情响应体。
 *
 * @param id 用户主键，序列化为字符串避免前端 Long 精度丢失。
 * @param username 登录用户名。
 * @param displayName 展示名称。
 * @param email 邮箱，可为空。
 * @param phone 手机号，可为空。
 * @param avatarUrl 头像地址，可为空。
 * @param userType 用户类型枚举名称。
 * @param userTypeLabel 用户类型中文展示文案。
 * @param status 用户状态枚举名称。
 * @param statusLabel 用户状态中文展示文案。
 * @param lastLoginAt 最近登录时间，可为空。
 * @param lastLoginIp 最近登录 IP，可为空。
 * @param createdAt 创建时间。
 * @param updatedAt 最近更新时间。
 */
public record AdminUserDetailResponse(
    Long id, // 用户主键。
    String username, // 登录用户名。
    String displayName, // 展示名称。
    String email, // 邮箱，可为空。
    String phone, // 手机号，可为空。
    String avatarUrl, // 头像地址，可为空。
    String userType, // 用户类型枚举名称。
    String userTypeLabel, // 用户类型中文展示文案。
    String status, // 用户状态枚举名称。
    String statusLabel, // 用户状态中文展示文案。
    LocalDateTime lastLoginAt, // 最近登录时间，可为空。
    String lastLoginIp, // 最近登录 IP，可为空。
    LocalDateTime createdAt, // 创建时间。
    LocalDateTime updatedAt // 最近更新时间。
) {
}
