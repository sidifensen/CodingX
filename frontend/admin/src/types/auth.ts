/**
 * 描述管理端保存的登录会话结构。
 */
export interface AdminAuthSession {
  token: string;
  userId: string;
  username: string;
  displayName: string;
  userType: string;
}

/**
 * 描述管理端登录表单提交参数。
 */
export interface AdminLoginFormPayload {
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
 * 描述后端 me 接口返回的当前登录用户信息。
 */
export interface MeResponseData {
  userId: string | number;
  username: string;
  displayName: string;
  userType: string;
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
