import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { publishAdminAuthExpired } from '@/auth/authEvents';
import { AuthStorage } from '@/utils/authStorage';
import { AdminChatApi } from '@/api/adminChatApi';

vi.mock('@/utils/authStorage', () => ({
  AuthStorage: {
    getSession: vi.fn(),
  },
}));

vi.mock('@/auth/authEvents', () => ({
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

  /**
   * Dashboard 聚合接口应支持时间窗口查询参数并解析新结构。
   */
  it('requests dashboard view with window query', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            window: '7d',
            generatedAt: '2026-05-28T15:52:32',
            kpis: {
              activeUserCount: 12,
              conversationCount: 18,
              messageCount: 86,
              workspaceCount: 6,
              traceCount: 42,
              runningTraceCount: 5,
            },
            resources: {
              skillCount: 9,
              toolCount: 11,
              expertCount: 3,
              mcpCount: 4,
              intentNodeCount: 22,
              mappingCount: 7,
              sampleQuestionCount: 5,
            },
            performance: {
              successRate: 83.3,
              failureRate: 8.3,
              runningRate: 8.4,
              avgTraceDurationMs: 9200,
              p95TraceDurationMs: 15000,
            },
            trendBuckets: [
              {
                label: '05-22',
                bucketStart: '2026-05-22T00:00:00',
                conversationCount: 2,
                messageCount: 10,
                activeUserCount: 2,
                traceCount: 3,
                successCount: 2,
                failedCount: 1,
                avgDurationMs: 8400,
              },
            ],
          },
        }),
        { status: 200 },
      ),
    );

    const result = await AdminChatApi.getDashboard('7d');

    expect(result.window).toBe('7d');
    expect(result.kpis.activeUserCount).toBe(12);
    expect(result.resources.toolCount).toBe(11);
    expect(result.performance.p95TraceDurationMs).toBe(15000);
    expect(result.trendBuckets[0].label).toBe('05-22');
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/admin/chat/dashboard?window=7d');
  });

  /**
   * 工作空间列表接口应携带分页、关键字和运行目标筛选参数。
   */
  it('requests workspace list with keyword and runtime target query', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            records: [
              {
                id: 3001,
                name: '本地项目',
                runtimeTarget: 'local',
                runtimeTargetLabel: '本地',
                conversationCount: 2,
              },
            ],
            total: 1,
            size: 10,
            current: 2,
            pages: 3,
          },
        }),
        { status: 200 },
      ),
    );

    const result = await AdminChatApi.listWorkspaces({
      current: 2,
      size: 10,
      keyword: 'codingx',
      runtimeTarget: 'local',
    });

    expect(result.records[0].name).toBe('本地项目');
    expect(result.records[0].runtimeTargetLabel).toBe('本地');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const requestUrl = String(fetchMock.mock.calls[0][0]);
    expect(requestUrl).toContain('/api/admin/workspaces?');
    expect(requestUrl).toContain('current=2');
    expect(requestUrl).toContain('size=10');
    expect(requestUrl).toContain('keyword=codingx');
    expect(requestUrl).toContain('runtimeTarget=local');
  });

  /**
   * 工作空间详情页应通过空间子资源接口分页查询会话。
   */
  it('requests workspace conversations with pagination and keyword query', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            records: [
              {
                id: 2001,
                title: '本地项目会话',
                createdBy: 1002,
                status: 'ACTIVE',
                statusLabel: '活跃',
              },
            ],
            total: 1,
            size: 10,
            current: 1,
            pages: 1,
          },
        }),
        { status: 200 },
      ),
    );

    const result = await AdminChatApi.listWorkspaceConversations(3001, {
      current: 1,
      size: 10,
      keyword: '项目',
    });

    expect(result.records[0].title).toBe('本地项目会话');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const requestUrl = String(fetchMock.mock.calls[0][0]);
    expect(requestUrl).toContain('/api/admin/workspaces/3001/conversations?');
    expect(requestUrl).toContain('current=1');
    expect(requestUrl).toContain('size=10');
    expect(requestUrl).toContain('keyword=%E9%A1%B9%E7%9B%AE');
  });

  /**
   * 治理中心权限策略接口应命中治理模块专用路径，避免复用工具管理或系统配置接口。
   */
  it('requests governance permission policies through admin governance api', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: 1,
              policyCode: 'deny-dangerous-delete',
              policyName: '禁止危险删除',
              toolCode: 'shell_command',
              commandPattern: 'rm -rf',
              action: 'DENY',
              riskLevel: 'HIGH',
              enabled: 1,
              sortNo: 10,
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const result = await AdminChatApi.listPermissionPolicies();

    expect(result[0].policyCode).toBe('deny-dangerous-delete');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/admin/governance/permission-policies');
  });

  /**
   * Hook 只保留规则配置，前端 API 不应再暴露 Hook 审计列表请求。
   */
  it('does not expose hook audit list api after hook automation migration', () => {
    expect('listHookAudits' in AdminChatApi).toBe(false);
  });

  /**
   * 治理中心保存接口必须继续透传后端 ApiResponse.message，页面不应改写权限策略错误语义。
   */
  it('keeps backend message when governance policy save fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          code: 'BAD_REQUEST',
          message: '策略编码不能为空',
          data: null,
        }),
        { status: 400 },
      ),
    );

    await expect(
      AdminChatApi.createPermissionPolicy({
        policyCode: '',
        policyName: '空策略',
        action: 'DENY',
        riskLevel: 'HIGH',
        enabled: 1,
        sortNo: 0,
      }),
    ).rejects.toThrow('策略编码不能为空');
  });

  /**
   * 管理端 API 不再暴露项目画像扫描能力，避免前端调用已下线接口。
   */
  it('does not expose project profile governance api helpers', () => {
    expect('listProjectProfiles' in AdminChatApi).toBe(false);
    expect('scanProjectProfile' in AdminChatApi).toBe(false);
  });
});
