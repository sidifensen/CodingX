const DEFAULT_CODINGX_API_BASE_URL = 'http://localhost:5001';

/**
 * 默认后端 API 地址；生产打包内置页面没有 Vite 代理时通过该地址访问 Spring Boot。
 */
export { DEFAULT_CODINGX_API_BASE_URL };

/**
 * 规整后端 API 基址，避免环境变量缺失或尾部斜杠导致请求改写不稳定。
 *
 * @param apiBaseUrl 环境变量中的后端地址，可为空。
 * @returns 去掉尾部斜杠后的后端地址。
 */
export function normalizeApiBaseUrl(apiBaseUrl?: string | null): string {
  const normalized = (apiBaseUrl ?? '').trim();
  return (normalized || DEFAULT_CODINGX_API_BASE_URL).replace(/\/+$/, '');
}

/**
 * 将打包态内置 file 页面发出的 `/api` 请求改写到后端服务。
 *
 * @param requestUrl Electron 捕获到的原始请求地址。
 * @param apiBaseUrl 后端 API 基址。
 * @returns 需要重定向的后端 URL；非 file 或非 `/api` 请求返回 null。
 */
export function resolvePackagedApiRedirectUrl(
  requestUrl: string,
  apiBaseUrl?: string | null,
): string | null {
  let parsedUrl: URL;
  try {
    parsedUrl = new URL(requestUrl);
  } catch {
    return null;
  }
  if (parsedUrl.protocol !== 'file:') {
    return null;
  }

  const decodedPath = decodeURIComponent(parsedUrl.pathname).replace(/\\/g, '/');
  const apiPathStart = decodedPath.toLowerCase().indexOf('/api/');
  if (apiPathStart < 0) {
    return null;
  }

  const apiPath = decodedPath.slice(apiPathStart);
  return `${normalizeApiBaseUrl(apiBaseUrl)}${apiPath}${parsedUrl.search}`;
}
