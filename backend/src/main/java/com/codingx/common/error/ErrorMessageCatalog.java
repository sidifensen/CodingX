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

    /**
     * 会话消息内容缺失提示。
     */
    public static final String CHAT_MESSAGE_CONTENT_REQUIRED = "消息内容不能为空";

    /**
     * 当前用户无权访问会话提示。
     */
    public static final String CHAT_CONVERSATION_FORBIDDEN = "你无权访问该会话";

    /**
     * 会话不存在提示。
     */
    public static final String CHAT_CONVERSATION_NOT_FOUND = "会话不存在";

    /**
     * 新建会话默认标题。
     */
    public static final String CHAT_CONVERSATION_DEFAULT_TITLE = "新会话";

    /**
     * 重命名会话成功提示。
     */
    public static final String CHAT_CONVERSATION_RENAMED = "会话重命名成功";

    /**
     * 删除会话成功提示。
     */
    public static final String CHAT_CONVERSATION_DELETED = "会话删除成功";

    /**
     * 消息处理成功提示。
     */
    public static final String CHAT_MESSAGE_PROCESSED = "消息处理成功";

    /**
     * 取消会话请求成功提示。
     */
    public static final String CHAT_CANCEL_REQUESTED = "已发起取消请求";

    /**
     * 提交反馈成功提示。
     */
    public static final String CHAT_FEEDBACK_SUBMITTED = "反馈提交成功";

    /**
     * 会话 MCP 配置缺失提示。
     */
    public static final String CHAT_MCP_TOOL_CONFIG_MISSING = "当前意图未配置可用 MCP 工具";

    /**
     * 模型回复失败兜底提示。
     */
    public static final String CHAT_AI_RESPONSE_FAILED = "AI 回复失败";

    /**
     * 任务不存在提示。
     */
    public static final String TASK_NOT_FOUND = "任务不存在";

    /**
     * 当前用户无权访问任务提示。
     */
    public static final String TASK_FORBIDDEN_ACCESS = "你无权访问该任务";

    /**
     * 当前用户无权操作任务提示。
     */
    public static final String TASK_FORBIDDEN_OPERATE = "你无权操作该任务";

    /**
     * 启动任务成功提示。
     */
    public static final String TASK_STARTED = "任务已启动";

    /**
     * 工作空间不存在提示。
     */
    public static final String WORKSPACE_NOT_FOUND = "工作空间不存在";

    /**
     * 登出成功提示。
     */
    public static final String AUTH_LOGOUT_SUCCESS = "退出登录成功";

    /**
     * 任务实体字段缺失提示。
     */
    public static final String TASK_FIELDS_REQUIRED = "任务字段不能为空";

    /**
     * 任务创建人缺失提示。
     */
    public static final String TASK_ID_AND_CREATOR_REQUIRED = "任务编号和创建人不能为空";

    /**
     * 任务标题或运行类型缺失提示。
     */
    public static final String TASK_TITLE_AND_RUNTIME_TYPE_REQUIRED = "任务标题和运行类型不能为空";

    /**
     * 仅允许已创建任务启动提示。
     */
    public static final String TASK_ONLY_CREATED_CAN_START = "仅创建状态的任务可以启动";

    /**
     * 仅允许运行中任务完成提示。
     */
    public static final String TASK_ONLY_RUNNING_CAN_COMPLETE = "仅运行中的任务可以完成";

    /**
     * 仅允许运行中任务失败提示。
     */
    public static final String TASK_ONLY_RUNNING_CAN_FAIL = "仅运行中的任务可以失败";

    /**
     * 任务失败默认错误提示。
     */
    public static final String TASK_UNKNOWN_ERROR = "未知错误";

    /**
     * 任务产物字段缺失提示。
     */
    public static final String TASK_ARTIFACT_FIELDS_REQUIRED = "任务产物字段不能为空";

    /**
     * 任务事件字段缺失提示。
     */
    public static final String TASK_EVENT_FIELDS_REQUIRED = "任务事件字段不能为空";

    /**
     * 会话字段缺失提示。
     */
    public static final String CHAT_CONVERSATION_FIELDS_REQUIRED = "会话字段不能为空";

    /**
     * 消息字段缺失提示。
     */
    public static final String CHAT_MESSAGE_FIELDS_REQUIRED = "消息字段不能为空";

    /**
     * Trace 未找到提示。
     */
    public static final String CHAT_TRACE_NOT_FOUND = "Trace 不存在";

    /**
     * Trace 上下文缺失提示。
     */
    public static final String CHAT_TRACE_CONTEXT_NOT_AVAILABLE = "Trace 上下文不可用";

    /**
     * 进入 AI 同步执行失败提示。
     */
    public static final String CHAT_PROMPT_EXECUTION_FAILED = "Prompt 执行失败";

    /**
     * Prompt 模板不存在提示。
     */
    public static final String CHAT_PROMPT_TEMPLATE_NOT_FOUND = "Prompt 模板不存在";

    /**
     * 登录状态失效提示。
     */
    public static final String AUTH_NOT_LOGGED_IN = "未登录";

    /**
     * AI 对话消息缺失提示。
     */
    public static final String AI_CONVERSATION_MESSAGES_REQUIRED = "AI 对话消息不能为空";

    private ErrorMessageCatalog() {
    }
}
