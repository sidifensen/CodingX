import { useEffect, useState } from 'react';
import { AuthApi } from '../api/authApi';
import { LoginFormPayload, AuthSession } from '../types/auth';
import { AuthStorage } from '../utils/authStorage';

/**
 * 聚合登录、退出与会话恢复逻辑，向 UI 暴露可复用的认证状态与动作。
 */
export function useAuth() {
  // 步骤：维护当前登录会话状态，默认按未登录处理。
  const [session, setSession] = useState<AuthSession | null>(null);
  // 步骤：维护认证请求进行状态，用于按钮禁用与防重复提交。
  const [isSubmitting, setIsSubmitting] = useState(false);
  // 步骤：维护认证错误文案，统一交给 UI 渲染。
  const [errorMessage, setErrorMessage] = useState<string>('');

  useEffect(() => {
    // 步骤：初始化阶段读取本地会话，实现刷新后登录状态恢复。
    const cachedSession = AuthStorage.getSession();
    if (cachedSession) {
      setSession(cachedSession);
    }
  }, []);

  /**
   * 提交账号密码并建立前端登录会话。
   * @param payload 登录输入参数。
   */
  const login = async (payload: LoginFormPayload): Promise<void> => {
    // 步骤：输入校验，缺少账号或密码时直接返回错误提示。
    if (!payload.username.trim() || !payload.password.trim()) {
      setErrorMessage('请输入账号和密码');
      return;
    }

    // 步骤：进入请求状态并清空历史错误，准备发起登录调用。
    setIsSubmitting(true);
    setErrorMessage('');
    try {
      // 步骤：调用后端登录接口并构造本地会话对象。
      const loginResult = await AuthApi.login(payload);
      const nextSession: AuthSession = {
        token: loginResult.token,
        userId: loginResult.userId,
        username: loginResult.username,
        displayName: loginResult.displayName,
        userType: loginResult.userType,
      };

      // 步骤：写入本地存储并更新内存状态，让 UI 立即切换为已登录态。
      AuthStorage.setSession(nextSession);
      setSession(nextSession);
    } catch (error) {
      // 步骤：将接口异常转换为用户可读提示，避免抛出未捕获错误。
      setErrorMessage(error instanceof Error ? error.message : '登录失败，请稍后重试');
      throw error;
    } finally {
      // 步骤：请求结束后恢复可提交状态。
      setIsSubmitting(false);
    }
  };

  /**
   * 退出当前登录会话，并清理本地登录状态。
   */
  const logout = async (): Promise<void> => {
    // 步骤：无会话时直接幂等返回，避免无意义请求。
    if (!session) {
      return;
    }

    // 步骤：进入请求状态并清理错误提示，执行退出调用。
    setIsSubmitting(true);
    setErrorMessage('');
    try {
      // 步骤：通知后端注销会话，保障服务端登录状态同步失效。
      await AuthApi.logout(session.token);
    } finally {
      // 步骤：无论后端调用是否成功，前端都应本地退出，避免状态悬挂。
      AuthStorage.clearSession();
      setSession(null);
      setIsSubmitting(false);
    }
  };

  /**
   * 清理当前错误提示文案。
   */
  const clearErrorMessage = () => {
    // 步骤：供弹窗关闭或重新输入时重置错误状态。
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
