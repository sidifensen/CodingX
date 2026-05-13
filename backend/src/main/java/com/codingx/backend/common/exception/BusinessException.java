package com.codingx.backend.common.exception;

/**
 * Defines the responsibilities handled by BusinessException.
 */
public class BusinessException extends RuntimeException {

    /**
     * code value.
     */
    private final String code;

    /**
     * Executes the logic defined by BusinessException.
     * @param code input argument.
     * @param message input argument.
     * @return processing result.
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Retrieves the result required by getCode.
     * @return processing result.
     */
    public String getCode() {
        return code;
    }
}
