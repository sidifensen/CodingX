import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ChatView from './ChatView';

/**
 * 验证聊天工作区会真实加载后端数据并消费 SSE 流。
 */
describe('ChatView', () => {
  beforeEach(() => {
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
    vi.restoreAllMocks();
  });

  /**
   * 未登录时发送消息仍应触发登录拦截。
   */
  it('应在未登录发送消息时调用登录回调', async () => {
    const onRequireLogin = vi.fn();

    render(<ChatView isAuthenticated={false} onRequireLogin={onRequireLogin} />);

    fireEvent.change(screen.getByPlaceholderText('输入指令以重构组件库或分析代码...'), {
      target: { value: '请帮我分析项目结构' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }));

    expect(onRequireLogin).toHaveBeenCalledTimes(1);
  });

  /**
   * 已登录时应从真实接口加载会话、消息与右栏回放数据。
   */
  it('应渲染真实会话与工作区回放', async () => {
    mockWorkspaceFetch();

    render(<ChatView isAuthenticated={true} onRequireLogin={vi.fn()} />);

    expect(await screen.findByText('Default Demo Conversation')).toBeInTheDocument();
    expect((await screen.findAllByText('请搜索 Spring Boot SSE 最佳实践')).length).toBeGreaterThan(0);
    expect(await screen.findByText('搜索资料')).toBeInTheDocument();
    expect(await screen.findByText('Spring Boot SSE 最佳实践')).toBeInTheDocument();
    expect(await screen.findByText('search-report.docx')).toBeInTheDocument();
  });

  /**
   * 提交消息后应消费 SSE 并把最新消息内容展示到中栏。
   */
  it('应在发送消息后消费 SSE 响应并刷新回放', async () => {
    mockWorkspaceFetch({
      messageContent: '旧回答',
      streamText: [
        'event:meta',
        'data:{"conversationId":2001}',
        '',
        'event:message',
        'data:{"type":"response","delta":"新的"}',
        '',
        'event:message',
        'data:{"type":"response","delta":"回答"}',
        '',
        'event:step',
        'data:{"id":1,"runId":5002,"stepType":"search","stepTitle":"搜索资料","stepStatus":"COMPLETED","sequenceNo":1}',
        '',
        'event:reference',
        'data:{"id":11,"runId":5002,"conversationId":2001,"title":"Spring Boot SSE 最佳实践","url":"https://docs.spring.io"}',
        '',
        'event:artifact',
        'data:{"id":21,"runId":5002,"conversationId":2001,"artifactType":"docx","name":"search-report.docx","storagePath":"artifacts/2001/search-report.docx"}',
        '',
        'event:finish',
        'data:{"conversationId":2001,"content":"新的回答","title":"Default Demo Conversation"}',
        '',
        'event:done',
        'data:{"conversationId":2001}',
        '',
      ].join('\n'),
      refreshedMessageContent: '新的回答',
    });

    render(<ChatView isAuthenticated={true} onRequireLogin={vi.fn()} />);

    await screen.findByText('Default Demo Conversation');
    fireEvent.change(screen.getByPlaceholderText('输入指令以重构组件库或分析代码...'), {
      target: { value: '请搜索 Spring Boot SSE 最佳实践' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }));

    await waitFor(() => {
      expect(screen.getByText('新的回答')).toBeInTheDocument();
    });
  });
});

/**
 * 统一模拟聊天页会访问的后端接口。
 * @param overrides 覆盖默认消息与流文本。
 */
function mockWorkspaceFetch(overrides?: {
  messageContent?: string;
  refreshedMessageContent?: string;
  streamText?: string;
}) {
  const messageContent = overrides?.messageContent ?? '我来为您总结 Spring Boot SSE 最佳实践。';
  const refreshedMessageContent = overrides?.refreshedMessageContent ?? messageContent;
  const streamText = overrides?.streamText ?? 'event:done\ndata:{"conversationId":2001}\n\n';
  let messageRequestCount = 0;

  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url === '/api/chat/conversations') {
      return jsonResponse([
        {
          id: 2001,
          title: 'Default Demo Conversation',
          status: 'ACTIVE',
          lastMessageAt: '2026-05-15 00:36:58',
          lastRunId: 5002,
        },
      ]);
    }
    if (url === '/api/chat/conversations/2001/messages') {
      messageRequestCount += 1;
      return jsonResponse([
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
          content: messageRequestCount > 1 ? refreshedMessageContent : messageContent,
          status: 'COMPLETED',
          createdAt: '2026-05-15 00:37:11',
        },
      ]);
    }
    if (url === '/api/chat/conversations/2001/steps') {
      return jsonResponse([
        {
          id: 1,
          runId: 5002,
          stepType: 'search',
          stepTitle: '搜索资料',
          stepStatus: 'COMPLETED',
          sequenceNo: 1,
          content: '请搜索 Spring Boot SSE 最佳实践',
        },
      ]);
    }
    if (url === '/api/chat/conversations/2001/references') {
      return jsonResponse([
        {
          id: 11,
          runId: 5002,
          messageId: 101,
          conversationId: 2001,
          title: 'Spring Boot SSE 最佳实践',
          url: 'https://docs.spring.io',
          siteName: 'Spring',
          snippet: 'SSE 最佳实践摘要',
        },
      ]);
    }
    if (url === '/api/chat/conversations/2001/artifacts') {
      return jsonResponse([
        {
          id: 21,
          runId: 5002,
          messageId: 101,
          conversationId: 2001,
          artifactType: 'docx',
          name: 'search-report.docx',
          storagePath: 'artifacts/2001/search-report.docx',
          contentPreview: '搜索结果整理中',
        },
      ]);
    }
    if (url.includes('/api/chat/stream')) {
      return new Response(streamText, { status: 200 });
    }
    throw new Error(`Unhandled fetch: ${url}`);
  });
}

/**
 * 构造统一成功响应。
 * @param data 响应数据。
 * @returns fetch Response。
 */
function jsonResponse(data: unknown) {
  return new Response(
    JSON.stringify({
      success: true,
      code: 'OK',
      message: 'success',
      data,
    }),
    { status: 200 },
  );
}
