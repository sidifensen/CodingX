package com.codingx.auth.interfaces.response;

import java.time.LocalDateTime;

/**
 * 定义管理端用户列表项响应字段。
 */
public record AdminUserSummaryResponse(
    Long id,
    String username,
    String displayName,
    String email,
    String phone,
    String avatarUrl,
    String userType,
    String userTypeLabel,
    String status,
    String statusLabel,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt
) {
}
