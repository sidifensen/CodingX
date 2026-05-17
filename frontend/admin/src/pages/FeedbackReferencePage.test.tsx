import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { AdminChatApi } from '../api/adminChatApi';
import { FeedbackReferencePage } from './FeedbackReferencePage';

vi.mock('../api/adminChatApi', () => ({
  AdminChatApi: {
    listFeedbackReferences: vi.fn(),
  },
}));

describe('FeedbackReferencePage', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listFeedbackReferences).mockResolvedValue([
      {
        id: 7001,
        runId: 5002,
        messageId: 102,
        conversationId: 2001,
        sourceType: 'web',
        title: '报销制度说明',
        url: 'https://example.com/policy',
        siteName: '内部门户',
        snippet: '差旅报销需提供票据',
        rankNo: 1,
        createdAt: '2026-05-17T09:55:00',
      },
    ]);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('loads references from feedback id and renders source rows', async () => {
    render(
      <MemoryRouter initialEntries={['/feedbacks/9001/references']}>
        <Routes>
          <Route path="/feedbacks/:feedbackId/references" element={<FeedbackReferencePage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: '反馈引用来源' })).toBeInTheDocument();
    expect(screen.getByText('报销制度说明')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '打开来源 报销制度说明' })).toHaveAttribute('href', 'https://example.com/policy');
    expect(screen.getByRole('link', { name: '返回反馈详情' })).toHaveAttribute('href', '/feedbacks/9001');
  });
});

