package com.codingx.common.model;

/**
 * 定义 ApiResponse 使用的数据载体。
 */
public record ApiResponse<T>(
    boolean success, // success 字段。
    String code, // code 字段。
    String message, // message 字段。
    T data // data 字段。
) {

    /**
     * 执行 success 定义的处理逻辑。
     * @param data 输入参数。
     * @return 输入参数。
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", "success", data);
    }

    /**
     * 执行 success 定义的处理逻辑。
     * @param message 输入参数。
     * @param data 输入参数。
     * @return 输入参数。
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, "OK", message, data);
    }

    /**
     * 执行 successMessage 定义的处理逻辑。
     * @param message 输入参数。
     * @return 输入参数。
     */
    public static ApiResponse<Void> successMessage(String message) {
        return new ApiResponse<>(true, "OK", message, null);
    }

    /**
     * 将 failure 处理的流程标记为失败。
     * @param code 输入参数。
     * @param message 输入参数。
     * @return 输入参数。
     */
    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
