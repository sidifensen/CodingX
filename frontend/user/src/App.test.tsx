import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App';

/**
 * 验证应用壳层在默认状态下可正常渲染。
 */
describe('App', () => {
  const previousCodingxHost = window.codingxHost;

  /**
   * 统一模拟聊天页在壳层测试期间会触发的基础数据请求，避免与认证测试互相污染。
   */
  const mockChatWorkspaceFetch = () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url.startsWith('/api/chat/conversations/') &&
        (url.endsWith('/current-skills') || url.endsWith('/current-mcps'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
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
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/steps')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/references')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/artifacts'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
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

    // 步骤：默认提供 Web 宿主桥接占位，避免测试环境因标题栏窗口事件能力缺失触发异常。
    window.codingxHost = {
      getContext: async () => ({
        hostType: 'web',
        executionTargets: ['cloud'],
        capabilities: {
          localFiles: false,
          localFolderPicker: false,
          shell: false,
          browserAutomation: true,
          desktopNotifications: false,
          officeInterop: false,
          localMcp: false,
          windowControls: false,
        },
        localResource: {
          boundRepositoryPath: null,
          permissionGranted: false,
        },
      }),
      getWindowState: async () => ({
        isMaximized: false,
        isMinimized: false,
        isFullScreen: false,
      }),
      minimizeWindow: async () => undefined,
      toggleMaximizeWindow: async () => ({
        isMaximized: false,
        isMinimized: false,
        isFullScreen: false,
      }),
      closeWindow: async () => undefined,
      invokeDesktopMenuAction: async () => undefined,
      onWindowStateChanged: () => () => undefined,
      pickRepositoryDirectory: async () => null,
      bindRepositoryPath: async () => ({
        hostType: 'web',
        executionTargets: ['cloud'],
        capabilities: {
          localFiles: false,
          localFolderPicker: false,
          shell: false,
          browserAutomation: true,
          desktopNotifications: false,
          officeInterop: false,
          localMcp: false,
          windowControls: false,
        },
        localResource: {
          boundRepositoryPath: null,
          permissionGranted: false,
        },
      }),
      requestFileAccess: async () => false,
      listDirectory: async () => [],
    };
  });

  afterAll(() => {
    window.codingxHost = previousCodingxHost;
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

    expect(screen.getByRole('button', { name: '打开MCP列表' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '打开技能列表' })).toBeInTheDocument();

    // 步骤：输入消息并触发发送。
    fireEvent.change(screen.getByPlaceholderText('输入问题，或先选择技能/MCP...'), {
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
      if (
        url.startsWith('/api/chat/conversations/') &&
        (url.endsWith('/current-skills') || url.endsWith('/current-mcps'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
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
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/messages')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/steps')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/references')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/artifacts'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
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
      if (
        url.startsWith('/api/chat/conversations/') &&
        (url.endsWith('/current-skills') || url.endsWith('/current-mcps'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
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
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'logged out', data: null }),
          {
            status: 200,
          },
        );
      }
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/messages')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/steps')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/references')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/artifacts'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
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
   * 本地残留会话但服务端判定未登录时，不应渲染左下角用户名。
   */
  it('应在本地会话失效时清理会话并展示登录入口', async () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'expired-token',
        userId: 1002,
        username: 'user',
        displayName: 'CodingX Admin',
        userType: 'USER',
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (
        url.startsWith('/api/chat/conversations/') &&
        (url.endsWith('/current-skills') || url.endsWith('/current-mcps'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/auth/me') {
        expect(init?.method).toBe('GET');
        expect((init?.headers as Record<string, string>)?.satoken).toBe('expired-token');
        return new Response(
          JSON.stringify({
            success: false,
            code: 'UNAUTHORIZED',
            message: '未登录',
            data: null,
          }),
          { status: 401 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.startsWith('/api/chat/conversations/') && url.endsWith('/messages')) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/steps')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/references')) ||
        (url.startsWith('/api/chat/conversations/') && url.endsWith('/artifacts'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in expired session test: ${url}`);
    });

    render(<App />);

    expect(await screen.findByRole('button', { name: '侧边栏登录入口' })).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'CodingX Admin 个人中心' }),
    ).not.toBeInTheDocument();
    expect(window.localStorage.getItem('codingx.auth.session')).toBeNull();
  });

  /**
   * 聊天请求若返回未登录错误，应立即清理本地会话并拉起登录弹窗，避免页面停留在错误态。
   */
  it('应在聊天流返回未登录时立即打开登录弹窗并清理会话', async () => {
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

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (
        url.startsWith('/api/chat/conversations/') &&
        (url.endsWith('/current-skills') || url.endsWith('/current-mcps'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/auth/me') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: {
              userId: 1002,
              username: 'user',
              displayName: 'CodingX User',
              userType: 'USER',
            },
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 2001,
                title: '会话A',
                status: 'ACTIVE',
                lastMessageAt: '2026-05-15 00:36:58',
                lastRunId: 5002,
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.startsWith('/api/chat/stream?')) {
        expect((init?.headers as Record<string, string>)?.satoken).toBe('token-123');
        return new Response(
          JSON.stringify({
            success: false,
            code: 'UNAUTHORIZED',
            message: '未登录或登录已失效，请重新登录',
            data: null,
          }),
          { status: 401 },
        );
      }
      throw new Error(`Unhandled fetch in stream unauthorized test: ${url}`);
    });

    render(<App />);

    await screen.findByRole('button', { name: 'CodingX User 个人中心' });
    fireEvent.change(screen.getByPlaceholderText('输入问题，或先选择技能/MCP...'), {
      target: { value: '北京天气' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }));

    expect(await screen.findByRole('heading', { name: '登录 CodingX' })).toBeInTheDocument();
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
      if (
        url.startsWith('/api/chat/conversations/') &&
        (url.endsWith('/current-skills') || url.endsWith('/current-mcps'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/auth/me') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: {
              userId: 1002,
              username: 'user',
              displayName: 'CodingX User',
              userType: 'USER',
            },
          }),
          { status: 200 },
        );
      }
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
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2001/messages' ||
        url === '/api/chat/conversations/2001/steps' ||
        url === '/api/chat/conversations/2001/references' ||
        url === '/api/chat/conversations/2001/artifacts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
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
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '云端工作空间',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now(),
            activeConversationId: '2001',
            conversations: [
              {
                id: '2001',
                title: 'Default Demo Conversation',
                status: 'ACTIVE',
                lastMessageAt: '2026-05-15 00:36:58',
                lastRunId: '5002',
              },
            ],
            conversationRecords: {
              '2001': {
                owned: true,
                messages: [
                  {
                    id: '101',
                    conversationId: '2001',
                    runId: '5002',
                    role: 'USER',
                    content: '请搜索 Spring Boot SSE 最佳实践',
                    status: 'COMPLETED',
                    createdAt: '2026-05-15 00:36:58',
                  },
                  {
                    id: '102',
                    conversationId: '2001',
                    runId: '5002',
                    role: 'ASSISTANT',
                    content: '旧会话回答',
                    status: 'COMPLETED',
                    createdAt: '2026-05-15 00:37:11',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
        },
      }),
    );
    const streamUrls: string[] = [];
    let messageRequestCount = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url.startsWith('/api/chat/conversations/') &&
        (url.endsWith('/current-skills') || url.endsWith('/current-mcps'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/auth/me') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: {
              userId: 1002,
              username: 'user',
              displayName: 'CodingX User',
              userType: 'USER',
            },
          }),
          { status: 200 },
        );
      }
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
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
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
            data:
              messageRequestCount > 1
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
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url.includes('/api/chat/stream')) {
        streamUrls.push(url);
        return new Response(
          [
            'event:meta',
            'data:{"conversationId":"2010"}',
            '',
            'event:message',
            'data:{"type":"response","delta":"新的会话回答"}',
            '',
            'event:finish',
            'data:{"conversationId":"2010","content":"新的会话回答","title":"新的会话标题"}',
            '',
            'event:done',
            'data:{"conversationId":"2010"}',
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
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in new conversation test: ${url}`);
    });

    render(<App />);

    await screen.findByText('旧会话回答');
    fireEvent.click(screen.getByRole('button', { name: '新建对话' }));

    expect(await screen.findByText('你好，我是 CodingX')).toBeInTheDocument();
    expect(screen.queryByText('旧会话回答')).not.toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText('输入问题，或先选择技能/MCP...'), {
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

  /**
   * 验证点击左侧不同真实会话后，主区会切换到对应对话内容。
   */
  it('应在点击侧边栏真实会话后切换到对应消息回放', async () => {
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
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '云端工作空间',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now(),
            activeConversationId: '2055114974648864768',
            conversations: [
              {
                id: '2055114974648864768',
                title: '第一个真实会话',
                status: 'ACTIVE',
                lastMessageAt: '2026-05-15 09:36:58',
                lastRunId: '5001',
              },
              {
                id: '2055120756043943936',
                title: '第二个真实会话',
                status: 'ACTIVE',
                lastMessageAt: '2026-05-15 09:56:58',
                lastRunId: '5002',
              },
            ],
            conversationRecords: {
              '2055114974648864768': {
                owned: true,
                messages: [
                  {
                    id: '101',
                    conversationId: '2055114974648864768',
                    runId: '5001',
                    role: 'ASSISTANT',
                    content: '这是第一个会话的回答',
                    status: 'COMPLETED',
                    createdAt: '2026-05-15 09:37:11',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentSkills: [],
                currentMcps: [],
              },
              '2055120756043943936': {
                owned: true,
                messages: [
                  {
                    id: '201',
                    conversationId: '2055120756043943936',
                    runId: '5002',
                    role: 'ASSISTANT',
                    content: '这是第二个会话的回答',
                    status: 'COMPLETED',
                    createdAt: '2026-05-15 09:57:11',
                  },
                ],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
        },
      }),
    );
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url.startsWith('/api/chat/conversations/') &&
        (url.endsWith('/current-skills') || url.endsWith('/current-mcps'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/auth/me') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: {
              userId: 1002,
              username: 'user',
              displayName: 'CodingX User',
              userType: 'USER',
            },
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2055114974648864768',
                title: '第一个真实会话',
                status: 'ACTIVE',
                lastMessageAt: '2026-05-15 09:36:58',
                lastRunId: '5001',
              },
              {
                id: '2055120756043943936',
                title: '第二个真实会话',
                status: 'ACTIVE',
                lastMessageAt: '2026-05-15 09:56:58',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2055114974648864768/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '101',
                conversationId: '2055114974648864768',
                runId: '5001',
                role: 'ASSISTANT',
                content: '这是第一个会话的回答',
                status: 'COMPLETED',
                createdAt: '2026-05-15 09:37:11',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations/2055120756043943936/messages') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '201',
                conversationId: '2055120756043943936',
                runId: '5002',
                role: 'ASSISTANT',
                content: '这是第二个会话的回答',
                status: 'COMPLETED',
                createdAt: '2026-05-15 09:57:11',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2055114974648864768/steps' ||
        url === '/api/chat/conversations/2055114974648864768/references' ||
        url === '/api/chat/conversations/2055114974648864768/artifacts' ||
        url === '/api/chat/conversations/2055120756043943936/steps' ||
        url === '/api/chat/conversations/2055120756043943936/references' ||
        url === '/api/chat/conversations/2055120756043943936/artifacts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in sidebar switch test: ${url}`);
    });

    render(<App />);

    expect(await screen.findByText('这是第一个会话的回答')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: /第二个真实会话/ })[0]);

    expect(await screen.findByText('这是第二个会话的回答')).toBeInTheDocument();
    expect(screen.queryByText('这是第一个会话的回答')).not.toBeInTheDocument();
  });

  /**
   * 左侧真实会话列表应显示相对时间，不再显示“今天/最近七天/更早”分组标题。
   */
  it('应在侧边栏仅显示会话标题与相对时间', async () => {
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
    const realDateNow = Date.now;
    vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-05-15T12:00:00').getTime());
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url.startsWith('/api/chat/conversations/') &&
        (url.endsWith('/current-skills') || url.endsWith('/current-mcps'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/auth/me') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: {
              userId: 1002,
              username: 'user',
              displayName: 'CodingX User',
              userType: 'USER',
            },
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2055114974648864768',
                title: '两个小时前的会话',
                status: 'ACTIVE',
                lastMessageAt: '2026-05-15 10:00:00',
                lastRunId: '5001',
              },
              {
                id: '2055120756043943936',
                title: '三天前的会话',
                status: 'ACTIVE',
                lastMessageAt: '2026-05-12 09:00:00',
                lastRunId: '5002',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2055114974648864768/messages' ||
        url === '/api/chat/conversations/2055114974648864768/steps' ||
        url === '/api/chat/conversations/2055114974648864768/references' ||
        url === '/api/chat/conversations/2055114974648864768/artifacts' ||
        url === '/api/chat/conversations/2055120756043943936/messages' ||
        url === '/api/chat/conversations/2055120756043943936/steps' ||
        url === '/api/chat/conversations/2055120756043943936/references' ||
        url === '/api/chat/conversations/2055120756043943936/artifacts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in relative time test: ${url}`);
    });

    render(<App />);

    expect(await screen.findByText('两个小时前的会话')).toBeInTheDocument();
    expect(screen.getByText('2 小时前')).toBeInTheDocument();
    expect(screen.getByText('3 天前')).toBeInTheDocument();
    expect(screen.queryByText('今天')).not.toBeInTheDocument();
    expect(screen.queryByText('最近七天')).not.toBeInTheDocument();
    expect(screen.queryByText('更早')).not.toBeInTheDocument();
    Date.now = realDateNow;
  });

  /**
   * 点击左侧会话三点按钮后应弹出重命名和删除菜单。
   */
  it('应在点击侧边栏会话操作按钮后展示重命名与删除菜单', async () => {
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
      if (
        url.startsWith('/api/chat/conversations/') &&
        (url.endsWith('/current-skills') || url.endsWith('/current-mcps'))
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/auth/me') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: {
              userId: 1002,
              username: 'user',
              displayName: 'CodingX User',
              userType: 'USER',
            },
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: '2055114974648864768',
                title: '可操作会话',
                status: 'ACTIVE',
                lastMessageAt: '2026-05-15 10:00:00',
                lastRunId: '5001',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/sample-questions') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/mcps') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (url === '/api/chat/skills') {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/conversations/2055114974648864768/messages' ||
        url === '/api/chat/conversations/2055114974648864768/steps' ||
        url === '/api/chat/conversations/2055114974648864768/references' ||
        url === '/api/chat/conversations/2055114974648864768/artifacts'
      ) {
        return new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
          { status: 200 },
        );
      }
      throw new Error(`Unhandled fetch in conversation menu test: ${url}`);
    });

    render(<App />);

    await screen.findByText('可操作会话');
    const menuButton = screen.getByRole('button', { name: '打开会话菜单 可操作会话' });
    fireEvent.mouseEnter(menuButton.parentElement as HTMLElement);
    expect(menuButton).toHaveStyle({ opacity: '1' });
    expect(screen.queryByRole('button', { name: '重命名对话' })).not.toBeInTheDocument();
    fireEvent.click(menuButton);

    expect(await screen.findByRole('button', { name: '重命名对话' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '删除对话' })).toBeInTheDocument();
  });

  /**
   * 内容区左上角按钮应支持折叠与展开桌面端左侧边栏。
   */
  it('应支持通过内容区按钮折叠和展开左侧边栏', async () => {
    render(<App />);

    const sidebarLabel = screen.getByText('我的空间');
    expect(sidebarLabel).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '折叠左侧边栏' }));
    expect(sidebarLabel.closest('aside')).toHaveAttribute('aria-hidden', 'true');

    fireEvent.click(screen.getByRole('button', { name: '展开左侧边栏' }));
    expect(sidebarLabel.closest('aside')).toHaveAttribute('aria-hidden', 'false');
  });

  /**
   * 左侧侧栏折叠时应保留 DOM 挂载，仅切换可见状态，避免卸载重建造成动画卡顿。
   */
  it('应在折叠左侧边栏时保留侧栏内容节点', async () => {
    render(<App />);

    const sidebarLabel = screen.getByText('我的空间');
    fireEvent.click(screen.getByRole('button', { name: '折叠左侧边栏' }));

    expect(sidebarLabel).toBeInTheDocument();
    expect(sidebarLabel.closest('aside')).toHaveAttribute('aria-hidden', 'true');
  });

  /**
   * 桌面宿主下应渲染自定义标题栏并展示窗口控制按钮，避免依赖原生标题栏。
   */
  it('应在桌面宿主下显示自定义窗口控制栏', async () => {
    window.codingxHost = {
      getContext: async () => ({
        hostType: 'desktop',
        executionTargets: ['cloud', 'local'],
        capabilities: {
          localFiles: true,
          localFolderPicker: true,
          shell: true,
          browserAutomation: true,
          desktopNotifications: true,
          officeInterop: true,
          localMcp: true,
          windowControls: true,
        },
        localResource: {
          boundRepositoryPath: null,
          permissionGranted: false,
        },
      }),
      getWindowState: async () => ({
        isMaximized: false,
        isMinimized: false,
        isFullScreen: false,
      }),
      minimizeWindow: async () => undefined,
      toggleMaximizeWindow: async () => ({
        isMaximized: true,
        isMinimized: false,
        isFullScreen: false,
      }),
      closeWindow: async () => undefined,
      invokeDesktopMenuAction: async () => undefined,
      onWindowStateChanged: () => () => undefined,
      pickRepositoryDirectory: async () => null,
      bindRepositoryPath: async () => ({
        hostType: 'desktop',
        executionTargets: ['cloud', 'local'],
        capabilities: {
          localFiles: true,
          localFolderPicker: true,
          shell: true,
          browserAutomation: true,
          desktopNotifications: true,
          officeInterop: true,
          localMcp: true,
          windowControls: true,
        },
        localResource: {
          boundRepositoryPath: null,
          permissionGranted: false,
        },
      }),
      requestFileAccess: async () => true,
      listDirectory: async () => [],
    };

    render(<App />);

    expect(await screen.findByRole('button', { name: '最小化窗口' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '最大化窗口' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '关闭窗口' })).toBeInTheDocument();
  });
});
