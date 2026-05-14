package com.codingx.common.exception;

/**
 * 定义 BusinessException 的职责边界。
 */
public class BusinessException extends RuntimeException {

    /**
     * code 字段。
     */
    private final String code;

    /**
     * 执行 BusinessException 定义的处理逻辑。
     * @param code 输入参数。
     * @param message 输入参数。
     * @return 输入参数。
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取 getCode 对应的结果。
     * @return 输入参数。
     */
    public String getCode() {
        return code;
    }
}
