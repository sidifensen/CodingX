package com.codingx.auth.interfaces.response;

import java.time.LocalDateTime;

/**
 * 定义管理端用户详情响应字段。
 */
public record AdminUserDetailResponse(
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
    String lastLoginIp,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
