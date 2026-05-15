import { AdminLoginFormPayload, ApiResponseEnvelope, LoginResponseData } from '../types/auth';

/**
 * 负责封装管理端认证相关接口调用，统一处理请求地址、鉴权头与异常语义。
 */
export class AuthApi {
  /**
   * 调用后端登录接口并返回登录数据。
   * @param payload 登录表单参数。
   * @returns 后端返回的登录用户与 token 信息。
   */
  static async login(payload: AdminLoginFormPayload): Promise<LoginResponseData> {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });

    const envelope = await parseApiEnvelope<LoginResponseData>(response);
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
    const response = await fetch('/api/auth/logout', {
      method: 'POST',
      headers: {
        satoken: token,
      },
    });

    const envelope = await parseApiEnvelope<null>(response);
    if (!response.ok || !envelope.success) {
      throw new Error(envelope.message || '退出登录失败');
    }
  }
}

/**
 * 解析后端响应为统一包裹结构；后端返回非 JSON 时兜底为可读错误文本。
 * @param response Fetch 响应对象。
 * @returns 统一响应结构。
 */
async function parseApiEnvelope<T>(response: Response): Promise<ApiResponseEnvelope<T>> {
  const rawText = await response.text();
  if (!rawText) {
    return {
      success: false,
      code: 'EMPTY_RESPONSE',
      message: '服务返回空响应，请检查后端服务状态',
      data: null as T,
    };
  }

  try {
    return JSON.parse(rawText) as ApiResponseEnvelope<T>;
  } catch {
    return {
      success: false,
      code: 'NON_JSON_RESPONSE',
      message: rawText,
      data: null as T,
    };
  }
}
