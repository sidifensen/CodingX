package com.codingx.common.exception;

/**
 * 业务异常基类，携带可返回给前端的业务错误码与中文错误文案。
 */
public class BusinessException extends RuntimeException {

    /**
     * 业务错误码，由全局异常处理器写入 ApiResponse.code，前端可据此做分支处理。
     */
    private final String code;

    /**
     * 创建业务异常。
     * @param code 业务错误码，不应为空。
     * @param message 返回给前端展示的中文错误文案。
     */
    public BusinessException(String code, String message) {
        // 步骤 1：异常 message 直接作为 ApiResponse.message 的来源，必须保持用户可读。
        super(message);
        // 步骤 2：单独保存业务错误码，避免调用方只能从异常类型推断错误语义。
        this.code = code;
    }

    /**
     * 获取业务错误码。
     * @return 可写入 ApiResponse.code 的错误码。
     */
    public String getCode() {
        return code;
    }
}
