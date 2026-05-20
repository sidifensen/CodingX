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
   * 会话列表应在本地工作空间场景携带 workspaceId 参数。
   */
  it('应在会话列表请求中携带 workspaceId', async () => {
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

    await ChatApi.listConversations('token-123', '3001');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations?workspaceId=3001',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
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
   * 示例问题请求应命中专用接口并返回字符串化后的标识。
   */
  it('应加载首页示例问题', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: 6001,
              questionText: '请介绍一下 OA 系统的主要功能',
              category: '业务系统',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listSampleQuestions('token-123');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/sample-questions',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
    expect(result[0].id).toBe('6001');
    expect(result[0].questionText).toBe('请介绍一下 OA 系统的主要功能');
  });

  /**
   * 技能列表请求应命中用户侧技能接口并携带鉴权头。
   */
  it('应携带 satoken 加载技能列表', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: 7101,
              skillCode: 'sales_query',
              displayName: '销售查询',
              description: '查询销售汇总、排名、趋势与明细',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listSkills('token-123');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/skills',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
    expect(result[0].skillCode).toBe('sales_query');
    expect(result[0].displayName).toBe('销售查询');
  });

  /**
   * 专家列表请求应命中用户侧专家接口并携带鉴权头。
   */
  it('应携带 satoken 加载专家列表', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: 8301,
              expertCode: 'solution-architect',
              displayName: '解决方案架构师',
              description: '擅长业务澄清、系统分层与落地架构取舍',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listExperts('token-123');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/experts',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
    expect(result[0].expertCode).toBe('solution-architect');
    expect(result[0].displayName).toBe('解决方案架构师');
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

  /**
   * 当前技能列表请求应命中会话级 current-skills 接口并返回字符串化标识。
   */
  it('应加载会话当前技能列表', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: 7101,
              skillCode: 'sales_query',
              displayName: '销售查询',
              category: '销售',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listCurrentSkills('token-123', '2055114974648864768');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2055114974648864768/current-skills',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
    expect(result[0].id).toBe('7101');
    expect(result[0].skillCode).toBe('sales_query');
  });

  /**
   * 当前 MCP 列表请求应命中会话级 current-mcps 接口并返回字符串化标识。
   */
  it('应加载会话当前MCP列表', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: 7001,
              mcpCode: 'sales_query',
              displayName: '销售查询',
              category: '检索',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listCurrentMcps('token-123', '2055114974648864768');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2055114974648864768/current-mcps',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
    expect(result[0].id).toBe('7001');
    expect(result[0].mcpCode).toBe('sales_query');
  });

  /**
   * 当前专家列表请求应命中会话级 current-experts 接口并返回字符串化标识。
   */
  it('应加载会话当前专家列表', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: 8301,
              expertCode: 'solution-architect',
              displayName: '解决方案架构师',
              category: '研发架构',
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listCurrentExperts('token-123', '2055114974648864768');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2055114974648864768/current-experts',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
    expect(result[0].id).toBe('8301');
    expect(result[0].expertCode).toBe('solution-architect');
  });

  /**
   * 绑定本地仓库路径应命中用户态工作区绑定接口。
   */
  it('应提交本地仓库路径绑定请求', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'workspace bound',
          data: {
            repositoryPath: 'D:/code/codingx',
            workspaceId: '3001',
            workspaceName: 'codingx',
          },
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.bindWorkspaceRepository('token-123', 'D:/code/codingx');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/workspace/bind-repository',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ repositoryPath: 'D:/code/codingx' }),
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
    expect(result.repositoryPath).toBe('D:/code/codingx');
    expect(result.workspaceId).toBe('3001');
    expect(result.workspaceName).toBe('codingx');
  });


  /**
   * 上传附件应使用 multipart/form-data，并命中附件上传接口。
   */
  it('应通过附件上传接口提交文件', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            id: 9001,
            conversationId: 2001,
            messageId: null,
            attachmentType: 'image',
            fileName: 'demo.png',
            fileSize: 1024,
            previewUrl: '/api/chat/attachments/9001/content',
            status: 'UPLOADED',
          },
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.uploadAttachment(
      'token-123',
      new File(['mock'], 'demo.png', { type: 'image/png' }),
      '2001',
    );

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/attachments/upload',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
        body: expect.any(FormData),
      }),
    );
    expect(result.id).toBe('9001');
    expect(result.attachmentType).toBe('image');
  });
});
