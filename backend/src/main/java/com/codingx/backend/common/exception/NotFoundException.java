package com.codingx.backend.common.exception;

/**
 * Defines the responsibilities handled by NotFoundException.
 */
public class NotFoundException extends BusinessException {

    /**
     * Executes the logic defined by NotFoundException.
     * @param message input argument.
     * @return processing result.
     */
    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
