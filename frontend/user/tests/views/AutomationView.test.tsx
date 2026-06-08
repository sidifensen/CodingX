import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import AutomationView from '@/views/AutomationView';
import { AutomationTask } from '@/views/automation/types';

const listAutomationTasksMock = vi.fn();
const createAutomationTaskMock = vi.fn();
const getSessionMock = vi.fn();

vi.mock('@/api/automationApi', () => ({
  AutomationApi: {
    listTasks: (...args: unknown[]) => listAutomationTasksMock(...args),
    createTask: (...args: unknown[]) => createAutomationTaskMock(...args),
  },
}));

vi.mock('@/utils/authStorage', () => ({
  AuthStorage: {
    getSession: () => getSessionMock(),
  },
}));

const createdTask: AutomationTask = {
  id: '91001',
  name: '每日项目总结',
  prompt: '总结项目状态并给出风险项',
  sourceType: 'MANUAL',
  sourceConversationId: null,
  scheduleType: 'DAILY',
  scheduleTime: '18:11',
  scheduleDayOfWeek: null,
  onceExecuteAt: null,
  nextRunAt: '2026-06-09T18:11:00',
  lastRunAt: null,
  lastRunStatus: 'PENDING',
  enabled: true,
  workspaceId: null,
};

describe('AutomationView', () => {
  beforeEach(() => {
    getSessionMock.mockReturnValue({ token: 'token-123' });
    listAutomationTasksMock.mockResolvedValue([]);
    createAutomationTaskMock.mockResolvedValue(createdTask);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  /**
   * 自动化页应从后端加载当前用户任务；没有任务时展示参考图风格的空态和创建入口。
   */
  it('loads empty automation tasks and opens the custom create dialog', async () => {
    render(<AutomationView />);

    expect(await screen.findByRole('heading', { name: '定时任务' })).toBeInTheDocument();
    expect(listAutomationTasksMock).toHaveBeenCalledWith('token-123');
    expect(screen.getByText('暂无定时任务')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '新建定时任务' }));

    const dialog = screen.getByRole('dialog', { name: '新建定时任务' });
    expect(within(dialog).getByLabelText('任务名称')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('任务需求')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('计划类型')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('执行时间')).toBeInTheDocument();
  });

  /**
   * 创建表单必须在必填字段为空时阻止提交，避免把无效请求推给后端。
   */
  it('keeps save disabled until required fields are provided', async () => {
    render(<AutomationView />);

    await screen.findByText('暂无定时任务');
    fireEvent.click(screen.getByRole('button', { name: '新建定时任务' }));

    const dialog = screen.getByRole('dialog', { name: '新建定时任务' });
    const saveButton = within(dialog).getByRole('button', { name: '创建任务' });
    expect(saveButton).toBeDisabled();

    fireEvent.change(within(dialog).getByLabelText('任务名称'), {
      target: { value: '每日项目总结' },
    });
    fireEvent.change(within(dialog).getByLabelText('任务需求'), {
      target: { value: '总结项目状态并给出风险项' },
    });
    fireEvent.change(within(dialog).getByLabelText('执行时间'), {
      target: { value: '18:11' },
    });

    expect(saveButton).toBeEnabled();
  });

  /**
   * 手动创建成功后应通过统一 API 保存任务，关闭弹窗并用最新列表刷新页面。
   */
  it('creates a manual automation task and refreshes the list', async () => {
    listAutomationTasksMock.mockResolvedValueOnce([]).mockResolvedValueOnce([createdTask]);

    render(<AutomationView />);

    await screen.findByText('暂无定时任务');
    fireEvent.click(screen.getByRole('button', { name: '新建定时任务' }));
    const dialog = screen.getByRole('dialog', { name: '新建定时任务' });

    fireEvent.change(within(dialog).getByLabelText('任务名称'), {
      target: { value: '每日项目总结' },
    });
    fireEvent.change(within(dialog).getByLabelText('任务需求'), {
      target: { value: '总结项目状态并给出风险项' },
    });
    fireEvent.change(within(dialog).getByLabelText('执行时间'), {
      target: { value: '18:11' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '创建任务' }));

    await waitFor(() => {
      expect(createAutomationTaskMock).toHaveBeenCalledWith('token-123', {
        name: '每日项目总结',
        prompt: '总结项目状态并给出风险项',
        scheduleType: 'DAILY',
        scheduleTime: '18:11',
        scheduleDayOfWeek: null,
        onceExecuteAt: null,
        workspaceId: null,
      });
    });

    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: '新建定时任务' })).not.toBeInTheDocument();
    });
    expect(screen.getByRole('heading', { name: '每日项目总结' })).toBeInTheDocument();
    expect(screen.getByText('每天 18:11')).toBeInTheDocument();
    expect(screen.getByText('总结项目状态并给出风险项')).toBeInTheDocument();
  });

  /**
   * 后端返回业务错误时，页面应直接展示后端中文 message，不在前端重写语义。
   */
  it('shows backend error message when creation fails', async () => {
    createAutomationTaskMock.mockRejectedValueOnce(new Error('执行时间格式不正确'));

    render(<AutomationView />);

    await screen.findByText('暂无定时任务');
    fireEvent.click(screen.getByRole('button', { name: '新建定时任务' }));
    const dialog = screen.getByRole('dialog', { name: '新建定时任务' });

    fireEvent.change(within(dialog).getByLabelText('任务名称'), {
      target: { value: '每日项目总结' },
    });
    fireEvent.change(within(dialog).getByLabelText('任务需求'), {
      target: { value: '总结项目状态并给出风险项' },
    });
    fireEvent.change(within(dialog).getByLabelText('执行时间'), {
      target: { value: '18:11' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '创建任务' }));

    expect(await screen.findByText('执行时间格式不正确')).toBeInTheDocument();
  });
});
