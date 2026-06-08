import { act, renderHook, waitFor } from '@testing-library/react';
import { useChatWorkspace } from '@/views/chat/useChatWorkspace';

/**
 * 回归覆盖聊天流过程卡片中的文件差异保留逻辑。
 */
describe('useChatWorkspace file diff retention', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.history.replaceState(window.history.state, '', '/');
    vi.restoreAllMocks();
  });

  /**
   * 后端 write 工具会先用 params 生成 pending diff，后续 progress 可能只更新进度文案。
   * 业务约束：后续无 diff 的进度事件不能清空前一次已生成的文件差异，否则右侧栏会在多轮更新后消失。
   */
  it('应在后续工具进度不含diff时保留已有文件差异', async () => {
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
    const metaEvent = encodeSseEvent('meta', { conversationId: '2001' });
    const firstProgressEvent = encodeSseEvent('tool-call', {
      callId: 'write-diff-retention-1',
      phase: 'progress',
      toolId: 'write',
      displayName: '写文件',
      params: {
        path: 'src/App.tsx',
        content: 'export const title = "CodingX";',
      },
      reactAction: '正在编辑 src/App.tsx',
      progressText: '正在生成 src/App.tsx',
    });
    const emptyProgressEvent = encodeSseEvent('tool-call', {
      callId: 'write-diff-retention-1',
      phase: 'progress',
      toolId: 'write',
      displayName: '写文件',
      reactAction: '正在编辑 src/App.tsx',
      progressText: '继续写入 src/App.tsx',
    });

    const mockReader = {
      read: vi.fn(() => {
        return new Promise<ReadableStreamReadResult<Uint8Array>>((resolve, reject) => {
          readQueue.push({ resolve, reject });
        });
      }),
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('/api/chat/stream')) {
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => mockReader,
          },
        } as unknown as Response;
      }
      if (isBootstrapFetch(url)) {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data:
              url === '/api/chat/conversations'
                ? [{ id: '2001', title: 'Default Demo Conversation', status: 'ACTIVE' }]
                : [],
          }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in file diff retention test: ${url}`);
    });

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await act(async () => {
      result.current.setInputValue('write src/App.tsx');
    });
    const submitPromise = result.current.submitMessage();
    await waitFor(() => {
      expect(readQueue.length).toBeGreaterThan(0);
    });

    for (const event of [metaEvent, firstProgressEvent, emptyProgressEvent]) {
      await act(async () => {
        readQueue.shift()?.resolve({ done: false, value: event });
      });
      await waitFor(() => {
        expect(readQueue.length).toBeGreaterThan(0);
      });
    }

    await waitFor(() => {
      const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
      const processCards = ((assistantMessage as Record<string, unknown> | undefined)?.processCards ?? []) as Array<Record<string, unknown>>;
      const toolCallCard = processCards.find((card) => card.type === 'tool_call');
      expect(toolCallCard?.summary).toBe('正在编辑 src/App.tsx');
      expect(toolCallCard?.fileDiffs).toEqual([
        expect.objectContaining({
          path: 'src/App.tsx',
          status: 'pending',
          diff: expect.stringContaining('+export const title = "CodingX";'),
        }),
      ]);
    });

    await act(async () => {
      readQueue.shift()?.resolve({ done: true, value: undefined });
    });
    await act(async () => {
      await submitPromise;
    });
  });
});

/**
 * 按 SSE 协议编码事件，保持测试输入和真实流式返回一致。
 */
function encodeSseEvent(eventName: string, payload: Record<string, unknown>) {
  return new TextEncoder().encode(`event:${eventName}\ndata:${JSON.stringify(payload)}\n\n`);
}

/**
 * 判断启动和流结束回放阶段会触发的空数据接口，避免测试被无关请求打断。
 */
function isBootstrapFetch(url: string) {
  return (
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
  );
}
