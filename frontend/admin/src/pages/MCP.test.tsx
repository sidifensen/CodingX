import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '../api/adminChatApi';
import { MCP } from './MCP';

vi.mock('../api/adminChatApi', () => ({
  AdminChatApi: {
    listMcpTools: vi.fn(),
    pingMcpTool: vi.fn(),
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
] as const;

describe('MCP page', () => {
  beforeEach(() => {
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

  it('loads MCP tools from backend api', async () => {
    render(<MCP />);

    expect(await screen.findByRole('heading', { name: 'MCP 工具管理' })).toBeInTheDocument();
    expect(AdminChatApi.listMcpTools).toHaveBeenCalledTimes(1);
    expect(screen.getByText('sales_query')).toBeInTheDocument();
    expect(screen.getByText('weather_query')).toBeInTheDocument();
    expect(screen.getAllByText('可用').length).toBeGreaterThanOrEqual(1);
  });

  it('opens in-page dialog to show ping result instead of browser alert', async () => {
    render(<MCP />);
    await screen.findByText('sales_query');

    fireEvent.click(screen.getByRole('button', { name: '测试 sales_query' }));

    expect(await screen.findByRole('dialog', { name: '工具探测结果' })).toBeInTheDocument();
    expect(screen.getByText('sales_query 可用')).toBeInTheDocument();
    expect(AdminChatApi.pingMcpTool).toHaveBeenCalledWith('sales_query');

    fireEvent.click(screen.getByRole('button', { name: '关闭结果弹窗' }));
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: '工具探测结果' })).not.toBeInTheDocument(),
    );
  });
});
