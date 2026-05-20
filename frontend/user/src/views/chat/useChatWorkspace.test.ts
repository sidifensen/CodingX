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
   * 技能输入应被序列化为结构化技能消息，避免将技能标记直接作为普通问题文本发送。
   */
  it('应在构建流请求时按技能类型生成结构化消息', () => {
    const requestUrl = buildStreamRequestUrl(
      '@sales_query 请分析订单趋势',
      '2001',
      false,
      true,
      ['sales_query'],
      ['sales_query'],
      undefined,
      ['9001', '9002'],
    );
    const searchParams = new URLSearchParams(requestUrl.split('?')[1] ?? '');

    expect(searchParams.get('question')).toBe('请分析订单趋势');
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
      false,
      false,
      [],
      ['sales_query', 'ticket_query'],
    );
    const searchParams = new URLSearchParams(requestUrl.split('?')[1] ?? '');
    expect(searchParams.get('question')).toBe('请总结今天的工单趋势');
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
});
