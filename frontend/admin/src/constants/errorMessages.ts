/**
 * 管理端统一错误文案常量，供 API/Hook 作为兜底提示复用。
 */
export const AdminErrorMessages = {
  AUTH_LOGIN_FAILED_CHECK_CREDENTIALS: '登录失败，请检查账号或密码',
  AUTH_LOGOUT_FAILED: '退出登录失败',
  AUTH_SESSION_EXPIRED: '登录已失效，请重新登录',
  AUTH_CREDENTIALS_REQUIRED: '请输入账号和密码',
  AUTH_LOGIN_FAILED_RETRY: '登录失败，请稍后重试',
  AUTH_ADMIN_ONLY: '仅管理员账号可登录管理端',
  API_EMPTY_RESPONSE: '服务返回空响应，请检查后端服务状态',
  API_REQUEST_FAILED: '管理端请求失败',
} as const;
