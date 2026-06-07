import { UserErrorMessages } from '../constants/errorMessages';
import {
  CliAuthorizePayload,
  CliAuthorizeResponseData,
  CliDeviceAuthorizePayload,
} from '../types/cliAuth';
import { ApiResponseParser } from './apiResponse';

/**
 * 封装 CLI 浏览器登录相关接口，统一保留后端 ApiResponse.message 错误语义。
 */
export class CliAuthApi {
  /**
   * 浏览器登录完成后创建 CLI loopback 一次性授权码。
   * @param token 当前浏览器登录 token。
   * @param payload loopback 授权参数。
   * @returns 后端创建的一次性授权码。
   */
  static async authorize(
    token: string,
    payload: CliAuthorizePayload,
  ): Promise<CliAuthorizeResponseData> {
    // 步骤：携带当前网页登录态请求后端创建短期 code，真实 token 不进入 URL。
    const response = await fetch('/api/auth/cli/authorize', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        satoken: token,
      },
      body: JSON.stringify(payload),
    });
    const envelope = await ApiResponseParser.parseEnvelope<CliAuthorizeResponseData>(
      response,
      UserErrorMessages.CLI_AUTH_AUTHORIZE_FAILED,
    );
    ApiResponseParser.assertSuccess(response, envelope, UserErrorMessages.CLI_AUTH_AUTHORIZE_FAILED);
    return envelope.data;
  }

  /**
   * 浏览器登录完成后确认 CLI 设备码授权。
   * @param token 当前浏览器登录 token。
   * @param payload 设备码授权参数。
   */
  static async authorizeDevice(token: string, payload: CliDeviceAuthorizePayload): Promise<void> {
    // 步骤：设备码模式只绑定 userCode 与当前用户，不返回 CLI deviceCode。
    const response = await fetch('/api/auth/cli/device/authorize', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        satoken: token,
      },
      body: JSON.stringify(payload),
    });
    const envelope = await ApiResponseParser.parseEnvelope<null>(
      response,
      UserErrorMessages.CLI_AUTH_AUTHORIZE_FAILED,
    );
    ApiResponseParser.assertSuccess(response, envelope, UserErrorMessages.CLI_AUTH_AUTHORIZE_FAILED);
  }
}

