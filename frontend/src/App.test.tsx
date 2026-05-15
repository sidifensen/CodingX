import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App';

/**
 * 验证应用壳层在默认状态下可正常渲染。
 */
describe('App', () => {
  /**
   * 统一模拟聊天页在壳层测试期间会触发的基础数据请求，避免与认证测试互相污染。
   */
  const mockChatWorkspaceFetch = () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [],
          }),
          { status: 200 },
        );
      }
      if (url.startsWith('/api/chat/conversations/') && url.endsWith('/messages')) {
        return new Response(JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }), { status: 200 });
      }
      if (
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/steps')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/references')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/artifacts'))
      ) {
        return new Response(JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }), { status: 200 });
      }
      throw new Error(`Unhandled fetch in App test: ${url}`);
    });
  };

  /**
   * 在每个测试前重置浏览器持久化状态与网络模拟，避免测试互相污染。
   */
  beforeEach(() => {
    // 步骤：清理 localStorage，保证每个测试都从未登录状态开始。
    window.localStorage.clear();

    // 步骤：清理 fetch 模拟，避免上一条用例的响应残留影响当前断言。
    vi.restoreAllMocks();
    mockChatWorkspaceFetch();
  });

  /**
   * 验证首页首次加载时会展示品牌标题。
   */
  it('应渲染 CodingX 品牌标题', () => {
    // 步骤：渲染应用入口组件。
    render(<App />);

    // 步骤：断言页面中存在品牌标题，确保基础渲染链路可用。
    expect(screen.getAllByText('CodingX').length).toBeGreaterThan(0);
  });

  /**
   * 验证未登录时可打开并关闭登录弹窗。
   */
  it('应在点击登录后展示登录弹窗并支持取消', async () => {
    // 步骤：渲染应用入口组件。
    render(<App />);

    // 步骤：触发登录入口，进入登录弹窗。
    fireEvent.click(screen.getByRole('button', { name: '侧边栏登录入口' }));

    // 步骤：断言弹窗标题出现，证明登录表单已展示。
    expect(await screen.findByRole('heading', { name: '登录 CodingX' })).toBeInTheDocument();
    expect(screen.getByLabelText('账号')).toHaveValue('admin');
    expect(screen.getByLabelText('密码')).toHaveValue('123456');

    // 步骤：点击取消按钮，关闭登录弹窗。
    fireEvent.click(screen.getByRole('button', { name: '取消' }));

    // 步骤：等待弹窗从 DOM 移除，确认关闭行为生效。
    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: '登录 CodingX' })).not.toBeInTheDocument();
    });
  });

  /**
   * 验证未登录时发送聊天会唤起登录弹窗，而不是直接进入提交状态。
   */
  it('应在未登录发送消息时弹出登录表单', async () => {
    // 步骤：渲染应用入口组件。
    render(<App />);

    // 步骤：输入消息并触发发送。
    fireEvent.change(screen.getByPlaceholderText('输入指令以重构组件库或分析代码...'), {
      target: { value: '请帮我分析项目结构' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }));

    // 步骤：断言登录弹窗出现，证明发送动作被登录拦截。
    expect(await screen.findByRole('heading', { name: '登录 CodingX' })).toBeInTheDocument();
  });

  /**
   * 验证登录成功后会保存登录状态并更新左下角菜单文案。
   */
  it('应在登录成功后持久化登录状态并显示用户名', async () => {
    // 步骤：按 URL 模拟登录接口与登录后自动触发的会话列表请求。
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/auth/login') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: {
              userId: 1,
              username: 'demo',
              displayName: '测试用户',
              userType: 'USER',
              token: 'token-123',
            },
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations') {
        return new Response(JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }), { status: 200 });
      }
      if (
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/messages')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/steps')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/references')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/artifacts'))
      ) {
        return new Response(JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }), { status: 200 });
      }
      throw new Error(`Unhandled fetch in login success test: ${url}`);
    });

    // 步骤：渲染应用并打开登录弹窗。
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: '侧边栏登录入口' }));
    const loginDialog = await screen.findByRole('dialog', { name: '登录弹窗' });

    // 步骤：填写账号密码并提交登录。
    fireEvent.change(within(loginDialog).getByLabelText('账号'), { target: { value: 'demo' } });
    fireEvent.change(within(loginDialog).getByLabelText('密码'), { target: { value: '123456' } });
    fireEvent.click(within(loginDialog).getByRole('button', { name: '登录' }));

    // 步骤：断言左下角入口显示登录用户名，表示登录态生效。
    expect(await screen.findByRole('button', { name: '测试用户 个人中心' })).toBeInTheDocument();

    // 步骤：断言登录状态被写入 localStorage，确保刷新后可恢复。
    expect(window.localStorage.getItem('codingx.auth.session')).toContain('token-123');
  });

  /**
   * 验证点击退出登录后会清空登录状态并恢复登录入口。
   */
  it('应在退出登录后清空状态并恢复登录入口', async () => {
    // 步骤：按 URL 模拟登录、聊天列表加载和退出接口响应。
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/auth/login') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: {
              userId: 1,
              username: 'demo',
              displayName: '测试用户',
              userType: 'USER',
              token: 'token-123',
            },
          }),
          { status: 200 },
        );
      }
      if (url === '/api/auth/logout') {
        return new Response(JSON.stringify({ success: true, code: 'OK', message: 'logged out', data: null }), {
          status: 200,
        });
      }
      if (url === '/api/chat/conversations') {
        return new Response(JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }), { status: 200 });
      }
      if (
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/messages')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/steps')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/references')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/artifacts'))
      ) {
        return new Response(JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }), { status: 200 });
      }
      throw new Error(`Unhandled fetch in logout test: ${url}`);
    });

    // 步骤：渲染应用并完成一次登录。
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: '侧边栏登录入口' }));
    const loginDialog = await screen.findByRole('dialog', { name: '登录弹窗' });
    fireEvent.change(within(loginDialog).getByLabelText('账号'), { target: { value: 'demo' } });
    fireEvent.change(within(loginDialog).getByLabelText('密码'), { target: { value: '123456' } });
    fireEvent.click(within(loginDialog).getByRole('button', { name: '登录' }));
    await screen.findByRole('button', { name: '测试用户 个人中心' });

    // 步骤：打开个人中心弹层并触发退出登录。
    fireEvent.click(screen.getByRole('button', { name: '测试用户 个人中心' }));
    fireEvent.click(screen.getByRole('button', { name: '退出登录' }));

    // 步骤：断言登录入口恢复，且 localStorage 中会话已被清空。
    expect(await screen.findByRole('button', { name: '侧边栏登录入口' })).toBeInTheDocument();
    expect(window.localStorage.getItem('codingx.auth.session')).toBeNull();
  });

  /**
   * 验证左侧侧边栏应展示真实会话，而不是保留演示假数据。
   */
  it('应在侧边栏展示真实会话并移除假数据', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: 1002,
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
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
                id: 2001,
                title: 'Spring Boot SSE 最佳实践',
                status: 'ACTIVE',
                lastMessageAt: '2026-05-15 00:36:58',
                lastRunId: 5002,
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts'
      ) {
        return new Response(JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }), { status: 200 });
      }
      throw new Error(`Unhandled fetch in sidebar conversation test: ${url}`);
    });

    render(<App />);

    expect(await screen.findByText('Spring Boot SSE 最佳实践')).toBeInTheDocument();
    expect(screen.queryByText('量子力学是什么')).not.toBeInTheDocument();
    expect(screen.queryByText('Lumina战略方向: 深色模式设...')).not.toBeInTheDocument();
  });

  /**
   * 验证点击新建对话后会回到欢迎页，并让下一次发送走新会话链路。
   */
  it('应在点击新建对话后回到欢迎页并以无旧会话参数发送消息', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: 1002,
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
    const streamUrls: string[] = [];
    let messageRequestCount = 0;
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
                id: 2001,
                title: 'Default Demo Conversation',
                status: 'ACTIVE',
                lastMessageAt: '2026-05-15 00:36:58',
                lastRunId: 5002,
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2001/messages') {
        messageRequestCount += 1;
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: messageRequestCount > 1
              ? [
                  {
                    id: 201,
                    conversationId: 2010,
                    runId: 5003,
                    role: 'USER',
                    content: '请搜索新的会话问题',
                    status: 'COMPLETED',
                    createdAt: '2026-05-15 00:38:10',
                  },
                  {
                    id: 202,
                    conversationId: 2010,
                    runId: 5003,
                    role: 'ASSISTANT',
                    content: '新的会话回答',
                    status: 'COMPLETED',
                    createdAt: '2026-05-15 00:38:22',
                  },
                ]
              : [
                  {
                    id: 101,
                    conversationId: 2001,
                    runId: 5002,
                    role: 'USER',
                    content: '请搜索 Spring Boot SSE 最佳实践',
                    status: 'COMPLETED',
                    createdAt: '2026-05-15 00:36:58',
                  },
                  {
                    id: 102,
                    conversationId: 2001,
                    runId: 5002,
                    role: 'ASSISTANT',
                    content: '旧会话回答',
                    status: 'COMPLETED',
                    createdAt: '2026-05-15 00:37:11',
                  },
                ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts'
      ) {
        return new Response(JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }), { status: 200 });
      }
      if (url.includes('/api/chat/stream')) {
        streamUrls.push(url);
        return new Response(
          [
            'event:meta',
            'data:{"conversationId":2010}',
            '',
            'event:message',
            'data:{"type":"response","delta":"新的会话回答"}',
            '',
            'event:finish',
            'data:{"conversationId":2010,"content":"新的会话回答","title":"新的会话标题"}',
            '',
            'event:done',
            'data:{"conversationId":2010}',
            '',
          ].join('\n'),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2010/messages' ||
        url === '/api/chat/conversations/2010/steps' ||
        url === '/api/chat/conversations/2010/references' ||
        url === '/api/chat/conversations/2010/artifacts'
      ) {
        return new Response(JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }), { status: 200 });
      }
      throw new Error(`Unhandled fetch in new conversation test: ${url}`);
    });

    render(<App />);

    await screen.findByText('旧会话回答');
    fireEvent.click(screen.getByRole('button', { name: '新建对话' }));

    expect(await screen.findByText('你好，我是 CodingX')).toBeInTheDocument();
    expect(screen.queryByText('旧会话回答')).not.toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText('输入指令以重构组件库或分析代码...'), {
      target: { value: '请搜索新的会话问题' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }));

    await waitFor(() => {
      expect(streamUrls.length).toBeGreaterThan(0);
    });
    expect(streamUrls[0]).toContain('/api/chat/stream?');
    expect(streamUrls[0]).toContain('question=');
    expect(streamUrls[0]).not.toContain('conversationId=2001');
  });
});
