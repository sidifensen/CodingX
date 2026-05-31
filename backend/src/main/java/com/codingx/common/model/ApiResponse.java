package com.codingx.common.model;

/**
 * 统一接口响应载体，所有 Controller 与全局异常处理器都通过该结构向前端返回结果。
 */
public record ApiResponse<T>(
    boolean success, // 请求是否成功；true 表示业务处理成功，false 表示已通过统一错误结构返回。
    String code, // 业务状态码；成功固定为 OK，失败时由异常或错误码目录提供。
    String message, // 展示给前端或用户的中文提示；前端应优先使用该字段展示错误语义。
    T data // 响应数据主体，可为空；失败响应固定为空以避免混淆错误语义。
) {

    /**
     * 创建默认成功响应。
     * @param data 需要返回给前端的数据主体，可为空。
     * @return 成功响应。
     */
    public static <T> ApiResponse<T> success(T data) {
        // 步骤 1：成功响应统一使用 OK 状态码和默认 success 文案。
        return new ApiResponse<>(true, "OK", "success", data);
    }

    /**
     * 创建带自定义提示的成功响应。
     * @param message 成功提示文案，通常用于需要前端 toast 的操作。
     * @param data 需要返回给前端的数据主体，可为空。
     * @return 成功响应。
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        // 步骤 1：允许业务层返回更明确的成功文案，但仍保持统一 OK 状态码。
        return new ApiResponse<>(true, "OK", message, data);
    }

    /**
     * 创建仅包含成功提示的响应。
     * @param message 成功提示文案。
     * @return 无数据成功响应。
     */
    public static ApiResponse<Void> successMessage(String message) {
        // 步骤 1：无返回数据的操作显式使用 null data，避免前端误读空对象。
        return new ApiResponse<>(true, "OK", message, null);
    }

    /**
     * 创建失败响应。
     * @param code 业务错误码，不应为空。
     * @param message 返回给前端展示的中文错误提示。
     * @return 无数据失败响应。
     */
    public static ApiResponse<Void> failure(String code, String message) {
        // 步骤 1：失败响应不携带 data，错误细节统一通过 code 和 message 传递。
        return new ApiResponse<>(false, code, message, null);
    }
}
