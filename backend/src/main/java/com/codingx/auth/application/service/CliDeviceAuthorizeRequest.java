package com.codingx.auth.application.service;

/**
 * 浏览器确认设备码授权的应用层请求。
 *
 * @param userCode CLI 展示给用户输入的短验证码。
 */
public record CliDeviceAuthorizeRequest(
    String userCode // 用户在浏览器输入或链接携带的设备验证码。
) {
}

