package com.codingx.backend.common.exception;

/**
 * Defines the responsibilities handled by UnauthorizedException.
 */
public class UnauthorizedException extends BusinessException {

    /**
     * Executes the logic defined by UnauthorizedException.
     * @param message input argument.
     * @return processing result.
     */
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }
}
