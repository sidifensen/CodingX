package com.codingx.auth.application.service;

/**
 * 浏览器为 CLI 创建授权码的应用层请求。
 *
 * @param state CLI 生成的回调校验串，授权码兑换时必须原样带回。
 * @param redirectUri CLI 本机 loopback 回调地址，仅允许 localhost/127.0.0.1/[::1]。
 * @param codeChallenge CLI 根据 codeVerifier 生成的 S256 challenge，用于兑换阶段校验调用方。
 */
public record CliAuthorizeRequest(
    String state, // CLI 生成的回调校验串，不能为空。
    String redirectUri, // CLI 本机 loopback 回调地址，不能为空。
    String codeChallenge // S256 code challenge，不能为空。
) {
}

