package com.codingx.common.exception;

/**
 * 定义 NotFoundException 的职责边界。
 */
public class NotFoundException extends BusinessException {

    /**
     * 执行 NotFoundException 定义的处理逻辑。
     * @param message 输入参数。
     * @return 输入参数。
     */
    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
