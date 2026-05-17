package com.codingx.auth.interfaces.request;

/**
 * 定义管理端重置密码请求字段。
 * @param newPassword 新密码明文。
 */
public record AdminUserResetPasswordRequest(
    String newPassword
) {
}
