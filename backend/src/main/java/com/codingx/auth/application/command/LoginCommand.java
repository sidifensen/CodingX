package com.codingx.auth.application.command;

/**
 * 定义 LoginCommand 使用的数据载体。
 */
public record LoginCommand(
    String username, // 登录用户名。
    String password // password 字段。
) {
}
