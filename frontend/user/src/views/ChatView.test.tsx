import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ChatView from './ChatView';
import { ChatWorkspaceController } from './chat/types';

/**
 * 验证聊天工作区会真实加载后端数据并消费 SSE 流。
 */
describe('ChatView', () => {
  beforeEach(() => {
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
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace()}
      />,
    );

    expect((await screen.findAllByText('请搜索 Spring Boot SSE 最佳实践')).length).toBeGreaterThan(0);
    expect(screen.getByText('搜索资料')).toBeInTheDocument();
    expect(screen.getByText('Spring Boot SSE 最佳实践')).toBeInTheDocument();
    expect(screen.getByText('search-report.docx')).toBeInTheDocument();
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
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace()}
      />,
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

    const inputWrapper = screen.getByPlaceholderText('输入指令以重构组件库或分析代码...').closest('form')?.parentElement?.parentElement;
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
   * 已选中历史会话但消息为空时，不应回退到新建页空态。
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
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace()}
      />,
    );

    expect(screen.queryByText('YOU')).not.toBeInTheDocument();
    expect(screen.queryByText('CODINGX')).not.toBeInTheDocument();

    const assistantMessage = screen.getByText('我来为您总结 Spring Boot SSE 最佳实践。').parentElement;
    expect(assistantMessage).not.toHaveClass('border');
  });

  /**
   * 用户消息气泡应保持更紧凑的尺寸与较小圆角，避免视觉上过高过椭圆。
   */
  it('应渲染紧凑的用户消息气泡样式', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace()}
      />,
    );

    const userMessageText = screen.getAllByText('请搜索 Spring Boot SSE 最佳实践').find((element) =>
      element.classList.contains('whitespace-pre-wrap'),
    );
    const userMessage = userMessageText?.closest('div.rounded-\\[20px\\]');
    expect(userMessage).toBeTruthy();
    expect(userMessage).toHaveClass('px-4');
    expect(userMessage).toHaveClass('py-2');
    expect(userMessageText).toHaveClass('leading-6');
  });

  /**
   * 消息滚动区需要固定底部安全留白，避免滚动条到底后仍可继续被压缩。
   */
  it('应为消息滚动区使用稳定的底部留白配置', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace()}
      />,
    );

    const scrollRegion = screen.getByTestId('chat-scroll-region');
    expect(scrollRegion).toHaveClass('pb-36');
    expect(scrollRegion).toHaveClass('md:pb-40');
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
              content: '## 可能性三：你有具体问题但打字不全\n\nsupercalifragilisticexpialidocioussupercalifragilisticexpialidocious',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const heading = screen.getByRole('heading', { name: '可能性三：你有具体问题但打字不全', level: 2 });
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
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace()}
      />,
    );

    await waitFor(() => {
      expect(scrollIntoView).toHaveBeenCalled();
    });
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
   * 真实会话页应支持通过内容区右上角按钮折叠与展开右侧工作区。
   */
  it('应支持折叠和展开右侧工作区面板', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace()}
      />,
    );

    const workspaceHeading = screen.getByText('执行回放');
    expect(workspaceHeading).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '折叠右侧工作区' }));
    expect(workspaceHeading).toBeInTheDocument();
    expect(workspaceHeading.closest('aside')).toHaveAttribute('aria-hidden', 'true');

    fireEvent.click(screen.getByRole('button', { name: '展开右侧工作区' }));
    expect(workspaceHeading.closest('aside')).toHaveAttribute('aria-hidden', 'false');
  });

  /**
   * 右侧回放区默认为折叠，仅当存在步骤、来源或产物时自动展开。
   */
  it('应在右侧无回放内容时默认折叠并在有内容时自动展开', async () => {
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

    expect(workspaceHeading.closest('aside')).toHaveAttribute('aria-hidden', 'false');
    expect(screen.getByRole('button', { name: '折叠右侧工作区' })).toBeInTheDocument();
  });

  /**
   * 助手消息底部应提供复制、复制更多、点赞和倒赞操作。
   */
  it('应渲染助手消息操作栏', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace()}
      />,
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
   * 点赞和倒赞应支持单选切换，保证反馈状态直观可见。
   */
  it('应支持点赞倒赞切换', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace()}
      />,
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
});

/**
 * 统一构造 ChatView 所需的工作台状态，避免在组件测试中依赖真实网络。
 */
function createWorkspace(overrides?: Partial<ChatWorkspaceController>): ChatWorkspaceController {
  return {
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
    isStreaming: false,
    isCancelling: false,
    deepThinkingEnabled: false,
    streamError: '',
    inputValue: '请搜索 Spring Boot SSE 最佳实践',
    isBootstrapping: false,
    setInputValue: vi.fn(),
    setDeepThinkingEnabled: vi.fn(),
    submitMessage: vi.fn().mockResolvedValue(undefined),
    cancelCurrentStream: vi.fn().mockResolvedValue(undefined),
    selectConversation: vi.fn().mockResolvedValue(undefined),
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
