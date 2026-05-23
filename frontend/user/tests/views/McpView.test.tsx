import { fireEvent, render, screen } from '@testing-library/react';

import McpView from '@/views/McpView';

/**
 * 验证用户侧 MCP 管理页可管理连接状态与启用列表。
 */
describe('McpView', () => {
  /**
   * 页面应展示 MCP 列表，并支持切换单项启用状态。
   */
  it('应展示MCP列表并支持切换单项启用', () => {
    const setSelectedMcpCodes = vi.fn();
    const setMcpConnected = vi.fn();

    render(
      <McpView
        availableMcps={[
          {
            id: '9001',
            mcpCode: 'sales_query',
            displayName: '销售查询',
            description: '查询销售统计',
            category: '业务',
          },
        ]}
        selectedMcpCodes={[]}
        mcpConnected={true}
        setSelectedMcpCodes={setSelectedMcpCodes}
        setMcpConnected={setMcpConnected}
      />,
    );

    expect(screen.getByRole('heading', { name: 'MCP 管理' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '切换MCP 销售查询' }));
    expect(setSelectedMcpCodes).toHaveBeenCalledTimes(1);
  });

  /**
   * 不可用 MCP 必须禁用切换，避免用户误开启不可执行工具。
   */
  it('应禁止切换不可用MCP', () => {
    const setSelectedMcpCodes = vi.fn();
    const setMcpConnected = vi.fn();

    render(
      <McpView
        availableMcps={[
          {
            id: '9002',
            mcpCode: 'weather_query',
            displayName: '天气查询',
            description: '查询天气',
            category: '业务',
            available: false,
          },
        ]}
        selectedMcpCodes={[]}
        mcpConnected={true}
        setSelectedMcpCodes={setSelectedMcpCodes}
        setMcpConnected={setMcpConnected}
      />,
    );

    const button = screen.getByRole('button', { name: '切换MCP 天气查询' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-disabled', 'true');
    fireEvent.click(button);
    expect(setSelectedMcpCodes).not.toHaveBeenCalled();
  });

  /**
   * 总连接开关应支持切换。
   */
  it('应支持切换MCP总连接状态', () => {
    const setSelectedMcpCodes = vi.fn();
    const setMcpConnected = vi.fn();

    render(
      <McpView
        availableMcps={[]}
        selectedMcpCodes={[]}
        mcpConnected={false}
        setSelectedMcpCodes={setSelectedMcpCodes}
        setMcpConnected={setMcpConnected}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '切换MCP总连接' }));
    expect(setMcpConnected).toHaveBeenCalledWith(true);
  });
});
