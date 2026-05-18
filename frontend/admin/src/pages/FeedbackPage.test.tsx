import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

import { AdminChatApi } from '../api/adminChatApi';
import { FeedbackPage } from './FeedbackPage';

vi.mock('../api/adminChatApi', () => ({
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

  it('renders feedback rows and allows jumping to detail page', async () => {
    render(
      <MemoryRouter>
        <FeedbackPage />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: '反馈管理' })).toBeInTheDocument();
    expect(screen.getByText('helpful')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '查看 9001' })).toHaveAttribute('href', '/feedbacks/9001');
    expect(screen.getByText('第 1 / 1 页，共 1 条')).toBeInTheDocument();
  });

  it('submits vote filter and refreshes data', async () => {
    render(
      <MemoryRouter>
        <FeedbackPage />
      </MemoryRouter>,
    );
    await screen.findByRole('heading', { name: '反馈管理' });

    fireEvent.change(screen.getByLabelText('投票筛选'), {
      target: { value: '-1' },
    });
    fireEvent.click(screen.getByRole('button', { name: '筛选' }));

    await waitFor(() => {
      expect(AdminChatApi.listFeedbacks).toHaveBeenLastCalledWith(
        expect.objectContaining({ vote: -1 }),
      );
    });
  });
});
