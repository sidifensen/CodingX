/**
 * 用户端统一错误文案常量，供 API/Hook 作为兜底提示复用。
 */
export const UserErrorMessages = {
  AUTH_LOGIN_FAILED_CHECK_CREDENTIALS: '登录失败，请检查账号或密码',
  AUTH_LOGOUT_FAILED: '退出登录失败',
  AUTH_SESSION_EXPIRED: '登录已失效，请重新登录',
  AUTH_CREDENTIALS_REQUIRED: '请输入账号和密码',
  AUTH_LOGIN_FAILED_RETRY: '登录失败，请稍后重试',
  API_EMPTY_RESPONSE: '服务返回空响应，请检查后端服务状态',
  CHAT_REQUEST_FAILED: '聊天请求失败',
  CHAT_ATTACHMENT_UPLOAD_FAILED: '附件上传失败',
  CHAT_QUEUE_BUSY: '当前会话并发已满，请稍后重试',
  CHAT_QUEUE_UNAVAILABLE: '当前会话暂不可执行，请稍后重试',
  CLI_AUTH_PARAMS_INVALID: 'CLI 登录参数不完整，请回到终端重新发起登录',
  CLI_AUTH_AUTHORIZE_FAILED: 'CLI 授权失败，请回到终端重试',
} as const;
