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
   * 技能列表接口应按 current/size 查询参数返回分页结构。
   */
  it('requests paged skills list with current and size query', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            records: [{ id: 7101, skillCode: 'conversation-core', displayName: '会话核心' }],
            total: 1,
            size: 10,
            current: 1,
            pages: 1,
          },
        }),
        { status: 200 },
      ),
    );

    const result = await AdminChatApi.listSkills({ current: 1, size: 10 });

    expect(result.records).toHaveLength(1);
    expect(result.total).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/admin/skills?current=1&size=10');
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

  /**
   * 上传目录文件时应以 files 字段携带相对路径文件名。
   */
  it('uploads directory files as multipart files payload', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            id: 7111,
            skillCode: 'meeting-notes',
            displayName: 'meeting-notes',
            sourceType: 'uploaded',
          },
        }),
        { status: 200 },
      ),
    );
    const fileA = new File(['manifest'], 'SKILL.md', { type: 'text/markdown' }) as File & { webkitRelativePath?: string };
    fileA.webkitRelativePath = 'meeting-notes/SKILL.md';
    const fileB = new File(['prompt'], 'prompt.txt', { type: 'text/plain' }) as File & { webkitRelativePath?: string };
    fileB.webkitRelativePath = 'meeting-notes/templates/prompt.txt';

    const result = await AdminChatApi.uploadSkillPackage(null, '文档处理', [fileA, fileB]);

    expect(result.skillCode).toBe('meeting-notes');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const call = fetchMock.mock.calls[0];
    const requestInit = call[1] as RequestInit;
    expect(requestInit.body).toBeInstanceOf(FormData);
  });

  /**
   * 迁移接口应返回总数、成功数与失败清单。
   */
  it('requests migrate skill packages summary', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            total: 10,
            migrated: 8,
            skipped: 1,
            failures: [{ skillCode: 'legacy-skill', reason: '下载失败' }],
          },
        }),
        { status: 200 },
      ),
    );

    const result = await AdminChatApi.migrateSkillPackages();

    expect(result.total).toBe(10);
    expect(result.migrated).toBe(8);
    expect(result.failures[0].skillCode).toBe('legacy-skill');
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/admin/skills/migrate-packages');
  });

  /**
   * 技能包文件内容接口应按 query 参数传递归档内路径。
   */
  it('requests skill package file content with encoded path query', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            path: 'templates/prompt.txt',
            content: 'prompt-body',
            truncated: false,
          },
        }),
        { status: 200 },
      ),
    );

    const response = await AdminChatApi.getSkillPackageFileContent(7110, 'templates/prompt.txt');

    expect(response.path).toBe('templates/prompt.txt');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const requestUrl = String(fetchMock.mock.calls[0][0]);
    expect(requestUrl).toContain('/api/admin/skills/7110/package/file-content?');
    expect(requestUrl).toContain('path=templates%2Fprompt.txt');
  });

  /**
   * 聊天运行时观测接口应返回队列与线程池双视图。
   */
  it('requests chat runtime dashboard snapshot', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            queue: {
              mode: 'redis',
              maxConcurrent: 2,
              activeCount: 1,
              waitingCount: 3,
              availablePermits: 1,
            },
            executor: {
              streamActiveCount: 1,
              streamPoolSize: 2,
              streamQueueSize: 3,
              streamQueueRemainingCapacity: 253,
              searchActiveCount: 2,
              searchPoolSize: 4,
              searchQueueSize: 0,
              searchQueueRemainingCapacity: 256,
            },
          },
        }),
        { status: 200 },
      ),
    );

    const result = await AdminChatApi.getRuntimeDashboard();

    expect(result.queue.mode).toBe('redis');
    expect(result.queue.waitingCount).toBe(3);
    expect(result.executor.streamQueueSize).toBe(3);
    expect(result.executor.searchPoolSize).toBe(4);
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/admin/chat/runtime');
  });
});
