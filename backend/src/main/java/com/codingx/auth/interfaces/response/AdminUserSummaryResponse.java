package com.codingx.auth.interfaces.response;

import java.time.LocalDateTime;

/**
 * 管理端用户列表项响应体。
 */
public record AdminUserSummaryResponse(
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
    LocalDateTime createdAt // 创建时间。
) {
}
