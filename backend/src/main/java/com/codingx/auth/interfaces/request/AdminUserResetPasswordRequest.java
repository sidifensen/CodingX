package com.codingx.auth.interfaces.request;

/**
 * 管理端重置用户密码请求体。
 * @param newPassword 新密码明文，服务层会转换为哈希后保存。
 */
public record AdminUserResetPasswordRequest(
    String newPassword
) {
}
