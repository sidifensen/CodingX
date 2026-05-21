package com.codingx.common.exception;

/**
 * 定义 ConflictException 的职责边界。
 */
public class ConflictException extends BusinessException {

    /**
     * 执行 ConflictException 定义的处理逻辑。
     * @param code 业务错误码。
     * @param message 中文错误文案。
     */
    public ConflictException(String code, String message) {
        super(code, message);
    }

    /**
     * 使用默认冲突错误码创建异常。
     * @param message 中文错误文案。
     */
    public ConflictException(String message) {
        super("CONFLICT", message);
    }
}
