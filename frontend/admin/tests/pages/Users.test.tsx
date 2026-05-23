import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

import { AdminUserApi } from '@/api/adminUserApi';
import { Users } from '@/pages/Users';

vi.mock('@/api/adminUserApi', () => ({
  AdminUserApi: {
    listUsers: vi.fn(),
    approveUser: vi.fn(),
    updateUserStatus: vi.fn(),
  },
}));

describe('Users page', () => {
  beforeEach(() => {
    vi.mocked(AdminUserApi.listUsers).mockResolvedValue({
      records: [
        {
          id: '2001',
          username: 'alice',
          displayName: 'Alice',
          email: 'alice@codingx.io',
          phone: '13800000001',
          avatarUrl: 'https://example.com/a.png',
          userType: 'USER',
          userTypeLabel: '普通用户',
          status: 'PENDING',
          statusLabel: '待审核',
          lastLoginAt: null,
          createdAt: '2026-05-17T11:00:00',
        },
      ],
      total: 1,
      current: 1,
      size: 10,
      pages: 1,
    });
    vi.mocked(AdminUserApi.approveUser).mockResolvedValue(undefined);
    vi.mocked(AdminUserApi.updateUserStatus).mockResolvedValue(undefined);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  /**
   * 用户页应从后端加载数据并显示真实行。
   */
  it('loads users from backend api', async () => {
    render(
      <MemoryRouter>
        <Users />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Alice')).toBeInTheDocument();
    expect(screen.getByTestId('users-table-scroll')).toBeInTheDocument();
    expect(screen.getByTestId('users-table-summary')).toHaveTextContent('共 1 条');
    expect(AdminUserApi.listUsers).toHaveBeenCalledWith(expect.objectContaining({ current: 1, size: 10 }));
  });

  /**
   * 点击审核通过应调用后端审核接口并刷新列表。
   */
  it('approves pending user', async () => {
    render(
      <MemoryRouter>
        <Users />
      </MemoryRouter>,
    );

    await screen.findByText('Alice');
    fireEvent.click(screen.getByRole('button', { name: '审核通过' }));

    await waitFor(() => {
      expect(AdminUserApi.approveUser).toHaveBeenCalledWith('2001');
    });
  });
});
