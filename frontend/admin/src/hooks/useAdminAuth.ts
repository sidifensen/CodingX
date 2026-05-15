import { useEffect, useState } from 'react';
import { AuthApi } from '../api/authApi';
import { AdminAuthSession, AdminLoginFormPayload } from '../types/auth';
import { AuthStorage } from '../utils/authStorage';

/**
 * 聚合管理端登录、退出与会话恢复逻辑，并强制限制为管理员账号。
 */
export function useAdminAuth() {
  const [session, setSession] = useState<AdminAuthSession | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string>('');

  useEffect(() => {
    const cachedSession = AuthStorage.getSession();
    if (cachedSession && String(cachedSession.userType || '').toUpperCase() === 'ADMIN') {
      setSession(cachedSession);
      return;
    }
    AuthStorage.clearSession();
  }, []);

  /**
   * 提交账号密码并建立管理端登录会话。
   * @param payload 登录输入参数。
   */
  const login = async (payload: AdminLoginFormPayload): Promise<void> => {
    if (!payload.username.trim() || !payload.password.trim()) {
      setErrorMessage('请输入账号和密码');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage('');
    try {
      const loginResult = await AuthApi.login(payload);
      const normalizedUserType = String(loginResult.userType || '').toUpperCase();

      // 步骤：管理端仅允许管理员进入，普通用户即使凭证正确也立即拒绝并销毁服务端会话。
      if (normalizedUserType !== 'ADMIN') {
        try {
          if (loginResult.token) {
            await AuthApi.logout(loginResult.token);
          }
        } catch {
          // 步骤：非管理员场景下退出失败不阻断主错误抛出，避免覆盖权限提示。
        }
        throw new Error('仅管理员账号可登录管理端');
      }

      const nextSession: AdminAuthSession = {
        token: loginResult.token,
        userId: String(loginResult.userId),
        username: loginResult.username,
        displayName: loginResult.displayName,
        userType: normalizedUserType,
      };

      AuthStorage.setSession(nextSession);
      setSession(nextSession);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '登录失败，请稍后重试');
      throw error;
    } finally {
      setIsSubmitting(false);
    }
  };

  /**
   * 退出当前登录会话，并清理本地登录状态。
   */
  const logout = async (): Promise<void> => {
    if (!session) {
      return;
    }

    setIsSubmitting(true);
    setErrorMessage('');
    try {
      await AuthApi.logout(session.token);
    } finally {
      AuthStorage.clearSession();
      setSession(null);
      setIsSubmitting(false);
    }
  };

  /**
   * 清理当前错误提示文案。
   */
  const clearErrorMessage = () => {
    setErrorMessage('');
  };

  return {
    session,
    isAuthenticated: Boolean(session),
    isSubmitting,
    errorMessage,
    login,
    logout,
    clearErrorMessage,
  };
}
