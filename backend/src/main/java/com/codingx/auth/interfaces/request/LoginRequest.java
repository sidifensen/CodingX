package com.codingx.auth.interfaces.request;
import jakarta.validation.constraints.NotBlank;

/**
 * 定义 LoginRequest 使用的数据载体。
 */
public record LoginRequest(
    @NotBlank(message = "username is required") String username, // 登录用户名。
    @NotBlank(message = "password is required") String password // password 字段。
) {
}
