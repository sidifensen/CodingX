import '@testing-library/jest-dom/vitest';

import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import App from './App';

const useAdminAuthMock = vi.fn();

vi.mock('./hooks/useAdminAuth', () => ({
  useAdminAuth: () => useAdminAuthMock(),
}));

describe('Admin App auth guard', () => {
  beforeEach(() => {
    useAdminAuthMock.mockReset();
  });

  /**
   * 未登录访问受保护路由时必须直接停留登录页，不能渲染后台内容。
   */
  it('blocks protected route and only renders login page when unauthenticated', () => {
    window.history.pushState({}, '', '/skills');

    useAdminAuthMock.mockReturnValue({
      isAuthenticated: false,
      isSubmitting: false,
      errorMessage: '',
      isBootstrapping: false,
      login: vi.fn(),
      logout: vi.fn(),
      clearErrorMessage: vi.fn(),
    });

    render(<App />);

    expect(screen.getByRole('heading', { name: 'CodingX 管理端登录' })).toBeInTheDocument();
    expect(screen.queryByText('技能管理 (Skills)')).not.toBeInTheDocument();
  });

  /**
   * 登录失效时应在登录页显示全局提示，明确告知需要重新登录。
   */
  it('renders global auth notice when auth expired message exists', () => {
    window.history.pushState({}, '', '/login');

    useAdminAuthMock.mockReturnValue({
      isAuthenticated: false,
      isSubmitting: false,
      errorMessage: '登录已失效，请重新登录',
      isBootstrapping: false,
      login: vi.fn(),
      logout: vi.fn(),
      clearErrorMessage: vi.fn(),
    });

    render(<App />);

    expect(screen.getByRole('alert')).toHaveTextContent('登录已失效，请重新登录');
  });
});
