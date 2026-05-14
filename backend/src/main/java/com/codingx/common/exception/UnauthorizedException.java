package com.codingx.common.exception;

/**
 * 定义 UnauthorizedException 的职责边界。
 */
public class UnauthorizedException extends BusinessException {

    /**
     * 执行 UnauthorizedException 定义的处理逻辑。
     * @param message 输入参数。
     * @return 输入参数。
     */
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }
}
