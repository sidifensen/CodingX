import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '@/api/adminChatApi';
import { WorkspacePage } from '@/pages/WorkspacePage';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    listWorkspaces: vi.fn(),
  },
}));

describe('WorkspacePage', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listWorkspaces).mockResolvedValue({
      records: [
        {
          id: 3001,
          name: '本地项目',
          repositoryUrl: 'https://example.com/codingx.git',
          branchName: 'main',
          workingDirectory: 'D:/code/CodingX',
          runtimeTarget: 'local',
          runtimeTargetLabel: '本地',
          createdBy: 1001,
          conversationCount: 2,
          createdAt: '2026-05-25T00:40:00',
          updatedAt: '2026-05-25T00:45:00',
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
   * 页面应展示工作空间核心字段、统计卡片和分页摘要。
   */
  it('renders workspace rows and summary', async () => {
    render(<WorkspacePage />);

    expect(await screen.findByRole('heading', { name: '工作空间管理' })).toBeInTheDocument();
    expect(screen.getByText('本地项目')).toBeInTheDocument();
    expect(screen.getAllByText('本地').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('https://example.com/codingx.git')).toBeInTheDocument();
    expect(screen.getByText('D:/code/CodingX')).toBeInTheDocument();
    expect(screen.getByText('第 1 / 1 页，共 1 条')).toBeInTheDocument();
  });

  /**
   * 运行目标筛选应重置到首页并传递 local 参数。
   */
  it('submits runtime target filter', async () => {
    render(<WorkspacePage />);
    await screen.findByRole('heading', { name: '工作空间管理' });

    fireEvent.change(screen.getByLabelText('运行目标'), {
      target: { value: 'local' },
    });
    fireEvent.click(screen.getByRole('button', { name: '筛选' }));

    await waitFor(() => {
      expect(AdminChatApi.listWorkspaces).toHaveBeenLastCalledWith(
        expect.objectContaining({ runtimeTarget: 'local' }),
      );
    });
  });

  /**
   * 首屏加载时应展示表格骨架行，避免空白闪烁。
   */
  it('shows skeleton rows while loading', async () => {
    let resolveListWorkspaces: ((value: Awaited<ReturnType<typeof AdminChatApi.listWorkspaces>>) => void) | undefined;
    vi.mocked(AdminChatApi.listWorkspaces).mockImplementationOnce(
      () => new Promise((resolve) => {
        resolveListWorkspaces = resolve;
      }),
    );

    render(<WorkspacePage />);

    expect(await screen.findByRole('heading', { name: '工作空间管理' })).toBeInTheDocument();
    expect(screen.getAllByTestId('workspace-loading-skeleton-row')).toHaveLength(10);

    resolveListWorkspaces?.({
      records: [],
      total: 0,
      size: 10,
      current: 1,
      pages: 1,
    });
    expect(await screen.findByText('暂无工作空间')).toBeInTheDocument();
  });

  /**
   * 接口异常时应展示统一错误文案。
   */
  it('shows api error message', async () => {
    vi.mocked(AdminChatApi.listWorkspaces).mockRejectedValueOnce(new Error('后端返回错误'));

    render(<WorkspacePage />);

    expect(await screen.findByText('后端返回错误')).toBeInTheDocument();
  });
});
