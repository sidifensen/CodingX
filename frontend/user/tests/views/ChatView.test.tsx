import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ChatView from '@/views/ChatView';
import { within } from '@testing-library/react';
import { ChatWorkspaceController } from '@/views/chat/types';

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
   * 已登录时应展示主区消息与消息内过程链路，且不再展示右栏执行回放。
   */
  it('应渲染消息内过程链路且不再展示右栏执行回放', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    expect((await screen.findAllByText('请搜索 Spring Boot SSE 最佳实践')).length).toBeGreaterThan(
      0,
    );
    const tracePanel = screen.getByTestId('process-trace-panel-102');
    expect(tracePanel).toBeInTheDocument();
    expect(screen.queryByText('过程时间线')).not.toBeInTheDocument();
    expect(tracePanel).toHaveTextContent('深度思考');
    expect(screen.queryByTestId('process-tool-group-toggle-102')).not.toBeInTheDocument();
    expect(screen.getByTestId('process-tool-row-102-tool-call-default')).toBeInTheDocument();
    expect(screen.getByTestId('process-tool-row-102-tool-result-default')).toBeInTheDocument();
    expect(screen.getByText('调用网页搜索')).toBeInTheDocument();
    expect(screen.getByText('已获取结果')).toBeInTheDocument();
    const resultRow = screen.getByTestId('process-tool-row-102-tool-result-default');
    expect(within(resultRow).queryByText('Spring Boot SSE 最佳实践')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('process-tool-detail-toggle-102-tool-result-default'));
    expect(within(resultRow).getByText('Spring Boot SSE 最佳实践')).toBeInTheDocument();
    expect(screen.queryByText('执行回放')).not.toBeInTheDocument();
  });

  /**
   * 搜索引用编号应连接到真实来源链接，避免展示成不能跳转的纯文本。
   */
  it('应将引用编号渲染为真实来源链接', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '101',
              conversationId: '2001',
              role: 'USER',
              content: '请对比三份资料',
              status: 'COMPLETED',
            },
            {
              id: '102',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '根据资料，结论分别为 [R1]、[R2] 和 [R3]。',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [
            {
              id: '11',
              runId: '5002',
              messageId: '101',
              conversationId: '2001',
              title: '资料一',
              url: 'https://example.com/a',
              siteName: 'Example',
            },
            {
              id: '12',
              runId: '5002',
              messageId: '101',
              conversationId: '2001',
              title: '资料二',
              url: 'https://example.com/b',
              siteName: 'Example',
              rankNo: 2,
            },
            {
              id: '13',
              runId: '5002',
              messageId: '101',
              conversationId: '2001',
              title: '资料三',
              url: 'https://example.com/c',
              siteName: 'Example',
              rankNo: 3,
            },
          ],
          artifacts: [],
        })}
      />,
    );

    const links = await screen.findAllByRole('link');
    const citationLinks = links.filter((link) => link.textContent?.includes('[R'));
    expect(citationLinks).toHaveLength(3);
    expect(citationLinks[0]).toHaveAttribute('href', 'https://example.com/a');
    expect(citationLinks[1]).toHaveAttribute('href', 'https://example.com/b');
    expect(citationLinks[2]).toHaveAttribute('href', 'https://example.com/c');
  });

  /**
   * 流式阶段用户消息仍是乐观 ID，后端引用已携带真实消息 ID 时，也应立即把引用编号转成链接。
   */
  it('应在乐观用户消息阶段将引用编号渲染为真实来源链接', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: 'optimistic-user-1779773736005',
              conversationId: '2001',
              role: 'USER',
              content: '请搜索实时资料',
              status: 'COMPLETED',
            },
            {
              id: 'optimistic-assistant-1779773736005',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '我查到的结果见 [R1]。',
              status: 'streaming',
            },
          ],
          executionSteps: [],
          references: [
            {
              id: '11',
              runId: '5002',
              messageId: '101',
              conversationId: '2001',
              title: '实时资料',
              url: 'https://example.com/live',
              siteName: 'Example',
              rankNo: 1,
            },
          ],
          artifacts: [],
        })}
      />,
    );

    const citationLink = await screen.findByRole('link', { name: '[R1]' });
    expect(citationLink).toHaveAttribute('href', 'https://example.com/live');
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
   * 并发拒绝会同时写入助手消息错误和流式错误，页面只保留消息内提示，避免重复打断阅读。
   */
  it('应在并发拒绝错误已进入消息时隐藏重复的流式错误提示', async () => {
    const queueBusyMessage = '当前会话并发已满，请稍后重试';

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          streamError: queueBusyMessage,
          messages: [
            {
              id: '401',
              conversationId: '2001',
              role: 'USER',
              content: '用 html 帮我写一个贪吃蛇游戏，并且运行起来',
              status: 'COMPLETED',
            },
            {
              id: '402',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '',
              status: 'error',
              errorMessage: queueBusyMessage,
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getAllByText(queueBusyMessage)).toHaveLength(1);
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
   * 已选中历史记录但消息为空时，不应回退到新建页空态，也不应保留右侧工作区切换按钮。
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
    expect(screen.queryByText('执行回放')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '展开右侧工作区' })).not.toBeInTheDocument();
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
   * 输入区应参与主列布局，消息滚动区只保留短间距，避免绝对覆盖导致底部内容和滚动条不可达。
   */
  it('应让输入区占据底部布局而不是覆盖消息滚动区', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    const scrollRegion = screen.getByTestId('chat-scroll-region');
    const inputDock = screen.getByTestId('chat-input-dock');
    expect(scrollRegion).toHaveStyle({
      paddingBottom: '24px',
      scrollPaddingBottom: '24px',
    });
    expect(scrollRegion).toHaveClass('min-h-0');
    expect(inputDock).toBeInTheDocument();
    expect(inputDock).toHaveClass('shrink-0');
    expect(inputDock).toHaveClass('z-30');
    expect(inputDock).toHaveClass('bg-background');
    expect(inputDock).not.toHaveClass('absolute');
    expect(inputDock).not.toHaveClass('bg-background/88');
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
   * PowerShell 目录清单应被识别成结构化结果面板，而不是原始对齐文本块。
   */
  it('应将 PowerShell 目录输出渲染为结果面板', async () => {
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
              content: [
                'Mode                LastWriteTime         Length Name',
                '----                -------------         ------ ----',
                'd-----         2026/5/11  17:57                trae solo',
                '-a----         2026/5/9   12:49             197 run-campus-login-hidden.vbs',
              ].join('\n\n'),
              status: 'COMPLETED',
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByTestId('directory-listing-panel-801')).toBeInTheDocument();
    expect(screen.getByText('目录清单')).toBeInTheDocument();
    expect(screen.getByText('PowerShell 输出')).toBeInTheDocument();
    expect(screen.getByText((_, element) => element?.textContent === '共 2 项')).toBeInTheDocument();
    expect(screen.getByText((_, element) => element?.textContent === '1 文件夹')).toBeInTheDocument();
    expect(screen.getByText((_, element) => element?.textContent === '1 文件')).toBeInTheDocument();
    expect(screen.getByText('trae solo')).toBeInTheDocument();
    expect(screen.getByText('197 B')).toBeInTheDocument();
  });

  /**
   * 助手消息存在过程链路时，应用 WorkBuddy 式内联段落展示，而不是卡片墙。
   */
  it('应在助手消息中渲染内联过程链路', async () => {
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
              processCards: [
                {
                  id: 'analysis-1',
                  type: 'analysis',
                  title: '分析问题',
                  summary: '先判断这个问题是否需要实时信息。',
                  status: 'completed',
                },
                {
                  id: 'tool-call-1',
                  type: 'tool_call',
                  title: '调用网页搜索',
                  summary: '正在检索 Google 官方发布页。',
                  status: 'running',
                  details: [
                    {
                      label: '参数',
                      content: '{\"q\":\"Gemini latest model\"}',
                    },
                  ],
                },
                {
                  id: 'tool-result-1',
                  type: 'tool_result',
                  title: '已获取结果',
                  summary: '找到官方来源，正在比对正式发布型号。',
                  status: 'completed',
                  details: [
                    {
                      label: '结果',
                      content: '命中 Google I/O keynote 和 Gemini models overview',
                    },
                  ],
                },
                {
                  id: 'synthesis-1',
                  type: 'synthesis',
                  title: '整理结论',
                  summary: '正在根据检索结果整理最终回答。',
                  status: 'running',
                },
              ],
              status: 'COMPLETED',
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const tracePanel = screen.getByTestId('process-trace-panel-701');
    expect(tracePanel).toBeInTheDocument();
    expect(tracePanel).not.toHaveTextContent('过程时间线');
    expect(tracePanel).toHaveTextContent('深度思考');
    const analysisToggle = screen.getByTestId('process-analysis-toggle-701-analysis-1');
    expect(analysisToggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('先判断这个问题是否需要实时信息。')).not.toBeInTheDocument();
    fireEvent.click(analysisToggle);
    expect(analysisToggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('先判断这个问题是否需要实时信息。')).toBeInTheDocument();
    expect(screen.queryByTestId('process-tool-group-toggle-701')).not.toBeInTheDocument();
    expect(screen.getByTestId('process-tool-row-701-tool-call-1')).toBeInTheDocument();
    expect(screen.getByTestId('process-tool-row-701-tool-result-1')).toBeInTheDocument();
    expect(screen.getByText('调用网页搜索')).toBeInTheDocument();
    expect(screen.getByText('已获取结果')).toBeInTheDocument();
    expect(tracePanel).not.toHaveTextContent('正在根据检索结果整理最终回答。');
    expect(tracePanel).not.toHaveTextContent('已完成');
    expect(screen.queryByText('思考过程')).not.toBeInTheDocument();
  });

  /**
   * 过程明细存在时应提供轻量展开按钮。
   */
  it('应在过程工具行中渲染展开控件', async () => {
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
              processCards: [
                {
                  id: 'tool-call-702',
                  type: 'tool_call',
                  title: '调用天气查询',
                  summary: '正在请求天气工具。',
                  status: 'running',
                  details: [
                    {
                      label: '参数',
                      content: '{\"city\":\"北京\"}',
                    },
                  ],
                },
              ],
              status: 'COMPLETED',
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByTestId('process-tool-row-702-tool-call-702')).toBeInTheDocument();
    expect(screen.getByTestId('process-tool-detail-toggle-702-tool-call-702')).toBeInTheDocument();
  });

  /**
   * 过程细节默认折叠，点击后应展开，再次点击后应收起。
   */
  it('应支持过程细节默认折叠并在点击后切换展开状态', async () => {
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
              processCards: [
                {
                  id: 'tool-result-703',
                  type: 'tool_result',
                  title: '已获取结果',
                  summary: '已获得搜索结果。',
                  status: 'completed',
                  details: [
                    {
                      label: '结果',
                      content: '这是过程卡片结果。',
                    },
                  ],
                },
              ],
              status: 'COMPLETED',
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const toggleButton = screen.getByTestId('process-tool-detail-toggle-703-tool-result-703');

    expect(screen.getByText('已获取结果')).toBeInTheDocument();
    expect(toggleButton).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('这是过程卡片结果。')).not.toBeInTheDocument();

    fireEvent.click(toggleButton);
    expect(toggleButton).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('这是过程卡片结果。')).toBeInTheDocument();

    fireEvent.click(toggleButton);
    expect(toggleButton).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('这是过程卡片结果。')).not.toBeInTheDocument();
  });

  /**
   * 深度思考在流式阶段默认展开，便于用户直接看到完整推理过程；用户仍可手动折叠。
   */
  it('应支持深度思考流式期间默认展开并可手动折叠', async () => {
    const longSummary =
      '用户要求对 Qwen 和 GLM 最新模型做实时对比，需要先确认发布时间、模型定位、上下文长度、工具调用能力和适用场景，再决定是否继续检索官方来源。';

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '704',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '最终回答',
              processCards: [
                {
                  id: 'analysis-704',
                  type: 'analysis',
                  title: '分析问题',
                  summary: longSummary,
                  status: 'running',
                },
              ],
              status: 'streaming',
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const toggleButton = screen.getByTestId('process-analysis-toggle-704-analysis-704');
    expect(toggleButton).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByTestId('process-analysis-text-704')).toHaveTextContent(longSummary);

    fireEvent.click(toggleButton);
    expect(toggleButton).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByTestId('process-analysis-text-704')).not.toBeInTheDocument();

    fireEvent.click(toggleButton);
    expect(toggleButton).toHaveAttribute('aria-expanded', 'true');
    const analysisCard = screen.getByTestId('process-analysis-card-704');
    const analysisText = screen.getByTestId('process-analysis-text-704');
    expect(analysisCard).toHaveClass('rounded-xl');
    expect(analysisCard).toHaveClass('border');
    expect(analysisCard).toHaveClass('bg-surface-container');
    expect(analysisCard).toHaveClass('px-4');
    expect(analysisCard).not.toHaveClass('before:bg-border');
    expect(analysisText).toHaveTextContent(longSummary);
    expect(analysisText).toHaveClass('whitespace-pre-wrap');
    expect(analysisText).toHaveClass('[overflow-wrap:anywhere]');
    expect(analysisText).toHaveClass('text-muted');
    expect(analysisText).not.toHaveClass('text-foreground');
  });

  /**
   * 深度思考在流式完成后应默认折叠，避免长过程持续占据主阅读区；用户仍可手动展开复核。
   */
  it('应在深度思考流式完成后默认折叠', async () => {
    const initialSummary = '正在判断是否需要检索最新资料。';

    const { rerender } = render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '704b',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '最终回答',
              processCards: [
                {
                  id: 'analysis-704b',
                  type: 'analysis',
                  title: '分析问题',
                  summary: initialSummary,
                  status: 'running',
                },
              ],
              status: 'streaming',
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const toggleButton = screen.getByTestId('process-analysis-toggle-704b-analysis-704b');
    expect(toggleButton).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByTestId('process-analysis-text-704b')).toHaveTextContent(initialSummary);

    rerender(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '704b',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '最终回答',
              processCards: [
                {
                  id: 'analysis-704b',
                  type: 'analysis',
                  title: '分析问题',
                  summary: initialSummary,
                  status: 'completed',
                },
              ],
              status: 'COMPLETED',
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const completedToggleButton = screen.getByTestId('process-analysis-toggle-704b-analysis-704b');
    expect(completedToggleButton).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByTestId('process-analysis-text-704b')).not.toBeInTheDocument();

    fireEvent.click(completedToggleButton);
    expect(completedToggleButton).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByTestId('process-analysis-text-704b')).toHaveTextContent(initialSummary);
  });

  /**
   * 工具结果后的分析应按真实过程顺序显示在工具结果之后，避免把“基于结果的思考”提前到工具上方。
   */
  it('应按过程卡片顺序渲染工具后的分析文本', async () => {
    const postToolThinking =
      '我需要根据检索到的证据来回答关于Qwen和GLM最新模型的问题，并进行对比。这个分析必须完整展示，不能被中间省略号截断。';

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '705',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '最终回答',
              processCards: [
                {
                  id: 'analysis-before-tools',
                  type: 'analysis',
                  title: '分析问题',
                  summary: '我先判断需要调用搜索工具。',
                  status: 'completed',
                },
                {
                  id: 'tool-call-705',
                  type: 'tool_call',
                  title: '搜索子问题 1',
                  summary: 'Qwen和GLM最新模型是什么',
                  status: 'completed',
                  toolId: 'search',
                },
                {
                  id: 'tool-result-705',
                  type: 'tool_result',
                  title: '已获取结果',
                  summary: '找到来源：Qwen 与 GLM 最新模型信息',
                  status: 'completed',
                  toolId: 'search',
                },
                {
                  id: 'analysis-after-tools',
                  type: 'analysis',
                  title: '分析检索结果',
                  summary: postToolThinking,
                  status: 'running',
                },
              ],
              status: 'streaming',
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const tracePanel = screen.getByTestId('process-trace-panel-705');
    const postToolToggle = screen.getByTestId('process-analysis-toggle-705-analysis-after-tools');
    expect(postToolToggle).toHaveAttribute('aria-expanded', 'true');
    expect(tracePanel).toHaveTextContent(postToolThinking);
    expect(tracePanel.textContent?.indexOf('已获取结果')).toBeLessThan(
      tracePanel.textContent?.indexOf(postToolThinking) ?? -1,
    );
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
   * 聊天页不再展示右侧工作区折叠入口。
   */
  it('不应再渲染右侧工作区折叠入口', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    expect(screen.queryByRole('button', { name: '展开右侧工作区' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '折叠右侧工作区' })).not.toBeInTheDocument();
  });

  /**
   * 助手消息底部应恢复复制、分享、重新生成、点赞和倒赞入口，避免消息能力被误收敛。
   */
  it('应渲染助手消息操作栏', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    expect(screen.getByTestId('copy-message-102')).toBeInTheDocument();
    expect(screen.getByTestId('copy-menu-toggle-102')).toBeInTheDocument();
    expect(screen.getByTestId('share-message-102')).toBeInTheDocument();
    expect(screen.getByTestId('regenerate-message-102')).toBeInTheDocument();
    expect(screen.getByTestId('thumbs-up-102')).toBeInTheDocument();
    expect(screen.getByTestId('thumbs-down-102')).toBeInTheDocument();
  });

  /**
   * 助手消息分享按钮应进入轮次选择模式，不能绕过选择流程直接生成并复制链接。
   */
  it('助手分享按钮应进入分享轮次选择模式', async () => {
    const shareConversation = vi.fn().mockResolvedValue('http://localhost/api/chat/conversations/shared/share_xxx');

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({ shareConversation })}
      />,
    );

    fireEvent.click(screen.getByTestId('share-message-102'));

    expect(screen.getByTestId('share-selection-shell')).toBeInTheDocument();
    expect(screen.queryByTestId('chat-input-dock')).not.toBeInTheDocument();
    expect(screen.getByTestId('share-round-card-102')).toHaveAttribute('data-selected', 'true');
    expect(screen.queryByText('分享选择模式')).not.toBeInTheDocument();
    expect(screen.queryByText('选择要分享的问答轮次')).not.toBeInTheDocument();
    expect(screen.queryByText(/只允许勾选 AI 回复/)).not.toBeInTheDocument();
    expect(shareConversation).not.toHaveBeenCalled();
    expect(navigator.clipboard.writeText).not.toHaveBeenCalled();
  });

  /**
   * 重新生成应带上当前助手消息 ID，保持覆盖当前回复的重试语义。
   */
  it('应重新生成最后一条助手消息', async () => {
    const regenerateConversation = vi.fn().mockResolvedValue(undefined);

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({ regenerateConversation })}
      />,
    );

    fireEvent.click(screen.getByTestId('regenerate-message-102'));
    await waitFor(() => {
      expect(regenerateConversation).toHaveBeenCalledWith('2001', {
        assistantMessageId: '102',
      });
    });
  });

  /**
   * 非最后一条助手消息的重新生成按钮应禁用，避免用户对旧消息触发无效重试。
   */
  it('应禁用非最后一条助手消息的重新生成按钮', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '101',
              conversationId: '2001',
              role: 'USER',
              content: '请帮我分析项目结构',
              status: 'COMPLETED',
            },
            {
              id: '102',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '第一条回答',
              status: 'COMPLETED',
            },
            {
              id: '103',
              conversationId: '2001',
              role: 'USER',
              content: '再补充一点',
              status: 'COMPLETED',
            },
            {
              id: '104',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '最后一条回答',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByTestId('regenerate-message-102')).toBeDisabled();
    expect(screen.getByTestId('regenerate-message-104')).not.toBeDisabled();
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
   * 用户消息悬浮操作应提供编辑、复制和删除三个入口，贴近千问消息气泡交互。
   */
  it('应渲染用户消息悬浮操作栏', async () => {
    render(
      <ChatView isAuthenticated={true} onRequireLogin={vi.fn()} workspace={createWorkspace()} />,
    );

    expect(screen.getByTestId('edit-user-message-101')).toBeInTheDocument();
    expect(screen.getByTestId('copy-user-message-101')).toBeInTheDocument();
    expect(screen.getByTestId('delete-user-message-101')).toBeInTheDocument();
    expect(screen.getByTestId('user-message-actions-101')).toHaveClass('chat-user-message-actions');
  });

  /**
   * 编辑用户消息后再次发送应交给工作台从该消息重新生成，而不是只改前端文本。
   */
  it('应支持编辑用户消息并再次发送', async () => {
    const resendUserMessage = vi.fn().mockResolvedValue(undefined);

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({ resendUserMessage })}
      />,
    );

    fireEvent.click(screen.getByTestId('edit-user-message-101'));
    const editor = screen.getByLabelText('编辑用户消息');
    fireEvent.change(editor, { target: { value: '请重新搜索 Spring Boot SSE 资料' } });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    await waitFor(() => {
      expect(resendUserMessage).toHaveBeenCalledWith('101', '请重新搜索 Spring Boot SSE 资料');
    });
  });

  /**
   * 分享应进入明显的轮次选择模式：聊天区卡片化、底部输入区隐藏，并默认选中触发分享的问答轮次。
   */
  it('应进入分享轮次选择模式并隐藏输入区', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '101',
              conversationId: '2001',
              role: 'USER',
              content: '第一问',
              status: 'COMPLETED',
            },
            {
              id: '102',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '第一答',
              status: 'COMPLETED',
            },
            {
              id: '201',
              conversationId: '2001',
              role: 'USER',
              content: '第二问',
              status: 'COMPLETED',
            },
            {
              id: '202',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '第二答',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    act(() => {
      window.dispatchEvent(
        new CustomEvent('codingx:start-share-conversation', {
          detail: { conversationId: '2001' },
        }),
      );
    });

    expect(screen.getByTestId('share-selection-shell')).toBeInTheDocument();
    expect(screen.queryByTestId('chat-input-dock')).not.toBeInTheDocument();
    expect(screen.getByRole('toolbar', { name: '分享选择工具栏' })).toBeInTheDocument();
    expect(screen.getByText('已选2组对话')).toBeInTheDocument();
    expect(screen.getByTestId('share-round-card-202')).toHaveAttribute('data-selected', 'true');
    expect(screen.getByTestId('share-round-card-102')).toHaveAttribute('data-selected', 'true');
    expect(screen.getByTestId('share-round-card-202')).not.toHaveClass('bg-surface-selected');
    expect(screen.getByTestId('share-round-card-102')).not.toHaveClass('bg-surface-selected');
    expect(screen.getByTestId('share-selection-list')).toBe(screen.getByTestId('share-round-card-202').parentElement);
    expect(screen.getByRole('checkbox', { name: '全选' })).toBeChecked();
    fireEvent.click(screen.getByRole('checkbox', { name: '全选' }));
    expect(screen.getByText('已选0组对话')).toBeInTheDocument();
    expect(screen.getByTestId('share-round-card-202')).toHaveAttribute('data-selected', 'false');
    expect(screen.getByTestId('share-round-card-102')).toHaveAttribute('data-selected', 'false');
    expect(screen.getByRole('checkbox', { name: '全选' })).not.toBeChecked();
    fireEvent.click(screen.getByRole('checkbox', { name: '全选' }));
    expect(screen.getByText('已选2组对话')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: '全选' })).toBeChecked();
    expect(screen.queryByLabelText('选择分享消息 第一问')).not.toBeInTheDocument();
  });

  /**
   * 分享选择只能按助手回复所在轮次勾选，提交时应自动带上对应用户问题一起分享。
   */
  it('应只允许按助手轮次选择并自动携带对应用户消息生成分享链接', async () => {
    const sharedUrl = new URL('/api/chat/conversations/shared/share_xxx', window.location.origin).toString();
    const shareConversation = vi.fn().mockResolvedValue(sharedUrl);

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '101',
              conversationId: '2001',
              role: 'USER',
              content: '第一问',
              status: 'COMPLETED',
            },
            {
              id: '102',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '第一答',
              status: 'COMPLETED',
            },
            {
              id: '201',
              conversationId: '2001',
              role: 'USER',
              content: '第二问',
              status: 'COMPLETED',
            },
            {
              id: '202',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '第二答',
              status: 'COMPLETED',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
          shareConversation,
        })}
      />,
    );

    act(() => {
      window.dispatchEvent(
        new CustomEvent('codingx:start-share-conversation', {
          detail: { conversationId: '2001' },
        }),
      );
    });
    fireEvent.click(screen.getByLabelText('选择分享轮次 第一答'));
    expect(screen.getByText('已选1组对话')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '生成分享链接' }));
    await waitFor(() => {
      expect(shareConversation).toHaveBeenCalledWith('2001', {
        messageIds: ['201', '202'],
      });
    });

    const shareDialog = screen.getByRole('dialog', { name: '分享对话' });
    expect(shareDialog).toBeInTheDocument();
    expect(screen.getByDisplayValue(sharedUrl)).toBeInTheDocument();
    expect(within(shareDialog).getByText('第二问')).toBeInTheDocument();
    expect(within(shareDialog).getByText('第二答')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '复制链接' }));
    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(sharedUrl);
    });
  });

  /**
   * 删除用户消息应进入选择模式，复用问答轮次容器并在确认后删除选中的消息 ID。
   */
  it('应进入删除轮次选择模式并确认删除', async () => {
    const deleteConversationMessages = vi.fn().mockResolvedValue(undefined);

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({ deleteConversationMessages })}
      />,
    );

    fireEvent.click(screen.getByTestId('delete-user-message-101'));

    expect(screen.getByTestId('delete-selection-shell')).toBeInTheDocument();
    expect(screen.queryByTestId('chat-input-dock')).not.toBeInTheDocument();
    expect(screen.getByRole('toolbar', { name: '删除选择工具栏' })).toBeInTheDocument();
    expect(screen.getByText('已选1组对话')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '删除所选' }));

    await waitFor(() => {
      expect(deleteConversationMessages).toHaveBeenCalledWith('2001', ['101', '102']);
    });
  });

  /**
   * 未落库的临时 assistant 不能进入分享选择，避免用户误选到无法分享的流式占位消息。
   */
  it('应忽略未落库assistant的分享选择', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '101',
              conversationId: '2001',
              role: 'USER',
              content: '请搜索 Spring Boot SSE 最佳实践',
              status: 'COMPLETED',
            },
            {
              id: 'optimistic-regenerate-assistant-1779773736005',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '正在生成回答...',
              status: 'streaming',
            },
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    act(() => {
      window.dispatchEvent(
        new CustomEvent('codingx:start-share-conversation', {
          detail: { conversationId: '2001' },
        }),
      );
    });

    expect(screen.queryByTestId('share-selection-shell')).not.toBeInTheDocument();
  });

  /**
   * 点击复制应写入剪贴板。
   */
  it('应支持复制助手消息', async () => {
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
    // 技能列表第二行展示业务描述，并用单行截断承接长描述，避免继续展示内部斜杠命令。
    const skillDescription = within(selectorPanel).getByText('查询销售汇总、排名、趋势与明细');
    expect(skillDescription).toHaveClass('truncate');
    expect(within(selectorPanel).queryByText('/sales_query')).not.toBeInTheDocument();
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
   * 专家列表中的已选项再次点击时应取消选中，保持与技能多选的切换交互一致。
   */
  it('应支持再次点击已选专家以取消选中', async () => {
    const setSelectedExpertCode = vi.fn();
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          availableExperts: [
            {
              id: '8101',
              expertCode: 'bi-analyst',
              displayName: 'BI 报表专家',
              description: '分析经营看板并给出指标建议',
              category: '数据分析',
            },
          ],
          selectedExpertCode: 'bi-analyst',
          setSelectedExpertCode,
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开专家列表' }));
    fireEvent.click(screen.getByRole('button', { name: '选择专家 BI 报表专家' }));

    expect(setSelectedExpertCode).toHaveBeenCalledWith(null);
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
   * 乐观助手消息尚未落库时不应提交反馈，避免请求携带临时消息 ID 触发后端类型转换异常。
   */
  it('应禁止对乐观助手消息提交反馈', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: 'optimistic-assistant-1779529346520',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '正在生成回答...',
              status: 'streaming',
            },
          ],
        })}
      />,
    );

    expect(
      screen.queryByTestId('thumbs-up-optimistic-assistant-1779529346520'),
    ).not.toBeInTheDocument();

    await waitFor(() => {
      expect(fetchSpy).not.toHaveBeenCalled();
    });
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
   * 助手消息存在工具过程时，应展示可折叠明细的内联工具行。
   */
  it('应渲染并支持折叠工具过程组', async () => {
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
              processCards: [
                {
                  id: 'tool-call-801',
                  type: 'tool_call',
                  title: '调用天气查询',
                  summary: '正在请求天气工具。',
                  status: 'completed',
                  details: [
                    {
                      label: '参数',
                      content: '北京今天天气怎么样',
                    },
                  ],
                },
              ],
              status: 'COMPLETED',
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const panel = screen.getByTestId('process-trace-panel-801');
    expect(panel).toBeInTheDocument();
    expect(screen.queryByTestId('process-tool-group-toggle-801')).not.toBeInTheDocument();
    expect(screen.getByText('调用天气查询')).toBeInTheDocument();
    expect(panel).not.toHaveClass('rounded-3xl');
    // 业务意图：工具参数默认折叠，先确认详情不直接外露，再通过展开按钮验证内容可见。
    expect(screen.queryByText('北京今天天气怎么样')).not.toBeInTheDocument();

    const toggleButton = screen.getByTestId('process-tool-detail-toggle-801-tool-call-801');
    expect(toggleButton).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(toggleButton);
    expect(toggleButton).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('北京今天天气怎么样')).toBeInTheDocument();
  });

  /**
   * 工具参数与结果应在各自过程行中默认折叠，展开后才显示具体内容。
   */
  it('应在内联工具行中默认折叠参数与结果并支持展开', async () => {
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
              processCards: [
                {
                  id: 'tool-call-861',
                  type: 'tool_call',
                  title: '调用天气查询',
                  summary: '正在请求天气工具。',
                  status: 'completed',
                  details: [
                    {
                      label: '参数',
                      content: '{\"city\":\"北京\",\"date\":\"2026-05-21\"}',
                    },
                  ],
                },
                {
                  id: 'tool-result-861',
                  type: 'tool_result',
                  title: '已获取结果',
                  summary: '北京今日晴，最高 28.6°C。',
                  status: 'completed',
                  details: [
                    {
                      label: '结果',
                      content: '{\"text\":\"北京今日晴\",\"temp\":28.6}',
                    },
                  ],
                },
              ],
              status: 'COMPLETED',
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.queryByTestId('process-tool-group-toggle-861')).not.toBeInTheDocument();
    expect(screen.getByTestId('process-tool-row-861-tool-call-861')).toBeInTheDocument();
    expect(screen.queryByTestId('process-tool-row-861-tool-result-861')).not.toBeInTheDocument();
    expect(screen.getByText('调用天气查询')).toBeInTheDocument();
    expect(screen.queryByText('{"city":"北京","date":"2026-05-21"}')).not.toBeInTheDocument();
    expect(screen.queryByText('{"text":"北京今日晴","temp":28.6}')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('process-tool-detail-toggle-861-tool-call-861'));
    expect(screen.getByText('{"city":"北京","date":"2026-05-21"}')).toBeInTheDocument();
    expect(screen.getByText('{"text":"北京今日晴","temp":28.6}')).toBeInTheDocument();
  });

  /**
   * 助手消息在联网搜索期间应以内联工具行展示搜索进行中与搜索结果。
   */
  it('应在助手消息中渲染搜索过程工具行', async () => {
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
              processCards: [
                {
                  id: 'tool-call-search-951',
                  type: 'tool_call',
                  title: '调用网页搜索',
                  summary: '正在检索 2 个来源站点。',
                  status: 'running',
                },
                {
                  id: 'tool-result-search-951',
                  type: 'tool_result',
                  title: '已获取结果',
                  summary: '已获取 2 条搜索结果。',
                  status: 'completed',
                  details: [
                    {
                      label: '结果',
                      content: 'OpenAI API 最新变更\nBing Search API 文档',
                    },
                  ],
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByTestId('process-trace-panel-951')).toBeInTheDocument();
    expect(screen.queryByTestId('process-tool-group-toggle-951')).not.toBeInTheDocument();
    expect(screen.getByTestId('process-tool-row-951-tool-call-search-951')).toBeInTheDocument();
    expect(screen.getByTestId('process-tool-row-951-tool-result-search-951')).toBeInTheDocument();
    expect(screen.getByText('调用网页搜索')).toBeInTheDocument();
    expect(screen.getByText('已获取结果')).toBeInTheDocument();
    // 业务意图：网页搜索结果只展示来源摘要，详情已由最终回答和来源面板承载。
    expect(screen.queryByText('OpenAI API 最新变更')).not.toBeInTheDocument();
    expect(screen.queryByText('Bing Search API 文档')).not.toBeInTheDocument();
    expect(screen.queryByTestId('process-tool-detail-toggle-951-tool-result-search-951')).not.toBeInTheDocument();
  });

  /**
   * 网页搜索调用的查询词已在摘要展示，参数明细不应再占用一行展开入口。
   */
  it('应隐藏网页搜索调用参数明细入口', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '952',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '正在汇总检索结果',
              status: 'streaming',
              processCards: [
                {
                  id: 'tool-call-search-952',
                  type: 'tool_call',
                  title: '调用网页搜索',
                  summary: '调用网页搜索：量子力学是什么',
                  status: 'completed',
                  toolId: 'search',
                  details: [
                    {
                      label: '参数',
                      content: '量子力学是什么',
                    },
                  ],
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const searchRow = screen.getByTestId('process-tool-row-952-tool-call-search-952');
    expect(searchRow).toHaveTextContent('调用网页搜索：量子力学是什么');
    expect(screen.queryByTestId('process-tool-detail-toggle-952-tool-call-search-952')).not.toBeInTheDocument();
    expect(screen.queryByText('查看明细')).not.toBeInTheDocument();
    expect(screen.queryByText('参数')).not.toBeInTheDocument();
  });

  /**
   * Local tool cards use inline rows so arguments and results can be inspected in place.
   */
  it('renders local tool arguments and results in the assistant message', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '963',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: 'Current directory is D:/code/CodingX',
              status: 'done',
              processCards: [
                {
                  id: 'tool-call-shell-963',
                  type: 'tool_call',
                  title: '调用shell_command',
                  summary: '正在执行本地命令。',
                  status: 'completed',
                  toolId: 'shell_command',
                  displayName: 'shell_command',
                  details: [
                    {
                      label: '参数',
                      content: '{"command":"pwd"}',
                    },
                  ],
                },
                {
                  id: 'tool-result-shell-963',
                  type: 'tool_result',
                  title: '已获取结果',
                  summary: 'D:/code/CodingX',
                  status: 'completed',
                  toolId: 'shell_command',
                  displayName: 'shell_command',
                  details: [
                    {
                      label: '结果',
                      content: 'D:/code/CodingX',
                    },
                  ],
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.queryByTestId('process-tool-group-toggle-963')).not.toBeInTheDocument();
    expect(screen.getByTestId('process-tool-row-963-tool-call-shell-963')).toBeInTheDocument();
    expect(screen.queryByTestId('process-tool-row-963-tool-result-shell-963')).not.toBeInTheDocument();
    expect(screen.getByText('调用shell_command')).toBeInTheDocument();
    expect(screen.getByText('已获取结果')).toBeInTheDocument();
    expect(screen.queryByText('{"command":"pwd"}')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('process-tool-detail-toggle-963-tool-call-shell-963'));

    const callRow = screen.getByTestId('process-tool-row-963-tool-call-shell-963');
    expect(callRow).toHaveTextContent('{"command":"pwd"}');
    expect(callRow).toHaveTextContent('D:/code/CodingX');
  });

  /**
   * ReAct 工具过程应直接展示普通过程文字、工具动作和观察内容，但不再额外渲染角色气泡标签。
   */
  it('renders ReAct tool steps inline without opening the tool group', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '964',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: 'Current directory is D:/code/CodingX',
              status: 'done',
              processCards: [
                {
                  id: 'react-thought-shell-964',
                  type: 'analysis',
                  title: '思考',
                  summary: '需要调用 shell_command 获取当前目录。',
                  status: 'completed',
                  presentation: 'react',
                },
                {
                  id: 'tool-call-shell-964',
                  type: 'tool_call',
                  title: '行动',
                  summary: '调用 shell_command',
                  status: 'completed',
                  toolId: 'shell_command',
                  displayName: 'shell_command',
                  presentation: 'react',
                  details: [
                    {
                      label: '参数',
                      content: '{"command":"pwd"}',
                    },
                  ],
                },
                {
                  id: 'tool-result-shell-964',
                  type: 'tool_result',
                  title: '观察',
                  summary: '工具返回：D:/code/CodingX',
                  status: 'completed',
                  toolId: 'shell_command',
                  displayName: 'shell_command',
                  presentation: 'react',
                  details: [
                    {
                      label: '结果',
                      content: 'D:/code/CodingX',
                    },
                  ],
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.queryByTestId('process-tool-group-toggle-964')).not.toBeInTheDocument();
    const tracePanel = screen.getByTestId('process-trace-panel-964');
    expect(within(tracePanel).queryByTestId('process-react-role-label')).not.toBeInTheDocument();
    expect(within(tracePanel).queryByText('深度思考')).not.toBeInTheDocument();
    expect(within(tracePanel).getByText('需要调用 shell_command 获取当前目录。')).toBeInTheDocument();
    expect(within(tracePanel).queryByText('Thought 思考')).not.toBeInTheDocument();
    expect(within(tracePanel).queryByText('Action 行动')).not.toBeInTheDocument();
    expect(within(tracePanel).queryByText('Observation 观察')).not.toBeInTheDocument();
    expect(within(tracePanel).queryByText('行动')).not.toBeInTheDocument();
    expect(screen.getByText('调用 shell_command')).toBeInTheDocument();
    expect(screen.getByText('观察')).toBeInTheDocument();
    expect(screen.queryByText('{"command":"pwd"}')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('process-tool-detail-toggle-964-tool-call-shell-964'));
    expect(screen.getByText('{"command":"pwd"}')).toBeInTheDocument();
    expect(screen.getByText('D:/code/CodingX')).toBeInTheDocument();
  });

  /**
   * 历史持久化的旧版搜索思考模板不是模型真实输出，渲染层必须丢弃。
   */
  it('应过滤历史旧版伪造搜索思考并保留真实工具调用', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '964b',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '已完成检索。',
              status: 'done',
              processCards: [
                {
                  id: 'react-thought-search-964b',
                  type: 'analysis',
                  title: '思考',
                  summary: '需要通过网页搜索确认资料：当前最强的AI模型是什么',
                  status: 'completed',
                  presentation: 'react',
                },
                {
                  id: 'tool-call-search-964b',
                  type: 'tool_call',
                  title: '行动',
                  summary: '调用网页搜索：当前最强的AI模型是什么',
                  status: 'completed',
                  toolId: 'search',
                  displayName: '网页搜索',
                  presentation: 'react',
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const tracePanel = screen.getByTestId('process-trace-panel-964b');
    expect(within(tracePanel).queryByText('深度思考')).not.toBeInTheDocument();
    expect(within(tracePanel).queryByText('需要通过网页搜索确认资料：当前最强的AI模型是什么')).not.toBeInTheDocument();
    expect(screen.getByText('调用网页搜索：当前最强的AI模型是什么')).toBeInTheDocument();
  });

  /**
   * 多轮工具过程必须按普通过程文字、工具动作、观察内容串联，避免退回“工具调用列表”的展示形态。
   */
  it('renders multiple ReAct rounds without role badges', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '965',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '已完成两轮检索。',
              status: 'done',
              processCards: [
                {
                  id: 'react-thought-search-1',
                  type: 'analysis',
                  title: '思考',
                  summary: '需要先检索 Qwen 最新模型。',
                  status: 'completed',
                  presentation: 'react',
                },
                {
                  id: 'tool-call-search-1',
                  type: 'tool_call',
                  title: '行动',
                  summary: '调用网页搜索：Qwen 最新模型',
                  status: 'completed',
                  toolId: 'search',
                  displayName: '网页搜索',
                  presentation: 'react',
                },
                {
                  id: 'tool-result-search-1',
                  type: 'tool_result',
                  title: '观察',
                  summary: '网页搜索返回 Qwen 模型来源。',
                  status: 'completed',
                  toolId: 'search',
                  displayName: '网页搜索',
                  presentation: 'react',
                },
                {
                  id: 'react-thought-search-2',
                  type: 'analysis',
                  title: '思考',
                  summary: '需要继续检索 GLM 最新模型。',
                  status: 'completed',
                  presentation: 'react',
                },
                {
                  id: 'tool-call-search-2',
                  type: 'tool_call',
                  title: '行动',
                  summary: '调用网页搜索：GLM 最新模型',
                  status: 'completed',
                  toolId: 'search',
                  displayName: '网页搜索',
                  presentation: 'react',
                },
                {
                  id: 'tool-result-search-2',
                  type: 'tool_result',
                  title: '观察',
                  summary: '网页搜索返回 GLM 模型来源。',
                  status: 'completed',
                  toolId: 'search',
                  displayName: '网页搜索',
                  presentation: 'react',
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const tracePanel = screen.getByTestId('process-trace-panel-965');
    expect(within(tracePanel).queryByTestId('process-react-role-label')).not.toBeInTheDocument();
    expect(within(tracePanel).queryByText('深度思考')).not.toBeInTheDocument();
    expect(within(tracePanel).queryByText('行动')).not.toBeInTheDocument();
    expect(within(tracePanel).queryByText('观察')).not.toBeInTheDocument();
    const traceText = tracePanel.textContent ?? '';
    expect(traceText.indexOf('需要先检索 Qwen 最新模型。')).toBeLessThan(
      traceText.indexOf('调用网页搜索：Qwen 最新模型'),
    );
    expect(traceText.indexOf('调用网页搜索：Qwen 最新模型')).toBeLessThan(
      traceText.indexOf('Qwen 模型来源。'),
    );
    expect(traceText).not.toContain('网页搜索返回 Qwen 模型来源。');
    expect(traceText.indexOf('Qwen 模型来源。')).toBeLessThan(
      traceText.indexOf('需要继续检索 GLM 最新模型。'),
    );
  });

  /**
   * 带时间线的消息必须按正文与过程事件的原始顺序渲染，避免所有过程块被固定挪到消息顶部。
   */
  it('按时间线穿插渲染助手正文与过程节点', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '969',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '我先检查当前目录。\n\n我再根据结果继续分析。',
              status: 'done',
              processCards: [
                {
                  id: 'tool-call-shell-969',
                  type: 'tool_call',
                  title: '行动',
                  summary: '调用 shell_command',
                  status: 'completed',
                  toolId: 'shell_command',
                  displayName: 'shell_command',
                  presentation: 'react',
                },
              ],
              timelineItems: [
                {
                  id: 'content-969-a',
                  type: 'content',
                  content: '我先检查当前目录。\n\n',
                },
                {
                  id: 'process-tool-call-shell-969',
                  type: 'process',
                  card: {
                    id: 'tool-call-shell-969',
                    type: 'tool_call',
                    title: '行动',
                    summary: '调用 shell_command',
                    status: 'completed',
                    toolId: 'shell_command',
                    displayName: 'shell_command',
                    presentation: 'react',
                  },
                },
                {
                  id: 'content-969-b',
                  type: 'content',
                  content: '我再根据结果继续分析。',
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const messageShell = screen.getByTestId('assistant-message-body-969');
    const messageText = messageShell.textContent ?? '';
    expect(messageText.indexOf('我先检查当前目录。')).toBeLessThan(
      messageText.indexOf('调用 shell_command'),
    );
    expect(messageText.indexOf('调用 shell_command')).toBeLessThan(
      messageText.indexOf('我再根据结果继续分析。'),
    );
    expect(screen.getAllByTestId('process-tool-row-969-tool-call-shell-969')).toHaveLength(1);
  });

  /**
   * 时间线中的连续搜索结果仍要复用原有汇总能力，避免长任务刷屏。
   */
  it('应在时间线内折叠连续搜索结果', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '970',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '根据两条来源汇总如下。',
              status: 'done',
              timelineItems: [
                {
                  id: 'content-970-a',
                  type: 'content',
                  content: '我先搜索最新资料。\n\n',
                },
                {
                  id: 'process-search-result-970-a',
                  type: 'process',
                  card: {
                    id: 'search-result-970-a',
                    type: 'tool_result',
                    title: '观察',
                    summary: '网页搜索返回 OpenAI：文档 A',
                    status: 'completed',
                    toolId: 'search',
                    displayName: '网页搜索',
                    presentation: 'react',
                  },
                },
                {
                  id: 'process-search-result-970-b',
                  type: 'process',
                  card: {
                    id: 'search-result-970-b',
                    type: 'tool_result',
                    title: '观察',
                    summary: '网页搜索返回 Microsoft：文档 B',
                    status: 'completed',
                    toolId: 'search',
                    displayName: '网页搜索',
                    presentation: 'react',
                  },
                },
                {
                  id: 'content-970-b',
                  type: 'content',
                  content: '根据两条来源汇总如下。',
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const messageShell = screen.getByTestId('assistant-message-body-970');
    expect(within(messageShell).getByTestId('process-search-summary-970-search-result-970-a')).toHaveTextContent(
      '已搜索网页 2 次',
    );
    const messageText = messageShell.textContent ?? '';
    expect(messageText.indexOf('我先搜索最新资料。')).toBeLessThan(
      messageText.indexOf('已搜索网页 2 次'),
    );
    expect(messageText.indexOf('已搜索网页 2 次')).toBeLessThan(
      messageText.indexOf('根据两条来源汇总如下。'),
    );
  });

  /**
   * 搜索工具结果只展示摘要，不再提供来源明细展开入口。
   */
  it('应隐藏搜索工具结果明细入口', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '961',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '正在汇总检索结果',
              status: 'streaming',
              processCards: [
                {
                  id: 'tool-call-search-961',
                  type: 'tool_call',
                  title: '调用网页搜索',
                  summary: '正在检索公开资料。',
                  status: 'completed',
                  toolId: 'search',
                },
                {
                  id: 'tool-result-search-961',
                  type: 'tool_result',
                  title: '已获取结果',
                  summary: '已获取 2 条搜索结果。',
                  status: 'completed',
                  toolId: 'search',
                  details: [
                    {
                      label: '结果',
                      content:
                        'OpenAI API 文档 | OpenAI | https://platform.openai.com/docs\nBing Search API 文档 | Microsoft Learn | https://learn.microsoft.com/bing/search-apis/',
                    },
                  ],
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByText('已获取 2 条搜索结果。')).toBeInTheDocument();
    expect(screen.queryByTestId('process-tool-detail-toggle-961-tool-result-search-961')).not.toBeInTheDocument();
    expect(screen.queryByText('OpenAI API 文档')).not.toBeInTheDocument();
    expect(screen.queryByText('Microsoft Learn')).not.toBeInTheDocument();
  });

  /**
   * 实时搜索来源按三行块写入时，仍不在过程行里暴露明细。
   */
  it('应隐藏实时搜索结果明细入口', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '962',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '正在汇总检索结果',
              status: 'streaming',
              processCards: [
                {
                  id: 'tool-call-search-962',
                  type: 'tool_call',
                  title: '调用网页搜索',
                  summary: '正在检索公开资料。',
                  status: 'completed',
                  toolId: 'search',
                },
                {
                  id: 'tool-result-search-962',
                  type: 'tool_result',
                  title: '已获取结果',
                  summary: '已获取 2 条搜索结果。',
                  status: 'completed',
                  toolId: 'search',
                  details: [
                    {
                      label: '结果',
                      content:
                        'OpenAI API 文档\nOpenAI\nhttps://platform.openai.com/docs\n\nBing Search API 文档\nMicrosoft Learn\nhttps://learn.microsoft.com/bing/search-apis/',
                    },
                  ],
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByText('已获取 2 条搜索结果。')).toBeInTheDocument();
    expect(screen.queryByTestId('process-tool-detail-toggle-962-tool-result-search-962')).not.toBeInTheDocument();
    expect(screen.queryByText('OpenAI API 文档')).not.toBeInTheDocument();
    expect(screen.queryByText('Microsoft Learn')).not.toBeInTheDocument();
    expect(screen.queryByText('https://platform.openai.com/docs')).not.toBeInTheDocument();
  });

  /**
   * 多条网页搜索返回应默认合并折叠，避免来源列表把主回答顶到视窗外。
   */
  it('应默认折叠连续网页搜索返回过程并按需展开', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '966',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '正在分析检索结果',
              status: 'streaming',
              processCards: [
                {
                  id: 'react-thought-search-966',
                  type: 'analysis',
                  title: '思考',
                  summary: '需要检索网上公开资料。',
                  status: 'completed',
                  presentation: 'react',
                },
                {
                  id: 'tool-call-search-966',
                  type: 'tool_call',
                  title: '行动',
                  summary: '调用网页搜索：哪个AI最厉害',
                  status: 'completed',
                  toolId: 'search',
                  presentation: 'react',
                },
                ...Array.from({ length: 4 }, (_, index) => ({
                  id: `search-result-${index + 1}`,
                  type: 'tool_result',
                  title: '观察',
                  summary: `网页搜索返回 site-${index + 1}.com：AI 资料 ${index + 1}`,
                  status: 'completed',
                  toolId: 'search',
                  displayName: '网页搜索',
                  presentation: 'react',
                  details: [
                    {
                      label: '结果',
                      content: `AI 资料 ${index + 1}\nsite-${index + 1}.com\nhttps://site-${index + 1}.com/ai`,
                    },
                  ],
                })),
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const tracePanel = screen.getByTestId('process-trace-panel-966');
    expect(screen.getByTestId('process-search-summary-966-search-result-1')).toHaveTextContent(
      '已搜索网页 4 次',
    );
    const summaryToggle = screen.getByTestId('process-search-summary-toggle-966-search-result-1');
    expect(summaryToggle.parentElement).toHaveTextContent('已搜索网页 4 次');
    expect(summaryToggle.parentElement).toHaveTextContent('展开来源');
    expect(within(tracePanel).queryByText('site-1.com：AI 资料 1')).not.toBeInTheDocument();
    expect(within(tracePanel).queryByText('site-4.com：AI 资料 4')).not.toBeInTheDocument();

    fireEvent.click(summaryToggle);

    expect(within(tracePanel).getByText('site-1.com：AI 资料 1')).toBeInTheDocument();
    expect(within(tracePanel).getByText('site-4.com：AI 资料 4')).toBeInTheDocument();
    expect(
      within(tracePanel).queryByTestId('process-tool-detail-toggle-966-search-result-1'),
    ).not.toBeInTheDocument();
    expect(within(tracePanel).queryByText('网页搜索返回 site-1.com：AI 资料 1')).not.toBeInTheDocument();
  });

  /**
   * 多条 shell 命令应像 Codex 一样默认聚合，避免长任务把主消息区挤满原始输出。
   */
  it('应默认折叠连续 shell 命令并在展开后显示命令输出', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '967',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '已经检查项目结构',
              status: 'done',
              processCards: [
                {
                  id: 'tool-call-shell-967-a',
                  type: 'tool_call',
                  title: '行动',
                  summary: '调用 shell_command',
                  status: 'completed',
                  toolId: 'shell_command',
                  displayName: 'shell_command',
                  presentation: 'react',
                  details: [
                    {
                      label: '参数',
                      content: '{"command":"Get-ChildItem"}',
                    },
                  ],
                },
                {
                  id: 'tool-result-shell-967-a',
                  type: 'tool_result',
                  title: '观察',
                  summary: '工具返回：package.json',
                  status: 'completed',
                  toolId: 'shell_command',
                  displayName: 'shell_command',
                  presentation: 'react',
                  details: [
                    {
                      label: '结果',
                      content: 'package.json\nsrc',
                    },
                  ],
                },
                {
                  id: 'tool-call-shell-967-b',
                  type: 'tool_call',
                  title: '行动',
                  summary: '调用 shell_command',
                  status: 'completed',
                  toolId: 'shell_command',
                  displayName: 'shell_command',
                  presentation: 'react',
                  details: [
                    {
                      label: '参数',
                      content: '{"command":"rg --files"}',
                    },
                  ],
                },
                {
                  id: 'tool-result-shell-967-b',
                  type: 'tool_result',
                  title: '观察',
                  summary: '工具返回：src/App.tsx',
                  status: 'completed',
                  toolId: 'shell_command',
                  displayName: 'shell_command',
                  presentation: 'react',
                  details: [
                    {
                      label: '结果',
                      content: 'src/App.tsx\nsrc/main.tsx',
                    },
                  ],
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    const tracePanel = screen.getByTestId('process-trace-panel-967');
    expect(screen.getByTestId('process-command-summary-967-tool-call-shell-967-a')).toHaveTextContent(
      '已运行 2 条命令',
    );
    expect(within(tracePanel).queryByText('$ Get-ChildItem')).not.toBeInTheDocument();
    expect(within(tracePanel).queryByText('package.json')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('process-command-summary-toggle-967-tool-call-shell-967-a'));

    expect(within(tracePanel).getByText('$ Get-ChildItem')).toBeInTheDocument();
    expect(within(tracePanel).getByText(/package\.json/)).toBeInTheDocument();
    expect(within(tracePanel).getByText('$ rg --files')).toBeInTheDocument();
    expect(within(tracePanel).getByText(/src\/App\.tsx/)).toBeInTheDocument();
  });

  /**
   * 连续普通工具不是 shell 命令，必须保持原有内联工具行展示。
   */
  it('不应把连续非 shell 工具折叠成命令摘要', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '968',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '已经完成天气查询',
              status: 'done',
              processCards: [
                {
                  id: 'tool-call-weather-968-a',
                  type: 'tool_call',
                  title: '调用天气查询',
                  summary: '查询北京天气。',
                  status: 'completed',
                  toolId: 'weather',
                  displayName: '天气查询',
                  details: [{ label: '参数', content: '{"city":"北京"}' }],
                },
                {
                  id: 'tool-result-weather-968-a',
                  type: 'tool_result',
                  title: '已获取结果',
                  summary: '北京晴。',
                  status: 'completed',
                  toolId: 'weather',
                  displayName: '天气查询',
                  details: [{ label: '结果', content: '北京晴' }],
                },
                {
                  id: 'tool-call-weather-968-b',
                  type: 'tool_call',
                  title: '调用天气查询',
                  summary: '查询上海天气。',
                  status: 'completed',
                  toolId: 'weather',
                  displayName: '天气查询',
                  details: [{ label: '参数', content: '{"city":"上海"}' }],
                },
              ],
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.queryByTestId('process-command-summary-968-tool-call-weather-968-a')).not.toBeInTheDocument();
    expect(screen.getByTestId('process-tool-row-968-tool-call-weather-968-a')).toBeInTheDocument();
    expect(screen.getByTestId('process-tool-row-968-tool-call-weather-968-b')).toBeInTheDocument();
  });

  /**
   * 搜索来源默认应折叠，只展示一行摘要；点击后再展开来源列表与原始链接。
   */
  it('应默认折叠搜索来源并在展开后显示列表项', async () => {
    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          messages: [
            {
              id: '971',
              conversationId: '2001',
              role: 'ASSISTANT',
              content: '已完成搜索来源整理',
              status: 'COMPLETED',
              searchProgress: {
                status: 'completed',
                items: [
                  {
                    id: 'ref-1',
                    title: 'OpenAI API 文档',
                    siteName: 'OpenAI',
                    url: 'https://platform.openai.com/docs',
                  },
                ],
              },
            } as any,
          ],
          executionSteps: [],
          references: [],
          artifacts: [],
        })}
      />,
    );

    expect(screen.getByTestId('search-progress-panel-971')).toBeInTheDocument();
    // 业务意图：搜索来源必须跟随倒赞按钮，作为消息操作栏的一部分展示。
    expect(
      screen
        .getByTestId('thumbs-down-971')
        .compareDocumentPosition(screen.getByTestId('search-progress-panel-971')) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    const toggleButton = screen.getByTestId('search-progress-toggle-971');
    const actionRow = screen.getByTestId('thumbs-down-971').parentElement;
    expect(screen.getByTestId('search-progress-panel-971').parentElement).toBe(actionRow);
    expect(actionRow).toHaveClass('flex-wrap');
    expect(screen.getByTestId('search-progress-panel-971')).toHaveClass('contents');
    expect(toggleButton).toHaveTextContent('搜索来源');
    expect(toggleButton).toHaveTextContent('1 条');
    expect(toggleButton).not.toHaveTextContent('来源已获取');
    expect(toggleButton).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByTestId('search-source-link-971-ref-1')).not.toBeInTheDocument();
    expect(screen.queryByText('OpenAI API 文档')).not.toBeInTheDocument();

    fireEvent.click(toggleButton);

    expect(toggleButton).toHaveAttribute('aria-expanded', 'true');
    const sourceContent = document.getElementById('search-progress-content-971');
    expect(sourceContent).toHaveClass('basis-full');
    expect(sourceContent).toHaveClass('w-full');
    expect(screen.getByText('OpenAI API 文档')).toBeInTheDocument();
    expect(screen.getByText('OpenAI')).toBeInTheDocument();
    expect(screen.getByText('platform.openai.com/docs')).toBeInTheDocument();
    expect(screen.getByTestId('search-source-favicon-971-ref-1')).toBeInTheDocument();

    const sourceLink = screen.getByTestId('search-source-link-971-ref-1');
    expect(sourceLink).toHaveAttribute('href', 'https://platform.openai.com/docs');
    expect(sourceLink).toHaveAttribute('target', '_blank');
    expect(sourceLink).toHaveAttribute('rel', expect.stringContaining('noopener'));
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

  /**
   * 重命名失败时应保留弹窗并展示后端错误，避免用户误判为确认按钮无响应。
   */
  it('应在重命名失败时展示错误并保持弹窗打开', async () => {
    const renameConversation = vi.fn().mockRejectedValue(new Error('会话标题不能为空'));
    const closeRenameDialog = vi.fn();

    render(
      <ChatView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspace={createWorkspace({
          renameConversation,
          renameDialog: {
            conversationId: '2001',
            initialTitle: '默认标题',
            isOpen: true,
            open: vi.fn(),
            close: closeRenameDialog,
          },
        })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '确认' }));

    await waitFor(() => {
      expect(renameConversation).toHaveBeenCalledWith('2001', '默认标题', undefined);
    });
    expect(await screen.findByRole('alert')).toHaveTextContent('会话标题不能为空');
    expect(closeRenameDialog).not.toHaveBeenCalled();
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
        processCards: [
          {
            id: 'analysis-default',
            type: 'analysis',
            title: '分析问题',
            summary: '正在判断需要检索哪些实时资料。',
            status: 'completed',
          },
          {
            id: 'tool-call-default',
            type: 'tool_call',
            title: '调用网页搜索',
            summary: '正在检索 Spring Boot SSE 最佳实践。',
            status: 'completed',
            details: [
              {
                label: '参数',
                content: '请搜索 Spring Boot SSE 最佳实践',
              },
            ],
          },
          {
            id: 'tool-result-default',
            type: 'tool_result',
            title: '已获取结果',
            summary: '已找到官方文档和相关文章。',
            status: 'completed',
            details: [
              {
                label: '结果',
                content: 'Spring Boot SSE 最佳实践',
              },
            ],
          },
          {
            id: 'synthesis-default',
            type: 'synthesis',
            title: '整理结论',
            summary: '正在根据检索结果整理最终回答。',
            status: 'completed',
          },
        ],
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
    deleteConversationMessages: vi.fn().mockResolvedValue(undefined),
    shareConversation: vi.fn().mockResolvedValue('http://localhost/api/chat/conversations/shared/share_xxx'),
    regenerateConversation: vi.fn().mockResolvedValue(undefined),
    resendUserMessage: vi.fn().mockResolvedValue(undefined),
    toggleConversationPin: vi.fn().mockResolvedValue(true),
    exportConversation: vi.fn().mockResolvedValue(undefined),
    exportConversations: vi.fn().mockResolvedValue(undefined),
    deleteConversations: vi.fn().mockResolvedValue(undefined),
    renameDialog: {
      conversationId: null,
      initialTitle: '',
      actionContext: undefined,
      isOpen: false,
      open: vi.fn(),
      close: vi.fn(),
    },
    deleteDialog: {
      conversationId: null,
      title: '',
      actionContext: undefined,
      isOpen: false,
      open: vi.fn(),
      close: vi.fn(),
    },
    ...overrides,
  };
}
