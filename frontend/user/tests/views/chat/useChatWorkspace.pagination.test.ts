import { act, renderHook, waitFor } from '@testing-library/react';
import { useChatWorkspace } from '@/views/chat/useChatWorkspace';

/**
 * 覆盖聊天工作区分页加载，确保会话和消息不再由前端一次性拉取全量列表。
 */
describe('useChatWorkspace pagination', () => {
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
   * 首屏启动必须调用带 pageSize 的会话分页接口，而不是旧的全量列表接口。
   */
  it('首屏应按分页接口加载会话第一页', async () => {
    const fetchSpy = mockPaginationFetch();

    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    expect(result.current.conversations.map((conversation) => conversation.id)).toEqual(['2001']);
    expect(fetchSpy.mock.calls.some(([input]) => String(input).includes('/api/chat/conversations?') && String(input).includes('pageSize='))).toBe(true);
  });

  /**
   * Sidebar 触发加载更多时应带上上一页 cursor，并追加新会话而不是替换已有列表。
   */
  it('应按 cursor 加载更多会话并去重追加', async () => {
    const fetchSpy = mockPaginationFetch();
    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.conversations).toHaveLength(1);
    });

    await act(async () => {
      await result.current.loadMoreConversations({
        partitionKey: 'cloud::__no_workspace__',
        runtimeTarget: 'cloud',
        workspacePath: null,
      });
    });

    expect(result.current.conversations.map((conversation) => conversation.id)).toEqual(['2001', '2002']);
    expect(fetchSpy.mock.calls.some(([input]) => String(input).includes('cursorId=2001'))).toBe(true);
  });

  /**
   * 选择会话时应只加载最近一页消息，并暴露是否还有更旧消息的状态。
   */
  it('选择会话时应按分页接口加载最近一页消息', async () => {
    const fetchSpy = mockPaginationFetch();
    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.conversations).toHaveLength(1);
    });

    await act(async () => {
      await result.current.selectConversation('2001', result.current.conversations);
    });

    expect(result.current.messages.map((message) => message.id)).toEqual(['5002']);
    expect(result.current.hasMoreMessagesBefore).toBe(true);
    expect(fetchSpy.mock.calls.some(([input]) => String(input).includes('/api/chat/conversations/2001/messages?') && String(input).includes('pageSize='))).toBe(true);
  });

  /**
   * 顶部加载旧消息时应 prepend 到现有消息前，不得清空当前最近页或产生重复消息。
   */
  it('应加载更旧消息并前置合并到当前消息列表', async () => {
    mockPaginationFetch();
    const { result } = renderHook(() => useChatWorkspace(true));

    await waitFor(() => {
      expect(result.current.conversations).toHaveLength(1);
    });
    await act(async () => {
      await result.current.selectConversation('2001', result.current.conversations);
    });
    await act(async () => {
      await result.current.loadOlderMessages();
    });

    expect(result.current.messages.map((message) => message.id)).toEqual(['5001', '5002']);
    expect(result.current.hasMoreMessagesBefore).toBe(false);
  });
});

/**
 * 构造分页测试的 fetch mock；旧全量 URL 也返回兼容数组，使失败集中在请求形态和 hook 状态。
 */
function mockPaginationFetch() {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (isConversationPageUrl(url)) {
      const searchParams = new URLSearchParams(url.split('?')[1] ?? '');
      if (searchParams.get('cursorId') === '2001') {
        return jsonResponse({
          items: [
            {
              id: '2002',
              title: '第二页会话',
              status: 'ACTIVE',
              lastMessageAt: '2026-06-08T11:00:00',
            },
          ],
          hasMore: false,
          nextCursor: null,
        });
      }
      return jsonResponse({
        items: [
          {
            id: '2001',
            title: '第一页会话',
            status: 'ACTIVE',
            lastMessageAt: '2026-06-08T12:00:00',
          },
        ],
        hasMore: true,
        nextCursor: {
          cursorPinned: false,
          cursorUpdatedAt: '2026-06-08T12:00:00',
          cursorId: '2001',
        },
      });
    }
    if (url === '/api/chat/conversations') {
      return jsonResponse([
        {
          id: '2001',
          title: '第一页会话',
          status: 'ACTIVE',
          lastMessageAt: '2026-06-08T12:00:00',
        },
      ]);
    }
    if (isMessagePageUrl(url)) {
      const searchParams = new URLSearchParams(url.split('?')[1] ?? '');
      if (searchParams.get('beforeId') === '5002') {
        return jsonResponse({
          items: [
            {
              id: '5001',
              conversationId: '2001',
              role: 'USER',
              content: '更早消息',
              status: 'COMPLETED',
            },
          ],
          hasMore: false,
          nextCursor: null,
        });
      }
      return jsonResponse({
        items: [
          {
            id: '5002',
            conversationId: '2001',
            role: 'ASSISTANT',
            content: '最近消息',
            status: 'COMPLETED',
          },
        ],
        hasMore: true,
        nextCursor: {
          cursorCreatedAt: '2026-06-08T11:30:00',
          cursorId: '5002',
        },
      });
    }
    if (url === '/api/chat/conversations/2001/messages') {
      return jsonResponse([
        {
          id: '5002',
          conversationId: '2001',
          role: 'ASSISTANT',
          content: '最近消息',
          status: 'COMPLETED',
        },
      ]);
    }
    if (isEmptyBootstrapUrl(url)) {
      return jsonResponse([]);
    }
    throw new Error(`Unhandled fetch in pagination hook test: ${url}`);
  });
}

function isConversationPageUrl(url: string) {
  return url.startsWith('/api/chat/conversations?') && url.includes('pageSize=');
}

function isMessagePageUrl(url: string) {
  return url.startsWith('/api/chat/conversations/2001/messages?') && url.includes('pageSize=');
}

function isEmptyBootstrapUrl(url: string) {
  return (
    url === '/api/chat/sample-questions' ||
    url === '/api/chat/experts' ||
    url === '/api/chat/skills' ||
    url === '/api/chat/mcps' ||
    url === '/api/chat/conversations/2001/steps' ||
    url === '/api/chat/conversations/2001/references' ||
    url === '/api/chat/conversations/2001/artifacts' ||
    url === '/api/chat/conversations/2001/current-experts' ||
    url === '/api/chat/conversations/2001/current-skills' ||
    url === '/api/chat/conversations/2001/current-mcps'
  );
}

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
