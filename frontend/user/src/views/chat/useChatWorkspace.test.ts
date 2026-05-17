import { renderHook, waitFor } from '@testing-library/react';
import { useChatWorkspace } from './useChatWorkspace';

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
});
