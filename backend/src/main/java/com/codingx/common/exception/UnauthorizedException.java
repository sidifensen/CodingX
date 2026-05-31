package com.codingx.common.exception;

/**
 * 表示当前请求未完成登录或登录态已失效的业务异常。
 */
public class UnauthorizedException extends BusinessException {

    /**
     * 创建未认证异常。
     * @param message 返回给前端展示的中文错误文案。
     */
    public UnauthorizedException(String message) {
        // 步骤 1：未认证场景统一使用 UNAUTHORIZED 错误码，便于前端触发登录流程。
        super("UNAUTHORIZED", message);
    }
}
