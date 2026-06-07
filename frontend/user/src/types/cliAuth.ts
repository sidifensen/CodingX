/**
 * CLI loopback 授权请求，浏览器登录成功后用它向后端申请一次性授权码。
 */
export interface CliAuthorizePayload {
  state: string;
  redirectUri: string;
  codeChallenge: string;
}

/**
 * CLI loopback 授权码响应，前端只负责把 code 带回本机回调地址。
 */
export interface CliAuthorizeResponseData {
  code: string;
  state: string;
  expiresInSeconds: number;
}

/**
 * CLI 设备码授权请求，浏览器登录成功后把用户可读验证码绑定到当前账号。
 */
export interface CliDeviceAuthorizePayload {
  userCode: string;
}

