import { Button, Space, Typography } from 'antd';

interface McpToolbarProps {
  loading: boolean;
  onRefresh: () => void;
  onCreate: () => void;
}

/**
 * MCP 页面头部：集中承载标题说明与列表级操作。
 */
export function McpToolbar({ loading, onRefresh, onCreate }: McpToolbarProps) {
  return (
    <header className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
      <div>
        <Typography.Title level={2} style={{ margin: 0 }}>
          MCP 管理
        </Typography.Title>
        <Typography.Text type="secondary">
          管理数据库 MCP 配置，并在线探测后端工具可用性。
        </Typography.Text>
      </div>
      <Space wrap>
        <Button aria-label="刷新列表" loading={loading} onClick={onRefresh}>
          刷新列表
        </Button>
        <Button type="primary" onClick={onCreate}>
          新增MCP配置
        </Button>
      </Space>
    </header>
  );
}
