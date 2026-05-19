import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '../api/adminChatApi';
import { MCP } from './MCP';

vi.mock('../api/adminChatApi', () => ({
  AdminChatApi: {
    listMcpConfigs: vi.fn(),
    createMcpConfig: vi.fn(),
    updateMcpConfig: vi.fn(),
    deleteMcpConfig: vi.fn(),
    listMcpTools: vi.fn(),
    pingMcpTool: vi.fn(),
    listTools: vi.fn(),
    createTool: vi.fn(),
    updateTool: vi.fn(),
    deleteTool: vi.fn(),
  },
}));

const mcpToolFixture = [
  {
    toolId: 'sales_query',
    displayName: '销售查询',
    category: '销售',
    source: '内置后端',
    status: 'healthy',
    statusLabel: '可用',
    description: '查询销售汇总、排名、趋势与明细',
    sampleQuestion: '本月华东销售总额是多少',
  },
  {
    toolId: 'weather_query',
    displayName: '天气查询',
    category: '天气',
    source: '内置后端',
    status: 'healthy',
    statusLabel: '可用',
    description: '查询当前天气与未来预报',
    sampleQuestion: '上海未来三天天气预报',
  },
  {
    toolId: 'code_search',
    displayName: '代码检索',
    category: '研发',
    source: '内置后端',
    status: 'healthy',
    statusLabel: '可用',
    description: '按关键词检索代码文件与行号',
    sampleQuestion: '查找 ChatController 的 sendMessage 方法',
  },
] as const;

const mcpConfigFixture = [
  {
    id: 7101,
    mcpCode: 'sales_query',
    displayName: '销售查询',
    description: '查询销售汇总、排名、趋势与明细',
    category: '销售',
    sourceType: 'built-in',
    enabled: 1,
    sortNo: 1,
  },
  {
    id: 7102,
    mcpCode: 'ticket_query',
    displayName: '工单查询',
    description: '查询工单状态、列表、优先级与解决率',
    category: '工单',
    sourceType: 'built-in',
    enabled: 0,
    sortNo: 2,
  },
] as const;

describe('MCP page', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listMcpConfigs).mockResolvedValue([...mcpConfigFixture]);
    vi.mocked(AdminChatApi.createMcpConfig).mockResolvedValue(mcpConfigFixture[0] as any);
    vi.mocked(AdminChatApi.updateMcpConfig).mockResolvedValue(mcpConfigFixture[0] as any);
    vi.mocked(AdminChatApi.deleteMcpConfig).mockResolvedValue(undefined);
    vi.mocked(AdminChatApi.listMcpTools).mockResolvedValue([...mcpToolFixture]);
    vi.mocked(AdminChatApi.pingMcpTool).mockResolvedValue({
      toolId: 'sales_query',
      ok: true,
      message: 'sales_query 可用',
      durationMs: 12,
      checkedAt: '2026-05-16 16:01:00',
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('loads MCP config and tools from backend api', async () => {
    render(<MCP />);

    expect(await screen.findByRole('heading', { name: 'MCP 管理' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'MCP 列表' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '刷新列表' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增MCP配置' })).toBeInTheDocument();
    expect(AdminChatApi.listMcpConfigs).toHaveBeenCalledTimes(1);
    expect(AdminChatApi.listMcpTools).toHaveBeenCalledTimes(1);
    expect(screen.getByText('/sales_query')).toBeInTheDocument();
    expect(screen.getByText('/ticket_query')).toBeInTheDocument();
    expect(screen.getByText('/weather_query')).toBeInTheDocument();
    expect(screen.getByText('/code_search')).toBeInTheDocument();
    expect(screen.queryAllByText('sales_query')).toHaveLength(0);
    expect(screen.getAllByText('可用').length).toBeGreaterThanOrEqual(1);
  });

  it('shows trace-style skeleton rows while MCP table is loading', async () => {
    let resolveListMcpConfigs: ((value: any) => void) | undefined;
    let resolveListMcpTools: ((value: any) => void) | undefined;
    vi.mocked(AdminChatApi.listMcpConfigs).mockImplementationOnce(
      () => new Promise((resolve) => {
        resolveListMcpConfigs = resolve;
      }) as any,
    );
    vi.mocked(AdminChatApi.listMcpTools).mockImplementationOnce(
      () => new Promise((resolve) => {
        resolveListMcpTools = resolve;
      }) as any,
    );

    render(<MCP />);
    expect(await screen.findByRole('heading', { name: 'MCP 管理' })).toBeInTheDocument();
    expect(screen.getAllByTestId('mcp-loading-skeleton-row')).toHaveLength(10);

    resolveListMcpConfigs?.([...mcpConfigFixture]);
    resolveListMcpTools?.([...mcpToolFixture]);
    expect(await screen.findByText('/sales_query')).toBeInTheDocument();
  });

  it('supports create mcp config in MCP page', async () => {
    render(<MCP />);
    await screen.findByText('/sales_query');

    fireEvent.click(screen.getByRole('button', { name: '新增MCP配置' }));
    const dialog = await screen.findByRole('dialog', { name: '新增MCP配置' });
    fireEvent.change(within(dialog).getByLabelText('MCP编码'), { target: { value: 'weather_query' } });
    fireEvent.change(within(dialog).getByLabelText('MCP名称'), { target: { value: '天气查询' } });
    fireEvent.change(within(dialog).getByLabelText('分类'), { target: { value: '天气' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '创建配置' }));

    await waitFor(() => {
      expect(AdminChatApi.createMcpConfig).toHaveBeenCalledWith(
        expect.objectContaining({
          mcpCode: 'weather_query',
          displayName: '天气查询',
          category: '天气',
        }),
      );
    });
  });

  it('supports edit mcp config in MCP page', async () => {
    render(<MCP />);
    const editButton = await screen.findByRole('button', { name: '编辑配置 sales_query' });
    fireEvent.click(editButton);

    const dialog = await screen.findByRole('dialog', { name: '编辑MCP配置' });
    expect(within(dialog).getByLabelText('MCP编码')).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText('MCP名称'), { target: { value: '销售查询增强版' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存修改' }));

    await waitFor(() => {
      expect(AdminChatApi.updateMcpConfig).toHaveBeenCalledWith(
        7101,
        expect.objectContaining({
          mcpCode: 'sales_query',
          displayName: '销售查询增强版',
        }),
      );
    });
  });

  it('supports delete mcp config in MCP page', async () => {
    render(<MCP />);
    const deleteButton = await screen.findByRole('button', { name: '删除配置 sales_query' });
    fireEvent.click(deleteButton);
    fireEvent.click(await screen.findByRole('button', { name: '确认删除' }));

    await waitFor(() => {
      expect(AdminChatApi.deleteMcpConfig).toHaveBeenCalledWith(7101);
    });
  });

  it('opens in-page dialog to show ping result instead of browser alert', async () => {
    render(<MCP />);
    await screen.findByText('/sales_query');

    fireEvent.click(screen.getByRole('button', { name: '测试 sales_query' }));

    expect(await screen.findByRole('dialog', { name: '工具探测结果' })).toBeInTheDocument();
    expect(screen.getByText('sales_query 可用')).toBeInTheDocument();
    expect(AdminChatApi.pingMcpTool).toHaveBeenCalledWith('sales_query');

    fireEvent.click(screen.getByRole('button', { name: '关闭结果弹窗' }));
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: '工具探测结果' })).not.toBeInTheDocument(),
    );
  });

  it('uses paginated table card and supports page navigation', async () => {
    vi.mocked(AdminChatApi.listMcpConfigs).mockResolvedValue(
      Array.from({ length: 12 }, (_, index) => ({
        id: 8000 + index,
        mcpCode: `mcp_${index + 1}`,
        displayName: `MCP ${index + 1}`,
        description: '',
        category: '测试',
        sourceType: 'built-in',
        enabled: 1,
        sortNo: index + 1,
      })) as any,
    );
    vi.mocked(AdminChatApi.listMcpTools).mockResolvedValue([]);

    render(<MCP />);
    expect(await screen.findByText('/mcp_1')).toBeInTheDocument();
    expect(screen.getByText('第 1 / 2 页，共 12 条')).toBeInTheDocument();
    expect(screen.queryByText('/mcp_11')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '第 2 页' }));
    expect(await screen.findByText('/mcp_11')).toBeInTheDocument();
    expect(screen.queryByText('/mcp_1')).not.toBeInTheDocument();
  });
});
