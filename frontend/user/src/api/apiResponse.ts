import { ApiResponseEnvelope } from '../types/auth';

/**
 * 统一解析后端响应并提取错误信息，确保前端优先展示后端返回的中文文案。
 */
export class ApiResponseParser {
  /**
   * 将响应体解析为统一包裹结构；当响应非 JSON 时兜底为可读文本错误。
   * @param response Fetch 响应对象。
   * @param fallbackMessage 兜底错误文案。
   * @returns 统一响应结构。
   */
  static async parseEnvelope<T>(
    response: Response,
    fallbackMessage: string,
  ): Promise<ApiResponseEnvelope<T>> {
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
        message: rawText || fallbackMessage,
        data: null as T,
      };
    }
  }

  /**
   * 根据统一响应判断是否失败，失败时抛出用于 UI 直接展示的错误。
   * @param response Fetch 响应对象。
   * @param envelope 统一响应结构。
   * @param fallbackMessage 兜底错误文案。
   */
  static assertSuccess<T>(
    response: Response,
    envelope: ApiResponseEnvelope<T>,
    fallbackMessage: string,
  ): void {
    if (!response.ok || !envelope.success) {
      throw new Error(envelope.message || fallbackMessage);
    }
  }
}
