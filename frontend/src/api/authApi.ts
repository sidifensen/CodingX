import { ApiResponseEnvelope, LoginFormPayload, LoginResponseData } from '../types/auth';

/**
 * 负责封装认证相关接口调用，统一处理请求地址、鉴权头与异常语义。
 */
export class AuthApi {
  /**
   * 调用后端登录接口并返回登录数据。
   * @param payload 登录表单参数。
   * @returns 后端返回的登录用户与 token 信息。
   */
  static async login(payload: LoginFormPayload): Promise<LoginResponseData> {
    // 步骤：构造登录请求并发送到后端认证接口。
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });

    // 步骤：解析响应体，统一处理网络层与业务层错误。
    const envelope = (await response.json()) as ApiResponseEnvelope<LoginResponseData>;
    if (!response.ok || !envelope.success) {
      throw new Error(envelope.message || '登录失败，请检查账号或密码');
    }
    return envelope.data;
  }

  /**
   * 调用后端退出登录接口。
   * @param token 当前会话 token，用于服务端识别并销毁会话。
   */
  static async logout(token: string): Promise<void> {
    // 步骤：携带 satoken 请求头调用退出接口，通知后端清理当前会话。
    const response = await fetch('/api/auth/logout', {
      method: 'POST',
      headers: {
        satoken: token,
      },
    });

    // 步骤：解析退出响应；若失败抛出异常供上层兜底处理。
    const envelope = (await response.json()) as ApiResponseEnvelope<null>;
    if (!response.ok || !envelope.success) {
      throw new Error(envelope.message || '退出登录失败');
    }
  }
}
