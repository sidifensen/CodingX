import { act, renderHook, waitFor } from '@testing-library/react';
import { buildStreamRequestUrl, useChatWorkspace } from '@/views/chat/useChatWorkspace';

/**
 * 验证聊天工作区初始化策略，避免技能在首次进入时被默认全选。
 */
describe('useChatWorkspace', () => {
  beforeEach(() => {
    window.localStorage.clear();
    // 关键约束：会话恢复逻辑强依赖 URL 参数，测试间必须清理地址栏避免互相污染。
    window.history.replaceState(window.history.state, '', '/');
    vi.restoreAllMocks();
  });

  /**
   * 启动后应仅拉取技能列表，不应把全部技能自动写入已选状态。
   */
  it('应在启动后默认保持技能未选中', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              { id: 7101, skillCode: 'sales_query', displayName: '销售查询' },
              { id: 7102, skillCode: 'ticket_query', displayName: '工单查询' },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in useChatWorkspace test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    expect(result.current.availableSkills).toHaveLength(2);
    expect(result.current.selectedSkillCodes).toEqual([]);
    expect(result.current.currentSkills).toEqual([]);
    expect(result.current.currentMcps).toEqual([]);
  });

  /**
   * 会话列表应根据后台任务完成时间与本地已读时间恢复完成提醒圆点。
   */
  it('应根据任务完成时间恢复会话完成提醒', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'task-conversation-1',
                title: '后台完成会话',
                status: 'ACTIVE',
                lastTaskId: 'task-1',
                lastTaskStatus: 'SUCCEEDED',
                lastTaskFinishedAt: '2026-05-25 10:00:00',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/active-task-conversation/messages' ||
        url === '/api/chat/conversations/active-task-conversation/steps' ||
        url === '/api/chat/conversations/active-task-conversation/references' ||
        url === '/api/chat/conversations/active-task-conversation/artifacts' ||
        url === '/api/chat/conversations/active-task-conversation/current-experts' ||
        url === '/api/chat/conversations/active-task-conversation/current-skills' ||
        url === '/api/chat/conversations/active-task-conversation/current-mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in task reminder hydration test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    expect(result.current.conversations[0]).toEqual(
      expect.objectContaining({
        id: 'task-conversation-1',
        hasUnreadTaskCompletion: true,
      }),
    );
  });

  /**
   * 打开带完成提醒的会话后，应立刻清除圆点并持久化已读完成时间。
   */
  it('应在打开会话后清除任务完成提醒', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    const finishedAt = '2026-05-25 10:00:00';

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'task-conversation-2',
                title: '待查看会话',
                status: 'ACTIVE',
                lastTaskId: 'task-2',
                lastTaskStatus: 'SUCCEEDED',
                lastTaskFinishedAt: finishedAt,
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/task-conversation-2/messages' ||
        url === '/api/chat/conversations/task-conversation-2/steps' ||
        url === '/api/chat/conversations/task-conversation-2/references' ||
        url === '/api/chat/conversations/task-conversation-2/artifacts' ||
        url === '/api/chat/conversations/task-conversation-2/current-experts' ||
        url === '/api/chat/conversations/task-conversation-2/current-skills' ||
        url === '/api/chat/conversations/task-conversation-2/current-mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in task reminder clear test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    expect(result.current.conversations[0]?.hasUnreadTaskCompletion).toBe(true);

    await act(async () => {
      await result.current.selectConversation('task-conversation-2', result.current.conversations);
    });

    expect(result.current.conversations[0]?.hasUnreadTaskCompletion).toBe(false);
    const snapshotStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    expect(
      snapshotStore.snapshots['cloud::__no_workspace__'].seenTaskFinishedAtByConversationId[
        'task-conversation-2'
      ],
    ).toBe(finishedAt);
  });

  /**
   * 后端返回未读提醒时，打开会话应调用服务端已读接口并立即清除本地圆点。
   */
  it('应在打开后端未读会话后提交任务完成提醒已读状态', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    const finishedAt = '2026-05-25 10:00:00';
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'task-conversation-db',
                title: '服务端未读会话',
                status: 'ACTIVE',
                lastTaskId: 'task-db',
                lastTaskStatus: 'SUCCEEDED',
                lastTaskFinishedAt: finishedAt,
                taskCompletionRead: false,
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/task-conversation-db/task-completion-read') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: null }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/task-conversation-db/messages' ||
        url === '/api/chat/conversations/task-conversation-db/steps' ||
        url === '/api/chat/conversations/task-conversation-db/references' ||
        url === '/api/chat/conversations/task-conversation-db/artifacts' ||
        url === '/api/chat/conversations/task-conversation-db/current-experts' ||
        url === '/api/chat/conversations/task-conversation-db/current-skills' ||
        url === '/api/chat/conversations/task-conversation-db/current-mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in db task reminder read test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    expect(result.current.conversations[0]?.hasUnreadTaskCompletion).toBe(true);

    await act(async () => {
      await result.current.selectConversation('task-conversation-db', result.current.conversations);
    });

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/task-conversation-db/task-completion-read',
      expect.objectContaining({
        method: 'PATCH',
      }),
    );
    expect(result.current.conversations[0]?.taskCompletionRead).toBe(true);
    expect(result.current.conversations[0]?.hasUnreadTaskCompletion).toBe(false);
  });

  /**
   * 当前打开的会话任务完成时，应自动记录已读完成时间，避免切走后再次误提示。
   */
  it('当前会话任务完成时应自动标记完成提醒已读', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    const finishedAt = '2026-05-25 10:00:00';
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__no_workspace__': {
            runtimeTarget: 'cloud',
            workspacePath: null,
            workspaceLabel: '云端历史',
            lastOpenedAt: Date.now(),
            activeConversationId: 'active-task-conversation',
            conversations: [
              {
                id: 'active-task-conversation',
                title: '当前会话',
                status: 'ACTIVE',
              },
            ],
            conversationRecords: {},
            seenTaskFinishedAtByConversationId: {},
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'active-task-conversation',
                title: '当前会话',
                status: 'ACTIVE',
                lastTaskId: 'task-3',
                lastTaskStatus: 'SUCCEEDED',
                lastTaskFinishedAt: finishedAt,
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/active-task-conversation/messages' ||
        url === '/api/chat/conversations/active-task-conversation/steps' ||
        url === '/api/chat/conversations/active-task-conversation/references' ||
        url === '/api/chat/conversations/active-task-conversation/artifacts' ||
        url === '/api/chat/conversations/active-task-conversation/current-experts' ||
        url === '/api/chat/conversations/active-task-conversation/current-skills' ||
        url === '/api/chat/conversations/active-task-conversation/current-mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in active task reminder test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    expect(result.current.conversations[0]).toEqual(
      expect.objectContaining({
        id: 'active-task-conversation',
        hasUnreadTaskCompletion: false,
      }),
    );
    const snapshotStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    expect(
      snapshotStore.snapshots['cloud::__no_workspace__'].seenTaskFinishedAtByConversationId[
        'active-task-conversation'
      ],
    ).toBe(finishedAt);
  });

  /**
   * MCP 列表包含不可用项时，应默认只选中可用项，并阻止手动写入不可用编码。
   */
  it('应在工作区状态中过滤不可用MCP', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills' || url === '/api/chat/experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              { id: 7001, mcpCode: 'sales_query', displayName: '销售查询', enabled: 1, available: true },
              { id: 7002, mcpCode: 'weather_query', displayName: '天气查询', enabled: 1, available: false },
            ],
          }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in unavailable mcp filter test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    expect(result.current.availableMcps).toHaveLength(2);
    expect(result.current.selectedMcpCodes).toEqual(['sales_query']);
    expect(result.current.mcpConnected).toBe(true);

    await act(async () => {
      result.current.setSelectedMcpCodes(['sales_query', 'weather_query']);
    });
    expect(result.current.selectedMcpCodes).toEqual(['sales_query']);
  });

  /**
   * 首次进入且 URL 未指定会话时应停留首页，避免自动跳转到最新会话破坏新建态体验。
   */
  it('应在首次进入且未指定conversationId时保持首页不自动选中会话', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.history.replaceState(window.history.state, '', '/');

    const requestUrls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      requestUrls.push(url);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '6001',
                title: '最新会话',
                status: 'ACTIVE',
                lastRunId: '9101',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/6001/messages' ||
        url === '/api/chat/conversations/6001/steps' ||
        url === '/api/chat/conversations/6001/references' ||
        url === '/api/chat/conversations/6001/artifacts' ||
        url === '/api/chat/conversations/6001/current-experts' ||
        url === '/api/chat/conversations/6001/current-skills' ||
        url === '/api/chat/conversations/6001/current-mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in homepage bootstrap test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    expect(result.current.activeConversationId).toBeNull();
    expect(result.current.messages).toEqual([]);
    expect(window.location.search).not.toContain('conversationId=');
    expect(requestUrls).not.toContain('/api/chat/conversations/6001/messages');
  });

  /**
   * 选中历史记录时应同步加载当前技能与当前 MCP 绑定，供右栏展示会话上下文。
   */
  it('应在选中会话时加载当前技能与当前MCP', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now(),
            activeConversationId: '2001',
            conversations: [
              {
                id: '2001',
                title: 'Default Demo Conversation',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
            conversationRecords: {
              '2001': {
                owned: true,
                messages: [],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: 'Default Demo Conversation',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/messages') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/steps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/references') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/artifacts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/current-skills') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              { id: 7101, skillCode: 'sales_query', displayName: '销售查询', category: '销售' },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/current-experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/current-mcps') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [{ id: 7001, mcpCode: 'sales_query', displayName: '销售查询', category: '检索' }],
          }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in useChatWorkspace current bindings test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      await result.current.selectConversationInWorkspace('2001', {
        partitionKey: 'cloud::__no_workspace__',
        runtimeTarget: 'cloud',
        workspacePath: null,
      });
    });

    await waitFor(() => {
      expect(result.current.currentSkills).toEqual([
        expect.objectContaining({ skillCode: 'sales_query', displayName: '销售查询' }),
      ]);
      expect(result.current.currentMcps).toEqual([
        expect.objectContaining({ mcpCode: 'sales_query', displayName: '销售查询' }),
      ]);
    });
  });

  /**
   * 本地工作空间只从本机快照恢复会话，不再把远端会话列表并入本地分组。
   */
  it('应在本地工作空间分组中仅展示本机快照会话', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'local::d:/code/workspace-a': {
            workspacePath: 'D:/code/workspace-a',
            workspaceLabel: 'workspace-a',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now(),
            activeConversationId: null,
            conversations: [
              {
                id: 'local-3001',
                title: 'A 工作空间本地会话',
                status: 'ACTIVE',
                workspaceType: 'LOCAL',
              },
            ],
            conversationRecords: {},
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3001/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '9001',
                conversationId: '3001',
                role: 'USER',
                content: '先在 A 工作空间建会话',
                status: 'COMPLETED',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3001/steps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3001/references') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3001/artifacts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3001/current-skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3001/current-experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3001/current-mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3002/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '9002',
                conversationId: '3002',
                role: 'USER',
                content: '这是 test 工作空间外的历史记录',
                status: 'COMPLETED',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3002/steps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3002/references') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3002/artifacts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3002/current-skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3002/current-experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/3002/current-mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.startsWith('/api/chat/conversations')) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in workspace history grouping test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/workspace-a',
        permissionGranted: true,
      },
    };

    const bindWorkspacePath = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() =>
      useChatWorkspace(true, {
        hostContext,
        bindWorkspacePath,
      }),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
      expect(
        result.current.workspaceGroups.some((group) => group.partitionKey === 'local::__history__'),
      ).toBe(false);
    });
    const localDefaultGroup = result.current.workspaceGroups.find(
      (group) => group.partitionKey === 'local::__no_workspace__',
    );
    expect(localDefaultGroup?.workspaceLabel).toBe('本地历史记录');

    expect(result.current.workspaceLabel).toBe('workspace-a');
    expect(result.current.conversations).toEqual([]);

    await act(async () => {
      await result.current.setActiveWorkspacePath('D:/code/test');
    });

    await waitFor(() => {
      expect(result.current.workspaceLabel).toBe('test');
    });

    expect(result.current.conversations).toEqual([]);
    await waitFor(() => {
      expect(
        result.current.workspaceGroups.some((group) => group.partitionKey === 'local::__history__'),
      ).toBe(false);
    });
  });

  it('切换到已有历史记录的工作空间时应恢复该空间会话，不应强制新建', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'local::d:/code/space-a': {
            workspacePath: 'D:/code/space-a',
            workspaceLabel: 'space-a',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now(),
            activeConversationId: '3001',
            conversations: [
              {
                id: '3001',
                title: 'A 会话',
                status: 'ACTIVE',
                lastRunId: '7001',
              },
            ],
            conversationRecords: {
              '3001': {
                owned: true,
                messages: [
                  {
                    id: '9001',
                    conversationId: '3001',
                    role: 'ASSISTANT',
                    content: 'A 空间缓存消息',
                    status: 'COMPLETED',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
          'local::d:/code/space-b': {
            workspacePath: 'D:/code/space-b',
            workspaceLabel: 'space-b',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now() - 1000,
            activeConversationId: '4001',
            conversations: [
              {
                id: '4001',
                title: 'B 会话',
                status: 'ACTIVE',
                lastRunId: '8001',
              },
            ],
            conversationRecords: {
              '4001': {
                owned: true,
                messages: [
                  {
                    id: '9101',
                    conversationId: '4001',
                    role: 'ASSISTANT',
                    content: 'B 空间缓存消息',
                    status: 'COMPLETED',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '3001',
                title: 'A 会话',
                status: 'ACTIVE',
                lastRunId: '7001',
              },
              {
                id: '4001',
                title: 'B 会话',
                status: 'ACTIVE',
                lastRunId: '8001',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url.endsWith('/messages') ||
        url.endsWith('/steps') ||
        url.endsWith('/references') ||
        url.endsWith('/artifacts') ||
        url.endsWith('/current-skills') ||
        url.endsWith('/current-mcps') ||
        url.endsWith('/current-experts')
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in workspace restore test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/space-a',
        permissionGranted: true,
      },
    };

    const { result } = renderHook(() =>
      useChatWorkspace(true, {
        hostContext,
      }),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('3001');
    });
    expect(result.current.workspaceLabel).toBe('space-a');
    await act(async () => {
      await result.current.selectConversationInWorkspace('4001', {
        partitionKey: 'local::d:/code/space-b',
        runtimeTarget: 'local',
        workspacePath: 'D:/code/space-b',
      });
    });
    expect(result.current.workspaceLabel).toBe('space-b');
    expect(result.current.activeConversationId).toBe('4001');

    await act(async () => {
      await result.current.setActiveWorkspacePath('D:/code/space-b');
    });

    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('4001');
    });
    expect(result.current.workspaceLabel).toBe('space-b');
  });

  /**
   * 云端运行环境下，历史分区应并入默认云端分组，不再单独渲染历史分组。
   */
  it('云端会话应仅保留默认云端分组，不再单独渲染历史分组', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__history__': {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now(),
            activeConversationId: '5001',
            conversations: [
              {
                id: '5001',
                title: '云端历史记录A',
                status: 'ACTIVE',
                lastRunId: '9001',
              },
            ],
            conversationRecords: {
              '5001': {
                owned: true,
                messages: [],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '5001',
                title: '云端历史记录A',
                status: 'ACTIVE',
                lastRunId: '9001',
              },
              {
                id: '5002',
                title: '云端历史记录B',
                status: 'ACTIVE',
                lastRunId: '9002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/5001/messages' ||
        url === '/api/chat/conversations/5001/steps' ||
        url === '/api/chat/conversations/5001/references' ||
        url === '/api/chat/conversations/5001/artifacts' ||
        url === '/api/chat/conversations/5001/current-skills' ||
        url === '/api/chat/conversations/5001/current-mcps' ||
        url === '/api/chat/conversations/5001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in cloud history grouping test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
      expect(
        result.current.workspaceGroups.some(
          (group) => group.groupType === 'history' && group.runtimeTarget === 'cloud',
        ),
      ).toBe(false);
    });

    expect(result.current.workspaceLabel).toBe('云端历史记录');
    expect(result.current.conversations.map((item) => item.id)).toEqual(['5001', '5002']);

    const cloudWorkspaceGroup = result.current.workspaceGroups.find(
      (group) => group.partitionKey === 'cloud::__no_workspace__',
    );
    expect(cloudWorkspaceGroup?.groupType).toBe('workspace');
    expect(cloudWorkspaceGroup?.conversations.map((item) => item.id)).toEqual(['5001', '5002']);
    const historyGroup = result.current.workspaceGroups.find(
      (group) => group.groupType === 'history' && group.runtimeTarget === 'cloud',
    );
    expect(historyGroup).toBeUndefined();

    await act(async () => {
      await result.current.selectConversationInWorkspace('5001', {
        partitionKey: 'cloud::__no_workspace__',
        runtimeTarget: 'cloud',
        workspacePath: null,
      });
    });

    const historyGroupAfterSelect = result.current.workspaceGroups.find(
      (group) => group.groupType === 'history' && group.runtimeTarget === 'cloud',
    );
    expect(historyGroupAfterSelect).toBeUndefined();
  });

  /**
   * 云端历史列表只应展示云端会话，桌面端本地工作空间创建的会话不应混入其中。
   */
  it('应在云端会话列表中排除本地工作空间会话', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '5001',
                title: '云端会话',
                status: 'ACTIVE',
                lastRunId: '9001',
                workspaceType: 'CLOUD',
              },
              {
                id: '6001',
                title: '本地会话',
                status: 'ACTIVE',
                lastRunId: '9002',
                workspaceType: 'LOCAL',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in cloud workspace visibility test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    expect(result.current.conversations.map((item) => item.id)).toEqual(['5001']);
    expect(
      result.current.workspaceGroups
        .find((group) => group.partitionKey === 'cloud::__no_workspace__')
        ?.conversations.map((item) => item.id),
    ).toEqual(['5001']);
    expect(
      result.current.workspaceGroups.some((group) =>
        group.conversations.some((conversation) => conversation.id === '6001'),
      ),
    ).toBe(false);
  });

  /**
   * 刷新时若快照仅有 activeConversationId 且会话列表为空，也应按会话 ID 回放，不应回退到首页空态。
   */
  it('应在快照会话列表为空但存在activeConversationId时仍恢复会话', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now(),
            activeConversationId: '5001',
            conversations: [],
            conversationRecords: {
              '5001': {
                owned: true,
                messages: [
                  {
                    id: '9101',
                    conversationId: '5001',
                    role: 'ASSISTANT',
                    content: '来自缓存的会话内容',
                    status: 'COMPLETED',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
          'cloud::__history__': {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now(),
            activeConversationId: null,
            conversations: [
              {
                id: '5001',
                title: '仅历史分组可见的会话',
                status: 'ACTIVE',
                lastRunId: '9001',
              },
            ],
            conversationRecords: {},
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '5001',
                title: '仅历史分组可见的会话',
                status: 'ACTIVE',
                lastRunId: '9001',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/5001/messages' ||
        url === '/api/chat/conversations/5001/steps' ||
        url === '/api/chat/conversations/5001/references' ||
        url === '/api/chat/conversations/5001/artifacts' ||
        url === '/api/chat/conversations/5001/current-skills' ||
        url === '/api/chat/conversations/5001/current-mcps' ||
        url === '/api/chat/conversations/5001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in active-id-only restore test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    expect(result.current.activeConversationId).toBe('5001');
    expect(result.current.messages.some((message) => message.content === '来自缓存的会话内容')).toBe(true);
  });

  /**
   * 刷新命中 URL 会话参数且本地存在可回放快照时，应优先恢复快照，
   * 不应被 conversations 列表接口慢响应阻塞到首页空态。
   */
  it('应在会话列表接口慢响应时仍优先恢复URL指定会话的本地快照', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.history.replaceState(window.history.state, '', '/?conversationId=2001');
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now(),
            activeConversationId: '2001',
            conversations: [
              {
                id: '2001',
                title: '刷新恢复测试会话',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
            conversationRecords: {
              '2001': {
                owned: true,
                messages: [
                  {
                    id: '9201',
                    conversationId: '2001',
                    role: 'ASSISTANT',
                    content: '应立即恢复的本地快照消息',
                    status: 'COMPLETED',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
        },
      }),
    );

    let resolveConversationsRequest: ((response: Response) => void) | null = null;
    const pendingConversationsResponse = new Promise<Response>((resolve) => {
      resolveConversationsRequest = resolve;
    });
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return pendingConversationsResponse;
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in delayed conversations restore test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(
      () => {
        expect(result.current.activeConversationId).toBe('2001');
        expect(
          result.current.messages.some((message) => message.content === '应立即恢复的本地快照消息'),
        ).toBe(true);
      },
      { timeout: 300 },
    );

    expect(resolveConversationsRequest).not.toBeNull();
    resolveConversationsRequest?.(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: '2001',
              title: '刷新恢复测试会话',
              status: 'ACTIVE',
              lastRunId: '5002',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
  });

  /**
   * 刷新恢复命中本地快照且会话仍在运行时，不能只做静态回放，必须续接会话 SSE。
   */
  it('刷新恢复运行中会话快照时应重新订阅会话流', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.history.replaceState(window.history.state, '', '/?conversationId=2001');
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '云端历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now(),
            activeConversationId: '2001',
            conversations: [
              {
                id: '2001',
                title: '刷新运行中会话',
                status: 'ACTIVE',
                activeTaskId: '9001',
                activeTaskStatus: 'RUNNING',
              },
            ],
            conversationRecords: {
              '2001': {
                owned: true,
                messages: [
                  {
                    id: '1001',
                    conversationId: '2001',
                    role: 'USER',
                    content: '开始后台任务',
                    status: 'COMPLETED',
                  },
                  {
                    id: '1002',
                    conversationId: '2001',
                    role: 'ASSISTANT',
                    content: '已有输出',
                    status: 'streaming',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
        },
      }),
    );

    const streamUrls: string[] = [];
    const streamReadQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const requestUrls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      requestUrls.push(url);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: '刷新运行中会话',
                status: 'ACTIVE',
                activeTaskId: '9001',
                activeTaskStatus: 'RUNNING',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/stream') {
        streamUrls.push(url);
        const reader = {
          read: vi.fn(
            () =>
              new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
                streamReadQueue.push({ resolve, reject });
              }),
          ),
        };
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => reader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in refresh running snapshot stream test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2001');
      expect(result.current.messages.find((message) => message.role === 'ASSISTANT')?.content).toBe('已有输出');
    });
    await waitFor(() => {
      expect(streamUrls).toEqual(['/api/chat/conversations/2001/stream']);
      expect(streamReadQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      streamReadQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
  });

  /**
   * 本地 token 存在时会在鉴权完成前先执行一次初始化；鉴权从未完成切到已完成后，
   * 第二轮初始化不应把第一轮已经恢复好的 URL 会话清空回首页。
   */
  it('应在鉴权状态切换后保留已恢复的URL会话', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.history.replaceState(window.history.state, '', '/?conversationId=2001');

    const bootstrapRequestUrls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      bootstrapRequestUrls.push(url);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: '鉴权切换恢复会话',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'user-2001',
                conversationId: '2001',
                role: 'USER',
                content: '人机',
                status: 'COMPLETED',
              },
              {
                id: 'assistant-2001',
                conversationId: '2001',
                role: 'ASSISTANT',
                content: '这条消息不应被第二轮初始化冲掉',
                status: 'COMPLETED',
              },
            ],
          }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in strict mode bootstrap test: ${url}`);
    });

    const { result, rerender } = renderHook(
      ({ isAuthenticated }) => useChatWorkspace(isAuthenticated),
      {
        initialProps: {
          isAuthenticated: false,
        },
      },
    );

    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2001');
      expect(
        result.current.messages.some(
          (message) => message.content === '这条消息不应被第二轮初始化冲掉',
        ),
      ).toBe(true);
    });

    rerender({ isAuthenticated: true });

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    expect(result.current.activeConversationId).toBe('2001');
    expect(
      result.current.messages.some(
        (message) => message.content === '这条消息不应被第二轮初始化冲掉',
      ),
    ).toBe(true);
    expect(bootstrapRequestUrls.filter((url) => url === '/api/chat/conversations')).toHaveLength(1);
  });

  /**
   * URL 指向本地历史分区会话时，刷新应先切到该分区再恢复消息，避免主区错误回退到首页。
   */
  it('应在刷新时恢复归属于其他本地分区的URL会话', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.history.replaceState(window.history.state, '', '/?conversationId=4001');
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'local::d:/code/codingx': {
            workspacePath: 'D:/code/CodingX',
            workspaceLabel: 'CodingX',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now(),
            activeConversationId: null,
            conversations: [],
            conversationRecords: {},
            seenTaskFinishedAtByConversationId: {},
          },
          'local::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now() - 1000,
            activeConversationId: '4001',
            conversations: [
              {
                id: '4001',
                title: '本地历史会话',
                status: 'ACTIVE',
                lastRunId: '8101',
              },
            ],
            conversationRecords: {
              '4001': {
                owned: true,
                messages: [
                  {
                    id: 'assistant-4001',
                    conversationId: '4001',
                    role: 'ASSISTANT',
                    content: '刷新后应恢复这条历史消息',
                    status: 'COMPLETED',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
            seenTaskFinishedAtByConversationId: {},
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '4001',
                title: '本地历史会话',
                status: 'ACTIVE',
                lastRunId: '8101',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/4001/messages' ||
        url === '/api/chat/conversations/4001/steps' ||
        url === '/api/chat/conversations/4001/references' ||
        url === '/api/chat/conversations/4001/artifacts' ||
        url === '/api/chat/conversations/4001/current-skills' ||
        url === '/api/chat/conversations/4001/current-mcps' ||
        url === '/api/chat/conversations/4001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in cross-partition url restore test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/CodingX',
        workspaceId: '3001',
        permissionGranted: true,
      },
    };

    const { result } = renderHook(() => useChatWorkspace(true, { hostContext }));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    expect(result.current.activeWorkspacePartitionKey).toBe('local::__no_workspace__');
    expect(result.current.workspaceLabel).toBe('本地历史记录');
    expect(result.current.activeConversationId).toBe('4001');
    expect(
      result.current.messages.some((message) => message.content === '刷新后应恢复这条历史消息'),
    ).toBe(true);
  });

  /**
   * 本地存在 token 且认证校验尚未返回时，不应把工作区直接重置为未登录态，
   * 否则会提前清空 conversationId，导致刷新恢复链路中断。
   */
  it('应在认证校验未完成但本地有token时保留URL会话参数并继续初始化', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.history.replaceState(window.history.state, '', '/?conversationId=2001');

    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in pending-auth bootstrap test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(false));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/chat/conversations', expect.any(Object));
    });
    expect(new URL(window.location.href).searchParams.get('conversationId')).toBe('2001');
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
  });

  /**
   * 刷新恢复会话时，消息应优先展示，不应被步骤/来源/产物等右栏回放接口阻塞。
   */
  it('刷新恢复会话时应优先渲染消息，不被右栏回放接口阻塞', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    // 显式带上会话参数，模拟“刷新恢复指定会话”而非“默认首页自动跳转”场景。
    window.history.replaceState(window.history.state, '', '/?conversationId=2001');

    let resolveStepsRequest: ((response: Response) => void) | null = null;
    const pendingStepsResponse = new Promise<Response>((resolve) => {
      resolveStepsRequest = resolve;
    });

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: '刷新恢复测试会话',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '9101',
                conversationId: '2001',
                role: 'ASSISTANT',
                content: '消息接口已返回',
                status: 'COMPLETED',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/steps') {
        return pendingStepsResponse;
      }
      if (
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in refresh-priority-message test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2001');
    });
    await waitFor(
      () => {
        expect(result.current.messages.some((message) => message.content === '消息接口已返回')).toBe(true);
      },
      { timeout: 300 },
    );
    expect(result.current.isBootstrapping).toBe(true);

    expect(resolveStepsRequest).not.toBeNull();
    resolveStepsRequest?.(
      new Response(
        JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
        { status: 200 },
      ),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
  });

  /**
   * 刷新恢复会话时，引用应在步骤接口完成前先回填，避免正文里的 [R1] 长时间保持纯文本。
   */
  it('刷新恢复会话时应优先回填引用链接', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    // 显式带上会话参数，模拟“刷新恢复指定会话”场景。
    window.history.replaceState(window.history.state, '', '/?conversationId=2001');

    let resolveStepsRequest: ((response: Response) => void) | null = null;
    const pendingStepsResponse = new Promise<Response>((resolve) => {
      resolveStepsRequest = resolve;
    });

    const requestUrls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      requestUrls.push(url);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: '刷新恢复测试会话',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '9100',
                conversationId: '2001',
                role: 'USER',
                content: '请给出资料来源',
                status: 'COMPLETED',
              },
              {
                id: '9101',
                conversationId: '2001',
                role: 'ASSISTANT',
                content: '结论见 [R1]。',
                status: 'COMPLETED',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/steps') {
        return pendingStepsResponse;
      }
      if (url === '/api/chat/conversations/2001/references') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'r-1',
                runId: '5002',
                messageId: '9100',
                conversationId: '2001',
                title: '资料一',
                url: 'https://example.com/a',
                siteName: 'Example',
                rankNo: 1,
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in refresh-priority-reference test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2001');
    });
    await waitFor(
      () => {
        expect(result.current.messages.some((message) => message.id === '9101')).toBe(true);
      },
      { timeout: 300 },
    );
    await waitFor(
      () => {
        expect(result.current.references).toHaveLength(1);
      },
      { timeout: 300 },
    );
    expect(result.current.isBootstrapping).toBe(true);

    expect(resolveStepsRequest).not.toBeNull();
    resolveStepsRequest?.(
      new Response(
        JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
        { status: 200 },
      ),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
  });

  /**
   * 本地运行环境下，只要已绑定本地目录（即便 workspaceId 为空）也应允许发送，
   * 避免桌面端尚未拿到 workspaceId 时“发送无响应”。
   */
  it('应在本地目录已绑定但workspaceId为空时仍允许发送消息', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const streamFetchMock = vi.fn();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: 'Default Demo Conversation',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts' ||
        url === '/api/chat/conversations/pending-conversation/messages' ||
        url === '/api/chat/conversations/pending-conversation/steps' ||
        url === '/api/chat/conversations/pending-conversation/references' ||
        url === '/api/chat/conversations/pending-conversation/artifacts' ||
        url === '/api/chat/conversations/pending-conversation/current-skills' ||
        url === '/api/chat/conversations/pending-conversation/current-mcps' ||
        url === '/api/chat/conversations/pending-conversation/current-experts' ||
        /^\/api\/chat\/conversations\/local-[^/]+\/(messages|steps|references|artifacts|current-skills|current-mcps|current-experts)$/.test(url)
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        streamFetchMock(url);
        return new Response('', { status: 200 });
      }
      throw new Error(`Unhandled fetch in local workspace fallback send test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/CodingX',
        permissionGranted: true,
      },
    };

    const { result } = renderHook(() =>
      useChatWorkspace(true, {
        hostContext,
      }),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('请继续分析这个项目');
    });
    await act(async () => {
      await result.current.submitMessage();
    });

    expect(streamFetchMock).toHaveBeenCalledTimes(1);
    expect(streamFetchMock.mock.calls[0][0]).toContain('/api/chat/stream?');
    expect(streamFetchMock.mock.calls[0][0]).toContain('repositoryPath=D%3A%2Fcode%2FCodingX');
    expect(result.current.streamError).toBe('');
  });

  /**
   * 未选择本地目录时也应允许发送，由后端创建或复用“本地历史记录”工作空间。
   */
  it('本地默认历史分区未选择目录时也应允许发送消息', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const streamFetchMock = vi.fn();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        /^\/api\/chat\/conversations\/local-[^/]+\/(messages|steps|references|artifacts|current-skills|current-mcps|current-experts)$/.test(url)
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        streamFetchMock(url);
        return new Response('', { status: 200 });
      }
      throw new Error(`Unhandled fetch in local default history send test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: null,
        permissionGranted: false,
      },
    };

    const { result } = renderHook(() =>
      useChatWorkspace(true, {
        hostContext,
      }),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('记录到本地历史');
    });
    await act(async () => {
      await result.current.submitMessage();
    });

    expect(streamFetchMock).toHaveBeenCalledTimes(1);
    const requestUrl = String(streamFetchMock.mock.calls[0][0]);
    expect(requestUrl).toContain('/api/chat/stream?');
    expect(requestUrl).toContain('runtimeTarget=local');
    expect(requestUrl).not.toContain('repositoryPath=');
    expect(requestUrl).not.toContain('workspaceId=');
    expect(result.current.streamError).toBe('');
  });

  /**
   * 分享会话应返回可直接复制的绝对链接，避免前端后续再拼接导致分享失效。
   */
  it('应返回绝对分享链接', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: 'Default Demo Conversation',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/share') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'conversation shared',
            data: {
              shareToken: 'share_xxx',
              shareUrl: '/api/chat/conversations/shared/share_xxx',
            },
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in share conversation test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      const shareUrl = await result.current.shareConversation('2001');
      expect(shareUrl).toBe(
        new URL('/api/chat/conversations/shared/share_xxx', window.location.origin).toString(),
      );
    });

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2001/share',
      expect.objectContaining({
        method: 'POST',
      }),
    );
  });

  /**
   * 选择消息分享时应把消息 ID 附加到分享链接，公开页据此过滤回放范围。
   */
  it('应返回带消息过滤参数的绝对分享链接', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [{ id: '2001', title: 'Default Demo Conversation', status: 'ACTIVE' }],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/share') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'conversation shared',
            data: {
              shareToken: 'share_xxx',
              shareUrl: '/share/chat/share_xxx',
            },
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in filtered share conversation test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      const shareUrl = await result.current.shareConversation('2001', {
        messageIds: ['101', '102'],
      });
      expect(shareUrl).toBe(
        new URL('/share/chat/share_xxx', window.location.origin).toString(),
      );
    });

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2001/share',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ messageIds: ['101', '102'] }),
      }),
    );
  });

  /**
   * 删除消息时应忽略临时字符串 ID，只把已落库的数值消息提交给后端。
   */
  it('应在删除消息时过滤掉未落库消息ID', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [{ id: '2001', title: 'Default Demo Conversation', status: 'ACTIVE' }],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in delete message normalization test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      await result.current.deleteConversationMessages('2001', [
        'optimistic-assistant-1779529346520',
        '102',
        '102',
        'optimistic-edit-assistant-1779529346521',
        '101',
      ]);
    });

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2001/messages',
      expect.objectContaining({
        method: 'DELETE',
        body: JSON.stringify({ messageIds: ['102', '101'] }),
      }),
    );
  });

  /**
   * 重新生成会话应命中专用接口。
   */
  it('应调用重新生成接口', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: 'Default Demo Conversation',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/regenerate') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'conversation regenerated',
            data: null,
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in regenerate conversation test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      await result.current.regenerateConversation('2001');
    });

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2001/regenerate',
      expect.objectContaining({
        method: 'POST',
      }),
    );
  });

  /**
   * 重新生成应先替换原助手消息槽位，再用上一条用户消息重新拉流，避免追加重复回答。
   */
  it('应覆盖原助手消息并重新拉流', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [{ id: '2001', title: 'Default Demo Conversation', status: 'ACTIVE' }],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '101',
                conversationId: '2001',
                role: 'USER',
                content: '原问题',
                status: 'COMPLETED',
              },
              {
                id: '102',
                conversationId: '2001',
                role: 'ASSISTANT',
                content: '原回答',
                status: 'COMPLETED',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url.startsWith('/api/chat/stream?')) {
        const stream = new ReadableStream({
          start(controller) {
            controller.enqueue(
              new TextEncoder().encode(
                'event: finish\n' +
                  'data: {"conversationId":"2001","content":"新回答","title":"Default Demo Conversation"}\n\n',
              ),
            );
            controller.close();
          },
        });
        return new Response(stream, { status: 200 });
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in regenerate stream test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      await result.current.selectConversation('2001', result.current.conversations);
    });
    await waitFor(() => {
      expect(result.current.messages).toHaveLength(2);
    });

    await act(async () => {
      await result.current.regenerateConversation('2001', {
        assistantMessageId: '102',
      });
    });

    await waitFor(() => {
      expect(result.current.messages).toHaveLength(2);
      expect(result.current.messages[1]).toMatchObject({
        role: 'ASSISTANT',
        content: '新回答',
        status: 'done',
      });
    });
    const streamCall = vi.mocked(globalThis.fetch).mock.calls.find(([input]) =>
      String(input).startsWith('/api/chat/stream?'),
    );
    expect(String(streamCall?.[0])).toContain('conversationId=2001');
    expect(String(streamCall?.[0])).toContain('question=%E5%8E%9F%E9%97%AE%E9%A2%98');
  });

  /**
   * 切换本地工作空间后应立即使用绑定返回的 workspaceId 发流，避免会话误落到默认云端空间。
   */
  it('应在切换本地工作空间后使用本地运行目标发送流请求', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const streamFetchMock = vi.fn();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.startsWith('/api/chat/conversations')) {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: 'Default Demo Conversation',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        streamFetchMock(url);
        return new Response('', { status: 200 });
      }
      throw new Error(`Unhandled fetch in local workspace id update test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/workspace-a',
        workspaceId: '3001',
        permissionGranted: true,
      },
    };

    const bindWorkspacePath = vi.fn().mockResolvedValue({
      repositoryPath: 'D:/code/workspace-b',
      workspaceId: '3002',
      workspaceName: 'workspace-b',
    });

    const { result } = renderHook(() =>
      useChatWorkspace(true, {
        hostContext,
        bindWorkspacePath,
      }),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      await result.current.setActiveWorkspacePath('D:/code/workspace-b');
    });

    await waitFor(() => {
      expect(bindWorkspacePath).toHaveBeenCalledWith('D:/code/workspace-b');
    });

    await act(async () => {
      result.current.setInputValue('请继续分析 workspace-b');
    });
    await act(async () => {
      await result.current.submitMessage();
    });

    expect(streamFetchMock).toHaveBeenCalledTimes(1);
    const requestUrl = String(streamFetchMock.mock.calls[0][0]);
    expect(requestUrl).toContain('runtimeTarget=local');
    expect(requestUrl).toContain('repositoryPath=D%3A%2Fcode%2Fworkspace-b');
    expect(requestUrl).toContain('workspaceId=3002');
  });

  /**
   * 本地目录绑定返回的项目画像和已生效记忆数量应同步进入工作台状态，供聊天页即时展示 Agent 上下文。
   */
  it('应在绑定本地工作空间后同步项目画像和已生效记忆数量', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.startsWith('/api/chat/conversations')) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in workspace intelligence sync test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/workspace-a',
        workspaceId: '3001',
        permissionGranted: true,
      },
    };
    const bindWorkspacePath = vi.fn().mockResolvedValue({
      repositoryPath: 'D:/code/workspace-b',
      workspaceId: '3002',
      workspaceName: 'workspace-b',
      projectProfile: {
        summary: 'Maven + Vite workspace',
        moduleMapJson: '[{"name":"backend","path":"backend"}]',
        testCommandsJson: '["mvn test","npm run build"]',
        keyEntrypointsJson: '["backend/src/main/java/com/codingx/CodingXApplication.java"]',
        riskPointsJson: '["缺少端到端测试"]',
        agentContext: '项目包含后端、用户端和管理端。',
      },
      activeMemoryCount: 2,
    });

    const { result } = renderHook(() =>
      useChatWorkspace(true, {
        hostContext,
        bindWorkspacePath,
      }),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      await result.current.setActiveWorkspacePath('D:/code/workspace-b');
    });

    expect(result.current.projectProfile?.summary).toBe('Maven + Vite workspace');
    expect(result.current.activeMemoryCount).toBe(2);
  });

  /**
   * 本地模式发送完成后应回查云端会话，确保后端 message 表成为历史主存储。
   */
  it('本地模式发送完成后应回查云端会话并刷新本地快照', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode(
      'event:meta\ndata:{"conversationId":"9901","runtimeTarget":"local","localOnly":false}\n\n',
    );
    const messageEvent = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"本地回答"}\n\n',
    );
    const finishEvent = new TextEncoder().encode(
      'event:finish\ndata:{"conversationId":"9901","content":"本地回答","title":"本地项目问题"}\n\n',
    );
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };
    const cloudConversationFetchMock = vi.fn();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.startsWith('/api/chat/conversations')) {
        cloudConversationFetchMock(url);
        if (url === '/api/chat/conversations?workspaceId=3001') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: '9901',
                  title: '本地项目问题',
                  status: 'ACTIVE',
                  workspaceId: '3001',
                  workspaceType: 'LOCAL',
                },
              ],
            }),
            { status: 200 },
          );
        }
        if (url === '/api/chat/conversations/9901/messages') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: 'user-9901',
                  conversationId: '9901',
                  role: 'USER',
                  content: '本地项目问题',
                  status: 'COMPLETED',
                },
                {
                  id: 'assistant-9901',
                  conversationId: '9901',
                  role: 'ASSISTANT',
                  content: '本地回答',
                  status: 'COMPLETED',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in local-only snapshot test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/local-project',
        workspaceId: '3001',
        permissionGranted: true,
      },
    };

    const { result } = renderHook(() =>
      useChatWorkspace(true, {
        hostContext,
      }),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('本地项目问题');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });
    for (const event of [metaEvent, messageEvent, finishEvent]) {
      await act(async () => {
        readQueue.shift()?.resolve({ done: false, value: event });
      });
    }
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });

    expect(cloudConversationFetchMock).toHaveBeenCalledWith('/api/chat/conversations?workspaceId=3001');
    expect(result.current.activeConversationId).toBe('9901');
    expect(result.current.conversations.map((conversation) => conversation.id)).toContain('9901');
    const snapshotStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    const localRecord =
      snapshotStore?.snapshots?.['local::d:/code/local-project']?.conversationRecords?.['9901'];
    expect(localRecord?.messages.map((message: { content: string }) => message.content)).toEqual(
      expect.arrayContaining(['本地项目问题', '本地回答']),
    );
  });

  /**
   * 发送流式消息过程中，宿主上下文对象刷新（引用变化但实际数据不变）不应清空当前会话。
   */
  it('应在发送中忽略无效宿主上下文引用刷新，避免回到首页', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001"}\n\n');
    const partialMessageEvent = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"第一段回答"}\n\n',
    );
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"完成回答"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: 'Default Demo Conversation',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        if (url === '/api/chat/conversations/2001/messages') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: 'assistant-2001',
                  conversationId: '2001',
                  role: 'ASSISTANT',
                  content: '完成回答',
                  status: 'COMPLETED',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in host context refresh test: ${url}`);
    });

    const baseHostContext = {
      hostType: 'desktop' as const,
      executionTargets: ['cloud', 'local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/CodingX',
        permissionGranted: true,
      },
    };

    const { result, rerender } = renderHook(
      ({ hostContext }) =>
        useChatWorkspace(true, {
          hostContext,
        }),
      {
        initialProps: { hostContext: baseHostContext },
      },
    );

    expect(result.current.runtimeTargets).toEqual(['cloud', 'local']);

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      await result.current.selectConversation('2001', [
        {
          id: '2001',
          title: 'Default Demo Conversation',
          status: 'ACTIVE',
          lastRunId: '5002',
        },
      ]);
      result.current.setInputValue('继续回答');
    });

    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: metaEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: partialMessageEvent });
    });
    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2001');
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.content?.length).toBeGreaterThan(0);
    });

    await act(async () => {
      rerender({
        hostContext: {
          ...baseHostContext,
          executionTargets: [...baseHostContext.executionTargets],
          capabilities: {
            ...baseHostContext.capabilities,
          },
          localResource: {
            ...baseHostContext.localResource,
          },
        },
      });
    });

    expect(result.current.isStreaming).toBe(true);
    expect(result.current.messages.length).toBeGreaterThan(0);
    expect(result.current.runtimeTargets).toEqual(['cloud', 'local']);
    expect(result.current.activeConversationId).toBe('2001');

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });

    expect(result.current.activeConversationId).toBe('2001');
    expect(new URL(window.location.href).searchParams.get('conversationId')).toBe('2001');
  });

  /**
   * 首屏 URL 会话恢复仍在等待接口时，用户可能已经发起新问题；旧恢复任务不能清空新流式消息。
   */
  it('发送中旧URL恢复失败不应清空当前流式消息', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.history.replaceState(window.history.state, '', '/?conversationId=stale-2000');

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"9010"}\n\n');
    const partialMessageEvent = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"流式回答片段"}\n\n',
    );
    const finishEvent = new TextEncoder().encode(
      'event:finish\ndata:{"conversationId":"9010","content":"流式最终回答","title":"新问题"}\n\n',
    );
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };
    let resolveInitialConversations: ((response: Response) => void) | null = null;
    const initialConversations = new Promise<Response>((resolve) => {
      resolveInitialConversations = resolve;
    });
    let conversationRequestCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        conversationRequestCount += 1;
        if (conversationRequestCount === 1) {
          return initialConversations;
        }
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '9010',
                title: '新问题',
                status: 'ACTIVE',
                lastRunId: '9102',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/9010/messages' ||
        url === '/api/chat/conversations/9010/steps' ||
        url === '/api/chat/conversations/9010/references' ||
        url === '/api/chat/conversations/9010/artifacts' ||
        url === '/api/chat/conversations/9010/current-skills' ||
        url === '/api/chat/conversations/9010/current-mcps' ||
        url === '/api/chat/conversations/9010/current-experts'
      ) {
        if (url === '/api/chat/conversations/9010/messages') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: 'user-9010',
                  conversationId: '9010',
                  role: 'USER',
                  content: '首屏未完成时提问',
                  status: 'COMPLETED',
                },
                {
                  id: 'assistant-9010',
                  conversationId: '9010',
                  role: 'ASSISTANT',
                  content: '流式最终回答',
                  status: 'COMPLETED',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in stale URL bootstrap streaming test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(conversationRequestCount).toBe(1);
      expect(result.current.activeWorkspacePartitionKey).toBe('cloud::__no_workspace__');
    });

    await act(async () => {
      result.current.setInputValue('首屏未完成时提问');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: metaEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: partialMessageEvent });
    });

    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('9010');
      expect(result.current.messages.some((message) => message.content.includes('流式回答片段'))).toBe(true);
    });

    await act(async () => {
      resolveInitialConversations?.(
        new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        ),
      );
    });

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    expect(result.current.isStreaming).toBe(true);
    expect(result.current.activeConversationId).toBe('9010');
    expect(result.current.messages).not.toHaveLength(0);
    expect(result.current.messages.some((message) => message.content.includes('流式回答片段'))).toBe(true);

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * 流式生成期间若宿主被动刷新到其他本地分区，不应打断当前会话。
   * 否则会出现消息区被清空但输入栏仍显示“停止中”的错位状态。
   */
  it('应在流式生成期间忽略宿主被动分区切换并保持当前消息', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'local::d:/code/workspace-a': {
            workspacePath: 'D:/code/workspace-a',
            workspaceLabel: 'workspace-a',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now(),
            activeConversationId: '2001',
            conversations: [
              {
                id: '2001',
                title: 'A 会话',
                status: 'ACTIVE',
                lastRunId: '5002',
                workspaceId: '3001',
              },
            ],
            conversationRecords: {
              '2001': {
                owned: true,
                messages: [
                  {
                    id: 'assistant-2001',
                    conversationId: '2001',
                    role: 'ASSISTANT',
                    content: '历史消息',
                    status: 'COMPLETED',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
          'local::d:/code/workspace-b': {
            workspacePath: 'D:/code/workspace-b',
            workspaceLabel: 'workspace-b',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now() - 1000,
            activeConversationId: null,
            conversations: [],
            conversationRecords: {},
          },
          'local::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now() - 2000,
            activeConversationId: null,
            conversations: [],
            conversationRecords: {},
          },
        },
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001"}\n\n');
    const partialMessageEvent = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"流式增量"}\n\n',
    );
    const finishEvent = new TextEncoder().encode(
      'event:finish\ndata:{"conversationId":"2001","content":"流式完成"}\n\n',
    );
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations?workspaceId=3001') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: 'A 会话',
                status: 'ACTIVE',
                lastRunId: '5002',
                workspaceId: '3001',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations?workspaceId=3002') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        if (url === '/api/chat/conversations/2001/messages') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: 'assistant-2001',
                  conversationId: '2001',
                  role: 'ASSISTANT',
                  content: '历史消息',
                  status: 'COMPLETED',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in streaming host partition switch test: ${url}`);
    });

    const baseHostContext = {
      hostType: 'desktop' as const,
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/workspace-a',
        workspaceId: '3001',
        permissionGranted: true,
      },
    };

    const { result, rerender } = renderHook(
      ({ hostContext }) =>
        useChatWorkspace(true, {
          hostContext,
        }),
      {
        initialProps: { hostContext: baseHostContext },
      },
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
      expect(result.current.activeConversationId).toBe('2001');
    });

    await act(async () => {
      result.current.setInputValue('继续回答');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: metaEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: partialMessageEvent });
    });

    await waitFor(() => {
      const hasStreamingDelta = result.current.messages.some(
        (item) => item.role === 'ASSISTANT' && item.content.includes('流式增量'),
      );
      expect(hasStreamingDelta).toBe(true);
    });

    await act(async () => {
      rerender({
        hostContext: {
          ...baseHostContext,
          executionTargets: [...baseHostContext.executionTargets],
          capabilities: {
            ...baseHostContext.capabilities,
          },
          localResource: {
            ...baseHostContext.localResource,
            boundRepositoryPath: 'D:/code/workspace-b',
            workspaceId: '3002',
          },
        },
      });
    });

    expect(result.current.isStreaming).toBe(true);
    expect(result.current.activeConversationId).toBe('2001');
    expect(result.current.workspacePath).toBe('D:/code/workspace-a');
    expect(result.current.messages.length).toBeGreaterThan(0);

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });

    expect(result.current.isStreaming).toBe(false);
    expect(result.current.activeConversationId).toBe('2001');
  });

  /**
   * 宿主仅刷新上下文引用且分区未变化时，不应清空当前会话回放，避免首页闪回与侧栏空态闪烁。
   */
  it('应在同分区宿主上下文刷新时保持当前会话与消息不变', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'local::d:/code/codingx': {
            workspacePath: 'D:/code/CodingX',
            workspaceLabel: 'CodingX',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now(),
            activeConversationId: '2001',
            conversations: [
              {
                id: '2001',
                title: '会话 2001',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
            conversationRecords: {
              '2001': {
                owned: true,
                messages: [
                  {
                    id: 'assistant-2001',
                    conversationId: '2001',
                    role: 'ASSISTANT',
                    content: '这是一条已存在的消息',
                    status: 'COMPLETED',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
          'local::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now() - 1000,
            activeConversationId: null,
            conversations: [],
            conversationRecords: {},
          },
        },
      }),
    );

    const requestUrls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      requestUrls.push(url);
      if (url === '/api/chat/conversations?workspaceId=3001') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: '会话 2001',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        if (url === '/api/chat/conversations/2001/messages') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: 'assistant-2001',
                  conversationId: '2001',
                  role: 'ASSISTANT',
                  content: '这是一条已存在的消息',
                  status: 'COMPLETED',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in same-partition host context refresh test: ${url}`);
    });

    const baseHostContext = {
      hostType: 'desktop' as const,
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/CodingX',
        workspaceId: '3001',
        permissionGranted: true,
      },
    };

    const { result, rerender } = renderHook(
      ({ hostContext }) =>
        useChatWorkspace(true, {
          hostContext,
        }),
      {
        initialProps: { hostContext: baseHostContext },
      },
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
      expect(result.current.activeRuntimeTarget).toBe('local');
      expect(result.current.activeConversationId).toBe('2001');
      expect(result.current.messages.length).toBe(1);
    });
    const messageReplayRequestCountBeforeRerender = requestUrls.filter(
      (url) => url === '/api/chat/conversations/2001/messages',
    ).length;

    await act(async () => {
      rerender({
        hostContext: {
          ...baseHostContext,
          executionTargets: [...baseHostContext.executionTargets],
          capabilities: {
            ...baseHostContext.capabilities,
          },
          localResource: {
            ...baseHostContext.localResource,
          },
        },
      });
    });

    expect(result.current.activeConversationId).toBe('2001');
    expect(result.current.messages).toHaveLength(1);
    expect(result.current.messages[0]?.content).toBe('这是一条已存在的消息');
    expect(result.current.workspacePath).toBe('D:/code/CodingX');
    // 宿主刷新仅同步上下文，不应追加会话回放请求。
    expect(
      requestUrls.filter((url) => url === '/api/chat/conversations/2001/messages').length,
    ).toBe(messageReplayRequestCountBeforeRerender);
  });

  /**
   * 从侧栏分组触发“新建对话”时，应先切换到对应环境和工作空间，再进入新会话空态。
   */
  it('应在按分组上下文新建会话时切换到目标环境和工作空间', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now(),
            activeConversationId: '2001',
            conversations: [
              {
                id: '2001',
                title: '云端会话',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
            conversationRecords: {
              '2001': {
                owned: true,
                messages: [],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
          'local::d:/code/workspace-b': {
            workspacePath: 'D:/code/workspace-b',
            workspaceLabel: 'workspace-b',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now(),
            activeConversationId: '4001',
            conversations: [
              {
                id: '4001',
                title: '本地会话',
                status: 'ACTIVE',
                lastRunId: '8001',
              },
            ],
            conversationRecords: {
              '4001': {
                owned: true,
                messages: [
                  {
                    id: '9101',
                    conversationId: '4001',
                    role: 'ASSISTANT',
                    content: '本地缓存消息',
                    status: 'COMPLETED',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
        url === '/api/chat/conversations?workspaceId=3001' ||
        url === '/api/chat/conversations?workspaceId=3002' ||
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        if (url === '/api/chat/conversations' || url === '/api/chat/conversations?workspaceId=3001') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: '2001',
                  title: '云端会话',
                  status: 'ACTIVE',
                  lastRunId: '5002',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in contextual new conversation test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['cloud', 'local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/workspace-a',
        workspaceId: '3002',
        permissionGranted: true,
      },
    };

    const { result } = renderHook(() =>
      useChatWorkspace(true, {
        hostContext,
      }),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    expect(result.current.activeRuntimeTarget).toBe('cloud');

    await act(async () => {
      await result.current.startNewConversation({
        partitionKey: 'local::d:/code/workspace-b',
        runtimeTarget: 'local',
        workspacePath: 'D:/code/workspace-b',
      });
    });

    await waitFor(() => {
      expect(result.current.activeRuntimeTarget).toBe('local');
      expect(result.current.workspacePath).toBe('D:/code/workspace-b');
      expect(result.current.workspaceLabel).toBe('workspace-b');
      expect(result.current.activeConversationId).toBeNull();
      expect(result.current.messages).toEqual([]);
    });
  });

  /**
   * 从首页切换本地工作空间时应保持新会话空态，不应先短暂选中旧会话再清空，避免页面抽搐。
   */
  it('应在切换本地工作空间后保持新建态且不触发历史会话高亮', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'local::d:/code/workspace-a': {
            workspacePath: 'D:/code/workspace-a',
            workspaceLabel: 'workspace-a',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now(),
            activeConversationId: '3001',
            conversations: [
              {
                id: '3001',
                title: 'A 会话',
                status: 'ACTIVE',
                lastRunId: '7001',
              },
            ],
            conversationRecords: {
              '3001': {
                owned: true,
                messages: [],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
          'local::d:/code/workspace-b': {
            workspacePath: 'D:/code/workspace-b',
            workspaceLabel: 'workspace-b',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now() - 1000,
            activeConversationId: '4001',
            conversations: [
              {
                id: '4001',
                title: 'B 会话',
                status: 'ACTIVE',
                lastRunId: '8001',
              },
            ],
            conversationRecords: {
              '4001': {
                owned: true,
                messages: [],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
        },
      }),
    );

    const requestUrls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      requestUrls.push(url);
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url.startsWith('/api/chat/conversations?workspaceId=') ||
        url === '/api/chat/conversations/3001/messages' ||
        url === '/api/chat/conversations/3001/steps' ||
        url === '/api/chat/conversations/3001/references' ||
        url === '/api/chat/conversations/3001/artifacts' ||
        url === '/api/chat/conversations/3001/current-skills' ||
        url === '/api/chat/conversations/3001/current-mcps' ||
        url === '/api/chat/conversations/3001/current-experts' ||
        url === '/api/chat/conversations/4001/messages' ||
        url === '/api/chat/conversations/4001/steps' ||
        url === '/api/chat/conversations/4001/references' ||
        url === '/api/chat/conversations/4001/artifacts' ||
        url === '/api/chat/conversations/4001/current-skills' ||
        url === '/api/chat/conversations/4001/current-mcps' ||
        url === '/api/chat/conversations/4001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in workspace switch no-flicker test: ${url}`);
    });

    const bindWorkspacePath = vi
      .fn()
      .mockResolvedValue({
        repositoryPath: 'D:/code/workspace-b',
        workspaceId: '3002',
        workspaceName: 'workspace-b',
      });
    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/workspace-a',
        workspaceId: '3001',
        permissionGranted: true,
      },
    };
    const { result } = renderHook(() =>
      useChatWorkspace(true, {
        hostContext,
        bindWorkspacePath,
      }),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      await result.current.startNewConversation();
    });
    expect(result.current.activeConversationId).toBeNull();

    await act(async () => {
      await result.current.setActiveWorkspacePath('D:/code/workspace-b');
    });

    expect(result.current.workspacePath).toBe('D:/code/workspace-b');
    expect(result.current.workspaceLabel).toBe('workspace-b');
    expect(result.current.activeConversationId).toBeNull();
    expect(requestUrls).toContain('/api/chat/conversations?workspaceId=3002');
  });

  /**
   * 技能输入应被序列化为结构化技能消息，避免将技能标记直接作为普通问题文本发送。
   */
  it('应在构建流请求时按技能类型生成结构化消息', () => {
    const requestUrl = buildStreamRequestUrl(
      '@sales_query 请分析订单趋势',
      '2001',
      '3001',
      false,
      true,
      ['sales_query'],
      ['sales_query'],
      undefined,
      ['9001', '9002'],
    );
    const searchParams = new URLSearchParams(requestUrl.split('?')[1] ?? '');

    expect(searchParams.get('question')).toBe('请分析订单趋势');
    expect(searchParams.get('workspaceId')).toBe('3001');
    expect(searchParams.get('skillCodes')).toBe('sales_query');
    expect(searchParams.get('attachmentIds')).toBe('9001,9002');
    const messagePayload = JSON.parse(searchParams.get('messages') ?? '[]');
    expect(messagePayload).toEqual([
      {
        type: 'slash_command',
        data: {
          id: '^/sales_query/SKILL.md',
          command: 'sales_query',
          command_type: 'skill',
          parameters: {
            argCount: 0,
            hasArgumentsVar: false,
            parameterValues: {},
          },
        },
      },
      {
        type: 'text',
        data: {
          content: '请分析订单趋势',
        },
      },
    ]);
  });

  /**
   * 当输入包含多个前缀 @skill 且用户已多选技能时，应保留所有结构化技能命令并透传显式 skillCodes 参数。
   */
  it('应在多技能前缀场景保留结构化技能并透传显式技能列表', () => {
    const requestUrl = buildStreamRequestUrl(
      '@agent-browser @ticket_query 请总结今天的工单趋势',
      '2001',
      '3001',
      false,
      false,
      [],
      ['sales_query', 'ticket_query'],
    );
    const searchParams = new URLSearchParams(requestUrl.split('?')[1] ?? '');
    expect(searchParams.get('question')).toBe('请总结今天的工单趋势');
    expect(searchParams.get('workspaceId')).toBe('3001');
    expect(searchParams.get('skillCodes')).toBe('sales_query,ticket_query');
    const messagePayload = JSON.parse(searchParams.get('messages') ?? '[]');
    expect(messagePayload).toEqual([
      {
        type: 'slash_command',
        data: {
          id: '^/agent-browser/SKILL.md',
          command: 'agent-browser',
          command_type: 'skill',
          parameters: {
            argCount: 0,
            hasArgumentsVar: false,
            parameterValues: {},
          },
        },
      },
      {
        type: 'slash_command',
        data: {
          id: '^/ticket_query/SKILL.md',
          command: 'ticket_query',
          command_type: 'skill',
          parameters: {
            argCount: 0,
            hasArgumentsVar: false,
            parameterValues: {},
          },
        },
      },
      {
        type: 'text',
        data: {
          content: '请总结今天的工单趋势',
        },
      },
    ]);
  });

  /**
   * 选中内置 Slash Command 时，应生成 builtin 类型结构化命令并把斜杠前缀从普通问题中剥离。
   */
  it('应在内置Slash Command场景生成builtin结构化消息', () => {
    const requestUrl = (buildStreamRequestUrl as any)(
      '/review 请审查当前改动',
      '2001',
      '3001',
      false,
      false,
      [],
      [],
      undefined,
      [],
      null,
      'local',
      {
        commandCode: 'review',
        displayName: '/review',
        commandType: 'BUILTIN',
      },
    );
    const searchParams = new URLSearchParams(requestUrl.split('?')[1] ?? '');

    expect(searchParams.get('question')).toBe('请审查当前改动');
    expect(searchParams.get('runtimeTarget')).toBe('local');
    const messagePayload = JSON.parse(searchParams.get('messages') ?? '[]');
    expect(messagePayload).toEqual([
      {
        type: 'slash_command',
        data: {
          command: 'review',
          command_type: 'builtin',
        },
      },
      {
        type: 'text',
        data: {
          content: '请审查当前改动',
        },
      },
    ]);
  });

  /**
   * 目标模式开启时应复用后端 planMode 通道，让桌面端请求进入目标跟进语境。
   */
  it('目标模式开启时应在流请求中携带planMode', () => {
    const requestUrl = (buildStreamRequestUrl as any)(
      '请按目标跟进这个大型改造',
      '2001',
      '3001',
      false,
      true,
      [],
      [],
      undefined,
      [],
      null,
      'local',
      null,
      null,
      true,
    );
    const searchParams = new URLSearchParams(requestUrl.split('?')[1] ?? '');

    expect(searchParams.get('planMode')).toBe('true');
  });

  /**
   * 普通模式必须保持既有请求语义，不应把 planMode 默认为 true。
   */
  it('目标模式关闭时不应在流请求中携带planMode', () => {
    const requestUrl = (buildStreamRequestUrl as any)(
      '普通对话',
      '2001',
      '3001',
      false,
      true,
      [],
      [],
      undefined,
      [],
      null,
      'cloud',
      null,
      null,
      false,
    );
    const searchParams = new URLSearchParams(requestUrl.split('?')[1] ?? '');

    expect(searchParams.has('planMode')).toBe(false);
  });

  /**
   * 本地模式应携带 workspaceId 归档到后端，同时保留目录作为工具执行上下文。
   */
  it('本地流式请求应标记local并携带workspaceId', () => {
    const requestUrl = buildStreamRequestUrl(
      '查看本地项目',
      'local-conversation-1',
      '3001',
      false,
      true,
      [],
      [],
      'agent',
      'D:/code/test',
      [],
      'local',
    );
    const searchParams = new URLSearchParams(requestUrl.split('?')[1] ?? '');

    expect(searchParams.get('runtimeTarget')).toBe('local');
    expect(searchParams.get('repositoryPath')).toBe('D:/code/test');
    expect(searchParams.get('workspaceId')).toBe('3001');
    expect(searchParams.has('conversationId')).toBe(false);
  });

  /**
   * 本地历史可能残留 local-* 客户端会话标识，流请求不得把它传给后端 Long 参数。
   */
  it('本地流式请求应保留数值会话并过滤非数值本地会话', () => {
    const numericRequestUrl = buildStreamRequestUrl(
      '继续查看本地项目',
      '9901',
      null,
      false,
      true,
      [],
      [],
      'agent',
      'D:/code/test',
      [],
      'local',
    );
    const localRequestUrl = buildStreamRequestUrl(
      '继续查看本地项目',
      'local-stale-1',
      null,
      false,
      true,
      [],
      [],
      'agent',
      'D:/code/test',
      [],
      'local',
    );

    expect(new URLSearchParams(numericRequestUrl.split('?')[1] ?? '').get('conversationId')).toBe('9901');
    expect(new URLSearchParams(localRequestUrl.split('?')[1] ?? '').has('conversationId')).toBe(false);
  });

  /**
   * PDF 导出必须生成浏览器 PDF 查看器可识别的二进制内容，避免把纯文本伪装成 PDF。
   */
  it('导出PDF时应生成合法PDF文件头', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '云端历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now(),
            activeConversationId: null,
            conversations: [
              {
                id: '2001',
                title: '上海今天天气',
                status: 'ACTIVE',
                lastRunId: '9001',
              },
            ],
            conversationRecords: {
              '2001': {
                owned: true,
                messages: [
                  {
                    id: 'm-1',
                    conversationId: '2001',
                    role: 'USER',
                    content: '上海今天天气',
                    status: 'COMPLETED',
                  },
                  {
                    id: 'm-2',
                    conversationId: '2001',
                    role: 'ASSISTANT',
                    content: '上海今天多云，适合出行。',
                    status: 'COMPLETED',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: '上海今天天气',
                status: 'ACTIVE',
                lastRunId: '9001',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in PDF export test: ${url}`);
    });

    const exportedBlobs: Blob[] = [];
    vi.spyOn(window.URL, 'createObjectURL').mockImplementation((blob) => {
      exportedBlobs.push(blob as Blob);
      return 'blob:pdf-export';
    });
    vi.spyOn(window.URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const anchorClicks: string[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function clickMock() {
      anchorClicks.push(this.download);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      await result.current.exportConversation('2001', 'pdf', {
        partitionKey: 'cloud::__no_workspace__',
        runtimeTarget: 'cloud',
        workspacePath: null,
      });
    });

    expect(anchorClicks).toEqual(['上海今天天气.pdf']);
    const pdfHeader = await exportedBlobs[0].slice(0, 5).text();
    expect(pdfHeader).toBe('%PDF-');
    expect(exportedBlobs[0]?.type).toBe('application/pdf');
  });

  /**
   * 停止后应立即终止当前流并保持已生成片段，避免旧流继续覆盖或清空消息。
   */
  it('应在停止生成后保留部分回答并标记为取消', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const encodedMeta = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001","taskId":"9001"}\n\n');
    const encodedDelta = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"第一段"}\n\n',
    );
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: 'Default Demo Conversation',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/experts') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        init?.signal?.addEventListener(
          'abort',
          () => {
            readQueue.splice(0).forEach((pending) =>
              pending.reject(new DOMException('Aborted', 'AbortError')),
            );
          },
          { once: true },
        );
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      if (url === '/api/chat/conversations/2001/cancel') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: null }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in cancel stream test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('你好');
    });
    await waitFor(() => {
      expect(result.current.inputValue).toBe('你好');
    });

    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      const firstRead = readQueue.shift();
      firstRead?.resolve({ done: false, value: encodedMeta });
    });

    await waitFor(() => {
      const runningConversation = result.current.conversations.find((item) => item.id === '2001');
      const runningSidebarConversation = result.current.workspaceGroups
        .flatMap((group) => group.conversations)
        .find((item) => item.id === '2001');
      expect(runningConversation?.activeTaskId).toBe('9001');
      expect(runningConversation?.activeTaskStatus).toBe('RUNNING');
      expect(runningSidebarConversation?.activeTaskStatus).toBe('RUNNING');
    });

    await act(async () => {
      const secondRead = readQueue.shift();
      secondRead?.resolve({ done: false, value: encodedDelta });
    });

    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.content).toBe('第一段');
      expect(assistantMessage?.status).toBe('streaming');
    });

    await act(async () => {
      await result.current.cancelCurrentStream();
    });

    await waitFor(() => {
      expect(mockReader.read).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(false);
      expect(result.current.streamError).toBe('已停止当前生成');
      const stoppedConversation = result.current.conversations.find((item) => item.id === '2001');
      const stoppedSidebarConversation = result.current.workspaceGroups
        .flatMap((group) => group.conversations)
        .find((item) => item.id === '2001');
      expect(stoppedConversation?.activeTaskId).toBeUndefined();
      expect(stoppedConversation?.activeTaskStatus).toBeUndefined();
      expect(stoppedSidebarConversation?.activeTaskStatus).toBeUndefined();
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.content).toBe('第一段');
      expect(assistantMessage?.status).toBe('cancelled');
    });

    await submitPromise;
  });

  /**
   * 重新生成完成后必须清理会话运行态；否则切换会话再返回时会误走续流恢复，主区重新出现“正在生成回答”。
   */
  it('重新生成完成后切换会话再返回不应恢复运行中续流', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const encodedMeta = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001","taskId":"9001"}\n\n');
    const encodedFinish = new TextEncoder().encode(
      'event:finish\ndata:{"conversationId":"2001","assistantMessageId":"103","content":"重新生成完成"}\n\n',
    );
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };
    const resumeStreamRequests: string[] = [];

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: '量子力学是什么',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
              {
                id: '2002',
                title: '另一个会话',
                status: 'ACTIVE',
                lastRunId: '5003',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '101',
                conversationId: '2001',
                role: 'USER',
                content: '量子力学是什么',
                status: 'COMPLETED',
              },
              {
                id: '102',
                conversationId: '2001',
                role: 'ASSISTANT',
                content: '旧回答',
                status: 'COMPLETED',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2002/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '201',
                conversationId: '2002',
                role: 'USER',
                content: '另一个问题',
                status: 'COMPLETED',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts' ||
        url === '/api/chat/conversations/2002/steps' ||
        url === '/api/chat/conversations/2002/references' ||
        url === '/api/chat/conversations/2002/artifacts' ||
        url === '/api/chat/conversations/2002/current-skills' ||
        url === '/api/chat/conversations/2002/current-mcps' ||
        url === '/api/chat/conversations/2002/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/messages/102') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: null }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      if (url === '/api/chat/conversations/2001/stream') {
        resumeStreamRequests.push(url);
        return new Response(
          'event:finish\ndata:{"conversationId":"2001","assistantMessageId":"103","content":"重新生成完成"}\n\n',
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in regenerate restore test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      await result.current.selectConversation('2001');
    });
    await waitFor(() => {
      expect(result.current.messages.find((message) => message.id === '102')?.content).toBe('旧回答');
    });

    let regeneratePromise: Promise<void> = Promise.resolve();
    await act(async () => {
      regeneratePromise = result.current.regenerateConversation('2001', { assistantMessageId: '102' });
    });

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: encodedMeta });
    });
    await waitFor(() => {
      const runningConversation = result.current.conversations.find((item) => item.id === '2001');
      expect(runningConversation?.activeTaskStatus).toBe('RUNNING');
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: encodedFinish });
    });
    await waitFor(() => {
      expect(readQueue.length).toBeGreaterThan(0);
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await regeneratePromise;

    await waitFor(() => {
      const finishedConversation = result.current.conversations.find((item) => item.id === '2001');
      expect(result.current.isStreaming).toBe(false);
      expect(finishedConversation?.activeTaskStatus).toBeUndefined();
      expect(result.current.messages.find((message) => message.id === '103')?.content).toBe('重新生成完成');
    });

    await act(async () => {
      await result.current.selectConversation('2002');
    });
    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2002');
    });

    await act(async () => {
      await result.current.selectConversation('2001');
    });

    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2001');
      expect(result.current.isStreaming).toBe(false);
      expect(resumeStreamRequests).toEqual([]);
      expect(result.current.messages.every((message) => message.status !== 'streaming')).toBe(true);
    });
  });

  /**
   * 旧快照可能在后台续流完成后仍残留空的 resumed-assistant streaming 占位；终态会话恢复时必须清掉。
   */
  it('恢复已完成会话快照时应移除残留的空流式助手占位', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    window.history.replaceState(window.history.state, '', '/?conversationId=2001');
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '云端历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now(),
            activeConversationId: '2001',
            conversations: [
              {
                id: '2001',
                title: '量子力学是什么',
                status: 'ACTIVE',
                lastRunId: '5002',
                lastTaskId: 'task-9001',
                lastTaskStatus: 'SUCCEEDED',
                lastTaskFinishedAt: '2026-06-01T19:57:03',
              },
            ],
            conversationRecords: {
              '2001': {
                owned: true,
                messages: [
                  {
                    id: '101',
                    conversationId: '2001',
                    role: 'USER',
                    content: '量子力学是什么',
                    status: 'COMPLETED',
                  },
                  {
                    id: '102',
                    conversationId: '2001',
                    role: 'ASSISTANT',
                    content: '量子力学是描述微观世界的基础理论。',
                    status: 'COMPLETED',
                  },
                  {
                    id: 'resumed-assistant-1780311672121',
                    conversationId: '2001',
                    role: 'ASSISTANT',
                    content: '',
                    processCards: [],
                    timelineItems: [],
                    status: 'streaming',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
            seenTaskFinishedAtByConversationId: {},
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: '量子力学是什么',
                status: 'ACTIVE',
                lastRunId: '5002',
                lastTaskId: 'task-9001',
                lastTaskStatus: 'SUCCEEDED',
                lastTaskFinishedAt: '2026-06-01T19:57:03',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in terminal snapshot restore test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
      expect(result.current.activeConversationId).toBe('2001');
    });

    expect(result.current.isStreaming).toBe(false);
    expect(result.current.messages.map((message) => message.id)).not.toContain('resumed-assistant-1780311672121');
    expect(result.current.messages.every((message) => message.status !== 'streaming')).toBe(true);

    const snapshotStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    const persistedMessages =
      snapshotStore?.snapshots?.['cloud::__no_workspace__']?.conversationRecords?.['2001']?.messages ?? [];
    expect(persistedMessages.map((message: { id: string }) => message.id)).not.toContain(
      'resumed-assistant-1780311672121',
    );
  });

  /**
   * 收到 queued 与 queue-accepted 事件时应展示并清理排队提示。
   */
  it('应在排队事件中展示并清理队列提示', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const queuedEvent = new TextEncoder().encode('event:queued\ndata:{"position":2}\n\n');
    const acceptedEvent = new TextEncoder().encode('event:queue-accepted\ndata:{"conversationId":"2001"}\n\n');
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"完成"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts' ||
        url === '/api/chat/conversations/pending-conversation/messages' ||
        url === '/api/chat/conversations/pending-conversation/steps' ||
        url === '/api/chat/conversations/pending-conversation/references' ||
        url === '/api/chat/conversations/pending-conversation/artifacts' ||
        url === '/api/chat/conversations/pending-conversation/current-skills' ||
        url === '/api/chat/conversations/pending-conversation/current-mcps' ||
        url === '/api/chat/conversations/pending-conversation/current-experts'
      ) {
        if (url === '/api/chat/conversations') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: '2001',
                  title: 'Default Demo Conversation',
                  status: 'ACTIVE',
                  lastRunId: '5002',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in queue events test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('你好');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: queuedEvent });
    });
    // 队列提示采用延迟展示：短暂抖动不应立即渲染，避免用户看到“闪一下”的黄色条。
    expect(result.current.streamQueueState).toBeNull();
    await waitFor(() => {
      expect(result.current.streamQueueState?.position).toBe(2);
      expect(result.current.streamQueueState?.message).toContain('前方还有 2 个会话');
    }, { timeout: 1200 });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: acceptedEvent });
    });
    await waitFor(() => {
      expect(result.current.streamQueueState).toBeNull();
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await submitPromise;
  });

  /**
   * queued 后若很快收到 queue-accepted，不应展示排队提示闪烁。
   */
  it('应在queued快速被accepted覆盖时不展示排队提示', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const queuedEvent = new TextEncoder().encode('event:queued\ndata:{"position":1}\n\n');
    const acceptedEvent = new TextEncoder().encode('event:queue-accepted\ndata:{"conversationId":"2001"}\n\n');
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"完成"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts' ||
        url === '/api/chat/conversations/pending-conversation/messages' ||
        url === '/api/chat/conversations/pending-conversation/steps' ||
        url === '/api/chat/conversations/pending-conversation/references' ||
        url === '/api/chat/conversations/pending-conversation/artifacts' ||
        url === '/api/chat/conversations/pending-conversation/current-skills' ||
        url === '/api/chat/conversations/pending-conversation/current-mcps' ||
        url === '/api/chat/conversations/pending-conversation/current-experts'
      ) {
        if (url === '/api/chat/conversations') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: '2001',
                  title: 'Default Demo Conversation',
                  status: 'ACTIVE',
                  lastRunId: '5002',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in queue flash test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('你好');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: queuedEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: acceptedEvent });
    });
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(result.current.streamQueueState).toBeNull();

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await submitPromise;
  });

  /**
   * 联网搜索事件应把来源条目实时写入当前助手消息，驱动消息内进度面板递增显示。
   */
  it('应在reference事件中递增更新助手消息搜索进度', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001"}\n\n');
    const firstMessageEvent = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"我先查看今天公开来源里的科技新闻。\\n\\n"}\n\n',
    );
    const searchStepEvent = new TextEncoder().encode(
      'event:step\ndata:{"id":"step-1","runId":"5002","stepType":"search","stepTitle":"搜索资料","stepStatus":"COMPLETED","sequenceNo":1}\n\n',
    );
    const referenceOneEvent = new TextEncoder().encode(
      'event:reference\ndata:{"id":"ref-1","runId":"5002","conversationId":"2001","title":"OpenAI API 最新文档","url":"https://platform.openai.com","siteName":"OpenAI","rankNo":1}\n\n',
    );
    const referenceTwoEvent = new TextEncoder().encode(
      'event:reference\ndata:{"id":"ref-2","runId":"5002","conversationId":"2001","title":"Bing Search API 文档","url":"https://learn.microsoft.com","siteName":"Microsoft Learn","rankNo":2}\n\n',
    );
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"检索完成"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        if (url === '/api/chat/conversations') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: '2001',
                  title: 'Default Demo Conversation',
                  status: 'ACTIVE',
                  lastRunId: '5002',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url.endsWith('/messages') ||
        url.endsWith('/steps') ||
        url.endsWith('/references') ||
        url.endsWith('/artifacts') ||
        url.endsWith('/current-skills') ||
        url.endsWith('/current-mcps') ||
        url.endsWith('/current-experts')
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in search progress sse test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('请搜索最新 API 信息');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: metaEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: firstMessageEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.content).toBe('我先查看今天公开来源里的科技新闻。\n\n');
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: searchStepEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.searchProgress?.status).toBe('running');
      expect(assistantMessage?.searchProgress?.items).toHaveLength(0);
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      expect(processCards.map((card) => card.type)).toEqual(['tool_call']);
      expect(processCards.some((card) => String(card.summary).includes('需要通过网页搜索确认资料'))).toBe(false);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: referenceOneEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.searchProgress?.items).toHaveLength(1);
      expect(assistantMessage?.searchProgress?.items[0].title).toBe('OpenAI API 最新文档');
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      expect(processCards.map((card) => card.type)).toEqual(['tool_call', 'tool_result']);
      expect(processCards.map((card) => card.presentation)).toEqual(['react', 'react']);
      expect(processCards.map((card) => card.summary)).toEqual([
        '调用网页搜索：OpenAI API 最新文档',
        'OpenAI：OpenAI API 最新文档',
      ]);
      expect(processCards.some((card) => String(card.summary).includes('需要通过网页搜索确认资料'))).toBe(false);
      const timelineItems = ((assistantMessage as Record<string, unknown> | undefined)?.timelineItems ?? []) as Array<Record<string, unknown>>;
      expect(timelineItems.map((item) => item.type)).toEqual(['content', 'process', 'process']);
      expect(timelineItems[0].content).toBe('我先查看今天公开来源里的科技新闻。\n\n');
      expect((timelineItems[1].card as Record<string, unknown>).id).toBe('process-step-1');
      expect((timelineItems[2].card as Record<string, unknown>).id).toBe('search-result-ref-1');
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: referenceTwoEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.searchProgress?.items).toHaveLength(2);
      expect(assistantMessage?.searchProgress?.items[1].title).toBe('Bing Search API 文档');
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      const searchResultCards = processCards.filter((card) => card.type === 'tool_result' && card.toolId === 'search');
      expect(searchResultCards.map((card) => card.summary)).toEqual([
        'OpenAI：OpenAI API 最新文档',
        'Microsoft Learn：Bing Search API 文档',
      ]);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.searchProgress?.status).toBe('completed');
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * 多段 thinking 增量应合并成稳定摘要，不能只显示最新片段导致文案像跑马灯一样滚动。
   */
  it('应累积thinking增量生成分析过程摘要', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const thinkingOneEvent = new TextEncoder().encode(
      'event:thinking\ndata:{"type":"thinking","delta":"先判断问题是否需要实时信息。"}\n\n',
    );
    const thinkingTwoEvent = new TextEncoder().encode(
      'event:thinking\ndata:{"type":"thinking","delta":"再决定调用搜索工具补充证据。"}\n\n',
    );
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"分析完成"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/pending-conversation/messages' ||
        url === '/api/chat/conversations/pending-conversation/steps' ||
        url === '/api/chat/conversations/pending-conversation/references' ||
        url === '/api/chat/conversations/pending-conversation/artifacts' ||
        url === '/api/chat/conversations/pending-conversation/current-experts' ||
        url === '/api/chat/conversations/pending-conversation/current-skills' ||
        url === '/api/chat/conversations/pending-conversation/current-mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in thinking accumulation test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      result.current.setInputValue('qwen和glm最新的模型是什么');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: thinkingOneEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: thinkingTwoEvent });
    });

    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      const thinkingCard = processCards.find((card) => card.id === 'analysis-thinking');
      expect(thinkingCard?.summary).toContain('先判断问题是否需要实时信息');
      expect(thinkingCard?.summary).toContain('再决定调用搜索工具补充证据');
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * 没有真实 thinking/tool 事件前，不应伪造分析过程，避免把前端占位文案误当成模型思考。
   */
  it('应避免在真实过程事件前展示固定分析占位', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"直接回答"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/pending-conversation/messages' ||
        url === '/api/chat/conversations/pending-conversation/steps' ||
        url === '/api/chat/conversations/pending-conversation/references' ||
        url === '/api/chat/conversations/pending-conversation/artifacts' ||
        url === '/api/chat/conversations/pending-conversation/current-experts' ||
        url === '/api/chat/conversations/pending-conversation/current-skills' ||
        url === '/api/chat/conversations/pending-conversation/current-mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in fixed analysis placeholder test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      result.current.setInputValue('你好');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    const streamingAssistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
    const processCards = streamingAssistantMessage?.processCards ?? [];
    expect(processCards).toHaveLength(0);
    expect(
      processCards.some((card) =>
        card.summary.includes('我会先判断这个问题需要哪些信息，再决定直接回答还是调用工具补充证据。'),
      ),
    ).toBe(false);

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * 工具结果之后继续产生的 thinking 应作为新的分析段追加到工具过程后，并完整保留原文。
   */
  it('应将工具结果后的thinking完整追加到工具过程之后', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const preToolThinking = '我先判断这个问题需要实时检索来确认最新模型。';
    const postToolThinking =
      '我需要根据检索到的证据来回答关于Qwen和GLM最新模型的问题，并进行对比。让我分析一下检索结果中关于这两个系列最新模型的信息。Qwen最新模型需要结合官方发布、模型定位、上下文长度、代码能力、工具调用能力、开源与闭源边界逐项核对；GLM最新模型也需要按同样维度整理，再给出差异。';
    const thinkingBeforeToolEvent = new TextEncoder().encode(
      `event:thinking\ndata:${JSON.stringify({ type: 'thinking', delta: preToolThinking })}\n\n`,
    );
    const searchStepEvent = new TextEncoder().encode(
      'event:step\ndata:{"id":"search-qwen-glm","runId":"5004","stepType":"search","stepTitle":"搜索子问题 1","stepStatus":"COMPLETED","sequenceNo":1,"content":"Qwen和GLM最新模型是什么"}\n\n',
    );
    const referenceEvent = new TextEncoder().encode(
      'event:reference\ndata:{"id":"ref-qwen-glm","runId":"5004","conversationId":"2004","title":"Qwen 与 GLM 最新模型信息","url":"https://example.com/models","siteName":"模型资料站","rankNo":1}\n\n',
    );
    const thinkingAfterToolEvent = new TextEncoder().encode(
      `event:thinking\ndata:${JSON.stringify({ type: 'thinking', delta: postToolThinking })}\n\n`,
    );
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"对比完成"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/pending-conversation/messages' ||
        url === '/api/chat/conversations/pending-conversation/steps' ||
        url === '/api/chat/conversations/pending-conversation/references' ||
        url === '/api/chat/conversations/pending-conversation/artifacts' ||
        url === '/api/chat/conversations/pending-conversation/current-experts' ||
        url === '/api/chat/conversations/pending-conversation/current-skills' ||
        url === '/api/chat/conversations/pending-conversation/current-mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in post-tool thinking test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      result.current.setInputValue('qwen和glm最新的模型是什么,帮我对比一下有什么区别');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });
    for (const event of [
      thinkingBeforeToolEvent,
      searchStepEvent,
      referenceEvent,
      thinkingAfterToolEvent,
    ]) {
      await act(async () => {
        readQueue.shift()?.resolve({ done: false, value: event });
      });
    }

    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      const searchResultIndex = processCards.findIndex((card) => card.type === 'tool_result' && card.toolId === 'search');
      const postToolThinkingIndex = processCards.findIndex((card) => card.id === 'analysis-after-tools');
      expect(searchResultIndex).toBeGreaterThan(-1);
      expect(postToolThinkingIndex).toBeGreaterThan(searchResultIndex);
      expect(processCards[searchResultIndex]?.summary).toBe('模型资料站：Qwen 与 GLM 最新模型信息');
      expect(processCards[postToolThinkingIndex]?.summary).toBe(postToolThinking);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * MCP 两阶段事件应按 callId 合并为同一条调用记录，开始即展示运行态，完成后更新原始结果。
   */
  it('应按callId合并mcp调用开始与完成事件', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001"}\n\n');
    const mcpStartEvent = new TextEncoder().encode(
      'event:mcp-call\ndata:{"callId":"call-1","phase":"start","toolId":"weather_query","displayName":"天气查询","params":{"city":"北京"},"startedAt":"2026-05-21T22:05:00"}\n\n',
    );
    const mcpProgressEvent = new TextEncoder().encode(
      'event:mcp-call\ndata:{"callId":"call-1","phase":"progress","toolId":"weather_query","displayName":"天气查询","progressText":"正在查询天气服务"}\n\n',
    );
    const mcpCompleteEvent = new TextEncoder().encode(
      'event:mcp-call\ndata:{"callId":"call-1","phase":"complete","toolId":"weather_query","displayName":"天气查询","rawResult":{"text":"北京今日晴"},"resultMetadata":{"source":"open-meteo"},"finishedAt":"2026-05-21T22:05:01"}\n\n',
    );
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"调用完成"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        if (url === '/api/chat/conversations') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: '2001',
                  title: 'Default Demo Conversation',
                  status: 'ACTIVE',
                  lastRunId: '5002',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in mcp call merge test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      result.current.setInputValue('请查北京天气');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: metaEvent });
    });
    await waitFor(() => {
      expect(readQueue.length).toBeGreaterThan(0);
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: mcpStartEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const calls = ((assistantMessage?.mcpCalls ?? []) as Array<Record<string, unknown>>);
      expect(calls).toHaveLength(1);
      expect(calls[0].callId).toBe('call-1');
      expect(calls[0].status).toBe('running');
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      expect(processCards.map((card) => card.title)).toEqual(
        expect.arrayContaining(['调用天气查询']),
      );
      expect(processCards.some((card) => card.type === 'analysis')).toBe(false);
      expect(processCards.some((card) => card.summary === '正在查询天气服务')).toBe(false);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: mcpProgressEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const calls = ((assistantMessage?.mcpCalls ?? []) as Array<Record<string, unknown>>);
      expect(calls).toHaveLength(1);
      expect(calls[0].callId).toBe('call-1');
      expect(calls[0].phase).toBe('progress');
      expect(calls[0].status).toBe('running');
      expect(calls[0].progressText).toBe('正在查询天气服务');
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      const toolCallCard = processCards.find((card) => card.type === 'tool_call');
      expect(toolCallCard?.summary).toBe('正在查询天气服务');
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: mcpCompleteEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const calls = ((assistantMessage?.mcpCalls ?? []) as Array<Record<string, unknown>>);
      expect(calls).toHaveLength(1);
      expect(calls[0].callId).toBe('call-1');
      expect(calls[0].status).toBe('completed');
      expect(calls[0].rawResult).toEqual({ text: '北京今日晴' });
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      expect(processCards.map((card) => card.title)).toEqual(
        expect.arrayContaining(['调用天气查询', '已获取结果']),
      );
      expect(processCards.some((card) => String(card.summary).includes('整理最终回答'))).toBe(false);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await waitFor(() => {
      expect(readQueue.length).toBeGreaterThan(0);
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * Generic local tool events reuse the message process chain so shell/apply_patch calls stay visible.
   */
  it('merges tool-call events into local tool process cards', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001"}\n\n');
    const toolStartEvent = new TextEncoder().encode(
      'event:tool-call\ndata:{"callId":"local-call-1","phase":"start","toolId":"shell_command","displayName":"shell_command","params":{"command":"pwd"},"input":"{\\"command\\":\\"pwd\\"}","reactThought":"需要调用 shell_command 获取当前目录。","reactAction":"调用 shell_command","startedAt":"2026-05-26T18:11:08"}\n\n',
    );
    const toolCompleteEvent = new TextEncoder().encode(
      'event:tool-call\ndata:{"callId":"local-call-1","phase":"complete","toolId":"shell_command","displayName":"shell_command","content":"D:/code/CodingX","rawResult":"D:/code/CodingX","reactObservation":"工具返回：D:/code/CodingX","resultMetadata":{"exitCode":0},"finishedAt":"2026-05-26T18:11:09"}\n\n',
    );
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"Current directory is D:/code/CodingX"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        if (url === '/api/chat/conversations') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: '2001',
                  title: 'Default Demo Conversation',
                  status: 'ACTIVE',
                  lastRunId: '5002',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in local tool-call stream test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      result.current.setInputValue('show current directory');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: metaEvent });
    });
    await waitFor(() => {
      expect(readQueue.length).toBeGreaterThan(0);
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: toolStartEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      expect(processCards.some((card) => card.type === 'analysis')).toBe(false);
      expect(processCards.some((card) => String(card.summary).includes('需要调用'))).toBe(false);
      const toolCallCard = processCards.find((card) => card.type === 'tool_call');
      expect(toolCallCard?.toolId).toBe('shell_command');
      expect(toolCallCard?.summary).toBe('调用 shell_command');
      expect(toolCallCard?.presentation).toBe('react');
      expect((toolCallCard?.details as Array<Record<string, unknown>> | undefined)?.[0]?.content).toContain('pwd');
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: toolCompleteEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const calls = ((assistantMessage?.mcpCalls ?? []) as Array<Record<string, unknown>>);
      expect(calls).toHaveLength(1);
      expect(calls[0].callId).toBe('local-call-1');
      expect(calls[0].status).toBe('completed');
      expect(calls[0].rawResult).toBe('D:/code/CodingX');
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      expect(processCards.map((card) => card.type)).toEqual(expect.arrayContaining(['tool_call', 'tool_result']));
      expect(processCards.some((card) => card.type === 'analysis')).toBe(false);
      expect(processCards.some((card) => String(card.summary).includes('需要调用'))).toBe(false);
      const toolResultCard = processCards.find((card) => card.type === 'tool_result');
      expect(toolResultCard?.summary).toBe('工具返回：D:/code/CodingX');
      expect(toolResultCard?.presentation).toBe('react');
      expect((toolResultCard?.details as Array<Record<string, unknown>> | undefined)?.[0]?.content).toContain('D:/code/CodingX');
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await waitFor(() => {
      expect(readQueue.length).toBeGreaterThan(0);
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * 正文与工具事件必须保留 SSE 到达顺序，供主消息区按 Codex 风格穿插展示。
   */
  it('按流式事件顺序记录正文与工具过程时间线', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001"}\n\n');
    const firstMessageEvent = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"我先检查当前目录。\\n\\n"}\n\n',
    );
    const toolStartEvent = new TextEncoder().encode(
      'event:tool-call\ndata:{"callId":"timeline-call-1","phase":"start","toolId":"shell_command","displayName":"shell_command","params":{"command":"pwd"},"reactThought":"需要调用 shell_command 获取当前目录。","reactAction":"调用 shell_command","startedAt":"2026-05-26T18:11:08"}\n\n',
    );
    const toolCompleteEvent = new TextEncoder().encode(
      'event:tool-call\ndata:{"callId":"timeline-call-1","phase":"complete","toolId":"shell_command","displayName":"shell_command","content":"D:/code/CodingX","rawResult":"D:/code/CodingX","reactObservation":"工具返回：D:/code/CodingX","resultMetadata":{"exitCode":0},"finishedAt":"2026-05-26T18:11:09"}\n\n',
    );
    const secondMessageEvent = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"我再根据结果继续分析。"}\n\n',
    );
    const finishEvent = new TextEncoder().encode(
      'event:finish\ndata:{"content":"我先检查当前目录。\\n\\n我再根据结果继续分析。"}\n\n',
    );
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        if (url === '/api/chat/conversations') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: '2001',
                  title: 'Default Demo Conversation',
                  status: 'ACTIVE',
                  lastRunId: '5002',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in timeline stream test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      result.current.setInputValue('show current directory');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    for (const event of [
      metaEvent,
      firstMessageEvent,
      toolStartEvent,
      toolCompleteEvent,
      secondMessageEvent,
    ]) {
      await act(async () => {
        readQueue.shift()?.resolve({ done: false, value: event });
      });
      await waitFor(() => {
        expect(readQueue.length).toBeGreaterThan(0);
      });
    }

    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const timelineItems = ((assistantMessage as Record<string, unknown> | undefined)?.timelineItems ?? []) as Array<Record<string, unknown>>;
      expect(assistantMessage?.content).toBe('我先检查当前目录。\n\n我再根据结果继续分析。');
      expect(timelineItems.map((item) => item.type)).toEqual([
        'content',
        'process',
        'process',
        'content',
      ]);
      expect(timelineItems[0].content).toBe('我先检查当前目录。\n\n');
      expect((timelineItems[1].card as Record<string, unknown>).id).toBe('tool-call-timeline-call-1');
      expect((timelineItems[2].card as Record<string, unknown>).id).toBe('tool-result-timeline-call-1');
      expect(timelineItems.some((item) => String((item.card as Record<string, unknown> | undefined)?.summary).includes('需要调用'))).toBe(false);
      expect(timelineItems[3].content).toBe('我再根据结果继续分析。');
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const timelineItems = ((assistantMessage as Record<string, unknown> | undefined)?.timelineItems ?? []) as Array<Record<string, unknown>>;
      expect(assistantMessage?.content).toBe('我先检查当前目录。\n\n我再根据结果继续分析。');
      expect(timelineItems.map((item) => item.type)).toEqual([
        'content',
        'process',
        'process',
        'content',
      ]);
      expect(timelineItems[0].content).toBe('我先检查当前目录。\n\n');
      expect((timelineItems[1].card as Record<string, unknown>).id).toBe('tool-call-timeline-call-1');
      expect((timelineItems[2].card as Record<string, unknown>).id).toBe('tool-result-timeline-call-1');
      expect(timelineItems[3].content).toBe('我再根据结果继续分析。');
    });
    await waitFor(() => {
      expect(readQueue.length).toBeGreaterThan(0);
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * AC-010：finish 后立即回放同一会话时，空历史正文只能补面板字段，不能覆盖本地流式正文或压扁时间线。
   */
  it('AC-010 finish后立即选择同一会话应保留本地流式正文与timeline', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001"}\n\n');
    const firstMessageEvent = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"我先检查当前目录。\\n\\n"}\n\n',
    );
    const toolStartEvent = new TextEncoder().encode(
      'event:tool-call\ndata:{"callId":"timeline-call-1","phase":"start","toolId":"shell_command","displayName":"shell_command","params":{"command":"pwd"},"reactAction":"调用 shell_command","startedAt":"2026-05-26T18:11:08"}\n\n',
    );
    const toolCompleteEvent = new TextEncoder().encode(
      'event:tool-call\ndata:{"callId":"timeline-call-1","phase":"complete","toolId":"shell_command","displayName":"shell_command","content":"D:/code/CodingX","rawResult":"D:/code/CodingX","reactObservation":"工具返回：D:/code/CodingX","resultMetadata":{"exitCode":0},"finishedAt":"2026-05-26T18:11:09"}\n\n',
    );
    const secondMessageEvent = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"我再根据结果继续分析。"}\n\n',
    );
    const finishEvent = new TextEncoder().encode(
      'event:finish\ndata:{"conversationId":"2001","content":"我先检查当前目录。\\n\\n我再根据结果继续分析。","title":"检查当前目录"}\n\n',
    );
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };
    let listConversationCallCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        listConversationCallCount += 1;
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data:
              listConversationCallCount > 1
                ? [
                    {
                      id: '2001',
                      title: '检查当前目录',
                      status: 'ACTIVE',
                      lastRunId: '5002',
                    },
                  ]
                : [],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'assistant-2001',
                conversationId: '2001',
                role: 'ASSISTANT',
                content: '',
                status: 'COMPLETED',
                processCards: [
                  {
                    id: 'replay-analysis',
                    type: 'analysis',
                    title: '分析问题',
                    summary: '已恢复历史上下文',
                    status: 'completed',
                  },
                ],
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/steps') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'step-1',
                runId: '5002',
                stepType: 'analysis',
                stepTitle: '回放补充过程',
                stepStatus: 'COMPLETED',
                sequenceNo: 1,
                content: '历史接口只补充过程字段',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in AC-010 stream replay test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      result.current.setInputValue('show current directory');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    for (const event of [
      metaEvent,
      firstMessageEvent,
      toolStartEvent,
      toolCompleteEvent,
      secondMessageEvent,
      finishEvent,
    ]) {
      await act(async () => {
        readQueue.shift()?.resolve({ done: false, value: event });
      });
      await waitFor(() => {
        expect(readQueue.length).toBeGreaterThan(0);
      });
    }

    await act(async () => {
      await result.current.selectConversation('2001', result.current.conversations);
    });

    const assertAssistantReplayState = () => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      const timelineItems = ((assistantMessage as Record<string, unknown> | undefined)?.timelineItems ?? []) as Array<Record<string, unknown>>;
      expect(assistantMessage?.content).toBe('我先检查当前目录。\n\n我再根据结果继续分析。');
      expect(processCards.map((card) => card.id)).toEqual(
        expect.arrayContaining(['tool-call-timeline-call-1', 'tool-result-timeline-call-1']),
      );
      expect(processCards.some((card) => String(card.summary).includes('已恢复历史'))).toBe(false);
      expect(timelineItems.map((item) => item.type)).toEqual([
        'content',
        'process',
        'process',
        'content',
      ]);
      expect(timelineItems[0].content).toBe('我先检查当前目录。\n\n');
      expect((timelineItems[1].card as Record<string, unknown>).id).toBe('tool-call-timeline-call-1');
      expect((timelineItems[2].card as Record<string, unknown>).id).toBe('tool-result-timeline-call-1');
      expect(timelineItems[3].content).toBe('我再根据结果继续分析。');
    };

    await waitFor(assertAssistantReplayState);

    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });

    await waitFor(assertAssistantReplayState);
  });

  /**
   * 同一会话回放时，如果服务端返回的 assistant 正文与本地流式正文等长但缺少 timeline，
   * 仍应保留本地已形成的正文/工具调用穿插顺序，不能退化成 processCards 顶置。
   */
  it('AC-010A 同长度正文回放缺少timeline时应保留本地timeline', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001"}\n\n');
    const firstMessageEvent = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"我先检查当前目录。\\n\\n"}\n\n',
    );
    const toolStartEvent = new TextEncoder().encode(
      'event:tool-call\ndata:{"callId":"timeline-call-1","phase":"start","toolId":"shell_command","displayName":"shell_command","params":{"command":"pwd"},"reactAction":"调用 shell_command","startedAt":"2026-05-26T18:11:08"}\n\n',
    );
    const toolCompleteEvent = new TextEncoder().encode(
      'event:tool-call\ndata:{"callId":"timeline-call-1","phase":"complete","toolId":"shell_command","displayName":"shell_command","content":"D:/code/CodingX","rawResult":"D:/code/CodingX","reactObservation":"工具返回：D:/code/CodingX","resultMetadata":{"exitCode":0},"finishedAt":"2026-05-26T18:11:09"}\n\n',
    );
    const secondMessageEvent = new TextEncoder().encode(
      'event:message\ndata:{"type":"response","delta":"我再根据结果继续分析。"}\n\n',
    );
    const finishEvent = new TextEncoder().encode(
      'event:finish\ndata:{"content":"我先检查当前目录。\\n\\n我再根据结果继续分析。"}\n\n',
    );
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };
    let listConversationCallCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        listConversationCallCount += 1;
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data:
              listConversationCallCount > 1
                ? [
                    {
                      id: '2001',
                      title: '测试会话',
                      updatedAt: '2026-05-26T18:11:09',
                      createdAt: '2026-05-26T18:11:00',
                    },
                  ]
                : [],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      if (url === '/api/chat/conversations/2001/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'assistant-server-1',
                role: 'ASSISTANT',
                conversationId: '2001',
                content: '我先检查当前目录。\n\n我再根据结果继续分析。',
                processCards: [
                  {
                    id: 'tool-call-timeline-call-1',
                    type: 'tool_call',
                    title: '调用 shell_command',
                    summary: '调用 shell_command',
                    status: 'completed',
                    toolId: 'shell_command',
                    displayName: 'shell_command',
                  },
                  {
                    id: 'tool-result-timeline-call-1',
                    type: 'tool_result',
                    title: '已获取结果',
                    summary: '工具返回：D:/code/CodingX',
                    status: 'completed',
                    toolId: 'shell_command',
                    displayName: 'shell_command',
                  },
                ],
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-experts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in equal-content replay test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      result.current.setInputValue('show current directory');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    for (const event of [
      metaEvent,
      firstMessageEvent,
      toolStartEvent,
      toolCompleteEvent,
      secondMessageEvent,
      finishEvent,
    ]) {
      await act(async () => {
        readQueue.shift()?.resolve({ done: false, value: event });
      });
      await waitFor(() => {
        expect(readQueue.length).toBeGreaterThan(0);
      });
    }

    await act(async () => {
      await result.current.selectConversation('2001', [
        {
          id: '2001',
          title: '测试会话',
          updatedAt: '2026-05-26T18:11:09',
          createdAt: '2026-05-26T18:11:00',
        },
      ]);
    });

    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const timelineItems = ((assistantMessage as Record<string, unknown> | undefined)?.timelineItems ?? []) as Array<Record<string, unknown>>;
      expect(assistantMessage?.content).toBe('我先检查当前目录。\n\n我再根据结果继续分析。');
      expect(timelineItems.map((item) => item.type)).toEqual([
        'content',
        'process',
        'process',
        'content',
      ]);
      expect(timelineItems[0].content).toBe('我先检查当前目录。\n\n');
      expect((timelineItems[1].card as Record<string, unknown>).id).toBe('tool-call-timeline-call-1');
      expect((timelineItems[2].card as Record<string, unknown>).id).toBe('tool-result-timeline-call-1');
      expect(timelineItems[3].content).toBe('我再根据结果继续分析。');
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * 流式结束后即使后端消息回放未返回 MCP/搜索字段，也应保留并持久化当前会话面板数据。
   */
  it('应在流式回放后保留MCP调用与搜索进度并写入本地快照', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001"}\n\n');
    const searchStepEvent = new TextEncoder().encode(
      'event:step\ndata:{"id":"step-1","runId":"5002","stepType":"search","stepTitle":"搜索资料","stepStatus":"COMPLETED","sequenceNo":1}\n\n',
    );
    const referenceEvent = new TextEncoder().encode(
      'event:reference\ndata:{"id":"ref-1","runId":"5002","conversationId":"2001","title":"OpenAI API 最新文档","url":"https://platform.openai.com","siteName":"OpenAI","rankNo":1}\n\n',
    );
    const mcpStartEvent = new TextEncoder().encode(
      'event:mcp-call\ndata:{"callId":"call-1","phase":"start","toolId":"weather_query","displayName":"天气查询","params":{"city":"北京"},"startedAt":"2026-05-21T22:05:00"}\n\n',
    );
    const mcpProgressEvent = new TextEncoder().encode(
      'event:mcp-call\ndata:{"callId":"call-1","phase":"progress","toolId":"weather_query","displayName":"天气查询","progressText":"正在查询天气服务"}\n\n',
    );
    const mcpCompleteEvent = new TextEncoder().encode(
      'event:mcp-call\ndata:{"callId":"call-1","phase":"complete","toolId":"weather_query","displayName":"天气查询","rawResult":{"text":"北京今日晴"},"resultMetadata":{"source":"open-meteo"},"finishedAt":"2026-05-21T22:05:01"}\n\n',
    );
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"检索完成"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };
    let listConversationCallCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        listConversationCallCount += 1;
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data:
              listConversationCallCount > 1
                ? [
                    {
                        id: '2001',
                        title: 'Default Demo Conversation',
                        status: 'ACTIVE',
                        lastRunId: '5002',
                      },
                  ]
                : [],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'assistant-2001',
                conversationId: '2001',
                role: 'ASSISTANT',
                content: '检索完成',
                status: 'COMPLETED',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/steps') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'step-1',
                runId: '5002',
                stepType: 'search',
                stepTitle: '搜索资料',
                stepStatus: 'COMPLETED',
                sequenceNo: 1,
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/references') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'ref-1',
                runId: '5002',
                conversationId: '2001',
                sourceType: 'web',
                title: 'OpenAI API 最新文档',
                url: 'https://platform.openai.com',
                siteName: 'OpenAI',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2001/current-mcps' ||
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in replay persistence test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('请帮我联网搜索北京天气');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: metaEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: searchStepEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: referenceEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: mcpStartEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: mcpProgressEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: mcpCompleteEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });

    const latestAssistantMessage = [...result.current.messages]
      .reverse()
      .find((item) => item.role === 'ASSISTANT');
    expect(latestAssistantMessage?.mcpCalls?.length).toBe(1);
    expect(latestAssistantMessage?.mcpCalls?.[0].callId).toBe('call-1');
    expect(latestAssistantMessage?.mcpCalls?.[0].status).toBe('completed');
    expect(latestAssistantMessage?.mcpCalls?.[0].phase).toBe('complete');
    expect(latestAssistantMessage?.searchProgress?.status).toBe('completed');
    expect(latestAssistantMessage?.searchProgress?.items).toHaveLength(1);
    expect(latestAssistantMessage?.searchProgress?.items[0].title).toBe('OpenAI API 最新文档');
    expect(latestAssistantMessage?.searchProgress?.items[0].siteName).toBe('OpenAI');
    const processCards = ((latestAssistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
    expect(processCards.map((card) => card.title)).toEqual(
      expect.arrayContaining(['调用天气查询', '已获取结果']),
    );
    expect(processCards.some((card) => card.type === 'analysis' && card.presentation === 'react')).toBe(false);
    expect(processCards.some((card) => String(card.summary).includes('需要通过网页搜索确认资料'))).toBe(false);
    expect(processCards.some((card) => String(card.summary).includes('已恢复历史'))).toBe(false);

    const snapshotStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    const persistedMessages =
      snapshotStore?.snapshots?.['cloud::__no_workspace__']?.conversationRecords?.['2001']?.messages ?? [];
    const persistedAssistantMessage = [...persistedMessages]
      .reverse()
      .find((item: Record<string, unknown>) => item.role === 'ASSISTANT');
    expect(persistedAssistantMessage?.mcpCalls?.length).toBe(1);
    expect(persistedAssistantMessage?.searchProgress?.status).toBe('completed');
    expect((persistedAssistantMessage?.processCards ?? []).length).toBeGreaterThanOrEqual(3);
  });

  /**
   * 流式结束后的接口回放不能把本地已形成的多条搜索子问题过程压缩成单条历史搜索兜底过程。
   */
  it('应在流式回放后保留多条搜索子问题过程', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2002"}\n\n');
    const searchStepOneEvent = new TextEncoder().encode(
      'event:step\ndata:{"id":"search-qwen","runId":"5003","stepType":"search","stepTitle":"搜索子问题 1","stepStatus":"COMPLETED","sequenceNo":1,"content":"Qwen最新发布的模型是什么"}\n\n',
    );
    const searchStepTwoEvent = new TextEncoder().encode(
      'event:step\ndata:{"id":"search-glm","runId":"5003","stepType":"search","stepTitle":"搜索子问题 2","stepStatus":"COMPLETED","sequenceNo":2,"content":"GLM最新发布的模型是什么"}\n\n',
    );
    const searchStepThreeEvent = new TextEncoder().encode(
      'event:step\ndata:{"id":"search-diff","runId":"5003","stepType":"search","stepTitle":"搜索子问题 3","stepStatus":"COMPLETED","sequenceNo":3,"content":"Qwen和GLM最新模型的区别"}\n\n',
    );
    const referenceEvent = new TextEncoder().encode(
      'event:reference\ndata:{"id":"ref-qwen-glm","runId":"5003","conversationId":"2002","title":"Qwen3 Code Plus & 智谱GLM4.7 模型基础功能对比","url":"https://example.com/models","siteName":"博客园","rankNo":1}\n\n',
    );
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"检索完成"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };
    let listConversationCallCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        listConversationCallCount += 1;
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data:
              listConversationCallCount > 1
                ? [
                    {
                      id: '2002',
                      title: 'qwen和glm最新模型',
                      status: 'ACTIVE',
                      lastRunId: '5003',
                    },
                  ]
                : [],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2002/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'assistant-2002',
                conversationId: '2002',
                role: 'ASSISTANT',
                content: '检索完成',
                status: 'COMPLETED',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2002/steps') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'search-qwen',
                runId: '5003',
                stepType: 'search',
                stepTitle: '搜索子问题 1',
                stepStatus: 'COMPLETED',
                sequenceNo: 1,
                content: 'Qwen最新发布的模型是什么',
              },
              {
                id: 'search-glm',
                runId: '5003',
                stepType: 'search',
                stepTitle: '搜索子问题 2',
                stepStatus: 'COMPLETED',
                sequenceNo: 2,
                content: 'GLM最新发布的模型是什么',
              },
              {
                id: 'search-diff',
                runId: '5003',
                stepType: 'search',
                stepTitle: '搜索子问题 3',
                stepStatus: 'COMPLETED',
                sequenceNo: 3,
                content: 'Qwen和GLM最新模型的区别',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2002/references') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'ref-qwen-glm',
                runId: '5003',
                conversationId: '2002',
                sourceType: 'web',
                title: 'Qwen3 Code Plus & 智谱GLM4.7 模型基础功能对比',
                url: 'https://example.com/models',
                siteName: '博客园',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2002/artifacts' ||
        url === '/api/chat/conversations/2002/current-skills' ||
        url === '/api/chat/conversations/2002/current-mcps' ||
        url === '/api/chat/conversations/2002/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in search replay persistence test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      result.current.setInputValue('qwen和glm最新的模型是什么,帮我对比一下有什么区别');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });
    for (const event of [
      metaEvent,
      searchStepOneEvent,
      searchStepTwoEvent,
      searchStepThreeEvent,
      referenceEvent,
      finishEvent,
    ]) {
      await act(async () => {
        readQueue.shift()?.resolve({ done: false, value: event });
      });
    }
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });

    const latestAssistantMessage = [...result.current.messages]
      .reverse()
      .find((item) => item.role === 'ASSISTANT');
    const processCards = ((latestAssistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
    const searchToolCards = processCards.filter((card) => card.type === 'tool_call' && card.toolId === 'search');
    const searchResultCards = processCards.filter((card) => card.type === 'tool_result' && card.toolId === 'search');
    expect(searchToolCards.map((card) => card.summary)).toEqual(
      expect.arrayContaining([
        '调用网页搜索：Qwen最新发布的模型是什么',
        '调用网页搜索：GLM最新发布的模型是什么',
        '调用网页搜索：Qwen和GLM最新模型的区别',
      ]),
    );
    expect(searchToolCards).toHaveLength(3);
    expect(searchToolCards.every((card) => card.presentation === 'react')).toBe(true);
    expect(searchResultCards.every((card) => card.presentation === 'react')).toBe(true);

    const snapshotStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    const persistedMessages =
      snapshotStore?.snapshots?.['cloud::__no_workspace__']?.conversationRecords?.['2002']?.messages ?? [];
    const persistedAssistantMessage = [...persistedMessages]
      .reverse()
      .find((item: Record<string, unknown>) => item.role === 'ASSISTANT');
    const persistedProcessCards = (persistedAssistantMessage?.processCards ?? []) as Array<Record<string, unknown>>;
    expect(persistedProcessCards.filter((card) => card.type === 'tool_call' && card.toolId === 'search')).toHaveLength(3);
  });

  /**
   * 收到 reject 事件时应把后端拒绝语义映射到 streamError。
   */
  it('应在reject事件中展示后端拒绝文案', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const rejectEvent = new TextEncoder().encode('event:reject\ndata:{"reason":"busy"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/pending-conversation/messages' ||
        url === '/api/chat/conversations/pending-conversation/steps' ||
        url === '/api/chat/conversations/pending-conversation/references' ||
        url === '/api/chat/conversations/pending-conversation/artifacts' ||
        url === '/api/chat/conversations/pending-conversation/current-skills' ||
        url === '/api/chat/conversations/pending-conversation/current-mcps' ||
        url === '/api/chat/conversations/pending-conversation/current-experts'
      ) {
        if (url === '/api/chat/conversations') {
          return new Response(
            JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in reject event test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('请继续');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: rejectEvent });
    });
    await waitFor(() => {
      expect(result.current.streamError).toBe('当前会话并发已满，请稍后重试');
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await submitPromise;
  });

  /**
   * 收到 finish 事件后应立即结束流式状态，避免右下角按钮长时间停留在“停止”。
   */
  it('应在finish事件到达后立即退出流式状态', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const finishEvent = new TextEncoder().encode('event:finish\ndata:{"content":"回答已完成"}\n\n');
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    let resolveConversationsAfterFinish: ((response: Response) => void) | null = null;
    const conversationsAfterFinish = new Promise<Response>((resolve) => {
      resolveConversationsAfterFinish = resolve;
    });
    let conversationsRequestCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        conversationsRequestCount += 1;
        if (conversationsRequestCount === 1) {
          return new Response(
            JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
            { status: 200 },
          );
        }
        return conversationsAfterFinish;
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/pending-conversation/messages' ||
        url === '/api/chat/conversations/pending-conversation/steps' ||
        url === '/api/chat/conversations/pending-conversation/references' ||
        url === '/api/chat/conversations/pending-conversation/artifacts' ||
        url === '/api/chat/conversations/pending-conversation/current-skills' ||
        url === '/api/chat/conversations/pending-conversation/current-mcps' ||
        url === '/api/chat/conversations/pending-conversation/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in finish-immediate-stop test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('请继续');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(false);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });

    resolveConversationsAfterFinish?.(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: '7001',
              title: '新会话',
              status: 'ACTIVE',
              lastRunId: '9101',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    await submitPromise;
  });

  /**
   * 新会话拿到 meta.conversationId 后应立即进入当前空间列表，避免依赖历史列表刷新才可见。
   */
  it('应在meta事件后立即将新会话写入当前会话列表', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const metaEvent = new TextEncoder().encode('event:meta\ndata:{"conversationId":"9010"}\n\n');
    const finishEvent = new TextEncoder().encode(
      'event:finish\ndata:{"conversationId":"9010","content":"ok","title":"新会话标题"}\n\n',
    );
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    let resolveConversationsAfterDone: ((response: Response) => void) | null = null;
    const conversationsAfterDone = new Promise<Response>((resolve) => {
      resolveConversationsAfterDone = resolve;
    });
    let conversationsRequestCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        conversationsRequestCount += 1;
        if (conversationsRequestCount === 1) {
          return new Response(
            JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
            { status: 200 },
          );
        }
        return conversationsAfterDone;
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/9010/messages' ||
        url === '/api/chat/conversations/9010/steps' ||
        url === '/api/chat/conversations/9010/references' ||
        url === '/api/chat/conversations/9010/artifacts' ||
        url === '/api/chat/conversations/9010/current-skills' ||
        url === '/api/chat/conversations/9010/current-mcps' ||
        url === '/api/chat/conversations/9010/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in meta-conversation-list test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('新建一个会话');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: metaEvent });
    });

    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('9010');
      expect(result.current.conversations.some((conversation) => conversation.id === '9010')).toBe(true);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });
    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });

    resolveConversationsAfterDone?.(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: '9010',
              title: '新会话标题',
              status: 'ACTIVE',
              lastRunId: '9102',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    await submitPromise;
  });

  /**
   * 即使会话列表接口延迟返回，finish 事件到达后也应立即在左侧出现新会话，避免“回答结束后还要等 1-2 秒”。
   */
  it('应在finish事件后立即将新会话写入当前会话列表', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const finishEvent = new TextEncoder().encode(
      'event:finish\ndata:{"conversationId":"9901","content":"ok","title":"完成后立即出现"}\n\n',
    );
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    let resolveConversationsAfterDone: ((response: Response) => void) | null = null;
    const conversationsAfterDone = new Promise<Response>((resolve) => {
      resolveConversationsAfterDone = resolve;
    });
    let conversationsRequestCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        conversationsRequestCount += 1;
        if (conversationsRequestCount === 1) {
          return new Response(
            JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
            { status: 200 },
          );
        }
        return conversationsAfterDone;
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/pending-conversation/messages' ||
        url === '/api/chat/conversations/pending-conversation/steps' ||
        url === '/api/chat/conversations/pending-conversation/references' ||
        url === '/api/chat/conversations/pending-conversation/artifacts' ||
        url === '/api/chat/conversations/pending-conversation/current-skills' ||
        url === '/api/chat/conversations/pending-conversation/current-mcps' ||
        url === '/api/chat/conversations/pending-conversation/current-experts' ||
        url === '/api/chat/conversations/9901/messages' ||
        url === '/api/chat/conversations/9901/steps' ||
        url === '/api/chat/conversations/9901/references' ||
        url === '/api/chat/conversations/9901/artifacts' ||
        url === '/api/chat/conversations/9901/current-skills' ||
        url === '/api/chat/conversations/9901/current-mcps' ||
        url === '/api/chat/conversations/9901/current-experts'
      ) {
        if (url === '/api/chat/conversations/9901/messages') {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: 'assistant-9901',
                  conversationId: '9901',
                  role: 'ASSISTANT',
                  content: '',
                  status: 'COMPLETED',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in finish-conversation-list test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('只发finish');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });

    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('9901');
      expect(result.current.conversations.some((conversation) => conversation.id === '9901')).toBe(true);
      expect(result.current.conversations[0]?.title).toBe('完成后立即出现');
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });

    resolveConversationsAfterDone?.(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: '9901',
              title: '完成后立即出现',
              status: 'ACTIVE',
              lastRunId: '9201',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    await submitPromise;

    await waitFor(() => {
      const latestAssistantMessage = [...result.current.messages]
        .reverse()
        .find((item) => item.role === 'ASSISTANT');
      expect(latestAssistantMessage?.content).toBe('ok');
    });
  });

  /**
   * finish 带回真实助手消息 ID 后应立即替换乐观消息，避免操作栏出现但反馈按钮仍不可用。
   */
  it('finish携带assistantMessageId时应立即回填助手消息真实标识', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );

    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const finishEvent = new TextEncoder().encode(
      'event:finish\ndata:{"conversationId":"9901","assistantMessageId":"102","content":"回答已完成","title":"完成标题"}\n\n',
    );
    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };

    let resolveConversationsAfterDone: ((response: Response) => void) | null = null;
    const conversationsAfterDone = new Promise<Response>((resolve) => {
      resolveConversationsAfterDone = resolve;
    });
    let conversationsRequestCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        conversationsRequestCount += 1;
        if (conversationsRequestCount === 1) {
          return new Response(
            JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
            { status: 200 },
          );
        }
        return conversationsAfterDone;
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/9901/messages' ||
        url === '/api/chat/conversations/9901/steps' ||
        url === '/api/chat/conversations/9901/references' ||
        url === '/api/chat/conversations/9901/artifacts' ||
        url === '/api/chat/conversations/9901/current-skills' ||
        url === '/api/chat/conversations/9901/current-mcps' ||
        url === '/api/chat/conversations/9901/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in finish-message-id hydration test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('生成一条回复');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: finishEvent });
    });

    await waitFor(() => {
      const latestAssistantMessage = [...result.current.messages]
        .reverse()
        .find((item) => item.role === 'ASSISTANT');
      expect(latestAssistantMessage).toEqual(
        expect.objectContaining({
          id: '102',
          conversationId: '9901',
          content: '回答已完成',
        }),
      );
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });

    resolveConversationsAfterDone?.(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: '9901',
              title: '完成标题',
              status: 'ACTIVE',
              lastRunId: '9201',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    await submitPromise;
  });
});
