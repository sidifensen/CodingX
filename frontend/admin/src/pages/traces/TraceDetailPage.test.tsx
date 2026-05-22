import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { AdminChatApi } from '../../api/adminChatApi';
import { TraceDetailPage } from './TraceDetailPage';

vi.mock('../../api/adminChatApi', () => ({
  AdminChatApi: {
    listTraces: vi.fn(),
    getTrace: vi.fn(),
  },
}));

describe('TraceDetailPage', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.getTrace).mockResolvedValue({
      traceRun: {
        traceId: 'trace-1',
        traceName: 'rag-stream-chat',
        conversationId: '1001',
        taskId: '2001',
        userId: '3001',
        username: 'admin',
        status: 'SUCCESS',
        durationMs: 6820,
        startedAt: '2026-05-16T18:00:00',
      },
      nodes: [
        {
          id: '1',
          traceId: 'trace-1',
          nodeId: 'n1',
          parentNodeId: 'root-node',
          depth: 1,
          nodeType: 'INTENT',
          nodeName: 'intent-resolve',
          status: 'SUCCESS',
          durationMs: 2650,
          className: 'com.codingx.chat.application.service.conversation.ConversationIntentResolver',
          methodName: 'resolve',
          extraDataJson: '{"intent":"chat","confidence":0.99}',
          startedAt: '2026-05-16T18:00:01',
          finishedAt: '2026-05-16T18:00:03',
        },
      ],
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders trace detail header, metric bar and waterfall section', async () => {
    render(
      <MemoryRouter initialEntries={['/traces/trace-1']}>
        <Routes>
          <Route path="/traces/:traceId" element={<TraceDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: 'rag-stream-chat' })).toBeInTheDocument();
    expect(screen.getByText('执行时序')).toBeInTheDocument();
    // 页面里“节点”会同时出现在指标区和表头，这里断言至少渲染出两个以避免歧义查询。
    expect(screen.getAllByText('节点')).toHaveLength(2);
    expect(screen.getByText('成功')).toBeInTheDocument();
    expect(screen.getByText('平均耗时')).toBeInTheDocument();
    expect(screen.getByText('意图识别')).toBeInTheDocument();
    expect(AdminChatApi.getTrace).toHaveBeenCalledWith('trace-1');
  });

  it('opens node detail panel after clicking a timeline row', async () => {
    render(
      <MemoryRouter initialEntries={['/traces/trace-1']}>
        <Routes>
          <Route path="/traces/:traceId" element={<TraceDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(await screen.findByText('意图识别'));

    expect(await screen.findByText('节点详情')).toBeInTheDocument();
    expect(screen.getByText('Node Id')).toBeInTheDocument();
    expect(screen.getByText('n1')).toBeInTheDocument();
    expect(screen.getByText('extraDataJson')).toBeInTheDocument();
    const rowNodeName = screen.getAllByText('意图识别')[0];
    expect(within(rowNodeName.closest('div') as HTMLElement).getByTestId('trace-tree-branch')).toBeInTheDocument();
  });
});
