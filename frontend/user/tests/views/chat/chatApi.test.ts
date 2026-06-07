import { ChatApi } from '@/views/chat/chatApi';

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
   * 会话列表应解析后端任务完成提醒已读字段，刷新后侧栏提醒圆点以该字段为准。
   */
  it('应解析任务完成提醒已读字段', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: 2001,
              title: '后台任务会话',
              status: 'ACTIVE',
              taskCompletionRead: false,
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listConversations('token-123');

    expect(result[0].taskCompletionRead).toBe(false);
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
   * Slash Command 目录请求应命中治理模块暴露的用户侧命令接口，并把数字主键规整为字符串。
   */
  it('应携带 satoken 加载Slash Command目录', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: [
            {
              id: 9101,
              commandCode: 'review',
              displayName: '/review',
              description: '执行代码审查',
              commandType: 'BUILTIN',
              enabled: 1,
              sortNo: 1,
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listSlashCommands('token-123');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/slash-commands',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
    expect(result[0].id).toBe('9101');
    expect(result[0].commandCode).toBe('review');
    expect(result[0].displayName).toBe('/review');
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
   * MCP 列表请求应解析 enabled/available 字段，供前端禁用不可用项。
   */
  it('应解析MCP可用状态字段', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
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
              enabled: 1,
              available: true,
            },
            {
              id: 7002,
              mcpCode: 'weather_query',
              displayName: '天气查询',
              enabled: 1,
              available: false,
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.listMcps('token-123');

    expect(result[0].enabled).toBe(1);
    expect(result[0].available).toBe(true);
    expect(result[1].enabled).toBe(1);
    expect(result[1].available).toBe(false);
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
   * 删除会话内消息应提交规范化后的消息 ID，保证编辑重发和删除选择都能持久生效。
   */
  it('应通过专用接口删除会话消息', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'messages deleted',
          data: null,
        }),
        { status: 200 },
      ),
    );

    await ChatApi.deleteConversationMessages('token-123', '2055114974648864768', [
      '101',
      '',
      '102',
      '101',
    ]);

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2055114974648864768/messages',
      expect.objectContaining({
        method: 'DELETE',
        body: JSON.stringify({ messageIds: ['101', '102'] }),
      }),
    );
  });

  /**
   * 打开会话后应调用任务完成提醒已读接口，避免刷新后重复提示。
   */
  it('应通过专用接口标记任务完成提醒已读', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'conversation task completion read',
          data: null,
        }),
        { status: 200 },
      ),
    );

    await ChatApi.markTaskCompletionRead('token-123', '2055114974648864768');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2055114974648864768/task-completion-read',
      expect.objectContaining({
        method: 'PATCH',
      }),
    );
  });

  /**
   * 分享会话应命中专用接口并返回分享令牌与分享路径。
   */
  it('应通过专用接口生成分享链接', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'conversation shared',
          data: {
            shareToken: 'share_xxx',
            shareUrl: '/api/chat/conversations/shared/share_xxx',
          },
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.shareConversation('token-123', '2055114974648864768');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2055114974648864768/share',
      expect.objectContaining({
        method: 'POST',
      }),
    );
    expect(result.shareToken).toBe('share_xxx');
    expect(result.shareUrl).toBe('/api/chat/conversations/shared/share_xxx');
  });

  /**
   * 选择轮次分享时仍可提交消息 ID，但后端返回的公开链接应保持短链接形式。
   */
  it('应携带选中消息生成分享链接', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'conversation shared',
          data: {
            shareToken: 'share_xxx',
            shareUrl: '/share/chat/share_xxx',
          },
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.shareConversation('token-123', '2055114974648864768', {
      messageIds: ['101', '102'],
    });

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2055114974648864768/share',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ messageIds: ['101', '102'] }),
      }),
    );
    expect(result.shareUrl).toBe('/share/chat/share_xxx');
  });

  /**
   * 公开分享页应支持按 messages 查询参数读取只读回放。
   */
  it('应按消息 ID 加载公开分享会话', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'success',
          data: {
            conversation: {
              id: 2055114974648864768,
              title: '分享标题',
              status: 'ACTIVE',
              lastMessageAt: '2026-05-26 08:00:00',
            },
            messages: [
              {
                id: '101',
                conversationId: '2055114974648864768',
                role: 'USER',
                content: '分享的问题',
                status: 'COMPLETED',
              },
            ],
          },
        }),
        { status: 200 },
      ),
    );

    const result = await ChatApi.getSharedConversation('share_xxx', ['101', '102']);

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/shared/share_xxx?messages=101%2C102',
      expect.objectContaining({
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
        }),
      }),
    );
    expect(result.conversation.title).toBe('分享标题');
    expect(result.messages).toHaveLength(1);
  });

  /**
   * 重新生成会话应命中专用接口。
   */
  it('应通过专用接口重新生成会话', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'conversation regenerated',
          data: null,
        }),
        { status: 200 },
      ),
    );

    await ChatApi.regenerateConversation('token-123', '2055114974648864768');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/chat/conversations/2055114974648864768/regenerate',
      expect.objectContaining({
        method: 'POST',
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
            projectProfile: {
              summary: 'Maven + Vite workspace',
              moduleMapJson: '[{"name":"backend","path":"backend"}]',
              testCommandsJson: '["mvn test","npm run build"]',
              keyEntrypointsJson: '["backend/src/main/java/com/codingx/CodingXApplication.java"]',
              riskPointsJson: '["缺少端到端测试"]',
              agentContext: '项目包含后端、用户端和管理端。',
            },
            pendingMemoryCount: 2,
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
    expect(result.projectProfile?.summary).toBe('Maven + Vite workspace');
    expect(result.pendingMemoryCount).toBe(2);
  });

  /**
   * 用户端长期记忆接口应复用统一 ApiResponse 解析，并把大整数 ID 归一为字符串。
   */
  it('应查询并更新用户长期记忆状态', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 9001,
                memoryScope: 'PROJECT',
                userId: 1002,
                workspaceId: 3001,
                content: '以后都按项目注释规范编写 Java 注释',
                status: 'PENDING',
                keywordJson: '["注释规范"]',
              },
            ],
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: {
              id: 9001,
              memoryScope: 'PROJECT',
              userId: 1002,
              workspaceId: 3001,
              content: '以后都按项目注释规范编写 Java 注释',
              status: 'ACTIVE',
            },
          }),
          { status: 200 },
        ),
      );

    const memories = await ChatApi.listLongTermMemories('token-123', '3001', 'PENDING');
    const updated = await ChatApi.updateLongTermMemoryStatus('token-123', '9001', 'ACTIVE');

    expect(fetchSpy).toHaveBeenNthCalledWith(
      1,
      '/api/chat/memories?workspaceId=3001&status=PENDING',
      expect.objectContaining({
        headers: expect.objectContaining({
          satoken: 'token-123',
        }),
      }),
    );
    expect(fetchSpy).toHaveBeenNthCalledWith(
      2,
      '/api/chat/memories/9001/status',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ status: 'ACTIVE' }),
      }),
    );
    expect(memories[0].id).toBe('9001');
    expect(memories[0].workspaceId).toBe('3001');
    expect(updated.status).toBe('ACTIVE');
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
