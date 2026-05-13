package com.codingx.backend.common.exception;

/**
 * Defines the responsibilities handled by ForbiddenException.
 */
public class ForbiddenException extends BusinessException {

    /**
     * Executes the logic defined by ForbiddenException.
     * @param message input argument.
     * @return processing result.
     */
    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
