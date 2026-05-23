import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthApi } from '@/api/authApi';
import { AuthStorage } from '@/utils/authStorage';
import { useAdminAuth } from '@/hooks/useAdminAuth';

vi.mock('@/api/authApi', () => ({
  AuthApi: {
    login: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
  },
}));

vi.mock('@/utils/authStorage', () => ({
  AuthStorage: {
    getSession: vi.fn(),
    setSession: vi.fn(),
    clearSession: vi.fn(),
  },
}));

describe('useAdminAuth bootstrap guard', () => {
  beforeEach(() => {
    vi.mocked(AuthStorage.getSession).mockReturnValue({
      token: 'expired-token',
      userId: '1',
      username: 'admin',
      displayName: '管理员',
      userType: 'ADMIN',
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  /**
   * 启动时发现本地会话失效应立即清理认证状态并给出统一提示，避免继续渲染管理页。
   */
  it('clears invalid session and exposes auth-expired message when bootstrap me check fails', async () => {
    vi.mocked(AuthApi.me).mockRejectedValue(new Error('登录已失效，请重新登录'));

    const { result } = renderHook(() => useAdminAuth());

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    expect(AuthStorage.clearSession).toHaveBeenCalledTimes(1);
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.errorMessage).toBe('登录已失效，请重新登录');
  });

  /**
   * 登录失效提示应支持手动清理，避免阻塞后续重新登录操作。
   */
  it('clears error message by explicit action', async () => {
    vi.mocked(AuthApi.me).mockRejectedValue(new Error('登录已失效，请重新登录'));
    const { result } = renderHook(() => useAdminAuth());

    await waitFor(() => {
      expect(result.current.errorMessage).toBe('登录已失效，请重新登录');
    });

    act(() => {
      result.current.clearErrorMessage();
    });

    expect(result.current.errorMessage).toBe('');
  });
});
