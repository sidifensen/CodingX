package com.codingx.common.exception;

/**
 * 表示请求的业务资源不存在或当前用户不可见的异常。
 */
public class NotFoundException extends BusinessException {

    /**
     * 创建资源不存在异常。
     * @param message 返回给前端展示的中文错误文案。
     */
    public NotFoundException(String message) {
        // 步骤 1：资源不存在场景统一使用 NOT_FOUND 错误码，隐藏底层存储细节。
        super("NOT_FOUND", message);
    }
}
