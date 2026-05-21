import { act, renderHook, waitFor } from '@testing-library/react';
import { buildStreamRequestUrl, useChatWorkspace } from './useChatWorkspace';

/**
 * 验证聊天工作区初始化策略，避免技能在首次进入时被默认全选。
 */
describe('useChatWorkspace', () => {
  beforeEach(() => {
    window.localStorage.clear();
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

    expect(result.current.currentSkills).toEqual([
      expect.objectContaining({ skillCode: 'sales_query', displayName: '销售查询' }),
    ]);
    expect(result.current.currentMcps).toEqual([
      expect.objectContaining({ mcpCode: 'sales_query', displayName: '销售查询' }),
    ]);
  });

  /**
   * 切换本地工作空间后，后端返回的跨工作空间会话应进入“历史记录”分组，不应污染当前工作空间。
   */
  it('应将未归属当前工作空间的会话归入历史记录分组', async () => {
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

    const chatConversations = [
      {
        id: '3001',
        title: 'A 工作空间会话',
        status: 'ACTIVE',
        lastRunId: '7001',
      },
      {
        id: '3002',
        title: '跨工作空间历史记录',
        status: 'ACTIVE',
        lastRunId: '7002',
      },
    ];

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: chatConversations,
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
      if (url === '/api/chat/conversations/3002/current-mcps') {
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
    });

    expect(result.current.workspaceLabel).toBe('workspace-a');
    expect(result.current.conversations.map((item) => item.id)).toEqual([]);
    expect(result.current.workspaceGroups.some((group) => group.workspaceLabel === '历史记录')).toBe(true);
    const initialHistoryGroup = result.current.workspaceGroups.find(
      (group) => group.workspaceLabel === '历史记录',
    );
    expect(initialHistoryGroup?.conversations.map((item) => item.id)).toEqual(['3001', '3002']);

    await act(async () => {
      await result.current.setActiveWorkspacePath('D:/code/test');
    });

    await waitFor(() => {
      expect(result.current.workspaceLabel).toBe('test');
    });

    expect(result.current.conversations).toEqual([]);
    const historyGroup = result.current.workspaceGroups.find((group) => group.workspaceLabel === '历史记录');
    expect(historyGroup?.conversations.map((item) => item.id)).toEqual(['3001', '3002']);
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
    expect(result.current.activeConversationId).toBe('3001');
    expect(result.current.workspaceLabel).toBe('space-a');

    await act(async () => {
      await result.current.setActiveWorkspacePath('D:/code/space-b');
    });

    expect(result.current.activeConversationId).toBe('4001');
    expect(result.current.workspaceLabel).toBe('space-b');
  });

  /**
   * 云端运行环境下，会话应直接进入默认云端分组，刷新后不应再落到历史分组里。
   */
  it('云端会话应只出现在默认云端分组且不存在历史记录分组', async () => {
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
    });

    expect(result.current.workspaceLabel).toBe('历史记录');
    expect(result.current.conversations.map((item) => item.id)).toEqual(['5001', '5002']);

    const cloudWorkspaceGroup = result.current.workspaceGroups.find(
      (group) => group.workspaceLabel === '历史记录' && group.runtimeTarget === 'cloud',
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
        url === '/api/chat/conversations/pending-conversation/current-experts'
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
   * 切换本地工作空间后应立即使用绑定返回的 workspaceId 发流，避免会话误落到默认云端空间。
   */
  it('应在切换本地工作空间后使用最新workspaceId发送流请求', async () => {
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
    expect(streamFetchMock.mock.calls[0][0]).toContain('workspaceId=3002');
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
    const encodedMeta = new TextEncoder().encode('event:meta\ndata:{"conversationId":"2001"}\n\n');
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
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.content).toBe('第一段');
      expect(assistantMessage?.status).toBe('cancelled');
    });

    await submitPromise;
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
      readQueue.shift()?.resolve({ done: false, value: searchStepEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.searchProgress?.status).toBe('running');
      expect(assistantMessage?.searchProgress?.items).toHaveLength(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: referenceOneEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.searchProgress?.items).toHaveLength(1);
      expect(assistantMessage?.searchProgress?.items[0].title).toBe('OpenAI API 最新文档');
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: false, value: referenceTwoEvent });
    });
    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      expect(assistantMessage?.searchProgress?.items).toHaveLength(2);
      expect(assistantMessage?.searchProgress?.items[1].title).toBe('Bing Search API 文档');
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
    expect(latestAssistantMessage?.searchProgress?.status).toBe('completed');
    expect(latestAssistantMessage?.searchProgress?.items).toHaveLength(1);
    expect(latestAssistantMessage?.searchProgress?.items[0].title).toBe('OpenAI API 最新文档');

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
});

