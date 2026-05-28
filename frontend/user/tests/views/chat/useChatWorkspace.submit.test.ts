import { act, renderHook, waitFor } from '@testing-library/react';
import { useChatWorkspace } from '@/views/chat/useChatWorkspace';

/**
 * 覆盖消息提交阶段的关键时序行为，避免输入框在网络往返期间残留旧文本。
 */
describe('useChatWorkspace submit behavior', () => {
  beforeEach(() => {
    window.localStorage.clear();
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
   * 当流式请求尚未返回时，输入框也应立即清空，避免用户误判消息未发出。
   */
  it('应在流式请求返回前立即清空输入框', async () => {
    let resolveStreamResponse: ((response: Response) => void) | null = null;
    const pendingStreamResponse = new Promise<Response>((resolve) => {
      resolveStreamResponse = resolve;
    });

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
        url === '/api/chat/conversations/pending-conversation/current-experts' ||
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
                  title: '测试会话',
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
        return pendingStreamResponse;
      }
      throw new Error(`Unhandled fetch in submit timing test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('请帮我分析这份文件');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(true);
    });
    expect(result.current.inputValue).toBe('');

    await act(async () => {
      resolveStreamResponse?.({
        ok: true,
        status: 200,
        body: {
          getReader: () => ({
            read: vi.fn().mockResolvedValue({ done: true, value: undefined }),
          }),
        },
      } as unknown as Response);
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * 提交锁必须覆盖 React 状态更新前的异步窗口，避免双击发送或回车重复触发导致同一问题进入两条流。
   */
  it('应在第一次流式请求未返回时忽略重复提交', async () => {
    let resolveStreamResponse: ((response: Response) => void) | null = null;
    const pendingStreamResponse = new Promise<Response>((resolve) => {
      resolveStreamResponse = resolve;
    });
    let streamRequestCount = 0;

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
        url === '/api/chat/conversations/pending-conversation/current-experts' ||
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
                  title: '测试会话',
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
        streamRequestCount += 1;
        return pendingStreamResponse;
      }
      throw new Error(`Unhandled fetch in duplicate submit test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('请帮我查最新资料');
    });

    const firstSubmitPromise = result.current.submitMessage();
    const secondSubmitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(streamRequestCount).toBe(1);
    });

    await act(async () => {
      resolveStreamResponse?.({
        ok: true,
        status: 200,
        body: {
          getReader: () => ({
            read: vi.fn().mockResolvedValue({ done: true, value: undefined }),
          }),
        },
      } as unknown as Response);
    });
    await act(async () => {
      await Promise.all([firstSubmitPromise, secondSubmitPromise]);
    });
  });

  /**
   * token 丢失时不应静默失败，应提示登录失效并触发未授权回调，避免用户误判“发送键无响应”。
   */
  it('应在token缺失时提示登录失效并触发未授权回调', async () => {
    const onUnauthorized = vi.fn();
    window.localStorage.removeItem('codingx.auth.session');
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/conversations' ||
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
      throw new Error(`Unhandled fetch in submit missing token test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true, { onUnauthorized }));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('你好');
    });

    await act(async () => {
      await result.current.submitMessage();
    });

    expect(result.current.streamError).toBe('登录已失效，请重新登录');
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(result.current.isStreaming).toBe(false);
  });
});
