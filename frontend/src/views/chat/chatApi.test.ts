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
   * 大整数会话标识必须按字符串原样传递，避免前端 Number 精度丢失导致查不到会话。
   */
  it('应原样使用字符串会话标识加载消息', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [],
        }),
        { status: 200 },
      ),
    );

    await ChatApi.listMessages('token-123', '2055114974648864768');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2055114974648864768/messages',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
  });

  /**
   * 会话重命名应通过专用接口提交新标题。
   */
  it('应通过专用接口提交会话重命名', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'conversation renamed',
          data: null,
        }),
        { status: 200 },
      ),
    );

    await ChatApi.renameConversation('token-123', '2055114974648864768', '新的会话标题');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2055114974648864768',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ title: '新的会话标题' }),
      }),
    );
  });

  /**
   * 删除会话应通过专用接口触发软删除。
   */
  it('应通过专用接口删除会话', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'conversation deleted',
          data: null,
        }),
        { status: 200 },
      ),
    );

    await ChatApi.deleteConversation('token-123', '2055114974648864768');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2055114974648864768',
      expect.objectContaining({
        method: 'DELETE',
      }),
    );
  });
});
