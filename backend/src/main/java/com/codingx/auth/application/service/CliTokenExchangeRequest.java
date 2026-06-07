package com.codingx.auth.application.service;

/**
 * CLI 使用授权码换取登录 token 的应用层请求。
 *
 * @param code 浏览器回调传给 CLI 的一次性授权码。
 * @param state CLI 原始 state，必须和授权码绑定值一致。
 * @param codeVerifier CLI 原始随机 verifier，用于校验 codeChallenge。
 */
public record CliTokenExchangeRequest(
    String code, // 一次性授权码，不能为空。
    String state, // CLI state，不能为空。
    String codeVerifier // PKCE verifier，不能为空。
) {
}

