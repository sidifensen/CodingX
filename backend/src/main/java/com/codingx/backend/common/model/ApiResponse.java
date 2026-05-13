package com.codingx.backend.common.model;

/**
 * Represents the request or response data carried by ApiResponse.
 */
public record ApiResponse<T>(
    boolean success, // success value.
    String code, // code value.
    String message, // message value.
    T data // data value.
) {
    /**
     * Executes the logic defined by success.
     * @param data input argument.
     * @return processing result.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", "success", data);
    }

    /**
     * Executes the logic defined by success.
     * @param message input argument.
     * @param data input argument.
     * @return processing result.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, "OK", message, data);
    }

    /**
     * Executes the logic defined by successMessage.
     * @param message input argument.
     * @return processing result.
     */
    public static ApiResponse<Void> successMessage(String message) {
        return new ApiResponse<>(true, "OK", message, null);
    }

    /**
     * Marks the workflow handled by failure as failed.
     * @param code input argument.
     * @param message input argument.
     * @return processing result.
     */
    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
