package com.codingx.auth.interfaces.request;

/**
 * 管理端用户状态更新请求体。
 * @param status 目标状态，只允许 ACTIVE、DISABLED 或 PENDING。
 */
public record AdminUserStatusUpdateRequest(
    String status
) {
}
