import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '@/api/adminChatApi';
import { Tasks } from '@/pages/Tasks';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    listConversations: vi.fn(),
  },
}));

describe('Tasks', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listConversations).mockResolvedValue({
      records: [
        {
          id: '2064220060822056960',
          title: '目标模式排查',
          createdBy: '1001',
          status: 'ACTIVE',
          statusLabel: '活跃',
          lastMessageAt: '2026-06-09T13:37:32',
          lastRunId: '2064220060855611392',
          createdAt: '2026-06-09T13:36:28',
          updatedAt: '2026-06-09T13:36:28',
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

  /**
   * 会话详情属于管理端站内路由，点击详情时必须走 React Router，避免浏览器整页刷新。
   */
  it('opens conversation detail through client-side routing', async () => {
    renderTasksPage('/tasks');

    expect(await screen.findByText('目标模式排查')).toBeInTheDocument();

    fireEvent.click(screen.getByText('查看详情'));

    expect(await screen.findByTestId('task-detail-route')).toHaveTextContent(
      '当前会话 2064220060822056960',
    );
  });
});

/**
 * 使用真实路由容器渲染会话管理页，覆盖列表页到详情页的站内跳转。
 * @param initialPath 初始路径。
 */
function renderTasksPage(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/tasks" element={<Tasks />} />
        <Route path="/tasks/:id" element={<TaskDetailRouteProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

/**
 * 测试探针用于确认点击后进入的是 React Router 管理的详情路由。
 */
function TaskDetailRouteProbe() {
  const { id } = useParams();

  return <div data-testid="task-detail-route">当前会话 {id}</div>;
}
