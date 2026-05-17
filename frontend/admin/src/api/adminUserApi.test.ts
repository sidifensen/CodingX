import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthStorage } from '../utils/authStorage';
import { AdminUserApi } from './adminUserApi';

vi.mock('../utils/authStorage', () => ({
  AuthStorage: {
    getSession: vi.fn(),
  },
}));

describe('AdminUserApi', () => {
  beforeEach(() => {
    vi.mocked(AuthStorage.getSession).mockReturnValue({
      token: 'admin-token',
      userId: '1001',
      username: 'admin',
      displayName: '管理员',
      userType: 'ADMIN',
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  /**
   * 分页查询应携带筛选参数并返回 records 数据。
   */
  it('requests paged users with status filter', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            records: [{ id: '2001', username: 'demo' }],
            total: 1,
            current: 1,
            size: 10,
            pages: 1,
          },
        }),
      ),
    );

    const result = await AdminUserApi.listUsers({ current: 1, size: 10, status: 'ACTIVE' });

    expect(result.records).toHaveLength(1);
    expect(fetch).toHaveBeenCalledWith('/api/admin/users?current=1&size=10&status=ACTIVE', expect.any(Object));
  });

  /**
   * 状态切换应调用对应后端动作接口。
   */
  it('updates user status', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: null,
        }),
      ),
    );

    await AdminUserApi.updateUserStatus('2001', 'DISABLED');

    expect(fetch).toHaveBeenCalledWith('/api/admin/users/2001/status', expect.objectContaining({ method: 'POST' }));
  });
});