import { ChatApi } from '@/views/chat/chatApi';

/**
 * 验证聊天分页 API 封装，确保列表首屏和历史追加都通过后端 cursor 协议完成。
 */
describe('ChatApi pagination', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  /**
   * 会话分页请求应带上 pageSize 和 cursor，并把后端 Long 主键统一转为字符串。
   */
  it('应解析会话分页响应并携带 cursor 参数', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            items: [
              {
                id: 2001,
                title: '分页会话',
                status: 'ACTIVE',
                lastMessageAt: '2026-06-08T12:00:00',
                workspaceId: 3001,
              },
            ],
            hasMore: true,
            nextCursor: {
              cursorPinned: false,
              cursorUpdatedAt: '2026-06-08T12:00:00',
              cursorId: 2001,
            },
          },
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listConversationPage('token-123', {
      workspaceId: '3001',
      pageSize: 20,
      cursor: {
        cursorPinned: false,
        cursorUpdatedAt: '2026-06-08T12:30:00',
        cursorId: '2000',
      },
    });

    const requestPath = String(fetchSpy.mock.calls[0]?.[0] ?? '');
    expect(requestPath).toContain('/api/chat/conversations?');
    expect(requestPath).toContain('workspaceId=3001');
    expect(requestPath).toContain('pageSize=20');
    expect(requestPath).toContain('cursorPinned=false');
    expect(requestPath).toContain('cursorUpdatedAt=2026-06-08T12%3A30%3A00');
    expect(requestPath).toContain('cursorId=2000');
    expect(result.items[0].id).toBe('2001');
    expect(result.items[0].workspaceId).toBe('3001');
    expect(result.hasMore).toBe(true);
    expect(result.nextCursor?.cursorId).toBe('2001');
  });

  /**
   * 消息分页请求应带上 before cursor，并复用旧消息列表的附件与技能字段归一化逻辑。
   */
  it('应解析消息分页响应并归一化附件字段', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            items: [
              {
                id: 5001,
                conversationId: 2001,
                role: 'USER',
                content: '@weather_query 上海天气',
                status: 'COMPLETED',
                skillCodes: ['weather_query'],
                attachments: [
                  {
                    id: 9001,
                    conversationId: 2001,
                    messageId: 5001,
                    attachmentType: 'image',
                    fileName: 'demo.png',
                    fileSize: 2048,
                    status: 'UPLOADED',
                  },
                ],
              },
            ],
            hasMore: true,
            nextCursor: {
              cursorCreatedAt: '2026-06-08T11:00:00',
              cursorId: 5001,
            },
          },
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listMessagePage('token-123', '2001', {
      pageSize: 10,
      before: {
        cursorCreatedAt: '2026-06-08T11:30:00',
        cursorId: '5002',
      },
    });

    const requestPath = String(fetchSpy.mock.calls[0]?.[0] ?? '');
    expect(requestPath).toContain('/api/chat/conversations/2001/messages?');
    expect(requestPath).toContain('pageSize=10');
    expect(requestPath).toContain('beforeCreatedAt=2026-06-08T11%3A30%3A00');
    expect(requestPath).toContain('beforeId=5002');
    expect(result.items[0].id).toBe('5001');
    expect(result.items[0].attachments?.[0].id).toBe('9001');
    expect(result.nextCursor?.cursorId).toBe('5001');
  });

  /**
   * 流式请求入口应集中在 API 层设置 satoken 和 signal，Hook 只负责传入已构造好的 URL。
   */
  it('应通过 API 层打开聊天流请求', async () => {
    const abortController = new AbortController();
    const response = new Response('', { status: 200 });
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response);

    const result = await ChatApi.openChatStream(
      '/api/chat/stream?question=%E4%BD%A0%E5%A5%BD',
      'token-123',
      abortController.signal,
    );

    expect(result).toBe(response);
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/stream?question=%E4%BD%A0%E5%A5%BD',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
        signal: abortController.signal,
      }),
    );
  });
});
