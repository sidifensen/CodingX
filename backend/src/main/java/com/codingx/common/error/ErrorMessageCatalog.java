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
     * 登录用户名缺失提示。
     */
    public static final String LOGIN_USERNAME_REQUIRED = "用户名不能为空";

    /**
     * 登录密码缺失提示。
     */
    public static final String LOGIN_PASSWORD_REQUIRED = "密码不能为空";

    /**
     * 账号或密码错误提示。
     */
    public static final String LOGIN_INVALID_CREDENTIALS = "账号或密码错误";

    /**
     * 当前用户不存在提示。
     */
    public static final String CURRENT_USER_NOT_FOUND = "当前登录用户不存在";

    /**
     * 用户编号缺失提示。
     */
    public static final String AUTH_USER_ID_REQUIRED = "用户编号不能为空";

    /**
     * 用户基础字段缺失提示。
     */
    public static final String AUTH_USER_FIELDS_REQUIRED = "用户名、展示名称、密码哈希、用户类型和状态不能为空";

    /**
     * 用户已禁用提示。
     */
    public static final String AUTH_USER_DISABLED = "当前用户已禁用";

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
     * 反馈会话编号缺失提示。
     */
    public static final String CHAT_FEEDBACK_CONVERSATION_ID_REQUIRED = "会话编号不能为空";

    /**
     * 反馈评分缺失提示。
     */
    public static final String CHAT_FEEDBACK_VOTE_REQUIRED = "评分不能为空";

    /**
     * 反馈评分非法提示。
     */
    public static final String CHAT_FEEDBACK_VOTE_INVALID = "评分只能是 -1 或 1";

    /**
     * 会话标题缺失提示。
     */
    public static final String CHAT_CONVERSATION_TITLE_REQUIRED = "会话标题不能为空";

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
    public static final String CHAT_MCP_TOOL_CONFIG_MISSING = "当前意图未配置可用工具";

    /**
     * 队列已满提示。
     */
    public static final String CHAT_QUEUE_BUSY = "当前会话并发已满，请稍后重试";


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
     * 任务命令缺失提示。
     */
    public static final String TASK_COMMAND_REQUIRED = "任务命令不能为空";

    /**
     * 任务标题缺失提示。
     */
    public static final String TASK_TITLE_REQUIRED = "任务标题不能为空";

    /**
     * 任务运行类型缺失提示。
     */
    public static final String TASK_RUNTIME_TYPE_REQUIRED = "运行类型不能为空";

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
    public static final String CHAT_TRACE_NOT_FOUND = "调用链不存在";

    /**
     * Trace 上下文缺失提示。
     */
    public static final String CHAT_TRACE_CONTEXT_NOT_AVAILABLE = "调用链上下文不可用";

    /**
     * 进入 AI 同步执行失败提示。
     */
    public static final String CHAT_PROMPT_EXECUTION_FAILED = "提示词执行失败";

    /**
     * Prompt 模板不存在提示。
     */
    public static final String CHAT_PROMPT_TEMPLATE_NOT_FOUND = "提示词模板不存在";

    /**
     * 登录状态失效提示。
     */
    public static final String AUTH_NOT_LOGGED_IN = "未登录";

    /**
     * 反馈关联消息不存在提示。
     */
    public static final String CHAT_FEEDBACK_MESSAGE_NOT_FOUND = "反馈关联消息不存在";

    /**
     * 反馈记录不存在提示。
     */
    public static final String CHAT_FEEDBACK_NOT_FOUND = "反馈记录不存在";

    /**
     * AI 对话消息缺失提示。
     */
    public static final String AI_CONVERSATION_MESSAGES_REQUIRED = "AI 对话消息不能为空";

    /**
     * 搜索服务提供方不支持提示。
     */
    public static final String WEB_SEARCH_PROVIDER_UNSUPPORTED = "不支持的搜索服务提供方";

    /**
     * 搜索服务地址不合法提示。
     */
    public static final String WEB_SEARCH_BASE_URL_INVALID = "搜索服务地址不合法";

    /**
     * 模型提供方不支持提示。
     */
    public static final String AI_PROVIDER_UNSUPPORTED = "不支持的模型提供方";

    /**
     * 模型 API 密钥缺失提示。
     */
    public static final String AI_API_KEY_NOT_CONFIGURED = "模型 API 密钥未配置";

    /**
     * 模型请求失败提示。
     */
    public static final String AI_REQUEST_FAILED = "模型请求失败";

    /**
     * 模型响应体为空提示。
     */
    public static final String AI_RESPONSE_BODY_EMPTY = "模型响应体为空";

    /**
     * 模型基础地址不合法提示。
     */
    public static final String AI_BASE_URL_INVALID = "模型基础地址不合法";

    /**
     * 模型聊天接口未配置提示。
     */
    public static final String AI_CHAT_ENDPOINT_NOT_CONFIGURED = "模型聊天接口未配置";

    /**
     * 模型聊天接口地址不合法提示。
     */
    public static final String AI_CHAT_ENDPOINT_INVALID = "模型聊天接口地址不合法";

    /**
     * 用户展示名称缺失提示。
     */
    public static final String AUTH_USER_DISPLAY_NAME_REQUIRED = "用户展示名称不能为空";

    /**
     * 用户状态缺失提示。
     */
    public static final String AUTH_USER_STATUS_REQUIRED = "用户状态不能为空";

    /**
     * 用户密码哈希缺失提示。
     */
    public static final String AUTH_USER_PASSWORD_HASH_REQUIRED = "密码哈希不能为空";

    /**
     * 意图节点不存在提示。
     */
    public static final String CHAT_INTENT_NOT_FOUND = "意图节点不存在";

    /**
     * 意图存在子节点不可删除提示。
     */
    public static final String CHAT_INTENT_HAS_CHILDREN = "该意图存在子节点，不能删除";

    /**
     * 意图编码缺失提示。
     */
    public static final String CHAT_INTENT_CODE_REQUIRED = "意图编码不能为空";

    /**
     * 意图名称缺失提示。
     */
    public static final String CHAT_INTENT_NAME_REQUIRED = "意图名称不能为空";

    /**
     * 意图编码重复提示。
     */
    public static final String CHAT_INTENT_DUPLICATE_CODE = "意图编码已存在";

    /**
     * 意图类型不支持提示。
     */
    public static final String CHAT_INTENT_KIND_UNSUPPORTED = "意图类型不支持";

    /**
     * 意图节点对象缺失提示。
     */
    public static final String CHAT_INTENT_NODE_REQUIRED = "意图节点不能为空";

    /**
     * 查询词映射规则不存在提示。
     */
    public static final String CHAT_QUERY_TERM_MAPPING_NOT_FOUND = "映射规则不存在";

    /**
     * 查询词映射原始词缺失提示。
     */
    public static final String CHAT_QUERY_TERM_MAPPING_SOURCE_REQUIRED = "原始词不能为空";

    /**
     * 查询词映射目标词缺失提示。
     */
    public static final String CHAT_QUERY_TERM_MAPPING_TARGET_REQUIRED = "目标词不能为空";

    /**
     * 运行时配置对象缺失提示。
     */
    public static final String CHAT_SETTING_REQUIRED = "配置不能为空";

    /**
     * 运行时配置键缺失提示。
     */
    public static final String CHAT_SETTING_KEY_REQUIRED = "配置键不能为空";

    /**
     * 运行时配置值类型缺失提示。
     */
    public static final String CHAT_SETTING_VALUE_TYPE_REQUIRED = "值类型不能为空";

    /**
     * 运行时配置值缺失提示。
     */
    public static final String CHAT_SETTING_VALUE_REQUIRED = "配置值不能为空";

    /**
     * 运行时配置值非法提示前缀。
     */
    public static final String CHAT_SETTING_VALUE_INVALID_PREFIX = "配置 ";

    /**
     * 运行时配置值非法提示中缀。
     */
    public static final String CHAT_SETTING_VALUE_INVALID_INFIX = " 值 ";

    /**
     * 运行时配置值非法提示后缀前半。
     */
    public static final String CHAT_SETTING_VALUE_INVALID_SUFFIX = " 不是合法 ";

    /**
     * 专家编码重复提示。
     */
    public static final String CHAT_EXPERT_DUPLICATE_CODE = "专家编码已存在";

    /**
     * 专家不存在提示。
     */
    public static final String CHAT_EXPERT_NOT_FOUND = "专家不存在";

    /**
     * 专家对象缺失提示。
     */
    public static final String CHAT_EXPERT_REQUIRED = "专家信息不能为空";

    /**
     * 专家编码缺失提示。
     */
    public static final String CHAT_EXPERT_CODE_REQUIRED = "专家编码不能为空";

    /**
     * 专家名称缺失提示。
     */
    public static final String CHAT_EXPERT_NAME_REQUIRED = "专家名称不能为空";

    /**
     * 专家提示词缺失提示。
     */
    public static final String CHAT_EXPERT_PROMPT_REQUIRED = "专家提示词不能为空";

    /**
     * MCP 编码重复提示。
     */
    public static final String CHAT_MCP_DUPLICATE_CODE = "MCP 编码已存在";

    /**
     * MCP 配置不存在提示。
     */
    public static final String CHAT_MCP_CONFIG_NOT_FOUND = "MCP 配置不存在";

    /**
     * MCP 配置对象缺失提示。
     */
    public static final String CHAT_MCP_CONFIG_REQUIRED = "MCP 配置不能为空";

    /**
     * MCP 编码缺失提示。
     */
    public static final String CHAT_MCP_CODE_REQUIRED = "MCP 编码不能为空";

    /**
     * MCP 名称缺失提示。
     */
    public static final String CHAT_MCP_NAME_REQUIRED = "MCP 名称不能为空";

    /**
     * MCP 工具返回空结果提示。
     */
    public static final String CHAT_MCP_EMPTY_RESPONSE = "工具返回空结果";

    /**
     * MCP 工具不存在提示前缀。
     */
    public static final String CHAT_MCP_TOOL_NOT_FOUND_PREFIX = "未找到 MCP 工具：";

    /**
     * 附件读取失败提示。
     */
    public static final String CHAT_ATTACHMENT_READ_FAILED = "上传文件读取失败";

    /**
     * 附件不存在提示。
     */
    public static final String CHAT_ATTACHMENT_NOT_FOUND = "附件不存在";

    /**
     * 部分附件不存在提示。
     */
    public static final String CHAT_ATTACHMENT_PARTIAL_NOT_FOUND = "部分附件不存在";

    /**
     * 当前用户无权访问附件提示。
     */
    public static final String CHAT_ATTACHMENT_FORBIDDEN_ACCESS = "你无权访问该附件";

    /**
     * 当前用户无权使用附件提示。
     */
    public static final String CHAT_ATTACHMENT_FORBIDDEN_USE = "你无权使用该附件";

    /**
     * 附件已被占用提示。
     */
    public static final String CHAT_ATTACHMENT_ALREADY_USED = "附件已被其他消息使用";

    /**
     * 附件与会话不匹配提示。
     */
    public static final String CHAT_ATTACHMENT_CONVERSATION_MISMATCH = "附件不属于当前会话";

    /**
     * 附件下载失败提示。
     */
    public static final String CHAT_ATTACHMENT_DOWNLOAD_FAILED = "附件下载失败";

    /**
     * 附件为空提示。
     */
    public static final String CHAT_ATTACHMENT_EMPTY = "请先选择上传文件";

    /**
     * 附件过大提示。
     */
    public static final String CHAT_ATTACHMENT_TOO_LARGE = "上传文件大小不能超过 10MB";

    /**
     * 附件类型不支持提示。
     */
    public static final String CHAT_ATTACHMENT_TYPE_NOT_ALLOWED = "当前文件格式暂不支持解析";

    /**
     * 音视频附件不支持提示。
     */
    public static final String CHAT_ATTACHMENT_AUDIO_VIDEO_NOT_SUPPORTED = "暂不支持音频和视频文件";

    /**
     * 仓库路径缺失提示。
     */
    public static final String CHAT_WORKSPACE_PATH_REQUIRED = "仓库路径不能为空";

    /**
     * 仓库路径不存在提示。
     */
    public static final String CHAT_WORKSPACE_PATH_NOT_FOUND = "仓库路径不存在";

    /**
     * 仓库路径非目录提示。
     */
    public static final String CHAT_WORKSPACE_PATH_INVALID_DIRECTORY = "仓库路径必须是目录";

    /**
     * 队列许可获取被中断提示。
     */
    public static final String CHAT_QUEUE_ACQUIRE_INTERRUPTED = "获取队列许可时线程被中断";

    /**
     * 队列许可等待被中断提示。
     */
    public static final String CHAT_QUEUE_WAIT_INTERRUPTED = "等待队列许可时线程被中断";

    /**
     * 技能文件列表缺失提示。
     */
    public static final String CHAT_SKILL_FILE_LIST_REQUIRED = "技能文件列表不能为空";

    /**
     * 技能目录前缀缺失提示。
     */
    public static final String CHAT_SKILL_DIRECTORY_PREFIX_REQUIRED = "目录前缀不能为空";

    /**
     * 技能目录前缀非法提示。
     */
    public static final String CHAT_SKILL_DIRECTORY_PREFIX_INVALID = "目录前缀非法";

    /**
     * 技能文件路径缺失提示。
     */
    public static final String CHAT_SKILL_FILE_PATH_REQUIRED = "文件路径不能为空";

    /**
     * 技能文件路径非法提示。
     */
    public static final String CHAT_SKILL_FILE_PATH_INVALID = "文件路径非法";

    /**
     * 技能不存在提示。
     */
    public static final String CHAT_SKILL_NOT_FOUND = "技能不存在";

    /**
     * 技能包文件不存在提示。
     */
    public static final String CHAT_SKILL_PACKAGE_FILE_NOT_FOUND = "技能包文件不存在";

    /**
     * 技能编码重复提示。
     */
    public static final String CHAT_SKILL_DUPLICATE_CODE = "技能编码已存在";

    /**
     * 技能包内容未变化提示。
     */
    public static final String CHAT_SKILL_UPLOAD_DUPLICATE = "技能包内容未变化，请勿重复上传";

    /**
     * 技能包上传失败提示。
     */
    public static final String CHAT_SKILL_UPLOAD_FAILED = "技能包上传失败";

    /**
     * 技能包路径参数缺失提示。
     */
    public static final String CHAT_SKILL_PACKAGE_PATH_REQUIRED = "文件路径不能为空";

    /**
     * 技能包文件为二进制提示。
     */
    public static final String CHAT_SKILL_PACKAGE_BINARY_FILE = "该文件为二进制文件，暂不支持在线预览";

    /**
     * 技能对象缺失提示。
     */
    public static final String CHAT_SKILL_REQUIRED = "技能信息不能为空";

    /**
     * 技能编码缺失提示。
     */
    public static final String CHAT_SKILL_CODE_REQUIRED = "技能编码不能为空";

    /**
     * 技能名称缺失提示。
     */
    public static final String CHAT_SKILL_NAME_REQUIRED = "技能名称不能为空";

    /**
     * 技能上传方式冲突提示。
     */
    public static final String CHAT_SKILL_UPLOAD_MODE_CONFLICT = "请仅选择一种上传方式";

    /**
     * 技能上传文件缺失提示。
     */
    public static final String CHAT_SKILL_UPLOAD_FILE_REQUIRED = "请上传技能包文件";

    /**
     * 技能包读取失败提示。
     */
    public static final String CHAT_SKILL_UPLOAD_READ_FAILED = "技能包读取失败";

    /**
     * 技能目录文件读取失败提示。
     */
    public static final String CHAT_SKILL_UPLOAD_DIRECTORY_READ_FAILED = "技能目录文件读取失败";

    /**
     * 技能包格式不支持提示。
     */
    public static final String CHAT_SKILL_UPLOAD_ARCHIVE_ONLY = "仅支持 zip 或 skill 文件";

    /**
     * 技能包缺少文件内容提示。
     */
    public static final String CHAT_SKILL_UPLOAD_EMPTY_CONTENT = "技能包缺少文件内容";

    /**
     * 技能包存在重复文件路径提示前缀。
     */
    public static final String CHAT_SKILL_UPLOAD_DUPLICATE_PATH_PREFIX = "技能包存在重复文件路径: ";

    /**
     * 技能包缺少可用文件提示。
     */
    public static final String CHAT_SKILL_UPLOAD_NO_USABLE_FILE = "技能包缺少可用文件";

    /**
     * 技能包压缩文件非法提示。
     */
    public static final String CHAT_SKILL_UPLOAD_INVALID_ARCHIVE = "技能包不是有效压缩文件";

    /**
     * 技能包缺少根目录 SKILL.md 提示。
     */
    public static final String CHAT_SKILL_UPLOAD_ROOT_MANIFEST_REQUIRED = "技能包缺少根目录 SKILL.md";

    /**
     * SKILL.md 缺少 YAML 元信息提示。
     */
    public static final String CHAT_SKILL_MANIFEST_YAML_REQUIRED = "SKILL.md 缺少 YAML 元信息";

    /**
     * SKILL.md YAML 元信息格式错误提示。
     */
    public static final String CHAT_SKILL_MANIFEST_YAML_INVALID = "SKILL.md YAML 元信息格式不正确";

    /**
     * SKILL.md 缺少 name 字段提示。
     */
    public static final String CHAT_SKILL_MANIFEST_NAME_REQUIRED = "SKILL.md 缺少 name 字段";

    /**
     * 技能没有可预览技能包提示。
     */
    public static final String CHAT_SKILL_PACKAGE_NOT_FOUND = "该技能没有可预览的技能包";

    /**
     * 技能包下载失败提示。
     */
    public static final String CHAT_SKILL_PACKAGE_DOWNLOAD_FAILED = "技能包下载失败";

    /**
     * 技能包迁移上传失败提示。
     */
    public static final String CHAT_SKILL_PACKAGE_MIGRATE_UPLOAD_FAILED = "技能包迁移上传失败";

    /**
     * 历史技能包清理失败提示。
     */
    public static final String CHAT_SKILL_PACKAGE_MIGRATE_DELETE_FAILED = "历史技能包清理失败";

    /**
     * 技能包目录解析失败提示。
     */
    public static final String CHAT_SKILL_PACKAGE_PARSE_FAILED = "技能包目录解析失败";

    /**
     * 技能包路径非法提示。
     */
    public static final String CHAT_SKILL_UPLOAD_PATH_INVALID = "技能包路径非法";

    /**
     * 工具编码重复提示。
     */
    public static final String CHAT_TOOL_DUPLICATE_CODE = "工具编码已存在";

    /**
     * 工具配置不存在提示。
     */
    public static final String CHAT_TOOL_CONFIG_NOT_FOUND = "工具配置不存在";

    /**
     * 工具禁用提示。
     */
    public static final String CHAT_TOOL_DISABLED = "工具已禁用，无法调用";

    /**
     * 当前工具不允许用户态调用提示。
     */
    public static final String CHAT_TOOL_NOT_ALLOWED = "当前工具不允许用户态调用";

    /**
     * 检测到高风险操作提示。
     */
    public static final String CHAT_TOOL_HIGH_RISK_CONFIRM_REQUIRED = "检测到高风险操作，请确认后再执行";

    /**
     * 工具未接入执行器提示。
     */
    public static final String CHAT_TOOL_EXECUTOR_NOT_FOUND = "工具未接入执行器";

    /**
     * 工具配置对象缺失提示。
     */
    public static final String CHAT_TOOL_CONFIG_REQUIRED = "工具配置不能为空";

    /**
     * 工具编码缺失提示。
     */
    public static final String CHAT_TOOL_CODE_REQUIRED = "工具编码不能为空";

    /**
     * 工具名称缺失提示。
     */
    public static final String CHAT_TOOL_NAME_REQUIRED = "工具名称不能为空";

    /**
     * 工具不支持提示。
     */
    public static final String CHAT_TOOL_UNSUPPORTED = "工具暂不支持";

    /**
     * Patch 校验失败提示。
     */
    public static final String CHAT_TOOL_PATCH_CHECK_FAILED = "Patch 校验失败";

    /**
     * Patch 应用失败（无细节）提示。
     */
    public static final String CHAT_TOOL_PATCH_APPLY_FAILED = "Patch 应用失败";

    /**
     * command 参数缺失提示。
     */
    public static final String CHAT_TOOL_COMMAND_REQUIRED = "请提供 command";

    /**
     * sessionId 参数缺失提示。
     */
    public static final String CHAT_TOOL_SESSION_ID_REQUIRED = "请提供 sessionId";

    /**
     * 命令会话不存在提示。
     */
    public static final String CHAT_TOOL_SESSION_NOT_FOUND = "命令会话不存在";

    /**
     * text 参数缺失提示。
     */
    public static final String CHAT_TOOL_TEXT_REQUIRED = "请提供 text";

    /**
     * patch 参数缺失提示。
     */
    public static final String CHAT_TOOL_PATCH_REQUIRED = "请提供 patch 内容";

    /**
     * Patch 应用失败提示前缀。
     */
    public static final String CHAT_TOOL_PATCH_APPLY_FAILED_PREFIX = "Patch 应用失败: ";

    /**
     * Patch 缺少 Begin 标记提示。
     */
    public static final String CHAT_TOOL_PATCH_BEGIN_MISSING = "Patch 缺少 Begin 标记";

    /**
     * Patch 缺少 End 标记提示。
     */
    public static final String CHAT_TOOL_PATCH_END_MISSING = "Patch 缺少 End 标记";

    /**
     * Patch 待更新文件不存在提示前缀。
     */
    public static final String CHAT_TOOL_PATCH_TARGET_NOT_FOUND_PREFIX = "待更新文件不存在: ";

    /**
     * Patch 更新文件失败提示前缀。
     */
    public static final String CHAT_TOOL_PATCH_UPDATE_FAILED_PREFIX = "更新文件失败: ";

    /**
     * Patch 新增文件失败提示前缀。
     */
    public static final String CHAT_TOOL_PATCH_ADD_FAILED_PREFIX = "新增文件失败: ";

    /**
     * Patch 上下文不匹配提示。
     */
    public static final String CHAT_TOOL_PATCH_CONTEXT_MISMATCH = "Patch 上下文不匹配，无法定位修改位置";

    /**
     * Patch 路径越界提示。
     */
    public static final String CHAT_TOOL_PATCH_PATH_OUT_OF_BOUND = "Patch 路径越界，已拒绝执行";

    /**
     * uri 参数缺失提示。
     */
    public static final String CHAT_TOOL_URI_REQUIRED = "请提供 uri";

    /**
     * 资源 URI 不支持提示。
     */
    public static final String CHAT_TOOL_URI_UNSUPPORTED = "不支持的资源 URI";

    /**
     * 资源不存在提示。
     */
    public static final String CHAT_TOOL_RESOURCE_NOT_FOUND = "资源不存在";

    /**
     * 计划步骤缺失提示。
     */
    public static final String CHAT_TOOL_PLAN_STEP_REQUIRED = "请提供至少一个步骤";

    /**
     * 图片路径缺失提示。
     */
    public static final String CHAT_TOOL_IMAGE_PATH_REQUIRED = "请提供图片绝对路径";

    /**
     * 图片不存在提示。
     */
    public static final String CHAT_TOOL_IMAGE_NOT_FOUND = "图片不存在";

    /**
     * 图片格式不合法提示。
     */
    public static final String CHAT_TOOL_IMAGE_INVALID = "文件不是可读图片";

    /**
     * 图片读取失败提示前缀。
     */
    public static final String CHAT_TOOL_IMAGE_READ_FAILED_PREFIX = "读取图片失败: ";

    /**
     * agentId 参数缺失提示。
     */
    public static final String CHAT_TOOL_AGENT_ID_REQUIRED = "请提供 agentId";

    /**
     * 子代理不存在提示。
     */
    public static final String CHAT_TOOL_AGENT_NOT_FOUND = "子代理不存在";

    /**
     * 目标不存在提示。
     */
    public static final String CHAT_TOOL_GOAL_NOT_FOUND = "目标不存在";

    /**
     * csvPath 参数缺失提示。
     */
    public static final String CHAT_TOOL_CSV_PATH_REQUIRED = "请提供 csvPath";

    /**
     * CSV 文件不存在提示。
     */
    public static final String CHAT_TOOL_CSV_NOT_FOUND = "CSV 文件不存在";

    /**
     * CSV 解析失败提示前缀。
     */
    public static final String CHAT_TOOL_CSV_PARSE_FAILED_PREFIX = "CSV 解析失败: ";

    /**
     * 命令执行失败提示前缀。
     */
    public static final String CHAT_TOOL_COMMAND_EXECUTE_FAILED_PREFIX = "命令执行失败: ";

    /**
     * 命令启动失败提示前缀。
     */
    public static final String CHAT_TOOL_EXEC_COMMAND_START_FAILED_PREFIX = "启动命令失败: ";

    /**
     * 标准输入写入失败提示前缀。
     */
    public static final String CHAT_TOOL_STDIN_WRITE_FAILED_PREFIX = "写入标准输入失败: ";

    /**
     * 幂等锁获取被中断提示。
     */
    public static final String IDEMPOTENT_LOCK_INTERRUPTED = "幂等锁获取被中断";

    /**
     * 天气地理编码服务调用失败提示。
     */
    public static final String WEATHER_GEOCODING_REQUEST_FAILED = "天气地理编码服务调用失败";

    /**
     * 天气地理编码未命中提示。
     */
    public static final String WEATHER_COORDINATE_NOT_FOUND = "未找到该城市的天气坐标";

    /**
     * 天气预报服务调用失败提示。
     */
    public static final String WEATHER_FORECAST_REQUEST_FAILED = "天气预报服务调用失败";

    /**
     * 天气服务返回结构异常提示。
     */
    public static final String WEATHER_RESPONSE_INVALID = "天气服务返回结构异常";

    /**
     * 天气预报为空提示。
     */
    public static final String WEATHER_FORECAST_EMPTY = "天气预报为空";

    /**
     * 天气预报字段缺失提示。
     */
    public static final String WEATHER_FORECAST_FIELD_MISSING = "天气预报字段缺失";

    /**
     * AI 首包后流式中断提示。
     */
    public static final String AI_STREAM_FAILED_AFTER_FIRST_TOKEN = "模型流式响应在首包后失败";

    /**
     * AI 路由线程中断提示。
     */
    public static final String AI_ROUTING_INTERRUPTED = "模型路由过程被中断";

    /**
     * AI 路由无可用提供方提示。
     */
    public static final String AI_NO_AVAILABLE_PROVIDER = "没有可用的模型服务可完成请求";

    /**
     * AI 流会话为空提示后缀。
     */
    public static final String AI_STREAM_SESSION_NULL_SUFFIX = " 返回空流式会话";

    /**
     * AI 首包前失败提示后缀。
     */
    public static final String AI_STREAM_FAILED_BEFORE_FIRST_TOKEN_SUFFIX = " 在首包前失败";

    /**
     * AI 首包超时提示后缀。
     */
    public static final String AI_STREAM_TIMEOUT_BEFORE_FIRST_TOKEN_SUFFIX = " 在首包前超时";

    /**
     * AI 无内容完成提示后缀。
     */
    public static final String AI_STREAM_COMPLETED_WITHOUT_CONTENT_SUFFIX = " 返回完成但无内容";

    /**
     * 工具执行器重复注册提示前缀。
     */
    public static final String CHAT_TOOL_DUPLICATE_EXECUTOR_PREFIX = "chat_tool 执行器重复注册: ";

    private ErrorMessageCatalog() {
    }
}
