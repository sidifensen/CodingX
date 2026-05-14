import { AUTH_SESSION_STORAGE_KEY } from '../constants/auth';
import { AuthSession } from '../types/auth';

/**
 * 提供登录会话的本地读写能力，封装 localStorage 细节并隔离异常影响范围。
 */
export class AuthStorage {
  /**
   * 从本地读取并反序列化登录会话。
   * @returns 已保存的会话信息，若不存在或解析失败则返回 null。
   */
  static getSession(): AuthSession | null {
    // 步骤：读取浏览器本地会话原始字符串。
    const rawValue = window.localStorage.getItem(AUTH_SESSION_STORAGE_KEY);
    if (!rawValue) {
      return null;
    }

    try {
      // 步骤：解析 JSON 并做最小结构校验，避免脏数据污染运行态。
      const parsedValue = JSON.parse(rawValue) as Partial<AuthSession>;
      if (
        typeof parsedValue.token !== 'string' ||
        typeof parsedValue.username !== 'string' ||
        typeof parsedValue.displayName !== 'string' ||
        typeof parsedValue.userType !== 'string' ||
        typeof parsedValue.userId !== 'number'
      ) {
        window.localStorage.removeItem(AUTH_SESSION_STORAGE_KEY);
        return null;
      }
      return parsedValue as AuthSession;
    } catch {
      // 步骤：解析异常时主动清理坏数据，确保后续流程回到未登录态。
      window.localStorage.removeItem(AUTH_SESSION_STORAGE_KEY);
      return null;
    }
  }

  /**
   * 持久化登录会话。
   * @param session 待保存的会话对象。
   */
  static setSession(session: AuthSession): void {
    // 步骤：序列化并写入 localStorage，供刷新恢复登录态使用。
    window.localStorage.setItem(AUTH_SESSION_STORAGE_KEY, JSON.stringify(session));
  }

  /**
   * 清理本地登录会话。
   */
  static clearSession(): void {
    // 步骤：删除本地会话键，确保界面回退为未登录态。
    window.localStorage.removeItem(AUTH_SESSION_STORAGE_KEY);
  }
}
