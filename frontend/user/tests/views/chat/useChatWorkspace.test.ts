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
   * 选中历史会话时应同步加载当前技能与当前 MCP 绑定，供右栏展示会话上下文。
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
            workspaceLabel: '云端工作空间',
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
   * 切换本地工作空间后，后端返回的跨工作空间会话应进入“历史会话”分组，不应污染当前工作空间。
   */
  it('应将未归属当前工作空间的会话归入历史会话分组', async () => {
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
        title: '跨工作空间历史会话',
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
      if (url === '/api/chat/skills') {
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
                content: '这是 test 工作空间外的历史会话',
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
    expect(result.current.workspaceGroups.some((group) => group.workspaceLabel === '历史会话')).toBe(true);
    const initialHistoryGroup = result.current.workspaceGroups.find(
      (group) => group.workspaceLabel === '历史会话',
    );
    expect(initialHistoryGroup?.conversations.map((item) => item.id)).toEqual(['3001', '3002']);

    await act(async () => {
      await result.current.setActiveWorkspacePath('D:/code/test');
    });

    await waitFor(() => {
      expect(result.current.workspaceLabel).toBe('test');
    });

    expect(result.current.conversations).toEqual([]);
    const historyGroup = result.current.workspaceGroups.find((group) => group.workspaceLabel === '历史会话');
    expect(historyGroup?.conversations.map((item) => item.id)).toEqual(['3001', '3002']);
  });

  it('切换到已有历史会话的工作空间时应恢复该空间会话，不应强制新建', async () => {
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
      if (url === '/api/chat/skills') {
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
        url.endsWith('/current-mcps')
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
   * 云端运行环境下，会话应统一归入“历史会话”分组，不应进入“云端工作空间”分组。
   */
  it('云端会话应只出现在历史会话分组且不存在云端工作空间分组', async () => {
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
                title: '云端历史会话A',
                status: 'ACTIVE',
                lastRunId: '9001',
              },
              {
                id: '5002',
                title: '云端历史会话B',
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
      if (url === '/api/chat/skills') {
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
        url === '/api/chat/conversations/5001/current-mcps'
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

    expect(result.current.workspaceLabel).toBe('云端工作空间');
    expect(result.current.conversations.map((item) => item.id)).toEqual([]);

    const cloudWorkspaceGroup = result.current.workspaceGroups.find(
      (group) => group.workspaceLabel === '云端工作空间' && group.runtimeTarget === 'cloud',
    );
    expect(cloudWorkspaceGroup).toBeUndefined();

    const historyGroup = result.current.workspaceGroups.find(
      (group) => group.workspaceLabel === '历史会话' && group.runtimeTarget === 'cloud',
    );
    expect(historyGroup?.conversations.map((item) => item.id)).toEqual(['5001', '5002']);

    await act(async () => {
      await result.current.selectConversationInWorkspace('5001', {
        partitionKey: 'cloud::__history__',
        runtimeTarget: 'cloud',
        workspacePath: null,
        groupType: 'history',
      });
    });

    const historyGroupAfterSelect = result.current.workspaceGroups.find(
      (group) => group.workspaceLabel === '历史会话' && group.runtimeTarget === 'cloud',
    );
    expect(historyGroupAfterSelect?.conversations.map((item) => item.id)).toEqual(['5001', '5002']);
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
      if (url === '/api/chat/skills') {
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
        url === '/api/chat/conversations/2001/current-mcps'
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
});
