/**
 * 描述前端保存的登录会话结构。
 */
export interface AuthSession {
  token: string;
  userId: string;
  username: string;
  displayName: string;
  userType: string;
}

/**
 * 描述登录表单提交参数。
 */
export interface LoginFormPayload {
  username: string;
  password: string;
}

/**
 * 描述后端登录接口返回的用户信息与令牌。
 */
export interface LoginResponseData {
  userId: string | number;
  username: string;
  displayName: string;
  userType: string;
  token: string;
}

/**
 * 描述后端统一响应包裹结构。
 */
export interface ApiResponseEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}
