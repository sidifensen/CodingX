import { ChatApi } from './chatApi';

/**
 * 验证聊天工作区 API 封装会携带鉴权头并解析统一响应。
 */
describe('ChatApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  /**
   * 会话列表请求应携带 satoken 并返回后端数据。
   */
  it('应携带 satoken 加载会话列表', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: 2001,
              title: 'Default Demo Conversation',
              status: 'ACTIVE',
              lastMessageAt: '2026-05-15 00:36:58',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listConversations('token-123');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
    expect(result[0].title).toBe('Default Demo Conversation');
  });

  /**
   * 反馈请求应命中兼容路径并透传请求体。
   */
  it('应通过兼容路径提交反馈', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'feedback submitted',
          data: null,
        }),
        { status: 200 },
      ),
    );

    await ChatApi.submitFeedback('token-123', 101, {
      conversationId: 2001,
      vote: 1,
      reason: 'helpful',
      comment: 'good',
    });

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/messages/101/feedback',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          conversationId: 2001,
          vote: 1,
          reason: 'helpful',
          comment: 'good',
        }),
      }),
    );
  });
});
