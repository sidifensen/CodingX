package com.codingx.auth.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录接口请求体，承载用户提交的账号密码。
 */
public record LoginRequest(
    @NotBlank(message = ErrorMessageCatalog.LOGIN_USERNAME_REQUIRED) String username, // 用户登录名，不能为空。
    @NotBlank(message = ErrorMessageCatalog.LOGIN_PASSWORD_REQUIRED) String password // 明文密码，仅用于本次登录校验。
) {
}
