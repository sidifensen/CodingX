import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

import { AdminChatApi } from '@/api/adminChatApi';
import { FeedbackPage } from '@/pages/FeedbackPage';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    listFeedbacks: vi.fn(),
    listTools: vi.fn(),
    createTool: vi.fn(),
    updateTool: vi.fn(),
    deleteTool: vi.fn(),
  },
}));

describe('FeedbackPage', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listFeedbacks).mockResolvedValue({
      records: [
        {
          id: 9001,
          messageId: 102,
          conversationId: 2001,
          vote: 1,
          reason: 'helpful',
          comment: 'good',
          createdAt: '2026-05-17T10:00:00',
          updatedAt: '2026-05-17T10:01:00',
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

  it('renders feedback rows in an Ant Design table and allows jumping to detail page', async () => {
    const { container } = render(
      <MemoryRouter>
        <FeedbackPage />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: '反馈管理' })).toBeInTheDocument();
    expect(screen.getByText('helpful')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '查看 9001' })).toHaveAttribute('href', '/feedbacks/9001');
    expect(screen.getByText('第 1 / 1 页，共 1 条')).toBeInTheDocument();
    expect(container.querySelector('.ant-table')).toBeInTheDocument();
    expect(container.querySelector('.ant-form')).toBeInTheDocument();
    expect(container.querySelector('.ant-select')).toBeInTheDocument();
  });

  it('shows Ant Design table loading state while feedback table is loading', async () => {
    let resolveListFeedbacks: ((value: any) => void) | undefined;
    vi.mocked(AdminChatApi.listFeedbacks).mockImplementationOnce(
      () => new Promise((resolve) => {
        resolveListFeedbacks = resolve;
      }) as any,
    );

    render(
      <MemoryRouter>
        <FeedbackPage />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: '反馈管理' })).toBeInTheDocument();
    await waitFor(() => {
      expect(document.querySelector('.ant-spin')).toBeInTheDocument();
    });

    resolveListFeedbacks?.({
      records: [
        {
          id: 9001,
          messageId: 102,
          conversationId: 2001,
          vote: 1,
          reason: 'helpful',
          comment: 'good',
          createdAt: '2026-05-17T10:00:00',
          updatedAt: '2026-05-17T10:01:00',
        },
      ],
      total: 1,
      size: 10,
      current: 1,
      pages: 1,
    });
    expect(await screen.findByText('helpful')).toBeInTheDocument();
  });

  it('submits vote filter and refreshes data', async () => {
    render(
      <MemoryRouter>
        <FeedbackPage />
      </MemoryRouter>,
    );
    await screen.findByRole('heading', { name: '反馈管理' });

    fireEvent.mouseDown(screen.getByRole('combobox', { name: '投票筛选' }));
    fireEvent.click(await screen.findByTitle('仅点踩'));
    fireEvent.click(screen.getByRole('button', { name: /筛\s*选/ }));

    await waitFor(() => {
      expect(AdminChatApi.listFeedbacks).toHaveBeenLastCalledWith(
        expect.objectContaining({ vote: -1 }),
      );
    });
  });

  it('keeps vote filter staged before applying it from the first page', async () => {
    vi.mocked(AdminChatApi.listFeedbacks).mockImplementation(async (params: any) => ({
      records: [
        {
          id: Number(params.current) === 2 ? 9002 : 9001,
          messageId: 102,
          conversationId: 2001,
          vote: 1,
          reason: 'helpful',
          comment: 'good',
          createdAt: '2026-05-17T10:00:00',
          updatedAt: '2026-05-17T10:01:00',
        },
      ],
      total: params.vote === 1 ? 1 : 11,
      size: 10,
      current: params.current,
      pages: params.vote === 1 ? 1 : 2,
    }));

    render(
      <MemoryRouter>
        <FeedbackPage />
      </MemoryRouter>,
    );
    await screen.findByRole('heading', { name: '反馈管理' });

    fireEvent.click(await screen.findByTitle('2'));
    await waitFor(() => {
      expect(AdminChatApi.listFeedbacks).toHaveBeenLastCalledWith(
        expect.objectContaining({ current: 2, vote: null }),
      );
    });
    const callCountAfterPageChange = vi.mocked(AdminChatApi.listFeedbacks).mock.calls.length;

    fireEvent.mouseDown(screen.getByRole('combobox', { name: '投票筛选' }));
    fireEvent.click(await screen.findByTitle('仅点赞'));
    // 投票下拉只是待应用条件，避免在第 2 页直接请求导致分页显示成“第 2 / 1 页”。
    await new Promise((resolve) => {
      window.setTimeout(resolve, 50);
    });
    expect(AdminChatApi.listFeedbacks).toHaveBeenCalledTimes(callCountAfterPageChange);

    fireEvent.click(screen.getByRole('button', { name: /筛\s*选/ }));
    await waitFor(() => {
      expect(AdminChatApi.listFeedbacks).toHaveBeenLastCalledWith(
        expect.objectContaining({ current: 1, vote: 1 }),
      );
    });
    expect(screen.getByText('第 1 / 1 页，共 1 条')).toBeInTheDocument();
  });

  it('keeps a legacy uncounted feedback response on one page', async () => {
    vi.mocked(AdminChatApi.listFeedbacks).mockResolvedValueOnce({
      records: Array.from({ length: 12 }, (_, index) => ({
        id: 9100 + index,
        messageId: 102 + index,
        conversationId: 2001,
        vote: 1,
        reason: `reason-${index}`,
        comment: 'good',
        createdAt: '2026-05-17T10:00:00',
        updatedAt: '2026-05-17T10:01:00',
      })),
      total: 0,
      size: 10,
      current: 1,
      pages: 0,
    });

    render(
      <MemoryRouter>
        <FeedbackPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText('reason-11')).toBeInTheDocument();
    expect(screen.getByText('第 1 / 1 页，共 12 条')).toBeInTheDocument();
    expect(document.querySelector('.ant-pagination-item-2')).not.toBeInTheDocument();
  });

  it('shows backend error message when feedback list request fails', async () => {
    vi.mocked(AdminChatApi.listFeedbacks).mockRejectedValueOnce(new Error('后端错误'));

    const { container } = render(
      <MemoryRouter>
        <FeedbackPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText('后端错误')).toBeInTheDocument();
    expect(container.querySelector('.ant-alert-error')).toBeInTheDocument();
  });
});
