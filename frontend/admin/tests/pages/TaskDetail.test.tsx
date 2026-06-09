import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '@/api/adminChatApi';
import { TaskDetail } from '@/pages/TaskDetail';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    getConversationDetail: vi.fn(),
  },
}));

describe('TaskDetail', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.getConversationDetail).mockResolvedValue(buildConversationDetailFixture());
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  /**
   * 有目标时先展示折叠入口，管理员展开后再查看三表明细。
   */
  it('renders goal records collapsed by default and expands details on demand', async () => {
    renderTaskDetailPage('/tasks/2001');

    expect(await screen.findByRole('heading', { name: '目标记录' })).toBeInTheDocument();
    expect(screen.getByText('目标 1')).toBeInTheDocument();
    expect(screen.getByText('步骤 1')).toBeInTheDocument();
    expect(screen.getByText('事件 1')).toBeInTheDocument();
    expect(screen.queryByText('修复聊天目标展示')).not.toBeInTheDocument();
    expect(screen.queryByText('管理端展示目标三表')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '展开目标记录' }));

    expect(screen.getByText('修复聊天目标展示')).toBeInTheDocument();
    expect(screen.getByText('管理端展示目标三表')).toBeInTheDocument();
    expect(screen.getByText('补充管理端测试')).toBeInTheDocument();
    expect(screen.getByText('先证明目标三表还未返回')).toBeInTheDocument();
    expect(screen.getByText('GOAL_UPDATED')).toBeInTheDocument();
    expect(screen.getByText('{"goal":{"title":"修复聊天目标展示"}}')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /编辑|删除|重置|完成|取消/ })).not.toBeInTheDocument();
  });

  /**
   * 没有目标时不渲染目标记录区，避免空状态挤占会话消息排查空间。
   */
  it('hides goal section when conversation has no goals', async () => {
    vi.mocked(AdminChatApi.getConversationDetail).mockResolvedValueOnce({
      ...buildConversationDetailFixture(),
      goals: [],
    });

    renderTaskDetailPage('/tasks/2001');

    expect(await screen.findByText('怎么报销？')).toBeInTheDocument();
    expect(screen.queryByTestId('admin-conversation-goals-section')).not.toBeInTheDocument();
    expect(screen.queryByText('当前会话暂无目标记录')).not.toBeInTheDocument();
  });

  /**
   * 路由中的雪花 ID 应原样透传给接口，避免转换为 number 后丢失精度。
   */
  it('preserves large snowflake conversation id when loading detail', async () => {
    renderTaskDetailPage('/tasks/2057422673730453504');

    await screen.findByRole('button', { name: '展开目标记录' });

    expect(AdminChatApi.getConversationDetail).toHaveBeenCalledWith('2057422673730453504');
  });
});

/**
 * 使用真实路由参数渲染会话详情页。
 * @param initialPath 初始路径。
 */
function renderTaskDetailPage(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/tasks/:id" element={<TaskDetail />} />
      </Routes>
    </MemoryRouter>,
  );
}

/**
 * 构造包含目标、步骤、事件和消息的会话详情响应。
 */
function buildConversationDetailFixture() {
  return {
    id: 2001,
    title: '目标模式排查',
    createdBy: 1002,
    status: 'ACTIVE',
    statusLabel: '活跃',
    lastMessageAt: '2026-06-09T10:02:00',
    lastRunId: 5001,
    createdAt: '2026-06-09T10:00:00',
    updatedAt: '2026-06-09T10:05:00',
    messages: [
      {
        id: 3001,
        conversationId: 2001,
        runId: 6001,
        role: 'USER',
        content: '怎么报销？',
        status: 'COMPLETED',
        createdAt: '2026-06-09T10:02:00',
        updatedAt: '2026-06-09T10:03:00',
        deleted: 0,
        attachments: [],
        skillCodes: [],
      },
    ],
    goals: [
      {
        id: '7001',
        conversationId: '2001',
        userId: '1002',
        goalKey: 'default',
        title: '修复聊天目标展示',
        description: '管理端展示目标三表',
        status: 'ACTIVE',
        progressSummary: '已进入实现阶段',
        createdRunId: '5001',
        updatedRunId: '5002',
        createdAt: '2026-06-09T10:01:00',
        updatedAt: '2026-06-09T10:04:00',
        completedAt: undefined,
        steps: [
          {
            id: '8001',
            goalId: '7001',
            stepKey: 'step-1',
            title: '补充管理端测试',
            status: 'COMPLETED',
            sortNo: 0,
            detail: '先证明目标三表还未返回',
            startedAt: '2026-06-09T10:02:00',
            completedAt: '2026-06-09T10:03:00',
            updatedAt: '2026-06-09T10:03:00',
          },
        ],
        events: [
          {
            id: '9001',
            goalId: '7001',
            conversationId: '2001',
            runId: '5002',
            eventType: 'GOAL_UPDATED',
            payloadJson: '{"goal":{"title":"修复聊天目标展示"}}',
            createdAt: '2026-06-09T10:04:00',
          },
        ],
      },
    ],
  };
}
