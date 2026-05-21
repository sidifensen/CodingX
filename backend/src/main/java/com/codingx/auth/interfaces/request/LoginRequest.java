package com.codingx.auth.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.NotBlank;

/**
 * 定义 LoginRequest 使用的数据载体。
 */
public record LoginRequest(
    @NotBlank(message = ErrorMessageCatalog.LOGIN_USERNAME_REQUIRED) String username, // 登录用户名。
    @NotBlank(message = ErrorMessageCatalog.LOGIN_PASSWORD_REQUIRED) String password // password 字段。
) {
}
