package com.codingx.auth.application.service;

/**
 * CLI 轮询设备码登录结果的应用层请求。
 *
 * @param deviceCode 后端创建设备授权时返回给 CLI 的内部设备码。
 */
public record CliDeviceTokenRequest(
    String deviceCode // 设备码，不能为空。
) {
}

