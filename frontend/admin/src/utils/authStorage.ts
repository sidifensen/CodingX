import { ADMIN_AUTH_SESSION_STORAGE_KEY } from '../constants/auth';
import { AdminAuthSession } from '../types/auth';

/**
 * 提供管理端登录会话的本地读写能力，封装 localStorage 细节并隔离异常影响范围。
 */
export class AuthStorage {
  /**
   * 从本地读取并反序列化登录会话。
   * @returns 已保存的会话信息，若不存在或解析失败则返回 null。
   */
  static getSession(): AdminAuthSession | null {
    const rawValue = window.localStorage.getItem(ADMIN_AUTH_SESSION_STORAGE_KEY);
    if (!rawValue) {
      return null;
    }

    try {
      const parsedValue = JSON.parse(rawValue) as Partial<AdminAuthSession>;
      const normalizedUserId = normalizeUserId(parsedValue.userId);
      if (
        typeof parsedValue.token !== 'string' ||
        typeof parsedValue.username !== 'string' ||
        typeof parsedValue.displayName !== 'string' ||
        typeof parsedValue.userType !== 'string' ||
        normalizedUserId == null
      ) {
        window.localStorage.removeItem(ADMIN_AUTH_SESSION_STORAGE_KEY);
        return null;
      }
      return {
        token: parsedValue.token,
        userId: normalizedUserId,
        username: parsedValue.username,
        displayName: parsedValue.displayName,
        userType: parsedValue.userType,
      } satisfies AdminAuthSession;
    } catch {
      window.localStorage.removeItem(ADMIN_AUTH_SESSION_STORAGE_KEY);
      return null;
    }
  }

  /**
   * 持久化登录会话。
   * @param session 待保存的会话对象。
   */
  static setSession(session: AdminAuthSession): void {
    window.localStorage.setItem(ADMIN_AUTH_SESSION_STORAGE_KEY, JSON.stringify(session));
  }

  /**
   * 清理本地登录会话。
   */
  static clearSession(): void {
    window.localStorage.removeItem(ADMIN_AUTH_SESSION_STORAGE_KEY);
  }
}

/**
 * 兼容 number 与 string 两种 userId 形态，统一转成字符串避免校验分歧。
 * @param rawUserId 原始 userId 值。
 * @returns 归一化后的字符串 userId，非法时返回 null。
 */
function normalizeUserId(rawUserId: unknown): string | null {
  if (typeof rawUserId === 'string' && rawUserId.trim()) {
    return rawUserId;
  }
  if (typeof rawUserId === 'number' && Number.isFinite(rawUserId)) {
    return String(rawUserId);
  }
  return null;
}
