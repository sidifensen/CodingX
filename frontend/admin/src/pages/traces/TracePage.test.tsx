import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

import { AdminChatApi } from '../../api/adminChatApi';
import { TracePage } from './TracePage';

class MockResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

vi.mock('../../api/adminChatApi', () => ({
  AdminChatApi: {
    listTraces: vi.fn(),
    getTrace: vi.fn(),
  },
}));

describe('TracePage', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', MockResizeObserver);
    vi.mocked(AdminChatApi.listTraces).mockResolvedValue({
      records: [
        {
          traceId: 'trace-1',
          traceName: 'chat-entry',
          conversationId: '1001',
          taskId: '2001',
          userId: '3001',
          username: 'admin',
          status: 'SUCCESS',
          durationMs: 6124,
          startedAt: '2026-05-16T18:00:00',
        },
      ],
      total: 1,
      size: 10,
      current: 1,
      pages: 1,
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  it('renders list header, metrics and run table columns', async () => {
    render(
      <MemoryRouter>
        <TracePage />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: '链路追踪' })).toBeInTheDocument();
    expect(screen.getByText('运行列表')).toBeInTheDocument();
    expect(screen.getByText('Trace Name')).toBeInTheDocument();
    expect(screen.getByText('Trace Id')).toBeInTheDocument();
    expect(screen.getByText('会话ID / TaskID')).toBeInTheDocument();
    expect(screen.getByText('执行时间')).toBeInTheDocument();
    expect(screen.getByText('chat-entry')).toBeInTheDocument();
    expect(AdminChatApi.listTraces).toHaveBeenCalled();
  });

  it('submits traceId filter on query button click', async () => {
    render(
      <MemoryRouter>
        <TracePage />
      </MemoryRouter>,
    );

    await screen.findByRole('heading', { name: '链路追踪' });

    const input = screen.getByPlaceholderText('搜索 Trace Id');
    fireEvent.change(input, { target: { value: 'trace-xyz' } });
    fireEvent.click(screen.getByRole('button', { name: '查询' }));

    await waitFor(() =>
      expect(AdminChatApi.listTraces).toHaveBeenLastCalledWith(
        expect.objectContaining({ traceId: 'trace-xyz' }),
      ),
    );
  });

  it('renders advanced pager and supports goto page interaction', async () => {
    vi.mocked(AdminChatApi.listTraces)
      .mockResolvedValueOnce({
        records: [
          {
            traceId: 'trace-1',
            traceName: 'chat-entry',
            conversationId: '1001',
            taskId: '2001',
            userId: '3001',
            username: 'admin',
            status: 'SUCCESS',
            durationMs: 6124,
            startedAt: '2026-05-16T18:00:00',
          },
        ],
        total: 77,
        size: 10,
        current: 1,
        pages: 8,
      } as any)
      .mockResolvedValueOnce({
        records: [
          {
            traceId: 'trace-6',
            traceName: 'chat-entry',
            conversationId: '1006',
            taskId: '2006',
            userId: '3006',
            username: 'admin',
            status: 'SUCCESS',
            durationMs: 5000,
            startedAt: '2026-05-16T19:00:00',
          },
        ],
        total: 77,
        size: 10,
        current: 6,
        pages: 8,
      } as any);

    render(
      <MemoryRouter>
        <TracePage />
      </MemoryRouter>,
    );

    await screen.findByRole('heading', { name: '链路追踪' });
    expect(screen.getByText('第 1 / 8 页，共 77 条')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '第 1 页' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '第 2 页' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '第 3 页' })).toBeInTheDocument();
    expect(screen.getByText('...')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '第 8 页' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('前往页码'), { target: { value: '6' } });
    fireEvent.click(screen.getByRole('button', { name: '前往' }));

    await waitFor(() =>
      expect(AdminChatApi.listTraces).toHaveBeenLastCalledWith(
        expect.objectContaining({ current: 6, size: 10 }),
      ),
    );
  });

  it('keeps table content height with loading overlay when switching pages', async () => {
    vi.mocked(AdminChatApi.listTraces)
      .mockResolvedValueOnce({
        records: [
          {
            traceId: 'trace-1',
            traceName: 'chat-entry',
            conversationId: '1001',
            taskId: '2001',
            userId: '3001',
            username: 'admin',
            status: 'SUCCESS',
            durationMs: 6124,
            startedAt: '2026-05-16T18:00:00',
          },
        ],
        total: 77,
        size: 10,
        current: 1,
        pages: 8,
      } as any)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) =>
            setTimeout(
              () =>
                resolve({
                  records: [
                    {
                      traceId: 'trace-2',
                      traceName: 'chat-entry',
                      conversationId: '1002',
                      taskId: '2002',
                      userId: '3002',
                      username: 'admin',
                      status: 'SUCCESS',
                      durationMs: 5123,
                      startedAt: '2026-05-16T19:00:00',
                    },
                  ],
                  total: 77,
                  size: 10,
                  current: 2,
                  pages: 8,
                }),
              20,
            ),
          ),
      );

    render(
      <MemoryRouter>
        <TracePage />
      </MemoryRouter>,
    );

    await screen.findByText('chat-entry');
    fireEvent.click(screen.getByRole('button', { name: '第 2 页' }));
    expect(await screen.findByText('加载中...')).toBeInTheDocument();
    expect(screen.getByText('chat-entry')).toBeInTheDocument();
  });

  it('uses full-expand mode when viewport has enough space', async () => {
    render(
      <MemoryRouter>
        <TracePage />
      </MemoryRouter>,
    );

    await screen.findByRole('heading', { name: '链路追踪' });
    const scrollContainer = screen.getByTestId('trace-runs-scroll');
    expect(scrollContainer).toHaveClass('overflow-x-auto');
    expect(scrollContainer).toHaveAttribute('data-scroll-mode', 'full-expand');
  });
});
