import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App';

/**
 * 验证应用壳层在默认状态下可正常渲染。
 */
describe('App', () => {
  /**
   * 在每个测试前重置浏览器持久化状态与网络模拟，避免测试互相污染。
   */
  beforeEach(() => {
    // 步骤：清理 localStorage，保证每个测试都从未登录状态开始。
    window.localStorage.clear();

    // 步骤：清理 fetch 模拟，避免上一条用例的响应残留影响当前断言。
    vi.restoreAllMocks();
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
    // 步骤：模拟登录接口返回成功响应。
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
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
      ),
    );

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
    // 步骤：模拟登录成功和退出成功两个接口响应。
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
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
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ success: true, code: 'OK', message: 'logged out', data: null }), {
          status: 200,
        }),
      );

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
});
