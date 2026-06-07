package com.codingx.auth.application.service;

/**
 * CLI 设备码登录启动结果。
 *
 * @param deviceCode CLI 轮询 token 使用的内部设备码，不展示给普通用户。
 * @param userCode 用户需要在浏览器页面输入或确认的短验证码。
 * @param verificationUri 用户端 CLI 登录页面地址。
 * @param expiresInSeconds 设备授权会话有效期秒数。
 * @param pollIntervalSeconds CLI 轮询推荐间隔秒数。
 */
public record CliDeviceStartResponse(
    String deviceCode, // CLI 轮询使用的内部设备码。
    String userCode, // 用户可读验证码。
    String verificationUri, // 浏览器验证入口。
    long expiresInSeconds, // 设备授权有效期秒数。
    long pollIntervalSeconds // 推荐轮询间隔秒数。
) {
}

