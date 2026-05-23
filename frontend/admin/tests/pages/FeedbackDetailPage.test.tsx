import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { AdminChatApi } from '@/api/adminChatApi';
import { FeedbackDetailPage } from '@/pages/FeedbackDetailPage';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    getFeedbackDetail: vi.fn(),
    listTools: vi.fn(),
    createTool: vi.fn(),
    updateTool: vi.fn(),
    deleteTool: vi.fn(),
  },
}));

describe('FeedbackDetailPage', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.getFeedbackDetail).mockResolvedValue({
      id: 9001,
      messageId: 102,
      conversationId: 2001,
      conversationTitle: '差旅报销说明',
      messageRole: 'ASSISTANT',
      messageContent: '请准备以下报销材料',
      userId: 1002,
      vote: 1,
      reason: 'helpful',
      comment: 'good',
      createdAt: '2026-05-17T10:00:00',
      updatedAt: '2026-05-17T10:01:00',
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('loads detail and provides link to references page', async () => {
    render(
      <MemoryRouter initialEntries={['/feedbacks/9001']}>
        <Routes>
          <Route path="/feedbacks/:feedbackId" element={<FeedbackDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: '反馈详情 #9001' })).toBeInTheDocument();
    expect(screen.getByText('差旅报销说明')).toBeInTheDocument();
    expect(screen.getByText('请准备以下报销材料')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '查看引用来源' })).toHaveAttribute('href', '/feedbacks/9001/references');
  });
});
