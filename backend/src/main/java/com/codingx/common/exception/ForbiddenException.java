package com.codingx.common.exception;

/**
 * 定义 ForbiddenException 的职责边界。
 */
public class ForbiddenException extends BusinessException {

    /**
     * 执行 ForbiddenException 定义的处理逻辑。
     * @param message 输入参数。
     * @return 输入参数。
     */
    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
