import { AdminLoginFormPayload, LoginResponseData, MeResponseData } from '../types/auth';
import { AdminErrorMessages } from '../constants/errorMessages';
import { ApiResponseParser } from './apiResponse';

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

    const envelope = await ApiResponseParser.parseEnvelope<LoginResponseData>(
      response,
      AdminErrorMessages.AUTH_LOGIN_FAILED_CHECK_CREDENTIALS,
    );
    ApiResponseParser.assertSuccess(response, envelope, AdminErrorMessages.AUTH_LOGIN_FAILED_CHECK_CREDENTIALS);
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

    const envelope = await ApiResponseParser.parseEnvelope<null>(response, AdminErrorMessages.AUTH_LOGOUT_FAILED);
    ApiResponseParser.assertSuccess(response, envelope, AdminErrorMessages.AUTH_LOGOUT_FAILED);
  }

  /**
   * 调用后端 me 接口校验当前 token 是否仍然有效，并返回当前登录用户。
   * @param token 当前会话 token。
   * @returns 当前登录用户信息。
   */
  static async me(token: string): Promise<MeResponseData> {
    const response = await fetch('/api/auth/me', {
      method: 'GET',
      headers: {
        satoken: token,
      },
    });

    const envelope = await ApiResponseParser.parseEnvelope<MeResponseData>(
      response,
      AdminErrorMessages.AUTH_SESSION_EXPIRED,
    );
    ApiResponseParser.assertSuccess(response, envelope, AdminErrorMessages.AUTH_SESSION_EXPIRED);
    return envelope.data;
  }
}
