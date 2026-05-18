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
     * 请求资源不存在通用提示。
     */
    public static final String RESOURCE_NOT_FOUND = "请求的资源不存在";

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

    /**
     * 管理端用户不存在提示。
     */
    public static final String ADMIN_USER_NOT_FOUND = "用户不存在或已删除";

    /**
     * 管理端用户请求体缺失提示。
     */
    public static final String ADMIN_USER_REQUEST_INVALID = "用户请求参数无效";

    /**
     * 管理端用户必填字段缺失提示。
     */
    public static final String ADMIN_USER_REQUIRED_FIELDS_MISSING = "用户名、展示名称和初始密码不能为空";

    /**
     * 管理端用户类型非法提示。
     */
    public static final String ADMIN_USER_TYPE_INVALID = "用户类型不合法";

    /**
     * 管理端用户状态非法提示。
     */
    public static final String ADMIN_USER_STATUS_INVALID = "用户状态不合法";

    /**
     * 管理端用户邮箱重复提示。
     */
    public static final String ADMIN_USER_EMAIL_DUPLICATED = "邮箱已被占用";

    /**
     * 管理端用户展示名缺失提示。
     */
    public static final String ADMIN_USER_DISPLAY_NAME_REQUIRED = "展示名称不能为空";

    /**
     * 管理端审核状态非法提示。
     */
    public static final String ADMIN_USER_APPROVE_STATUS_INVALID = "仅待审核用户可执行审核通过";

    /**
     * 管理端重置密码必填提示。
     */
    public static final String ADMIN_USER_PASSWORD_REQUIRED = "新密码不能为空";

    private ErrorMessageCatalog() {
    }
}
