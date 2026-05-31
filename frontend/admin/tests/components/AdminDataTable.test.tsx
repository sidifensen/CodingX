import { EditOutlined, EyeOutlined } from '@ant-design/icons';
import { fireEvent, render, screen } from '@testing-library/react';
import type { ColumnsType } from 'antd/es/table';
import { describe, expect, it, vi } from 'vitest';

import { AdminDataTable, AdminTableActions } from '@/components/AdminDataTable';

interface DemoRow {
  id: number;
  name: string;
}

const columns: ColumnsType<DemoRow> = [
  {
    title: '名称',
    dataIndex: 'name',
  },
];

describe('AdminDataTable', () => {
  it('renders an Ant Design table without bordered vertical separators and with unified pagination text', () => {
    const onChangePage = vi.fn();

    const { container } = render(
      <AdminDataTable<DemoRow>
        columns={columns}
        dataSource={[{ id: 1, name: '演示数据' }]}
        pagination={{
          current: 1,
          pageSize: 10,
          total: 1,
          onChange: onChangePage,
        }}
        rowKey="id"
      />,
    );

    expect(container.querySelector('.admin-data-table')).not.toBeNull();
    expect(container.querySelector('.ant-table-bordered')).toBeNull();
    expect(screen.getByText('演示数据')).toBeInTheDocument();
    expect(screen.getByText('第 1 / 1 页，共 1 条')).toBeInTheDocument();
  });

  it('uses the live pagination total when the table falls back to local row count', () => {
    const onChangePage = vi.fn();

    const rows = Array.from({ length: 105 }, (_, index) => ({
      id: index + 1,
      name: `演示数据 ${index + 1}`,
    }));

    render(
      <AdminDataTable<DemoRow>
        columns={columns}
        dataSource={rows}
        pagination={{
          current: 1,
          pageSize: 10,
          total: 0,
          onChange: onChangePage,
        }}
        rowKey="id"
      />,
    );

    expect(screen.getByText('第 1 / 11 页，共 105 条')).toBeInTheDocument();
  });

  it('renders action buttons with Ant Design icons', () => {
    const onView = vi.fn();
    const onEdit = vi.fn();

    const { container } = render(
      <AdminTableActions
        actions={[
          { key: 'view', label: '查看', icon: <EyeOutlined />, onClick: onView },
          { key: 'edit', label: '编辑', icon: <EditOutlined />, onClick: onEdit },
        ]}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '查看' }));

    expect(onView).toHaveBeenCalledTimes(1);
    expect(container.querySelector('.anticon-eye')).not.toBeNull();
    expect(container.querySelector('.anticon-edit')).not.toBeNull();
  });
});
