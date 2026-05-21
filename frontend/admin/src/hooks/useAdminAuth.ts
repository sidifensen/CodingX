import { useCallback, useEffect, useRef, useState } from 'react';
import { AuthApi } from '../api/authApi';
import { subscribeAdminAuthExpired } from '../auth/authEvents';
import { AdminErrorMessages } from '../constants/errorMessages';
import { AdminAuthSession, AdminLoginFormPayload } from '../types/auth';
import { AuthStorage } from '../utils/authStorage';

/**
 * 聚合管理端登录、退出与会话恢复逻辑，并强制限制为管理员账号。
 */
export function useAdminAuth() {
  const [session, setSession] = useState<AdminAuthSession | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isBootstrapping, setIsBootstrapping] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string>('');
  const hasHandledAuthExpiredRef = useRef(false);

  /**
   * 统一处理会话失效：清理本地会话、回收登录态并输出可展示文案。
   * @param message 后端返回的错误文案，缺失时使用统一兜底提示。
   */
  const handleAuthExpired = useCallback((message?: string) => {
    const normalizedMessage = message?.trim() || AdminErrorMessages.AUTH_SESSION_EXPIRED;

    // 步骤：会话失效处理允许重复进入，但所有路径都走同一清理分支，避免状态分叉。
    AuthStorage.clearSession();
    hasHandledAuthExpiredRef.current = true;
    setSession(null);
    setErrorMessage((previous) => (previous === normalizedMessage ? previous : normalizedMessage));
  }, []);

  useEffect(() => {
    let isMounted = true;

    /**
     * 启动阶段先做服务端会话校验，阻止失效 token 进入管理页。
     */
    const bootstrapAdminSession = async () => {
      const cachedSession = AuthStorage.getSession();
      if (!cachedSession || String(cachedSession.userType || '').toUpperCase() !== 'ADMIN') {
        AuthStorage.clearSession();
        if (isMounted) {
          setSession(null);
          setIsBootstrapping(false);
        }
        return;
      }

      try {
        await AuthApi.me(cachedSession.token);
        if (!isMounted) {
          return;
        }
        hasHandledAuthExpiredRef.current = false;
        setSession(cachedSession);
      } catch (error) {
        if (!isMounted) {
          return;
        }
        const message = error instanceof Error ? error.message : AdminErrorMessages.AUTH_SESSION_EXPIRED;
        handleAuthExpired(message);
      } finally {
        if (isMounted) {
          setIsBootstrapping(false);
        }
      }
    };

    void bootstrapAdminSession();

    return () => {
      isMounted = false;
    };
  }, [handleAuthExpired]);

  useEffect(() => {
    // 步骤：监听 API 层会话失效事件，统一执行清理与提示，避免各页面重复处理 401。
    return subscribeAdminAuthExpired((message) => {
      if (hasHandledAuthExpiredRef.current && !session) {
        return;
      }
      handleAuthExpired(message);
    });
  }, [handleAuthExpired, session]);

  /**
   * 提交账号密码并建立管理端登录会话。
   * @param payload 登录输入参数。
   */
  const login = async (payload: AdminLoginFormPayload): Promise<void> => {
    if (!payload.username.trim() || !payload.password.trim()) {
      setErrorMessage(AdminErrorMessages.AUTH_CREDENTIALS_REQUIRED);
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
        throw new Error(AdminErrorMessages.AUTH_ADMIN_ONLY);
      }

      const nextSession: AdminAuthSession = {
        token: loginResult.token,
        userId: String(loginResult.userId),
        username: loginResult.username,
        displayName: loginResult.displayName,
        userType: normalizedUserType,
      };

      AuthStorage.setSession(nextSession);
      hasHandledAuthExpiredRef.current = false;
      setSession(nextSession);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : AdminErrorMessages.AUTH_LOGIN_FAILED_RETRY);
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
      hasHandledAuthExpiredRef.current = false;
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
    isBootstrapping,
    errorMessage,
    login,
    logout,
    clearErrorMessage,
  };
}
