import { act, renderHook, waitFor } from '@testing-library/react';
import { useChatWorkspace } from './useChatWorkspace';

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
});
