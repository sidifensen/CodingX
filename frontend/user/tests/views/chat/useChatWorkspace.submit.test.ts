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
   * finish 事件已经代表模型输出结束，后续会话回放慢响应不应继续占用发送锁。
   * 业务边界：否则输入区已经恢复“发送”按钮，但点击第三条消息会被内部状态静默拦截。
   */
  it('应在finish后立即允许下一次提交并持久化最终助手消息', async () => {
    const streamReaders: Array<{
      readQueue: Array<{
        resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
        reject: (reason?: unknown) => void;
      }>;
      read: ReturnType<typeof vi.fn>;
    }> = [];
    let streamRequestCount = 0;
    let conversationsRequestCount = 0;
    let resolveConversationsAfterFinish: ((response: Response) => void) | null = null;
    const conversationsAfterFinish = new Promise<Response>((resolve) => {
      resolveConversationsAfterFinish = resolve;
    });

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
        streamRequestCount += 1;
        const readQueue: Array<{
          resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
          reject: (reason?: unknown) => void;
        }> = [];
        const reader = {
          readQueue,
          read: vi.fn(
            () =>
              new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
                readQueue.push({ resolve, reject });
              }),
          ),
        };
        streamReaders.push(reader);
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => reader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in finish unlock submit test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('第二个请求');
    });
    const firstSubmitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(streamReaders[0]?.readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      streamReaders[0].readQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:finish\ndata:{"conversationId":"2001","assistantMessageId":"3001","content":"第二个回答","title":"第二个会话"}\n\n',
        ),
      });
    });

    await waitFor(() => {
      expect(result.current.isStreaming).toBe(false);
    });

    const persistedStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    const persistedMessage =
      persistedStore.snapshots?.['cloud::__no_workspace__']?.conversationRecords?.['2001']
        ?.messages?.[1];
    expect(persistedMessage).toEqual(
      expect.objectContaining({
        id: '3001',
        content: '第二个回答',
        status: 'done',
      }),
    );

    await act(async () => {
      streamReaders[0].readQueue.shift()?.resolve({ done: true, value: undefined });
    });

    await waitFor(() => {
      expect(conversationsRequestCount).toBe(2);
    });

    await act(async () => {
      result.current.setInputValue('第三个请求');
    });
    await act(async () => {
      void result.current.submitMessage();
    });

    await waitFor(() => {
      expect(streamRequestCount).toBe(2);
    });

    await act(async () => {
      resolveConversationsAfterFinish?.(
        new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2001',
                title: '第二个会话',
                status: 'ACTIVE',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        ),
      );
    });
    await act(async () => {
      await firstSubmitPromise;
    });

    const persistedStoreAfterSlowReplay = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    const persistedContentsAfterSlowReplay =
      persistedStoreAfterSlowReplay.snapshots?.['cloud::__no_workspace__']
        ?.conversationRecords?.['2001']?.messages?.map(
          (message: { content?: string }) => message.content,
        ) ?? [];
    expect(persistedContentsAfterSlowReplay).toEqual(
      expect.arrayContaining(['第二个请求', '第二个回答']),
    );

    await act(async () => {
      streamReaders[1].readQueue.shift()?.resolve({ done: true, value: undefined });
    });
  });

  /**
   * finish 后用户立即进入新建态时，旧流的慢速历史回放不能重新选中旧会话。
   * 业务边界：离开页面是本地订阅脱离，不能被旧异步链路当作“继续查看旧会话”覆盖。
   */
  it('新建对话后应忽略旧流finish后的慢回放选中', async () => {
    const streamReaders: Array<{
      readQueue: Array<{
        resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
        reject: (reason?: unknown) => void;
      }>;
      read: ReturnType<typeof vi.fn>;
    }> = [];
    let conversationsRequestCount = 0;
    let resolveConversationsAfterFinish: ((response: Response) => void) | null = null;
    const conversationsAfterFinish = new Promise<Response>((resolve) => {
      resolveConversationsAfterFinish = resolve;
    });

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
        const readQueue: Array<{
          resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
          reject: (reason?: unknown) => void;
        }> = [];
        const reader = {
          readQueue,
          read: vi.fn(
            () =>
              new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
                readQueue.push({ resolve, reject });
              }),
          ),
        };
        streamReaders.push(reader);
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => reader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in finish start-new replay race test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('旧会话请求');
    });
    const submitPromise = result.current.submitMessage();

    await waitFor(() => {
      expect(streamReaders[0]?.readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      streamReaders[0].readQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:finish\ndata:{"conversationId":"2001","assistantMessageId":"3001","content":"旧会话回答","title":"旧会话"}\n\n',
        ),
      });
    });
    await waitFor(() => {
      expect(result.current.isStreaming).toBe(false);
    });

    await act(async () => {
      await result.current.startNewConversation();
    });
    expect(result.current.activeConversationId).toBeNull();
    expect(result.current.messages).toEqual([]);

    await act(async () => {
      streamReaders[0].readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });

    expect(resolveConversationsAfterFinish).not.toBeNull();
    expect(conversationsRequestCount).toBe(1);
    expect(result.current.activeConversationId).toBeNull();
    expect(result.current.messages).toEqual([]);
  });

  /**
   * 已有会话里继续提问后立即新建时，旧提交闭包中的 activeConversationId 不能把页面拉回旧会话。
   */
  it('已有会话提交完成后新建应阻止旧闭包重新选中原会话', async () => {
    const streamReaders: Array<{
      readQueue: Array<{
        resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
        reject: (reason?: unknown) => void;
      }>;
      read: ReturnType<typeof vi.fn>;
    }> = [];
    let conversationsRequestCount = 0;
    let resolveConversationsAfterFinish: ((response: Response) => void) | null = null;
    const conversationsAfterFinish = new Promise<Response>((resolve) => {
      resolveConversationsAfterFinish = resolve;
    });

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        conversationsRequestCount += 1;
        if (conversationsRequestCount === 1) {
          return new Response(
            JSON.stringify({
              success: true,
              code: 'OK',
              message: 'success',
              data: [
                {
                  id: '2001',
                  title: '原会话',
                  status: 'ACTIVE',
                  lastRunId: '5001',
                },
              ],
            }),
            { status: 200 },
          );
        }
        return conversationsAfterFinish;
      }
      if (url === '/api/chat/conversations/2001/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '1001',
                conversationId: '2001',
                role: 'USER',
                content: '原问题',
                status: 'COMPLETED',
              },
              {
                id: '1002',
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
      if (url.includes('/api/chat/stream')) {
        const readQueue: Array<{
          resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
          reject: (reason?: unknown) => void;
        }> = [];
        const reader = {
          readQueue,
          read: vi.fn(
            () =>
              new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
                readQueue.push({ resolve, reject });
              }),
          ),
        };
        streamReaders.push(reader);
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => reader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in existing conversation finish start-new race test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      await result.current.selectConversation('2001', result.current.conversations);
    });
    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2001');
    });

    await act(async () => {
      result.current.setInputValue('继续追问');
    });
    const submitPromise = result.current.submitMessage();
    await waitFor(() => {
      expect(streamReaders[0]?.readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      streamReaders[0].readQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:finish\ndata:{"conversationId":"2001","assistantMessageId":"3001","content":"追问回答","title":"原会话"}\n\n',
        ),
      });
    });
    await waitFor(() => {
      expect(result.current.isStreaming).toBe(false);
    });

    await act(async () => {
      await result.current.startNewConversation();
    });
    expect(result.current.activeConversationId).toBeNull();
    expect(result.current.messages).toEqual([]);

    await act(async () => {
      streamReaders[0].readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });

    expect(resolveConversationsAfterFinish).not.toBeNull();
    expect(conversationsRequestCount).toBe(1);
    expect(result.current.activeConversationId).toBeNull();
    expect(result.current.messages).toEqual([]);
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

  /**
   * 离开当前会话只应断开本地 SSE 订阅，后台任务必须继续运行，不能等同于显式停止生成。
   */
  it('切到新建会话时不应取消正在后台运行的任务', async () => {
    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const cancelRequests: string[] = [];
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
      if (url === '/api/chat/conversations/2001/cancel') {
        cancelRequests.push(`${init?.method ?? 'GET'} ${url}`);
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
      throw new Error(`Unhandled fetch in start-new background task test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('请持续输出一段内容');
    });
    const submitPromise = result.current.submitMessage();
    await waitFor(() => {
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:meta\ndata:{"conversationId":"2001","taskId":"9001"}\n\n',
        ),
      });
    });
    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2001');
    });

    await act(async () => {
      await result.current.startNewConversation();
    });

    expect(cancelRequests).toEqual([]);

    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * 浏览器刷新会中断当前页面的 SSE 连接，但后端任务仍在运行；此时不能把本地半截回答标记为用户停止。
   */
  it('刷新页面时应只脱离本地流并保留后台运行快照', async () => {
    const readQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];

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
            getReader: () => ({
              read: vi.fn(
                () =>
                  new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
                    readQueue.push({ resolve, reject });
                  }),
              ),
            }),
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in refresh detach stream test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('请持续输出一段内容');
    });
    const submitPromise = result.current.submitMessage();
    await waitFor(() => {
      expect(readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      readQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:meta\ndata:{"conversationId":"2001","taskId":"9001"}\n\n',
        ),
      });
    });
    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2001');
    });

    await act(async () => {
      readQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:message\ndata:{"type":"response","delta":"刷新前输出"}\n\n',
        ),
      });
    });
    await waitFor(() => {
      expect(result.current.messages.find((message) => message.role === 'ASSISTANT')?.content)
        .toBe('刷新前输出');
    });

    window.dispatchEvent(new Event('pagehide'));
    await act(async () => {
      readQueue.shift()?.reject(new DOMException('Aborted', 'AbortError'));
    });
    await act(async () => {
      await submitPromise;
    });

    const assistantMessage = result.current.messages.find((message) => message.role === 'ASSISTANT');
    expect(result.current.streamError).not.toBe('已停止当前生成');
    expect(assistantMessage).toEqual(
      expect.objectContaining({
        content: '刷新前输出',
        status: 'streaming',
      }),
    );
    const persistedStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    const persistedMessages =
      persistedStore.snapshots?.['cloud::__no_workspace__']?.conversationRecords?.['2001']
        ?.messages ?? [];
    expect(persistedMessages).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          content: '刷新前输出',
          status: 'streaming',
        }),
      ]),
    );
  });

  /**
   * 新会话流式 meta 创建的本地侧栏条目也必须携带运行态；否则离开后点回只会静态回放，无法续接后台输出。
   */
  it('重新打开由流式 meta 创建的运行中会话时应续接会话流', async () => {
    const submitReadQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const resumeReadQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const streamUrls: string[] = [];

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
                id: '1001',
                conversationId: '2001',
                role: 'USER',
                content: '开始后台任务',
                status: 'COMPLETED',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        const reader = {
          read: vi.fn(
            () =>
              new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
                submitReadQueue.push({ resolve, reject });
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
      if (url === '/api/chat/conversations/2001/stream') {
        streamUrls.push(url);
        const reader = {
          read: vi.fn(
            () =>
              new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
                resumeReadQueue.push({ resolve, reject });
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
      throw new Error(`Unhandled fetch in meta-created conversation resume test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('请持续输出一段内容');
    });
    const submitPromise = result.current.submitMessage();
    await waitFor(() => {
      expect(submitReadQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      submitReadQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:meta\ndata:{"conversationId":"2001","taskId":"9001"}\n\n',
        ),
      });
    });
    await waitFor(() => {
      expect(result.current.conversations[0]).toEqual(
        expect.objectContaining({
          id: '2001',
          activeTaskId: '9001',
          activeTaskStatus: 'RUNNING',
        }),
      );
    });

    await act(async () => {
      await result.current.startNewConversation();
    });
    await act(async () => {
      await result.current.selectConversation('2001', result.current.conversations);
    });

    await waitFor(() => {
      expect(streamUrls).toEqual(['/api/chat/conversations/2001/stream']);
      expect(resumeReadQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      resumeReadQueue.shift()?.resolve({ done: true, value: undefined });
      submitReadQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });

  /**
   * 重新打开仍在运行的会话时，应订阅会话 SSE 续接后台输出，而不是只做一次静态回放。
   */
  it('打开运行中会话时应重新订阅会话流并继续追加输出', async () => {
    const streamReaders: Array<{
      readQueue: Array<{
        resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
        reject: (reason?: unknown) => void;
      }>;
      read: ReturnType<typeof vi.fn>;
    }> = [];
    const streamUrls: string[] = [];

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
                title: '后台运行会话',
                status: 'ACTIVE',
                activeTaskId: '9001',
                activeTaskStatus: 'RUNNING',
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
                id: '1001',
                conversationId: '2001',
                role: 'USER',
                content: '开始后台任务',
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
        url === '/api/chat/conversations/2001/current-experts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/stream') {
        streamUrls.push(url);
        const readQueue: Array<{
          resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
          reject: (reason?: unknown) => void;
        }> = [];
        const reader = {
          readQueue,
          read: vi.fn(
            () =>
              new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
                readQueue.push({ resolve, reject });
              }),
          ),
        };
        streamReaders.push(reader);
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => reader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in running conversation resume test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      await result.current.selectConversation('2001', result.current.conversations);
    });

    await waitFor(() => {
      expect(streamUrls).toEqual(['/api/chat/conversations/2001/stream']);
      expect(streamReaders[0]?.readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      streamReaders[0].readQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:message\ndata:{"type":"response","delta":"继续输出"}\n\n',
        ),
      });
    });

    await waitFor(() => {
      const assistantMessage = result.current.messages.find((message) => message.role === 'ASSISTANT');
      expect(assistantMessage).toEqual(
        expect.objectContaining({
          content: '继续输出',
          status: 'streaming',
        }),
      );
    });

    await act(async () => {
      streamReaders[0].readQueue.shift()?.resolve({ done: true, value: undefined });
    });
  });

  /**
   * 从运行中会话切走时，只应断开当前页面订阅；旧流后续事件不能再把主区和 URL 拉回旧会话。
   */
  it('切到其他会话时应脱离旧运行流并立即显示目标会话', async () => {
    const submitReadQueue: Array<{
      resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const streamAbortSignals: AbortSignal[] = [];

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
                id: '2002',
                title: '目标会话',
                status: 'ACTIVE',
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
                id: 'target-user',
                conversationId: '2002',
                role: 'USER',
                content: '目标会话问题',
                status: 'COMPLETED',
              },
              {
                id: 'target-assistant',
                conversationId: '2002',
                role: 'ASSISTANT',
                content: '目标会话回答',
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
      if (url.includes('/api/chat/stream')) {
        if (init?.signal) {
          streamAbortSignals.push(init.signal);
        }
        const abortSignal = init?.signal;
        const reader = {
          read: vi.fn(
            () => {
              if (abortSignal?.aborted) {
                return Promise.reject(new DOMException('Aborted', 'AbortError'));
              }
              return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
                submitReadQueue.push({ resolve, reject });
                abortSignal?.addEventListener(
                  'abort',
                  () => reject(new DOMException('Aborted', 'AbortError')),
                  { once: true },
                );
              });
            },
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
      throw new Error(`Unhandled fetch in switch-away running stream test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      result.current.setInputValue('启动旧会话后台流');
    });
    const submitPromise = result.current.submitMessage();
    await waitFor(() => {
      expect(submitReadQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      submitReadQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:meta\ndata:{"conversationId":"2001","taskId":"9001"}\n\n',
        ),
      });
    });
    await waitFor(() => {
      expect(result.current.activeConversationId).toBe('2001');
    });

    await act(async () => {
      await result.current.selectConversation('2002', result.current.conversations);
    });

    expect(streamAbortSignals[0]?.aborted).toBe(true);
    expect(result.current.activeConversationId).toBe('2002');
    expect(result.current.isStreaming).toBe(false);
    expect(result.current.messages.some((message) => message.content === '目标会话回答')).toBe(true);

    await act(async () => {
      submitReadQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:message\ndata:{"type":"response","delta":"旧流迟到输出"}\n\n',
        ),
      });
      submitReadQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:finish\ndata:{"conversationId":"2001","content":"旧流完成","title":"旧会话"}\n\n',
        ),
      });
      submitReadQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });

    expect(result.current.activeConversationId).toBe('2002');
    expect(result.current.messages.some((message) => message.content === '旧流完成')).toBe(false);
  });

  /**
   * 恢复运行中会话时，后端会回放运行期缓冲；本地已有的半截输出不能被重复拼接。
   */
  it('恢复运行中会话时应跳过本地已展示的缓冲前缀', async () => {
    const streamReaders: Array<{
      readQueue: Array<{
        resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
        reject: (reason?: unknown) => void;
      }>;
      read: ReturnType<typeof vi.fn>;
    }> = [];

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
                title: '后台运行会话',
                status: 'ACTIVE',
                activeTaskId: '9001',
                activeTaskStatus: 'RUNNING',
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
                id: '1001',
                conversationId: '2001',
                role: 'USER',
                content: '开始后台任务',
                status: 'completed',
              },
              {
                id: '1002',
                conversationId: '2001',
                role: 'ASSISTANT',
                content: '已有半截',
                status: 'streaming',
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
      if (url === '/api/chat/conversations/2001/stream') {
        const readQueue: Array<{
          resolve: (value: ReadableStreamReadResult<Uint8Array>) => void;
          reject: (reason?: unknown) => void;
        }> = [];
        const reader = {
          readQueue,
          read: vi.fn(
            () =>
              new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
                readQueue.push({ resolve, reject });
              }),
          ),
        };
        streamReaders.push(reader);
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => reader,
          },
        } as unknown as Response;
      }
      throw new Error(`Unhandled fetch in running conversation resume prefix test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));
    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      await result.current.selectConversation('2001', result.current.conversations);
    });
    await waitFor(() => {
      expect(streamReaders[0]?.readQueue.length).toBeGreaterThan(0);
    });

    await act(async () => {
      streamReaders[0].readQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:message\ndata:{"type":"response","delta":"已有半截"}\n\n',
        ),
      });
    });

    expect(result.current.messages.find((message) => message.role === 'ASSISTANT')?.content).toBe('已有半截');

    await act(async () => {
      streamReaders[0].readQueue.shift()?.resolve({
        done: false,
        value: new TextEncoder().encode(
          'event:message\ndata:{"type":"response","delta":"新增输出"}\n\n',
        ),
      });
    });

    await waitFor(() => {
      expect(result.current.messages.find((message) => message.role === 'ASSISTANT')?.content).toBe(
        '已有半截新增输出',
      );
    });

    await act(async () => {
      streamReaders[0].readQueue.shift()?.resolve({ done: true, value: undefined });
    });
  });
});
