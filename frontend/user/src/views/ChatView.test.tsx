import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ChatView from './ChatView';
import { ChatWorkspaceController } from './chat/types';

/**
 * 验证聊天工作区会真实加载后端数据并消费 SSE 流。
 */
describe('ChatView', () => {
  beforeEach(() => {
    window.URL.createObjectURL = vi.fn(() => 'blob:mock-preview');
    window.URL.revokeObjectURL = vi.fn();
    Object.defineProperty(window.HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: vi.fn(),
    });
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
    vi.restoreAllMocks();
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    });
  });

  /**
   * 未登录时发送消息仍应触发登录拦截。
   */
  it('应在未登录发送消息时调用登录回调', async () => {
    const onRequireLogin = vi.fn();
    const submitMessage = vi.fn();

    render(
      <ChatView
        isAuthenticated={false}
        onRequireLogin={onRequireLogin}
        workspace={createWorkspace({
          inputValue: '请帮我分析项目结构',
          submitMessage,
        })}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }));

    expect(onRequireLogin).toHaveBeenCalledTimes(1);
    expect(submitMessage).not.toHaveBeenCalled();
  });

  /**
   * 已登录时应展示主区消息与右栏回放数据。
   */
  it('应渲染消息与工作区回放', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    expect((await screen.findAllByText('请搜索 Spring Boot SSE 最佳实践')).length).toBeGreaterThan(
      0,
    );
    expect(screen.getByText('搜索资料')).toBeInTheDocument();
    expect(screen.getByText('Spring Boot SSE 最佳实践')).toBeInTheDocument();
    expect(screen.getByText('search-report.docx')).toBeInTheDocument();
    expect(screen.queryByText('当前技能')).not.toBeInTheDocument();
    expect(screen.queryByText('当前 MCP')).not.toBeInTheDocument();
  });

  /**
   * 已登录发送消息时应调用工作台提交动作。
   */
  it('应在已登录发送消息时调用提交动作', async () => {
    const submitMessage = vi.fn().mockResolvedValue(undefined);

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '请搜索新的会话问题',
          submitMessage,
          messages: [],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '发送消息' }));

    await waitFor(() => {
      expect(submitMessage).toHaveBeenCalledTimes(1);
    });
  });

  /**
   * 新建对话空态下若发送失败，仍应展示错误提示，避免用户误判为发送按钮失效。
   */
  it('应在新建对话空态展示发送错误提示', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          activeConversationId: null,
          messages: [],
          executionSteps: [],
          references: [],
          artifacts: [],
          streamError: '请选择本地工作空间后再发送消息',
        })}
      />,
    );

    expect(screen.getByText('请选择本地工作空间后再发送消息')).toBeInTheDocument();
  });

  /**
   * 输入框上方不应重复渲染流式错误提示，避免与消息区提示重复占位。
   */
  it('应只在消息区渲染一条流式错误提示', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          activeConversationId: null,
          messages: [],
          executionSteps: [],
          references: [],
          artifacts: [],
          streamError: '请选择本地工作空间后再发送消息',
        })}
      />,
    );

    expect(screen.getAllByText('请选择本地工作空间后再发送消息')).toHaveLength(1);
  });

  /**
   * 排队中应展示独立提示条，且不影响原错误提示区域语义。
   */
  it('应展示排队提示条', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          streamQueueState: {
            position: 3,
            message: '请求排队中，前方还有 3 个会话',
          },
          streamError: '',
        })}
      />,
    );

    expect(screen.getByText('请求排队中，前方还有 3 个会话')).toBeInTheDocument();
  });

  /**
   * 输入区应支持切换深度思考开关，避免功能只停留在后端参数。
   */
  it('应支持切换深度思考开关', async () => {
    const setDeepThinkingEnabled = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          deepThinkingEnabled: false,
          setDeepThinkingEnabled,
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '切换深度思考' }));
    expect(setDeepThinkingEnabled).toHaveBeenCalledWith(true);
  });

  /**
   * 聊天页改造后不应继续渲染内部 Conversations 侧栏。
   */
  it('不应继续渲染内部会话侧栏标题', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    expect(screen.queryByText('聊天工作台')).not.toBeInTheDocument();
    expect(screen.queryByText('Conversations')).not.toBeInTheDocument();
  });

  /**
   * 新建页输入区应去掉状态行、反馈按钮和顶部边线。
   */
  it('应在新建页移除输入区状态行与顶部分割线', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          activeConversationId: null,
          messages: [],
          executionSteps: [],
          references: [],
          artifacts: [],
          inputValue: '',
        })}
      />,
    );

    expect(screen.getByText('你好，我是 CodingX')).toBeInTheDocument();
    expect(screen.queryByText('主页面待命')).not.toBeInTheDocument();
    expect(screen.queryByText('反馈')).not.toBeInTheDocument();
    expect(screen.queryByText(/会话 #/)).not.toBeInTheDocument();
    expect(screen.queryByText('执行回放')).not.toBeInTheDocument();

    const inputWrapper = screen
      .getByPlaceholderText('输入问题，或先选择技能/MCP...')
      .closest('form')?.parentElement?.parentElement;
    expect(inputWrapper).not.toHaveClass('border-t');
  });

  /**
   * 首页欢迎卡片在后端返回示例问题时必须展示真实 API 数据，避免误回退到静态 fallback。
   */
  it('应在新建页展示真实示例问题卡片并标记数据源', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          activeConversationId: null,
          messages: [],
          executionSteps: [],
          references: [],
          artifacts: [],
          inputValue: '',
          availableExperts: [],
    selectedExpertCode: null,
    currentExperts: [],
    sampleQuestions: [
            {
              id: '9001',
              questionText: '真实示例：帮我分析本周销售数据',
              category: '经营分析',
            },
          ],
        })}
      />,
    );

    const realQuestion = screen.getByText('真实示例：帮我分析本周销售数据');
    expect(realQuestion.closest('button')).toHaveAttribute('data-source', 'api');
    expect(screen.getByText('经营分析')).toBeInTheDocument();
    expect(screen.queryByText('网页读取')).not.toBeInTheDocument();
    expect(screen.queryByText('解析并总结外部网页内容')).not.toBeInTheDocument();
  });

  /**
   * 已选中历史记录但消息为空时，不应回退到新建页空态。
   */
  it('应在选中空会话时展示会话空态而不是新建页', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          activeConversationId: '2002',
          messages: [],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.queryByText('你好，我是 CodingX')).not.toBeInTheDocument();
    expect(screen.getByText('当前会话暂无消息')).toBeInTheDocument();
    expect(screen.queryByText('反馈')).not.toBeInTheDocument();
    const workspaceHeading = screen.getByText('执行回放');
    expect(workspaceHeading.closest('aside')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByRole('button', { name: '展开右侧工作区' })).toBeInTheDocument();
  });

  /**
   * 会话消息区应去掉角色头与助手卡片边框，保持更干净的正文排版。
   */
  it('应在消息区隐藏角色头并移除助手卡片边框', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    expect(screen.queryByText('YOU')).not.toBeInTheDocument();
    expect(screen.queryByText('CODINGX')).not.toBeInTheDocument();

    const assistantMessage = screen.getByText(
      '我来为您总结 Spring Boot SSE 最佳实践。',
    ).parentElement;
    expect(assistantMessage).not.toHaveClass('border');
  });

  /**
   * 用户消息气泡应保持更紧凑的尺寸与较小圆角，避免视觉上过高过椭圆。
   */
  it('应渲染紧凑的用户消息气泡样式', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    const userMessageText = screen
      .getAllByText('请搜索 Spring Boot SSE 最佳实践')
      .find((element) => element.classList.contains('whitespace-pre-wrap'));
    const userMessage = userMessageText?.closest('div.rounded-\\[20px\\]');
    expect(userMessage).toBeTruthy();
    expect(userMessage).toHaveClass('px-4');
    expect(userMessage).toHaveClass('py-2');
    expect(userMessageText).toHaveClass('leading-6');
  });

  /**
   * 用户消息绑定技能时，应在消息气泡内保留技能气泡展示，避免流式结束后技能信息消失。
   */
  it('应在用户消息气泡内展示技能气泡', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          availableSkills: [
            {
              id: '9901',
              skillCode: 'codebase-migrate',
              displayName: '代码迁移',
              description: '批量迁移仓库代码',
              category: '工程',
            },
          ],
          messages: [
            {
              id: '311',
              conversationId: '2001',
              role: 'USER',
              content: '这是什么',
              status: 'COMPLETED',
              skillCodes: ['codebase-migrate'],
            } as any,
            {
              id: '312',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '这是技能说明。',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByTestId('message-skill-chip-311-codebase-migrate')).toBeInTheDocument();
  });

  /**
   * 消息滚动区需要固定底部安全留白，避免滚动条到底后仍可继续被压缩。
   */
  it('应按输入区高度动态设置消息滚动区底部留白', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    const scrollRegion = screen.getByTestId('chat-scroll-region');
    const inputDock = screen.getByTestId('chat-input-dock');
    expect(scrollRegion).toHaveStyle({
      paddingBottom: '160px',
      scrollPaddingBottom: '160px',
    });
    expect(inputDock).toBeInTheDocument();
  });

  /**
   * 助手消息正文应按 Markdown 渲染，避免把标题、加粗和列表原样当纯文本展示。
   */
  it('应将助手消息按 Markdown 渲染', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '201',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '### 总结\n\n- **要点一**\n- `ThreadLocal`',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByRole('heading', { name: '总结', level: 3 })).toBeInTheDocument();
    expect(screen.getByText('要点一')).toBeInTheDocument();
    expect(screen.getByText('ThreadLocal')).toContainHTML('code');
  });

  /**
   * 助手消息存在思考内容时，应展示可展开的思考区块。
   */
  it('应渲染助手思考内容区块', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '701',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '最终回答',
              thinkingContent: '先分析问题，再组织答案。',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByText('思考过程')).toBeInTheDocument();
    expect(screen.getByText('先分析问题，再组织答案。')).toBeInTheDocument();
    expect(screen.getByTestId('thinking-toggle-button-701')).toHaveAttribute(
      'aria-expanded',
      'true',
    );
  });

  /**
   * 思考区块标题右侧应提供显式箭头控件，避免折叠状态只能依赖浏览器默认标记。
   */
  it('应在思考区块标题右侧渲染箭头控件', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '702',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '最终回答',
              thinkingContent: '先分析问题，再组织答案。',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByTestId('thinking-summary-702')).toBeInTheDocument();
    expect(screen.getByTestId('thinking-toggle-icon-702')).toBeInTheDocument();
  });

  /**
   * 思考区块应支持展开与折叠动画状态切换，且默认保持展开。
   */
  it('应支持思考区块默认展开并在点击后切换动画状态', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '703',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '最终回答',
              thinkingContent: '这是思考内容。',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const toggleButton = screen.getByTestId('thinking-toggle-button-703');
    const content = screen.getByTestId('thinking-content-703');
    const panel = screen.getByTestId('thinking-panel-703');

    expect(toggleButton).toHaveAttribute('aria-expanded', 'true');
    expect(panel).toHaveClass('w-full');
    expect(panel).toHaveClass('px-4');
    expect(panel).toHaveClass('py-3');
    expect(content).toHaveClass('max-h-[640px]');
    expect(content).toHaveClass('opacity-100');

    fireEvent.click(toggleButton);
    expect(toggleButton).toHaveAttribute('aria-expanded', 'false');
    expect(panel).toHaveClass('w-fit');
    expect(panel).toHaveClass('px-4');
    expect(panel).toHaveClass('py-3');
    expect(content).toHaveClass('max-h-0');
    expect(content).toHaveClass('max-w-0');
    expect(content).toHaveClass('opacity-0');

    fireEvent.click(toggleButton);
    expect(toggleButton).toHaveAttribute('aria-expanded', 'true');
    expect(panel).toHaveClass('w-full');
    expect(content).toHaveClass('max-h-[640px]');
    expect(content).toHaveClass('opacity-100');
  });

  /**
   * 移动端长标题与长词应允许在容器内换行，避免横向撑出页面。
   */
  it('应为助手 Markdown 消息启用窄屏断行约束', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '202',
              conversationId: '2001',
              role: 'ASSISTANT',
              content:
                '## 可能性三：你有具体问题但打字不全\n\nsupercalifragilisticexpialidocioussupercalifragilisticexpialidocious',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const heading = screen.getByRole('heading', {
      name: '可能性三：你有具体问题但打字不全',
      level: 2,
    });
    const markdownContainer = heading.closest('.chat-markdown');
    expect(markdownContainer).toHaveClass('min-w-0');
    expect(markdownContainer).toHaveClass('[overflow-wrap:anywhere]');
  });

  /**
   * 进入有消息的会话页后应自动滚动到最后一条消息，避免用户手动拖到底部。
   */
  it('应在加载会话消息后自动滚动到最新消息', async () => {
    const scrollIntoView = vi.mocked(window.HTMLElement.prototype.scrollIntoView);

    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    await waitFor(() => {
      expect(scrollIntoView).toHaveBeenCalled();
    });
  });

  /**
   * 发送消息时应先贴底，再进入后续提交流程，避免回答首帧被底部输入区遮挡。
   */
  it('应在发送消息时立即滚动到底部并提交消息', async () => {
    const scrollIntoView = vi.mocked(window.HTMLElement.prototype.scrollIntoView);
    const submitMessage = vi.fn().mockResolvedValue(undefined);

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          activeConversationId: '2001',
          messages: [
            {
              id: '701',
              conversationId: '2001',
              role: 'USER',
              content: '先看这一段历史消息',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
          inputValue: '请继续生成下一段内容',
          submitMessage,
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '发送消息' }));

    await waitFor(() => {
      expect(submitMessage).toHaveBeenCalledTimes(1);
    });
    expect(scrollIntoView).toHaveBeenCalled();
  });

  /**
   * 流式生成时每次新内容到达都应继续跟随滚动到底部，保证页面随着消息增长而下移。
   */
  it('应在流式消息增长时持续滚动到最新内容', async () => {
    const scrollIntoView = vi.mocked(window.HTMLElement.prototype.scrollIntoView);
    const { rerender } = render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '301',
              conversationId: '2001',
              role: 'USER',
              content: '继续解释 ThreadLocal',
              status: 'COMPLETED',
            },
            {
              id: '302',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '第一段',
              status: 'streaming',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
          isStreaming: true,
        })}
      />,
    );

    const initialCalls = scrollIntoView.mock.calls.length;

    rerender(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '301',
              conversationId: '2001',
              role: 'USER',
              content: '继续解释 ThreadLocal',
              status: 'COMPLETED',
            },
            {
              id: '302',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '第一段\n第二段',
              status: 'streaming',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
          isStreaming: true,
        })}
      />,
    );

    await waitFor(() => {
      expect(scrollIntoView.mock.calls.length).toBeGreaterThan(initialCalls);
    });
  });

  /**
   * 用户手动上滑查看历史内容后，流式追加不应强制把滚动位置拉回到底部。
   */
  it('应在用户离开底部后暂停流式自动跟随滚动', async () => {
    const scrollIntoView = vi.mocked(window.HTMLElement.prototype.scrollIntoView);
    const { rerender } = render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '401',
              conversationId: '2001',
              role: 'USER',
              content: '请继续补充',
              status: 'COMPLETED',
            },
            {
              id: '402',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '第一段',
              status: 'streaming',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
          isStreaming: true,
        })}
      />,
    );

    const scrollRegion = screen.getByTestId('chat-scroll-region');
    Object.defineProperty(scrollRegion, 'scrollHeight', {
      configurable: true,
      value: 1000,
    });
    Object.defineProperty(scrollRegion, 'clientHeight', {
      configurable: true,
      value: 600,
    });
    Object.defineProperty(scrollRegion, 'scrollTop', {
      configurable: true,
      writable: true,
      value: 120,
    });
    // 业务意图：模拟用户主动上滑离开底部（距离底部约 280px），后续流式更新不应抢夺滚动焦点。
    fireEvent.scroll(scrollRegion);
    scrollIntoView.mockClear();

    rerender(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '401',
              conversationId: '2001',
              role: 'USER',
              content: '请继续补充',
              status: 'COMPLETED',
            },
            {
              id: '402',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '第一段\n第二段',
              status: 'streaming',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
          isStreaming: true,
        })}
      />,
    );

    await waitFor(() => {
      expect(scrollIntoView).not.toHaveBeenCalled();
    });
  });

  /**
   * 真实会话页应支持通过内容区右上角按钮折叠与展开右侧工作区。
   */
  it('应支持折叠和展开右侧工作区面板', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    const workspaceHeading = screen.getByText('执行回放');
    expect(workspaceHeading).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '展开右侧工作区' }));
    expect(workspaceHeading.closest('aside')).toHaveAttribute('aria-hidden', 'false');

    fireEvent.click(screen.getByRole('button', { name: '折叠右侧工作区' }));
    expect(workspaceHeading).toBeInTheDocument();
    expect(workspaceHeading.closest('aside')).toHaveAttribute('aria-hidden', 'true');
  });

  /**
   * 右侧回放区应保持默认折叠，存在回放内容时也需用户手动展开。
   */
  it('应在右侧有无回放内容时都默认折叠', async () => {
    const { rerender } = render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const workspaceHeading = screen.getByText('执行回放');
    expect(workspaceHeading.closest('aside')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByRole('button', { name: '展开右侧工作区' })).toBeInTheDocument();

    rerender(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          executionSteps: [
            {
              id: '9',
              runId: '5009',
              stepType: 'search',
              stepTitle: '生成新步骤',
              stepStatus: 'COMPLETED',
              sequenceNo: 1,
              content: '新的步骤内容',
            },
          ],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(workspaceHeading.closest('aside')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByRole('button', { name: '展开右侧工作区' })).toBeInTheDocument();
  });

  /**
   * 助手消息底部应提供复制、复制更多、点赞和倒赞操作。
   */
  it('应渲染助手消息操作栏', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    expect(screen.getByTestId('copy-message-102')).toBeInTheDocument();
    expect(screen.getByTestId('copy-menu-toggle-102')).toBeInTheDocument();
    expect(screen.getByTestId('thumbs-up-102')).toBeInTheDocument();
    expect(screen.getByTestId('thumbs-down-102')).toBeInTheDocument();
  });

  /**
   * 点击复制应写入剪贴板，复制更多中应支持复制 Markdown 原文。
   */
  it('应支持复制消息与复制 Markdown', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '501',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '### 标题\n\n- 列表项',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const writeText = vi.mocked(navigator.clipboard.writeText);
    fireEvent.click(screen.getByTestId('copy-message-501'));
    await waitFor(() => {
      expect(writeText).toHaveBeenCalledWith(expect.stringContaining('标题'));
    });

    fireEvent.click(screen.getByTestId('copy-menu-toggle-501'));
    fireEvent.click(screen.getByTestId('copy-markdown-501'));
    await waitFor(() => {
      expect(writeText).toHaveBeenCalledWith('### 标题\n\n- 列表项');
    });
  });

  /**
   * 输入区应通过 MCP 按钮打开滚动列表，而不是继续依赖斜杠命令面板。
   */
  it('应通过MCP按钮展开滚动列表并展示可选MCP', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    const mcpTriggerButton = screen.getByRole('button', { name: '打开MCP列表' });
    // 交互约束：MCP 按钮保留颜色/阴影反馈，但不应出现上浮抖动。
    expect(mcpTriggerButton.className).not.toContain('hover:-translate-y-0.5');
    expect(screen.getByTestId('mcp-trigger-chevron')).toBeInTheDocument();

    fireEvent.click(mcpTriggerButton);

    const selectorPanel = screen.getByTestId('mcp-selector-panel');
    expect(selectorPanel).toBeInTheDocument();
    expect(screen.getByTestId('mcp-trigger-icon')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '选择MCP 销售查询' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '选择MCP 工单查询' })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: '切换MCP 销售查询' })).toBeInTheDocument();
  });

  /**
   * MCP 列表项点击后应更新 selectedMcpCodes，确保不再依赖气泡删除入口。
   */
  it('应支持通过MCP列表切换选中状态', async () => {
    const setSelectedMcpCodes = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          setSelectedMcpCodes,
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开MCP列表' }));
    fireEvent.click(screen.getByRole('button', { name: '选择MCP 工单查询' }));

    expect(setSelectedMcpCodes).toHaveBeenCalled();
    const updater = setSelectedMcpCodes.mock.calls[0][0] as (codes: string[]) => string[];
    expect(updater(['sales_query'])).toEqual(['sales_query', 'ticket_query']);
  });

  /**
   * MCP 列表中不可用项必须禁用且不可触发选中，避免误把不可执行工具加入会话上下文。
   */
  it('应禁止通过MCP列表选择不可用MCP', async () => {
    const setSelectedMcpCodes = vi.fn();
    const setMcpConnected = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          setSelectedMcpCodes,
          setMcpConnected,
          availableMcps: [
            {
              id: '7001',
              mcpCode: 'sales_query',
              displayName: '销售查询',
              description: '联网检索信息并生成摘要',
              category: '检索',
            },
            {
              id: '7003',
              mcpCode: 'weather_query',
              displayName: '天气查询',
              description: '天气服务未接入',
              category: '检索',
              available: false,
            },
          ],
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开MCP列表' }));
    const unavailableOption = screen.getByRole('button', { name: '选择MCP 天气查询' });
    expect(unavailableOption).toBeDisabled();
    expect(unavailableOption).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByRole('switch', { name: '切换MCP 天气查询' })).toHaveAttribute(
      'aria-disabled',
      'true',
    );
    fireEvent.click(unavailableOption);
    expect(setSelectedMcpCodes).not.toHaveBeenCalled();
    expect(setMcpConnected).not.toHaveBeenCalled();
  });

  /**
   * 技能按钮应打开可滚动列表，并支持输入过滤。
   */
  it('应通过技能按钮展开滚动列表并展示可选技能', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开技能列表' }));

    const selectorPanel = screen.getByTestId('skill-selector-panel');
    expect(selectorPanel).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '选择技能 销售查询' })).toBeInTheDocument();
  });

  /**
   * 点击技能按钮时应自动补充斜杠，便于快速触发技能检索。
   */
  it('应在点击技能按钮时自动写入斜杠', async () => {
    const setInputValue = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '',
          setInputValue,
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开技能列表' }));
    expect(setInputValue).toHaveBeenCalledWith('/');
  });

  /**
   * 输入框以斜杠开头时应自动弹出技能列表，并把斜杠后的关键字作为过滤词。
   */
  it('应在输入斜杠时自动展开技能列表并同步过滤关键字', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '/sale',
        })}
      />,
    );

    expect(screen.getByTestId('skill-selector-panel')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('搜索技能')).toHaveValue('sale');
  });

  /**
   * 斜杠触发技能面板后切换到 MCP 时，应保持 MCP 面板可见，避免被自动逻辑抢回技能面板。
   */
  it('应在斜杠输入场景支持从技能面板切换到MCP面板', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '/sale',
        })}
      />,
    );

    expect(screen.getByTestId('skill-selector-panel')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '打开MCP列表' }));

    await waitFor(() => {
      expect(screen.getByTestId('mcp-selector-panel')).toBeInTheDocument();
      expect(screen.queryByTestId('skill-selector-panel')).not.toBeInTheDocument();
    });
  });

  /**
   * 斜杠触发技能面板后再次点击技能按钮，应允许正常关闭，避免面板锁死。
   */
  it('应在斜杠输入场景支持关闭技能面板', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '/sale',
        })}
      />,
    );

    expect(screen.getByTestId('skill-selector-panel')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '打开技能列表' }));

    await waitFor(() => {
      expect(screen.queryByTestId('skill-selector-panel')).not.toBeInTheDocument();
    });
  });

  /**
   * 选择技能后应把技能文本标记插入输入内容，支持在正文中自然混排。
   */
  it('应在选择技能后写入可编辑技能文本标记', async () => {
    const setInputValue = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '帮我分析本周销售',
          setInputValue,
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开技能列表' }));
    fireEvent.click(screen.getByRole('button', { name: '选择技能 销售查询' }));

    expect(setInputValue).toHaveBeenCalledWith(expect.stringContaining('@sales_query'));
  });

  /**
   * 斜杠触发选择技能后应替换为技能标记文本，而不是保留斜杠触发词。
   */
  it('应在斜杠触发技能选择后写入技能文本标记', async () => {
    const setInputValue = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '/sale',
          setInputValue,
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '选择技能 销售查询' }));

    expect(setInputValue).toHaveBeenCalledWith('@sales_query ');
  });

  /**
   * 技能列表应支持多选，连续选择时输入中应出现多个技能标记。
   */
  it('应支持多技能文本标记累积', async () => {
    const setInputValue = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '请先看一下',
          selectedSkillCodes: ['ticket_query'],
          setInputValue,
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开技能列表' }));
    fireEvent.click(screen.getByRole('button', { name: '选择技能 销售查询' }));

    expect(setInputValue).toHaveBeenCalledWith(expect.stringContaining('@sales_query'));
  });

  /**
   * 输入区仍应保持多行 textarea，确保技能文本和正文共享一个可编辑区域。
   */
  it('应使用多行输入框承载技能文本与正文', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '@sales_query 第一行\n第二行',
        })}
      />,
    );

    const inlineTokenContainer = screen.getByTestId('input-inline-skill-tokens');
    const inlineContentFlow = screen.getByTestId('input-inline-content-flow');
    const textarea = screen.getByPlaceholderText('输入问题，或先选择技能/MCP...');

    expect(textarea.tagName).toBe('TEXTAREA');
    expect(textarea).toHaveAttribute('rows', '1');
    expect(textarea).toHaveClass('w-full');
    expect(inlineTokenContainer).toHaveAttribute('data-max-lines', '9');
    expect(inlineTokenContainer).toContainElement(textarea);
    expect(inlineContentFlow).toContainElement(textarea);
  });

  /**
   * 用户删除技能文本标记后，应同步取消对应技能选中状态。
   */
  it('应在删除技能文本标记后同步取消技能选择', async () => {
    const setSelectedSkillCodes = vi.fn();
    const setInputValue = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '@sales_query 帮我分析一下',
          selectedSkillCodes: ['sales_query'],
          setSelectedSkillCodes,
          setInputValue,
        })}
      />,
    );

    const textarea = screen.getByPlaceholderText('输入问题，或先选择技能/MCP...');
    fireEvent.change(textarea, { target: { value: '帮我分析一下' } });

    expect(setInputValue).toHaveBeenCalledWith('帮我分析一下');
    expect(setSelectedSkillCodes).toHaveBeenCalledWith([]);
  });

  /**
   * 技能文本标记应在同位渲染层展示为气泡样式，保持原有视觉形态。
   */
  it('应将技能文本标记渲染为气泡样式预览', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '@sales_query 帮我分析一下',
          selectedSkillCodes: ['sales_query'],
        })}
      />,
    );

    expect(screen.getByTestId('input-rich-preview')).toBeInTheDocument();
    expect(screen.getByTestId('selected-skill-chip-sales_query')).toBeInTheDocument();
    expect(screen.getByTestId('input-rich-preview')).toContainElement(
      screen.getByText('@sales_query', { selector: 'span.invisible.whitespace-pre' }),
    );
  });

  /**
   * 技能气泡应提供显式删除按钮，点击后直接移除文本标记并同步取消技能选择。
   */
  it('应支持点击技能气泡删除按钮移除技能标记', async () => {
    const setInputValue = vi.fn();
    const setSelectedSkillCodes = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '@sales_query 帮我分析一下',
          selectedSkillCodes: ['sales_query'],
          setInputValue,
          setSelectedSkillCodes,
        })}
      />,
    );

    fireEvent.click(screen.getByTestId('remove-selected-skill-chip-sales_query'));

    expect(setInputValue).toHaveBeenCalledWith('帮我分析一下');
    expect(setSelectedSkillCodes).toHaveBeenCalledWith([]);
  });

  /**
   * 中断后继续输入普通文本时，不应把全部可用技能写回选中状态。
   */
  it('应在普通文本输入时保持技能选择为空', async () => {
    const setSelectedSkillCodes = vi.fn();
    const setInputValue = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          isStreaming: false,
          inputValue: '',
          selectedSkillCodes: [],
          availableSkills: [
            {
              id: '7101',
              skillCode: 'sales_query',
              displayName: '销售查询',
              description: '查询销售汇总、排名、趋势与明细',
              category: '销售',
            },
            {
              id: '7102',
              skillCode: 'ticket_query',
              displayName: '工单查询',
              description: '查询工单状态、列表、优先级与解决率',
              category: '工单',
            },
          ],
          setSelectedSkillCodes,
          setInputValue,
        })}
      />,
    );

    const textarea = screen.getByPlaceholderText('输入问题，或先选择技能/MCP...');
    fireEvent.change(textarea, { target: { value: '只问一个普通问题' } });

    expect(setInputValue).toHaveBeenCalledWith('只问一个普通问题');
    expect(setSelectedSkillCodes).not.toHaveBeenCalledWith(['sales_query', 'ticket_query']);
  });

  /**
   * 当光标位于技能标记后方时，按 Backspace 应一次删除整枚技能标记。
   */
  it('应在技能标记后按退格键时整枚删除技能标记', async () => {
    const setInputValue = vi.fn();
    const setSelectedSkillCodes = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '@sales_query',
          selectedSkillCodes: ['sales_query'],
          setInputValue,
          setSelectedSkillCodes,
        })}
      />,
    );

    const textarea = screen.getByPlaceholderText('输入问题，或先选择技能/MCP...') as HTMLTextAreaElement;
    textarea.setSelectionRange('@sales_query'.length, '@sales_query'.length);
    fireEvent.keyDown(textarea, { key: 'Backspace' });

    expect(setInputValue).toHaveBeenCalledWith('');
    expect(setSelectedSkillCodes).toHaveBeenCalledWith([]);
  });

  /**
   * 无技能标签时占位文案不应被挤压换行，输入框应占满整行。
   */
  it('应在无技能标签时让输入框占满整行', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          selectedSkillCodes: [],
          inputValue: '',
        })}
      />,
    );

    const textarea = screen.getByPlaceholderText('输入问题，或先选择技能/MCP...');
    expect(textarea).toHaveClass('w-full');
    expect(textarea).toHaveClass('py-1');
    expect(textarea).toHaveClass('overflow-x-hidden');
    expect(textarea).toHaveClass('placeholder:whitespace-nowrap');
  });

  /**
   * 输入区应拆分为“上内容区 + 下工具栏”，避免长文本时工具按钮被挤压到中间。
   */
  it('应将附件与操作按钮固定在输入区底部工具栏', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue:
            '这是一个很长的输入内容这是一个很长的输入内容这是一个很长的输入内容\n第二行内容继续拉长以触发更明显的布局占用',
        })}
      />,
    );

    const contentArea = screen.getByTestId('chat-input-content-area');
    const toolbar = screen.getByTestId('chat-input-toolbar');
    const toolbarLeft = screen.getByTestId('chat-input-toolbar-left');
    const toolbarRight = screen.getByTestId('chat-input-toolbar-right');
    const deepThinkingButton = screen.getByRole('button', { name: '切换深度思考' });
    const sendButton = screen.getByRole('button', { name: '发送消息' });
    const attachButton = toolbarLeft.querySelector('button');

    expect(contentArea).toBeInTheDocument();
    expect(toolbar).toBeInTheDocument();
    expect(toolbarLeft).toContainElement(attachButton);
    expect(toolbarRight).toContainElement(deepThinkingButton);
    expect(toolbarRight).toContainElement(sendButton);
  });

  /**
   * 环境与工作空间切换应独立于输入框容器，避免占用输入框内部高度。
   */
  it('应在输入框下方渲染环境与工作空间切换行并移除旧状态栏', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          activeConversationId: null,
          messages: [],
          executionSteps: [],
          references: [],
          artifacts: [],
          inputValue: '',
        })}
      />,
    );

    const switcher = screen.getByTestId('chat-runtime-workspace-switcher');
    const inputForm = screen.getByRole('button', { name: '发送消息' }).closest('form');
    expect(switcher).toBeInTheDocument();
    expect(inputForm).not.toContainElement(switcher);
    expect(screen.queryByTestId('chat-workspace-bar')).not.toBeInTheDocument();
    expect(screen.queryByText('当前环境')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '打开运行环境列表' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '打开工作空间列表' })).toBeInTheDocument();
    expect(screen.getByTestId('runtime-active-icon-local')).toBeInTheDocument();
  });

  /**
   * 运行环境切换器主按钮与下拉选项应渲染语义图标，便于快速区分云端与本地。
   */
  it('应为运行环境按钮及选项渲染云端和本地图标', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          activeConversationId: null,
          messages: [],
          executionSteps: [],
          references: [],
          artifacts: [],
          inputValue: '',
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开运行环境列表' }));

    expect(screen.getByTestId('runtime-option-icon-cloud')).toBeInTheDocument();
    expect(screen.getByTestId('runtime-option-icon-local')).toBeInTheDocument();
  });

  /**
   * 环境切换应通过下拉列表触发统一运行环境切换动作。
   */
  it('应支持通过下拉列表切换运行环境', async () => {
    const setActiveRuntimeTarget = vi.fn().mockResolvedValue(undefined);
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          activeRuntimeTarget: 'local',
          activeConversationId: null,
          messages: [],
          executionSteps: [],
          references: [],
          artifacts: [],
          inputValue: '',
          setActiveRuntimeTarget,
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开运行环境列表' }));
    fireEvent.click(screen.getByRole('button', { name: '切换运行环境 云端' }));

    expect(setActiveRuntimeTarget).toHaveBeenCalledWith('cloud');
  });

  /**
   * 工作空间切换应通过下拉列表选择目标目录，而不是仅保留按钮触发。
   */
  it('应支持通过下拉列表切换工作空间', async () => {
    const setActiveWorkspacePath = vi.fn().mockResolvedValue(undefined);
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          activeConversationId: null,
          messages: [],
          executionSteps: [],
          references: [],
          artifacts: [],
          inputValue: '',
          setActiveWorkspacePath,
          workspaceGroups: [
            {
              partitionKey: 'cloud::__no_workspace__',
              workspacePath: null,
              workspaceLabel: '历史记录',
              runtimeTarget: 'cloud',
              lastOpenedAt: 1716101111000,
              activeConversationId: null,
              conversations: [],
            },
            {
              partitionKey: 'local::d:/code/codingx',
              workspacePath: 'D:/code/CodingX',
              workspaceLabel: 'CodingX',
              runtimeTarget: 'local',
              lastOpenedAt: 1716102222000,
              activeConversationId: '2001',
              conversations: [],
            },
            {
              partitionKey: 'local::d:/code/designsystem',
              workspacePath: 'D:/code/DesignSystem',
              workspaceLabel: 'DesignSystem',
              runtimeTarget: 'local',
              lastOpenedAt: 1716103333000,
              activeConversationId: null,
              conversations: [],
            },
          ],
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开工作空间列表' }));
    fireEvent.click(screen.getByRole('button', { name: '切换工作空间 DesignSystem' }));

    expect(setActiveWorkspacePath).toHaveBeenCalledWith('D:/code/DesignSystem');
  });

  /**
   * 历史记录页不应展示底部环境/工作空间切换，避免与会话上下文重复。
   */
  it('应在历史记录页隐藏底部环境与工作空间切换', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    expect(screen.queryByTestId('chat-runtime-workspace-switcher')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '打开运行环境列表' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '打开工作空间列表' })).not.toBeInTheDocument();
  });

  /**
   * 新对话页切到云端运行环境时，不应再展示“历史记录”下拉入口。
   */
  it('应在云端环境隐藏工作空间下拉入口', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          activeRuntimeTarget: 'cloud',
          activeConversationId: null,
          messages: [],
          executionSteps: [],
          references: [],
          artifacts: [],
          inputValue: '',
          workspaceLabel: '历史记录',
          activeWorkspacePartitionKey: 'cloud::__no_workspace__',
          workspacePath: null,
        })}
      />,
    );

    expect(screen.getByTestId('chat-runtime-workspace-switcher')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '打开运行环境列表' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '打开工作空间列表' })).not.toBeInTheDocument();
  });

  /**
   * MCP 与技能应共用同一个浮层区域，并且浮层在输入区上方弹出，不应挤压输入区。
   */
  it('应在输入区上方复用同一个选择浮层', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开技能列表' }));
    expect(screen.getByTestId('skill-selector-panel')).toBeInTheDocument();
    expect(screen.queryByTestId('mcp-selector-panel')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '打开MCP列表' }));
    expect(screen.getByTestId('mcp-selector-panel')).toBeInTheDocument();
    expect(screen.queryByTestId('skill-selector-panel')).not.toBeInTheDocument();
  });

  /**
   * 输入区应展示待发送附件缩略图，并支持打开预览弹窗与移除附件。
   */
  it('应支持待发送图片预览与删除', async () => {
    const removePendingAttachment = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          pendingAttachments: [
            {
              clientId: 'pending-1',
              file: new File(['mock'], 'demo.png', { type: 'image/png' }),
              previewUrl: 'blob:demo-preview',
              uploadStatus: 'pending',
            },
          ],
          removePendingAttachment,
        })}
      />,
    );

    expect(screen.getByTestId('pending-attachment-list')).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText('预览图片 demo.png'));
    expect(screen.getByRole('dialog', { name: '图片预览弹窗' })).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('删除附件 demo.png'));
    expect(removePendingAttachment).toHaveBeenCalledWith('pending-1');
  });

  /**
   * 消息中图片附件应渲染为缩略图，点击后可打开预览弹窗。
   */
  it('应在消息内渲染附件并支持预览', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '301',
              conversationId: '2001',
              role: 'USER',
              content: '请看这张图',
              status: 'COMPLETED',
              attachments: [
                {
                  id: '9001',
                  conversationId: '2001',
                  messageId: '301',
                  attachmentType: 'image',
                  fileName: 'evidence.png',
                  fileSize: 2048,
                  previewUrl: '/api/chat/attachments/9001/content',
                  status: 'UPLOADED',
                },
              ],
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    fireEvent.click(screen.getByTestId('message-image-attachment-9001'));
    expect(screen.getByRole('dialog', { name: '图片预览弹窗' })).toBeInTheDocument();
  });

  /**
   * 输入区不再展示右侧 MCP 连接开关，避免与按钮选择逻辑重复。
   */
  it('不应再渲染右侧MCP连接开关', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    expect(screen.queryByRole('button', { name: '切换MCP连接' })).not.toBeInTheDocument();
  });

  /**
   * 输入以斜杠开头时不应再触发旧命令面板，避免与按钮式列表并存。
   */
  it('不应再渲染旧的斜杠命令面板', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          inputValue: '/ticket',
        })}
      />,
    );

    expect(screen.queryByTestId('mcp-command-panel')).not.toBeInTheDocument();
  });

  /**
   * 点赞和倒赞应支持单选切换，保证反馈状态直观可见。
   */
  it('应支持点赞倒赞切换', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    const upButton = screen.getByTestId('thumbs-up-102');
    const downButton = screen.getByTestId('thumbs-down-102');
    expect(upButton).toHaveAttribute('aria-pressed', 'false');
    expect(downButton).toHaveAttribute('aria-pressed', 'false');

    fireEvent.click(upButton);
    expect(upButton).toHaveAttribute('aria-pressed', 'true');
    expect(downButton).toHaveAttribute('aria-pressed', 'false');

    fireEvent.click(downButton);
    expect(upButton).toHaveAttribute('aria-pressed', 'false');
    expect(downButton).toHaveAttribute('aria-pressed', 'true');
  });

  /**
   * 助手 Markdown 图片应以消息内图片样式渲染，保证与文本消息视觉一致。
   */
  it('应在助手消息中渲染 Markdown 图片', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '601',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '![演示图](https://example.com/demo.png)',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const image = screen.getByAltText('演示图');
    expect(image).toHaveClass('chat-message-image');
  });

  /**
   * 点赞时应调用真实反馈接口，确保前端状态与后端反馈记录一致。
   */
  it('点赞应调用反馈接口并更新选中态', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          code: 'OK',
          message: 'feedback submitted',
          data: null,
        }),
        {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
          },
        },
      ),
    );

    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    const upButton = screen.getByTestId('thumbs-up-102');
    fireEvent.click(upButton);

    await waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/chat/messages/102/feedback',
        expect.objectContaining({
          method: 'POST',
        }),
      );
    });
    expect(upButton).toHaveAttribute('aria-pressed', 'true');
  });

  /**
   * 复制按钮与箭头应属于同一组合，避免视觉断层。
   */
  it('复制按钮和下拉按钮应位于同一复制操作组', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    const copyButton = screen.getByTestId('copy-message-102');
    const toggleButton = screen.getByTestId('copy-menu-toggle-102');
    const copyGroup = screen.getByTestId('copy-action-group-102');

    expect(copyGroup).toContainElement(copyButton);
    expect(copyGroup).toContainElement(toggleButton);
  });

  /**
   * 助手消息存在 MCP 调用时，应展示可折叠的 MCP 调用信息面板。
   */
  it('应渲染并支持折叠MCP调用面板', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '801',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '已完成天气查询',
              mcpCalls: [
                {
                  toolId: 'weather_query',
                  displayName: '天气查询',
                  input: '北京今天天气怎么样',
                  content: '北京今日晴，最高 28°C',
                  metadata: {
                    source: 'open-meteo',
                  },
                },
              ],
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const panel = screen.getByTestId('mcp-call-panel-801');
    const panelTitle = screen.getByText('MCP 调用');
    expect(panel).toBeInTheDocument();
    expect(panel).toHaveClass('px-4');
    expect(panel).toHaveClass('py-3');
    expect(panelTitle).toBeInTheDocument();
    expect(panelTitle).toHaveClass('shrink-0');
    expect(screen.getByText('天气查询')).toBeInTheDocument();
    expect(screen.getByText('北京今天天气怎么样')).toBeInTheDocument();
    expect(screen.getByTestId('mcp-call-status-801')).toHaveTextContent('调用完成');

    const toggleButton = screen.getByTestId('mcp-call-toggle-button-801');
    expect(toggleButton).toHaveAttribute('aria-expanded', 'true');
    fireEvent.click(toggleButton);
    expect(toggleButton).toHaveAttribute('aria-expanded', 'false');
    expect(panel).toHaveClass('px-4');
    expect(panel).toHaveClass('py-3');
  });

  /**
   * MCP 调用项应展示参数、原始结果与元数据，避免仅复述对话输入输出。
   */
  it('应在MCP调用面板展示参数与原始结果字段', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '861',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '已完成天气查询',
              mcpCalls: [
                {
                  toolId: 'weather_query',
                  displayName: '天气查询',
                  input: '北京天气',
                  content: '北京今日晴',
                  params: { city: '北京', date: '2026-05-21' },
                  rawResult: { text: '北京今日晴', temp: 28.6 },
                  resultMetadata: { source: 'open-meteo' },
                } as any,
              ],
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByTestId('mcp-call-panel-861')).toBeInTheDocument();
    expect(screen.getByText('参数')).toBeInTheDocument();
    expect(screen.getByText('原始结果')).toBeInTheDocument();
    expect(screen.getByText('元数据')).toBeInTheDocument();
    expect(screen.getByText((text) => text.includes('"city"') && text.includes('北京'))).toBeInTheDocument();
    expect(
      screen.getByText((text) => text.includes('"source"') && text.includes('open-meteo')),
    ).toBeInTheDocument();
  });

  /**
   * MCP 调用状态在流结束后应至少短暂停留“调用中”，避免状态闪现导致用户难以感知。
   */
  it('应在流结束后维持MCP调用中状态最短可见时长', async () => {
    vi.useFakeTimers();
    try {
      const streamingWorkspace = createWorkspace({
        messages: [
          {
            id: '901',
            conversationId: '2001',
            role: 'ASSISTANT',
            content: '处理中',
            mcpCalls: [
              {
                toolId: 'weather_query',
                displayName: '天气查询',
                input: '美国天气',
                content: '处理中',
              },
            ],
            status: 'streaming',
          },
        ],
        executionSteps: [],
        references: [],
        artifacts: [],
      });
      const { rerender } = render(
        <ChatView
          isAuthenticated={true}
          onRequireLogin={vi.fn()}
          workspace={streamingWorkspace}
        />,
      );

      expect(screen.getByTestId('mcp-call-status-901')).toHaveTextContent('调用中');

      const completedWorkspace = {
        ...streamingWorkspace,
        messages: [
          {
            ...streamingWorkspace.messages[0],
            content: '已完成',
            status: 'COMPLETED',
          },
        ],
      };
      rerender(
        <ChatView
          isAuthenticated={true}
          onRequireLogin={vi.fn()}
          workspace={completedWorkspace}
        />,
      );

      expect(screen.getByTestId('mcp-call-status-901')).toHaveTextContent('调用中');
      act(() => {
        vi.advanceTimersByTime(1000);
      });
      expect(screen.getByTestId('mcp-call-status-901')).toHaveTextContent('调用完成');
    } finally {
      vi.useRealTimers();
    }
  });

  /**
   * 助手消息在联网搜索期间应展示内嵌进度面板，并实时回显已检索站点。
   */
  it('应在助手消息中渲染联网搜索进度面板', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '951',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '正在汇总检索结果',
              status: 'streaming',
              searchProgress: {
                status: 'running',
                items: [
                  {
                    id: 'ref-1',
                    title: 'OpenAI API 最新变更',
                    siteName: 'OpenAI',
                    url: 'https://platform.openai.com',
                  },
                  {
                    id: 'ref-2',
                    title: 'Bing Search API 文档',
                    siteName: 'Microsoft Learn',
                    url: 'https://learn.microsoft.com',
                  },
                ],
              },
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByTestId('search-progress-panel-951')).toBeInTheDocument();
    expect(screen.getByTestId('search-progress-status-951')).toHaveTextContent('搜索中');
    expect(screen.getByTestId('search-progress-count-951')).toHaveTextContent('2');
    expect(screen.getByText('OpenAI API 最新变更')).toBeInTheDocument();
    expect(screen.getByText('Bing Search API 文档')).toBeInTheDocument();

    const toggleButton = screen.getByTestId('search-progress-toggle-button-951');
    expect(toggleButton).toHaveAttribute('aria-expanded', 'true');
    fireEvent.click(toggleButton);
    expect(toggleButton).toHaveAttribute('aria-expanded', 'false');
  });

  /**
   * 重命名弹窗应使用固定定位和高层级，避免被侧栏或主区遮挡。
   */
  it('重命名弹窗应使用fixed高层级容器', () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          renameDialog: {
            conversationId: '2001',
            initialTitle: '默认标题',
            isOpen: true,
            open: vi.fn(),
            close: vi.fn(),
          },
        })}
      />,
    );

    const panel = screen.getByText('重命名对话').closest('div');
    const overlay = panel?.parentElement;
    expect(overlay).toHaveClass('fixed');
    expect(overlay).toHaveClass('z-[120]');
  });
});

/**
 * 统一构造 ChatView 所需的工作台状态，避免在组件测试中依赖真实网络。
 */
function createWorkspace(overrides?: Partial<ChatWorkspaceController>): ChatWorkspaceController {
  return {
    runtimeTargets: ['cloud', 'local'],
    activeRuntimeTarget: 'local',
    workspaceGroups: [
      {
        partitionKey: 'local::d:/code/codingx',
        workspacePath: 'D:/code/CodingX',
        workspaceLabel: 'CodingX',
        runtimeTarget: 'local',
        lastOpenedAt: 1716102222000,
        activeConversationId: '2001',
        conversations: [],
      },
      {
        partitionKey: 'cloud::__no_workspace__',
        workspacePath: null,
        workspaceLabel: '历史记录',
        runtimeTarget: 'cloud',
        lastOpenedAt: 1716101111000,
        activeConversationId: null,
        conversations: [],
      },
    ],
    activeWorkspacePartitionKey: 'local::d:/code/codingx',
    workspacePath: 'D:/code/CodingX',
    workspaceLabel: 'CodingX',
    workspaceRuntimeTarget: 'local',
    conversations: [
      {
        id: '2001',
        title: 'Default Demo Conversation',
        status: 'ACTIVE',
        lastMessageAt: '2026-05-15 00:36:58',
        lastRunId: '5002',
      },
    ],
    activeConversationId: '2001',
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
        content: '我来为您总结 Spring Boot SSE 最佳实践。',
        status: 'COMPLETED',
        createdAt: '2026-05-15 00:37:11',
      },
    ],
    executionSteps: [
      {
        id: '1',
        runId: '5002',
        stepType: 'search',
        stepTitle: '搜索资料',
        stepStatus: 'COMPLETED',
        sequenceNo: 1,
        content: '请搜索 Spring Boot SSE 最佳实践',
      },
    ],
    references: [
      {
        id: '11',
        runId: '5002',
        messageId: '101',
        conversationId: '2001',
        title: 'Spring Boot SSE 最佳实践',
        url: 'https://docs.spring.io',
        siteName: 'Spring',
        snippet: 'SSE 最佳实践摘要',
      },
    ],
    artifacts: [
      {
        id: '21',
        runId: '5002',
        messageId: '101',
        conversationId: '2001',
        artifactType: 'docx',
        name: 'search-report.docx',
        storagePath: 'artifacts/2001/search-report.docx',
        contentPreview: '搜索结果整理中',
      },
    ],
    availableSkills: [
      {
        id: '7101',
        skillCode: 'sales_query',
        displayName: '销售查询',
        description: '查询销售汇总、排名、趋势与明细',
        category: '销售',
      },
      {
        id: '7102',
        skillCode: 'ticket_query',
        displayName: '工单查询',
        description: '查询工单状态、列表、优先级与解决率',
        category: '工单',
      },
    ],
    currentSkills: [
      {
        id: '7101',
        skillCode: 'sales_query',
        displayName: '销售查询',
        description: '查询销售汇总、排名、趋势与明细',
        category: '销售',
      },
    ],
    selectedSkillCodes: [],
    sampleQuestions: [
      {
        id: '6001',
        questionText: '请介绍一下 OA 系统的主要功能',
        category: '业务系统',
      },
      {
        id: '6002',
        questionText: '公司 VPN 连不上怎么办？',
        category: 'IT支持',
      },
    ],
    availableMcps: [
      {
        id: '7001',
        mcpCode: 'sales_query',
        displayName: '销售查询',
        description: '联网检索信息并生成摘要',
        category: '检索',
      },
      {
        id: '7002',
        mcpCode: 'ticket_query',
        displayName: '工单查询',
        description: '分析代码结构与缺陷',
        category: '工程',
      },
    ],
    currentMcps: [
      {
        id: '7001',
        mcpCode: 'sales_query',
        displayName: '销售查询',
        description: '联网检索信息并生成摘要',
        category: '检索',
      },
    ],
    selectedMcpCodes: [],
    mcpConnected: true,
    isStreaming: false,
    isCancelling: false,
    deepThinkingEnabled: false,
    streamQueueState: null,
    streamError: '',
    inputValue: '请搜索 Spring Boot SSE 最佳实践',
    pendingAttachments: [],
    isBootstrapping: false,
    setInputValue: vi.fn(),
    setSelectedExpertCode: vi.fn(),
    addPendingAttachments: vi.fn().mockResolvedValue(undefined),
    removePendingAttachment: vi.fn(),
    clearPendingAttachments: vi.fn(),
    setDeepThinkingEnabled: vi.fn(),
    setSelectedSkillCodes: vi.fn(),
    setSelectedMcpCodes: vi.fn(),
    setMcpConnected: vi.fn(),
    setActiveRuntimeTarget: vi.fn().mockResolvedValue(undefined),
    pickRepositoryDirectory: vi.fn().mockResolvedValue(undefined),
    setActiveWorkspacePath: vi.fn().mockResolvedValue(undefined),
    submitMessage: vi.fn().mockResolvedValue(undefined),
    cancelCurrentStream: vi.fn().mockResolvedValue(undefined),
    selectConversation: vi.fn().mockResolvedValue(undefined),
    selectConversationInWorkspace: vi.fn().mockResolvedValue(undefined),
    startNewConversation: vi.fn().mockResolvedValue(undefined),
    renameConversation: vi.fn().mockResolvedValue(undefined),
    deleteConversation: vi.fn().mockResolvedValue(undefined),
    renameDialog: {
      conversationId: null,
      initialTitle: '',
      isOpen: false,
      open: vi.fn(),
      close: vi.fn(),
    },
    deleteDialog: {
      conversationId: null,
      title: '',
      isOpen: false,
      open: vi.fn(),
      close: vi.fn(),
    },
    ...overrides,
  };
}


