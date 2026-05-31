package com.codingx.auth.interfaces.request;

/**
 * 管理端重置用户密码请求体。
 * @param newPassword 新密码明文，仅用于重置时生成哈希，服务层禁止保存明文。
 */
public record AdminUserResetPasswordRequest(
    String newPassword // 新密码明文，仅用于重置时生成哈希，服务层禁止保存明文。
) {
}
