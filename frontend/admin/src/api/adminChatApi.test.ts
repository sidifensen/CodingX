import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { publishAdminAuthExpired } from '../auth/authEvents';
import { AuthStorage } from '../utils/authStorage';
import { AdminChatApi } from './adminChatApi';

vi.mock('../utils/authStorage', () => ({
  AuthStorage: {
    getSession: vi.fn(),
  },
}));

vi.mock('../auth/authEvents', () => ({
  publishAdminAuthExpired: vi.fn(),
}));

describe('AdminChatApi unauthorized handling', () => {
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
   * 401 未登录场景必须触发全局会话失效事件，交由认证层统一回收会话并跳转登录页。
   */
  it('publishes auth-expired event when admin api responds unauthorized', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          code: 'UNAUTHORIZED',
          message: '登录已失效，请重新登录',
          data: null,
        }),
        { status: 401 },
      ),
    );

    await expect(AdminChatApi.listSkills()).rejects.toThrow('登录已失效，请重新登录');
    expect(publishAdminAuthExpired).toHaveBeenCalledWith('登录已失效，请重新登录');
  });

  /**
   * 反馈列表接口应返回 records/total/current/size/pages 分页结构。
   */
  it('parses feedback list page payload', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            records: [{ id: 9001, messageId: 102, vote: 1 }],
            total: 1,
            size: 10,
            current: 1,
            pages: 1,
          },
        }),
        { status: 200 },
      ),
    );

    const result = await AdminChatApi.listFeedbacks();

    expect(result.total).toBe(1);
    expect(result.records).toHaveLength(1);
    expect(result.records[0].id).toBe(9001);
  });

  /**
   * 上传技能包时应以 multipart/form-data 提交，并携带 satoken。
   */
  it('uploads skill package as multipart payload', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            id: 7109,
            skillCode: 'pdf-processing',
            displayName: 'pdf-processing',
            sourceType: 'uploaded',
          },
        }),
        { status: 200 },
      ),
    );
    const file = new File(['dummy'], 'pdf-processing.skill', { type: 'application/octet-stream' });

    const result = await AdminChatApi.uploadSkillPackage(file, '文档处理');

    expect(result.skillCode).toBe('pdf-processing');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const call = fetchMock.mock.calls[0];
    const requestInit = call[1] as RequestInit;
    const requestHeaders = requestInit.headers as Headers;
    expect(requestHeaders.get('satoken')).toBe('expired-token');
    expect(requestHeaders.get('Content-Type')).toBeNull();
    expect(requestInit.body).toBeInstanceOf(FormData);
  });
});
