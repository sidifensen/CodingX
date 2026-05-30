import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '@/api/adminChatApi';
import { ToolsPage } from '@/pages/ToolsPage';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    listTools: vi.fn(),
    createTool: vi.fn(),
    updateTool: vi.fn(),
    deleteTool: vi.fn(),
    listToolHealthViews: vi.fn(),
    pingTool: vi.fn(),
    invokeTool: vi.fn(),
  },
}));

const toolFixture = [
  {
    id: 9101,
    toolCode: 'shell_command',
    displayName: 'Shell 命令执行',
    description: '在当前工作区执行终端命令',
    category: '终端',
    sourceType: 'codex-cli',
    enabled: 1,
    sortNo: 1,
  },
  {
    id: 9102,
    toolCode: 'apply_patch',
    displayName: '补丁编辑',
    description: '通过补丁语法修改本地文件',
    category: '代码编辑',
    sourceType: 'codex-cli',
    enabled: 1,
    sortNo: 2,
  },
] as const;

const toolHealthFixture = [
  {
    toolCode: 'shell_command',
    displayName: 'Shell 命令执行',
    category: '终端',
    source: 'codex-cli',
    status: 'healthy',
    statusLabel: '可用',
    ok: true,
    message: 'shell_command 已接入内置执行器',
    sampleQuestion: 'command=date',
  },
  {
    toolCode: 'apply_patch',
    displayName: '补丁编辑',
    category: '代码编辑',
    source: 'codex-cli',
    status: 'healthy',
    statusLabel: '可用',
    ok: true,
    message: 'apply_patch 已接入内置执行器',
    sampleQuestion: 'patch=*** Begin Patch\\n*** End Patch',
  },
] as const;

describe('Tools page', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listTools).mockResolvedValue([...toolFixture]);
    vi.mocked(AdminChatApi.createTool).mockResolvedValue(toolFixture[0] as any);
    vi.mocked(AdminChatApi.updateTool).mockResolvedValue(toolFixture[0] as any);
    vi.mocked(AdminChatApi.deleteTool).mockResolvedValue(undefined);
    vi.mocked(AdminChatApi.listToolHealthViews).mockResolvedValue([...toolHealthFixture] as any);
    vi.mocked(AdminChatApi.pingTool).mockResolvedValue({
      toolCode: 'shell_command',
      status: 'healthy',
      statusLabel: '可用',
      ok: true,
      message: 'shell_command 调用成功',
      durationMs: 11,
      checkedAt: '2026-05-18 10:11:00',
    } as any);
    vi.mocked(AdminChatApi.invokeTool).mockResolvedValue({
      toolCode: 'shell_command',
      displayName: 'Shell 命令执行',
      ok: true,
      status: 'success',
      statusLabel: '成功',
      message: '工具调用成功',
      requestQuestion: 'command=date',
      content: 'exitCode: 0',
      metadata: { exitCode: 0 },
    } as any);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('loads tools from backend api', async () => {
    render(<ToolsPage />);

    expect(await screen.findByRole('heading', { name: '工具管理' })).toBeInTheDocument();
    expect(screen.queryByText('工具列表')).not.toBeInTheDocument();
    expect(screen.queryByText('统一展示工具配置与执行器状态，支持探测、调用和配置维护。')).not.toBeInTheDocument();
    expect(AdminChatApi.listTools).toHaveBeenCalledTimes(1);
    expect(AdminChatApi.listToolHealthViews).toHaveBeenCalledTimes(1);
    expect(screen.getByText('/shell_command')).toBeInTheDocument();
    expect(screen.getByText('/apply_patch')).toBeInTheDocument();
    expect(screen.getAllByText('可用').length).toBeGreaterThanOrEqual(1);
  });

  it('shows trace-style skeleton rows while tools table is loading', async () => {
    let resolveListTools: ((value: any) => void) | undefined;
    let resolveListToolHealthViews: ((value: any) => void) | undefined;
    vi.mocked(AdminChatApi.listTools).mockImplementationOnce(
      () => new Promise((resolve) => {
        resolveListTools = resolve;
      }) as any,
    );
    vi.mocked(AdminChatApi.listToolHealthViews).mockImplementationOnce(
      () => new Promise((resolve) => {
        resolveListToolHealthViews = resolve;
      }) as any,
    );

    render(<ToolsPage />);
    expect(await screen.findByRole('heading', { name: '工具管理' })).toBeInTheDocument();
    expect(screen.getAllByTestId('tools-loading-skeleton-row')).toHaveLength(10);

    resolveListTools?.([...toolFixture]);
    resolveListToolHealthViews?.([...toolHealthFixture]);
    expect(await screen.findByText('/shell_command')).toBeInTheDocument();
  });

  it('supports create tool config', async () => {
    render(<ToolsPage />);
    await screen.findByText('/shell_command');

    fireEvent.click(screen.getByRole('button', { name: '新增工具配置' }));
    const dialog = await screen.findByRole('dialog', { name: '新增工具配置' });
    fireEvent.change(within(dialog).getByLabelText('工具编码'), { target: { value: 'my_tool' } });
    fireEvent.change(within(dialog).getByLabelText('工具名称'), { target: { value: '我的工具' } });
    fireEvent.change(within(dialog).getByLabelText('分类'), { target: { value: '扩展' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '创建配置' }));

    await waitFor(() => {
      expect(AdminChatApi.createTool).toHaveBeenCalledWith(
        expect.objectContaining({
          toolCode: 'my_tool',
          displayName: '我的工具',
          category: '扩展',
        }),
      );
    });
  });

  it('supports edit tool config', async () => {
    render(<ToolsPage />);
    const editButton = await screen.findByRole('button', { name: '编辑工具 shell_command' });
    fireEvent.click(editButton);

    const dialog = await screen.findByRole('dialog', { name: '编辑工具配置' });
    expect(within(dialog).getByLabelText('工具编码')).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText('工具名称'), { target: { value: 'Shell 命令执行升级' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存修改' }));

    await waitFor(() => {
      expect(AdminChatApi.updateTool).toHaveBeenCalledWith(
        9101,
        expect.objectContaining({
          toolCode: 'shell_command',
          displayName: 'Shell 命令执行升级',
        }),
      );
    });
  });

  it('supports toggling tool enabled state from the action column', async () => {
    render(<ToolsPage />);
    const disableButton = await screen.findByRole('button', { name: '禁用工具 shell_command' });
    fireEvent.click(disableButton);

    await waitFor(() => {
      expect(AdminChatApi.updateTool).toHaveBeenCalledWith(
        9101,
        expect.objectContaining({
          toolCode: 'shell_command',
          enabled: 0,
        }),
      );
    });
    expect(AdminChatApi.listTools).toHaveBeenCalledTimes(2);
    expect(AdminChatApi.listToolHealthViews).toHaveBeenCalledTimes(2);
  });

  it('supports delete tool config', async () => {
    render(<ToolsPage />);
    const deleteButton = await screen.findByRole('button', { name: '删除工具 shell_command' });
    fireEvent.click(deleteButton);
    fireEvent.click(await screen.findByRole('button', { name: '确认删除' }));

    await waitFor(() => {
      expect(AdminChatApi.deleteTool).toHaveBeenCalledWith(9101);
    });
  });

  it('supports ping tool and shows dialog result', async () => {
    render(<ToolsPage />);
    await screen.findByText('/shell_command');

    fireEvent.click(screen.getByRole('button', { name: '测试工具 shell_command' }));
    expect(await screen.findByRole('dialog', { name: '工具探测结果' })).toBeInTheDocument();
    expect(screen.getByText('shell_command 调用成功')).toBeInTheDocument();
    expect(AdminChatApi.pingTool).toHaveBeenCalledWith('shell_command');

    fireEvent.click(screen.getByRole('button', { name: '关闭探测结果弹窗' }));
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: '工具探测结果' })).not.toBeInTheDocument(),
    );
  });

  it('supports invoke tool in dialog', async () => {
    render(<ToolsPage />);
    await screen.findByText('/shell_command');

    fireEvent.click(screen.getByRole('button', { name: '调用工具 shell_command' }));
    const dialog = await screen.findByRole('dialog', { name: '调用工具' });
    fireEvent.change(within(dialog).getByLabelText('调用参数（自然语言或 JSON）'), { target: { value: 'command=date' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '开始调用' }));

    await waitFor(() => {
      expect(AdminChatApi.invokeTool).toHaveBeenCalledWith('shell_command', 'command=date');
    });
    expect(await screen.findByText('exitCode: 0')).toBeInTheDocument();
  });

  it('uses paginated table card and supports page switch', async () => {
    vi.mocked(AdminChatApi.listTools).mockResolvedValue(
      Array.from({ length: 12 }, (_, index) => ({
        id: 9900 + index,
        toolCode: `tool_${index + 1}`,
        displayName: `工具 ${index + 1}`,
        description: '',
        category: '测试',
        sourceType: 'codex-cli',
        enabled: 1,
        sortNo: index + 1,
      })) as any,
    );
    vi.mocked(AdminChatApi.listToolHealthViews).mockResolvedValue([]);

    render(<ToolsPage />);
    expect(await screen.findByText('/tool_1')).toBeInTheDocument();
    expect(screen.getByText('第 1 / 2 页，共 12 条')).toBeInTheDocument();
    expect(screen.queryByText('/tool_11')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '第 2 页' }));
    expect(await screen.findByText('/tool_11')).toBeInTheDocument();
    expect(screen.queryByText('/tool_1')).not.toBeInTheDocument();
  });

  it('supports switching to intent tree view and selecting tool nodes', async () => {
    render(<ToolsPage />);
    await screen.findByText('/shell_command');

    fireEvent.click(screen.getByRole('button', { name: '意图树视图' }));

    const treePanel = await screen.findByRole('region', { name: '工具意图树' });
    const detailPanel = screen.getByRole('region', { name: '工具详情' });

    expect(within(treePanel).getByText('终端')).toBeInTheDocument();
    expect(within(treePanel).getByRole('button', { name: '收起分类 代码编辑' })).toHaveClass(
      'admin-tool-intent-category-toggle',
    );
    const applyPatchButton = within(treePanel).getByRole('button', { name: '选择工具 补丁编辑' });
    expect(applyPatchButton).toHaveClass('admin-tool-intent-tool-button');
    fireEvent.click(applyPatchButton);

    expect(within(detailPanel).getByText('/apply_patch')).toBeInTheDocument();
    fireEvent.click(within(detailPanel).getByRole('button', { name: '探测工具 apply_patch' }));

    await waitFor(() => {
      expect(AdminChatApi.pingTool).toHaveBeenCalledWith('apply_patch');
    });
  });
});
