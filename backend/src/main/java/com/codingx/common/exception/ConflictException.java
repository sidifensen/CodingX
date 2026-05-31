package com.codingx.common.exception;

/**
 * 表示请求与当前业务状态冲突的异常，例如重复提交、唯一性冲突或状态不可重复流转。
 */
public class ConflictException extends BusinessException {

    /**
     * 使用指定错误码创建业务冲突异常。
     * @param code 业务错误码。
     * @param message 中文错误文案。
     */
    public ConflictException(String code, String message) {
        // 步骤 1：允许调用方传入更细分的冲突错误码，便于前端区分重复提交等具体场景。
        super(code, message);
    }

    /**
     * 使用默认冲突错误码创建异常。
     * @param message 中文错误文案。
     */
    public ConflictException(String message) {
        // 步骤 1：没有细分错误码时使用通用 CONFLICT，保持 ApiResponse 结构稳定。
        super("CONFLICT", message);
    }
}
