package com.codingx.auth.application.service;

/**
 * 浏览器 CLI 授权码创建结果。
 *
 * @param code 一次性授权码，只能由 CLI 兑换一次。
 * @param state 原样回传的 state，方便前端跳回 CLI 时保持校验串。
 * @param expiresInSeconds 授权码剩余有效秒数。
 */
public record CliAuthorizeResponse(
    String code, // 一次性授权码，短期有效且兑换后失效。
    String state, // 原样回传的 CLI state。
    long expiresInSeconds // 授权码有效期秒数。
) {
}

