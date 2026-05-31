package com.codingx.common.exception;

/**
 * 表示当前登录用户没有执行目标操作权限的业务异常。
 */
public class ForbiddenException extends BusinessException {

    /**
     * 创建权限不足异常。
     * @param message 返回给前端展示的中文错误文案。
     */
    public ForbiddenException(String message) {
        // 步骤 1：权限不足场景统一使用 FORBIDDEN 错误码，前端无需解析异常文案判断权限状态。
        super("FORBIDDEN", message);
    }
}
