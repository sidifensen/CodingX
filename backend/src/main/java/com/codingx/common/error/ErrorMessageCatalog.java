package com.codingx.common.error;

/**
 * 统一维护后端对外错误文案，确保前端可直接展示中文提示并保持一致。
 */
public final class ErrorMessageCatalog {

    /**
     * 系统异常通用提示。
     */
    public static final String INTERNAL_ERROR = "系统异常，请稍后重试";

    /**
     * 未登录或登录态失效提示。
     */
    public static final String UNAUTHORIZED = "未登录或登录已失效，请重新登录";

    /**
     * 无权限提示。
     */
    public static final String FORBIDDEN = "当前账号无权限执行该操作";

    /**
     * 参数校验失败通用提示。
     */
    public static final String VALIDATION_ERROR = "请求参数校验失败";

    /**
     * 账号密码缺失提示。
     */
    public static final String LOGIN_REQUIRED_CREDENTIALS = "请输入账号和密码";

    /**
     * 账号或密码错误提示。
     */
    public static final String LOGIN_INVALID_CREDENTIALS = "账号或密码错误";

    /**
     * 当前用户不存在提示。
     */
    public static final String CURRENT_USER_NOT_FOUND = "当前登录用户不存在";

    private ErrorMessageCatalog() {
    }
}
