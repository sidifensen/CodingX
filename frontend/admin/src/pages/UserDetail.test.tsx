import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { AdminUserApi } from '../api/adminUserApi';
import { UserDetail } from './UserDetail';

vi.mock('../api/adminUserApi', () => ({
  AdminUserApi: {
    getUserDetail: vi.fn(),
    updateUser: vi.fn(),
    resetPassword: vi.fn(),
    updateUserStatus: vi.fn(),
  },
}));

describe('UserDetail page', () => {
  beforeEach(() => {
    vi.mocked(AdminUserApi.getUserDetail).mockResolvedValue({
      id: '2001',
      username: 'alice',
      displayName: 'Alice',
      email: 'alice@codingx.io',
      phone: '13800000001',
      avatarUrl: 'https://example.com/a.png',
      userType: 'USER',
      userTypeLabel: '普通用户',
      status: 'ACTIVE',
      statusLabel: '正常',
      lastLoginAt: '2026-05-17T09:00:00',
      lastLoginIp: '127.0.0.1',
      createdAt: '2026-05-16T09:00:00',
      updatedAt: '2026-05-17T09:00:00',
    });
    vi.mocked(AdminUserApi.updateUser).mockResolvedValue(undefined);
    vi.mocked(AdminUserApi.resetPassword).mockResolvedValue(undefined);
    vi.mocked(AdminUserApi.updateUserStatus).mockResolvedValue(undefined);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  /**
   * 详情页应根据路由参数加载真实用户详情。
   */
  it('loads user detail by route id', async () => {
    render(
      <MemoryRouter initialEntries={['/users/2001']}>
        <Routes>
          <Route path="/users/:id" element={<UserDetail />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('Alice')).toBeInTheDocument();
    expect(AdminUserApi.getUserDetail).toHaveBeenCalledWith('2001');
  });

  /**
   * 重置密码交互应走自定义弹窗并触发后端接口。
   */
  it('resets password from custom dialog', async () => {
    render(
      <MemoryRouter initialEntries={['/users/2001']}>
        <Routes>
          <Route path="/users/:id" element={<UserDetail />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('Alice');
    fireEvent.click(screen.getByRole('button', { name: '重置密码' }));
    fireEvent.change(await screen.findByLabelText('新密码'), { target: { value: 'new-pass-123' } });
    fireEvent.click(screen.getByRole('button', { name: '确认重置' }));

    await waitFor(() => {
      expect(AdminUserApi.resetPassword).toHaveBeenCalledWith('2001', 'new-pass-123');
    });
  });
});