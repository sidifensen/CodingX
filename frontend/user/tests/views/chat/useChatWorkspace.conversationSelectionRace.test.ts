import { act, renderHook, waitFor } from '@testing-library/react';
import { isConversationListRequest, isConversationMessageListRequest } from '../../support/chatPaginationMock';
import { useChatWorkspace } from '@/views/chat/useChatWorkspace';

/**
 * 覆盖会话回放请求的异步竞态，避免旧会话慢响应覆盖当前已打开会话。
 */
describe('useChatWorkspace conversation selection race', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.history.replaceState(window.history.state, '', '/');
    vi.restoreAllMocks();
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
  });

  /**
   * A 会话回放慢于 B 会话时，A 的消息和工具状态不能在迟到后写入当前 B 会话视图。
   */
  it('慢速会话回放返回后不应覆盖当前会话消息和工具状态', async () => {
    const delayedConversation2001Messages = createDeferred<Response>();
    const requestUrls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      requestUrls.push(url);
      if (isConversationListRequest(url)) {
        return jsonResponse({
          items: [
            {
              id: '2001',
              title: '旧会话',
              status: 'ACTIVE',
              lastRunId: 'run-2001',
            },
            {
              id: '2002',
              title: '当前会话',
              status: 'ACTIVE',
              lastRunId: 'run-2002',
            },
          ],
          hasMore: false,
          nextCursor: null,
        });
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return jsonResponse([]);
      }
      if (isConversationMessageListRequest(url, '2001')) {
        return delayedConversation2001Messages.promise;
      }
      if (isConversationMessageListRequest(url, '2002')) {
        return jsonResponse({
          items: [
            {
              id: 'user-2002',
              conversationId: '2002',
              role: 'USER',
              content: '当前会话问题',
              status: 'COMPLETED',
            },
            {
              id: 'assistant-2002',
              conversationId: '2002',
              runId: 'run-2002',
              role: 'ASSISTANT',
              content: '当前会话回答',
              status: 'COMPLETED',
            },
          ],
          hasMore: false,
          nextCursor: null,
        });
      }
      if (url === '/api/chat/conversations/2001/goal/active') {
        return jsonResponse(null);
      }
      if (url === '/api/chat/conversations/2002/goal/active') {
        return jsonResponse(null);
      }
      if (url === '/api/chat/conversations/2001/steps') {
        return jsonResponse([
          {
            id: 'step-2001',
            runId: 'run-2001',
            stepType: 'tool',
            stepTitle: '旧会话工具',
            stepStatus: 'COMPLETED',
            sequenceNo: 1,
            content: '旧会话工具结果',
          },
        ]);
      }
      if (url === '/api/chat/conversations/2002/steps') {
        return jsonResponse([
          {
            id: 'step-2002',
            runId: 'run-2002',
            stepType: 'tool',
            stepTitle: '当前会话工具',
            stepStatus: 'COMPLETED',
            sequenceNo: 1,
            content: '当前会话工具结果',
          },
        ]);
      }
      if (
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts' ||
        url === '/api/chat/conversations/2001/current-experts' ||
        url === '/api/chat/conversations/2001/current-skills' ||
        url === '/api/chat/conversations/2002/references' ||
        url === '/api/chat/conversations/2002/artifacts' ||
        url === '/api/chat/conversations/2002/current-experts' ||
        url === '/api/chat/conversations/2002/current-skills'
      ) {
        return jsonResponse([]);
      }
      if (url === '/api/chat/conversations/2001/current-mcps') {
        return jsonResponse([
          {
            id: 'mcp-2001',
            mcpCode: 'old_tool',
            displayName: '旧会话工具',
          },
        ]);
      }
      if (url === '/api/chat/conversations/2002/current-mcps') {
        return jsonResponse([
          {
            id: 'mcp-2002',
            mcpCode: 'current_tool',
            displayName: '当前会话工具',
          },
        ]);
      }
      throw new Error(`Unhandled fetch in conversation selection race test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    let staleSelectionPromise: Promise<void> | undefined;
    await act(async () => {
      staleSelectionPromise = result.current.selectConversation('2001', result.current.conversations);
      await Promise.resolve();
    });
    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2001');
    });

    await act(async () => {
      await result.current.selectConversation('2002', result.current.conversations);
    });
    expect(result.current.activeConversationId).toBe('2002');
    expect(result.current.messages.map((message) => message.content)).toEqual([
      '当前会话问题',
      '当前会话回答',
    ]);
    expect(result.current.executionSteps.map((step) => step.stepTitle)).toEqual(['当前会话工具']);
    expect(result.current.currentMcps.map((mcp) => mcp.mcpCode)).toEqual(['current_tool']);

    await act(async () => {
      delayedConversation2001Messages.resolve(
        jsonResponse({
          items: [
            {
              id: 'user-2001',
              conversationId: '2001',
              role: 'USER',
              content: '旧会话问题',
              status: 'COMPLETED',
            },
            {
              id: 'assistant-2001',
              conversationId: '2001',
              runId: 'run-2001',
              role: 'ASSISTANT',
              content: '旧会话回答',
              status: 'COMPLETED',
            },
          ],
          hasMore: false,
          nextCursor: null,
        }),
      );
      await staleSelectionPromise;
    });

    expect(requestUrls).toContain('/api/chat/conversations/2001/messages?pageSize=30');
    expect(requestUrls).toContain('/api/chat/conversations/2002/messages?pageSize=30');
    expect(result.current.activeConversationId).toBe('2002');
    expect(result.current.messages.map((message) => message.content)).toEqual([
      '当前会话问题',
      '当前会话回答',
    ]);
    expect(result.current.executionSteps.map((step) => step.stepTitle)).toEqual(['当前会话工具']);
    expect(result.current.currentMcps.map((mcp) => mcp.mcpCode)).toEqual(['current_tool']);
  });
});

/**
 * 创建可由测试主动释放的 Promise，用于精确控制接口响应顺序。
 */
function createDeferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return {
    promise,
    resolve,
    reject,
  };
}

/**
 * 返回后端统一 ApiResponse 包装，保持测试桩与真实接口结构一致。
 */
function jsonResponse(data: unknown) {
  return new Response(
    JSON.stringify({
      success: true,
      code: 'OK',
      message: 'success',
      data,
    }),
    { status: 200 },
  );
}
