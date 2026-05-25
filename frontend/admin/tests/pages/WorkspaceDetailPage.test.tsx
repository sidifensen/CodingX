import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '@/api/adminChatApi';
import { WorkspaceDetailPage } from '@/pages/WorkspaceDetailPage';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    listWorkspaceConversations: vi.fn(),
  },
}));

describe('WorkspaceDetailPage', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listWorkspaceConversations).mockResolvedValue({
      records: [
        {
          id: 2001,
          title: '本地项目会话',
          createdBy: 1002,
          status: 'ACTIVE',
          statusLabel: '活跃',
          lastMessageAt: '2026-05-25T10:02:00',
          lastRunId: 5001,
          createdAt: '2026-05-25T10:00:00',
          updatedAt: '2026-05-25T10:05:00',
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
   * 详情页应展示当前工作空间下的会话，并提供现有会话详情入口。
   */
  it('renders workspace conversations and task detail links', async () => {
    renderWorkspaceDetailPage('/workspaces/3001');

    expect(await screen.findByRole('heading', { name: '工作空间 #3001' })).toBeInTheDocument();
    expect(screen.getByText('本地项目会话')).toBeInTheDocument();
    expect(screen.getByText('活跃')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /查看详情/ })).toHaveAttribute('href', '/tasks/2001');
  });

  /**
   * 关键字搜索应重置分页并传给工作空间会话接口。
   */
  it('submits keyword search', async () => {
    renderWorkspaceDetailPage('/workspaces/3001');
    await screen.findByText('本地项目会话');

    fireEvent.change(screen.getByPlaceholderText('搜索会话标题或 ID'), {
      target: { value: '报销' },
    });
    fireEvent.click(screen.getByRole('button', { name: '查询' }));

    await waitFor(() => {
      expect(AdminChatApi.listWorkspaceConversations).toHaveBeenLastCalledWith(
        '3001',
        expect.objectContaining({ keyword: '报销' }),
      );
    });
  });

  /**
   * 雪花 ID 超过 JS 安全整数范围，详情页请求必须保留路由里的原始字符串。
   */
  it('preserves large snowflake workspace id when requesting conversations', async () => {
    renderWorkspaceDetailPage('/workspaces/2057422673730453504');

    await screen.findByText('本地项目会话');

    expect(AdminChatApi.listWorkspaceConversations).toHaveBeenCalledWith(
      '2057422673730453504',
      expect.objectContaining({ current: 1, size: 10 }),
    );
  });

  /**
   * 首屏加载时应展示骨架行，避免详情页表格区域空白闪烁。
   */
  it('shows skeleton rows while loading', async () => {
    let resolveList: ((value: Awaited<ReturnType<typeof AdminChatApi.listWorkspaceConversations>>) => void) | undefined;
    vi.mocked(AdminChatApi.listWorkspaceConversations).mockImplementationOnce(
      () => new Promise((resolve) => {
        resolveList = resolve;
      }),
    );

    renderWorkspaceDetailPage('/workspaces/3001');

    expect(await screen.findByRole('heading', { name: '工作空间 #3001' })).toBeInTheDocument();
    expect(screen.getAllByTestId('workspace-conversation-loading-skeleton-row')).toHaveLength(10);

    resolveList?.({
      records: [],
      total: 0,
      size: 10,
      current: 1,
      pages: 1,
    });
    expect(await screen.findByText('当前工作空间暂无会话')).toBeInTheDocument();
  });

  /**
   * 接口异常时应展示后端返回的错误语义。
   */
  it('shows api error message', async () => {
    vi.mocked(AdminChatApi.listWorkspaceConversations).mockRejectedValueOnce(new Error('后端返回错误'));

    renderWorkspaceDetailPage('/workspaces/3001');

    expect(await screen.findByText('后端返回错误')).toBeInTheDocument();
  });
});

/**
 * 使用真实路由参数渲染详情页，覆盖 `/workspaces/:workspaceId` 的参数解析。
 * @param initialPath 初始路径。
 */
function renderWorkspaceDetailPage(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/workspaces/:workspaceId" element={<WorkspaceDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}
