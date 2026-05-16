import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

import { AdminChatApi } from '../../api/adminChatApi';
import { TracePage } from './TracePage';

vi.mock('../../api/adminChatApi', () => ({
  AdminChatApi: {
    listTraces: vi.fn(),
    getTrace: vi.fn(),
  },
}));

describe('TracePage', () => {
  beforeEach(() => {
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
});
