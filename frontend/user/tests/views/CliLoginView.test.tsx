import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import CliLoginView from '@/views/CliLoginView';

/**
 * 验证 CLI 浏览器登录页的 loopback 授权、设备码授权和错误展示。
 */
describe('CliLoginView', () => {
  /**
   * 跳转测试替身，避免 JSDOM 真正执行 location.assign。
   */
  const navigateMock = vi.fn();

  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
    navigateMock.mockReset();
  });

  afterEach(() => {
    cleanup();
    document.documentElement.classList.remove('dark');
  });

  /**
   * CLI 登录页绕过主应用壳层时，也必须初始化默认暗色主题。
   */
  it('initializes dark theme when rendered as standalone route', () => {
    window.history.replaceState(
      {},
      '',
      '/cli-login?redirectUri=http%3A%2F%2F127.0.0.1%3A49152%2Fcallback&state=state-1&codeChallenge=challenge-1',
    );

    render(<CliLoginView navigate={navigateMock} />);

    expect(document.documentElement).toHaveClass('dark');
  });

  /**
   * 未登录用户在 loopback 模式下输入账号密码后，应先登录再创建授权码并跳回 CLI 本机回调地址。
   */
  it('logs in and redirects to CLI callback with authorization code', async () => {
    window.history.replaceState(
      {},
      '',
      '/cli-login?redirectUri=http%3A%2F%2F127.0.0.1%3A49152%2Fcallback&state=state-1&codeChallenge=challenge-1',
    );
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url === '/api/auth/login') {
        return jsonResponse({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            token: 'web-token',
            userId: '1002',
            username: 'demo',
            displayName: '演示用户',
            userType: 'USER',
          },
        });
      }
      if (url === '/api/auth/cli/authorize') {
        expect((init?.headers as Record<string, string>).satoken).toBe('web-token');
        expect(JSON.parse(String(init?.body))).toEqual({
          redirectUri: 'http://127.0.0.1:49152/callback',
          state: 'state-1',
          codeChallenge: 'challenge-1',
        });
        return jsonResponse({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            code: 'cli-code-1',
            state: 'state-1',
            expiresInSeconds: 120,
          },
        });
      }
      throw new Error(`Unhandled fetch in CliLoginView loopback test: ${url}`);
    });

    render(<CliLoginView navigate={navigateMock} />);

    fireEvent.change(screen.getByLabelText('账号'), { target: { value: 'demo' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'password' } });
    fireEvent.click(screen.getByRole('button', { name: '登录并授权' }));

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith(
        'http://127.0.0.1:49152/callback?code=cli-code-1&state=state-1',
      );
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(window.localStorage.getItem('codingx.auth.session')).toContain('web-token');
  });

  /**
   * 已登录用户在设备码模式下不需要重复输入账号密码，确认后应授权设备并停留在完成页。
   */
  it('authorizes device code for existing authenticated session', async () => {
    window.history.replaceState({}, '', '/cli-login?deviceCode=ABCD-EFGH');
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'web-token',
        userId: '1002',
        username: 'demo',
        displayName: '演示用户',
        userType: 'USER',
      }),
    );
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url === '/api/auth/me') {
        return jsonResponse({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            userId: '1002',
            username: 'demo',
            displayName: '演示用户',
            userType: 'USER',
          },
        });
      }
      if (url === '/api/auth/cli/device/authorize') {
        expect((init?.headers as Record<string, string>).satoken).toBe('web-token');
        expect(JSON.parse(String(init?.body))).toEqual({ userCode: 'ABCD-EFGH' });
        return jsonResponse({
          success: true,
          code: 'OK',
          message: 'CLI 授权完成，请回到终端继续使用',
          data: null,
        });
      }
      throw new Error(`Unhandled fetch in CliLoginView device test: ${url}`);
    });

    render(<CliLoginView navigate={navigateMock} />);

    expect(await screen.findByText('演示用户')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '授权终端' }));

    expect(await screen.findByText('授权完成，请回到终端')).toBeInTheDocument();
    expect(navigateMock).not.toHaveBeenCalled();
  });

  /**
   * 后端返回失败时页面应展示 ApiResponse.message，而不是自行改写错误语义。
   */
  it('shows backend message when CLI authorization fails', async () => {
    window.history.replaceState(
      {},
      '',
      '/cli-login?redirectUri=http%3A%2F%2F127.0.0.1%3A49152%2Fcallback&state=state-1&codeChallenge=challenge-1',
    );
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'web-token',
        userId: '1002',
        username: 'demo',
        displayName: '演示用户',
        userType: 'USER',
      }),
    );
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/auth/me') {
        return jsonResponse({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            userId: '1002',
            username: 'demo',
            displayName: '演示用户',
            userType: 'USER',
          },
        });
      }
      if (url === '/api/auth/cli/authorize') {
        return jsonResponse(
          {
            success: false,
            code: 'CLI_AUTH_REDIRECT_INVALID',
            message: 'CLI 登录只允许使用本机回调地址',
            data: null,
          },
          400,
        );
      }
      throw new Error(`Unhandled fetch in CliLoginView error test: ${url}`);
    });

    render(<CliLoginView navigate={navigateMock} />);

    fireEvent.click(await screen.findByRole('button', { name: '授权终端' }));

    expect(await screen.findByText('CLI 登录只允许使用本机回调地址')).toBeInTheDocument();
    expect(navigateMock).not.toHaveBeenCalled();
  });
});

/**
 * 构造统一 ApiResponse JSON 响应。
 * @param payload 响应体。
 * @param status HTTP 状态码。
 * @returns Fetch Response。
 */
function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  });
}
