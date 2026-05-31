package com.codingx.auth.application.command;

/**
 * 登录命令，承载 Controller 传入应用服务的账号密码。
 */
public record LoginCommand(
    String username, // 用户输入的登录名，应用服务会据此查询账号。
    String password // 用户输入的明文密码，仅用于本次密码哈希校验，不允许持久化。
) {
}
