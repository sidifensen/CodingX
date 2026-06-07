package com.codingx.auth.application.service;

/**
 * 设备码 token 轮询结果，pending 时不携带登录结果，approved 时携带 LoginResult。
 *
 * @param approved 是否已经完成浏览器授权。
 * @param status 当前设备授权状态，CLI 用于决定继续轮询还是保存 token。
 * @param login 授权完成后的登录结果，pending 时为空。
 */
public record CliDeviceTokenResponse(
    boolean approved, // true 表示已授权并返回登录结果。
    String status, // authorization_pending 或 approved。
    LoginResult login // approved 时不为空，pending 时为空。
) {
    /**
     * 创建 pending 响应，供 CLI 继续轮询。
     *
     * @param status pending 状态码。
     * @return pending 响应。
     */
    public static CliDeviceTokenResponse pending(String status) {
        return new CliDeviceTokenResponse(false, status, null);
    }

    /**
     * 创建 approved 响应，供 CLI 保存 token。
     *
     * @param login 登录结果。
     * @return approved 响应。
     */
    public static CliDeviceTokenResponse approved(LoginResult login) {
        return new CliDeviceTokenResponse(true, "approved", login);
    }
}

