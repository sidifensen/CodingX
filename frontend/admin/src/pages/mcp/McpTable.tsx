import React from 'react';
import { DeleteOutlined, EditOutlined, PlusOutlined, ExperimentOutlined } from '@ant-design/icons';
import { Badge, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import type { AdminMcpConfig } from '../../api/adminChatApi';
import { AdminDataTable, AdminTableActions } from '../../components/AdminDataTable';
import { MCP_TABLE_PAGE_SIZE, type UnifiedMcpRow } from './mcpTypes';
import { toBadgeStatus } from './mcpUtils';

interface McpTableProps {
  rows: UnifiedMcpRow[];
  loading: boolean;
  currentPage: number;
  pingingToolId: string | null;
  onPageChange: (page: number) => void;
  onPing: (toolId: string) => void;
  onEdit: (config: AdminMcpConfig) => void;
  onCreateWithCode: (mcpCode: string) => void;
  onDelete: (config: AdminMcpConfig) => void;
}

/**
 * MCP 配置表：使用 AntD Table 承载配置与执行器状态的合并视图。
 */
export function McpTable({
  rows,
  loading,
  currentPage,
  pingingToolId,
  onPageChange,
  onPing,
  onEdit,
  onCreateWithCode,
  onDelete,
}: McpTableProps) {
  const pageCount = Math.max(1, Math.ceil(rows.length / MCP_TABLE_PAGE_SIZE));
  const columns = React.useMemo<ColumnsType<UnifiedMcpRow>>(() => [
    {
      title: '编码',
      dataIndex: 'mcpCode',
      width: 180,
      fixed: 'left',
      render: (value: string) => (
        <Typography.Text code>{`/${value}`}</Typography.Text>
      ),
    },
    {
      title: '名称',
      width: 180,
      render: (_, row) => row.config?.displayName || row.tool?.displayName || row.mcpCode,
    },
    {
      title: '分类',
      width: 120,
      render: (_, row) => row.config?.category || row.tool?.category || '-',
    },
    {
      title: '来源',
      width: 140,
      render: (_, row) => row.config?.sourceType || row.tool?.source || '-',
    },
    {
      title: '配置状态',
      width: 120,
      render: (_, row) => {
        if (!row.config) {
          return <Tag>未配置</Tag>;
        }
        return row.config.enabled === 0 ? <Tag>停用</Tag> : <Tag color="success">启用</Tag>;
      },
    },
    {
      title: '执行器状态',
      width: 140,
      render: (_, row) => {
        const statusLabel = row.tool?.statusLabel ?? (row.config ? '未接入' : '仅执行器');
        return <Badge status={toBadgeStatus(row.tool?.status)} text={statusLabel} />;
      },
    },
    {
      title: '最近探测',
      width: 170,
      render: (_, row) => row.tool?.checkedAt || '-',
    },
    {
      title: '排序',
      width: 90,
      align: 'right',
      render: (_, row) => row.config?.sortNo ?? 0,
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 230,
      align: 'right',
      render: (_, row) => (
        <AdminTableActions
          actions={[
            {
              key: 'ping',
              label: '测试',
              ariaLabel: `测试 ${row.mcpCode}`,
              icon: <ExperimentOutlined />,
              loading: pingingToolId === row.mcpCode,
              onClick: () => onPing(row.mcpCode),
            },
            {
              key: 'edit',
              label: row.config ? '编辑' : '补配置',
              ariaLabel: `编辑配置 ${row.mcpCode}`,
              icon: row.config ? <EditOutlined /> : <PlusOutlined />,
              onClick: () => row.config ? onEdit(row.config) : onCreateWithCode(row.mcpCode),
            },
            {
              key: 'delete',
              label: '删除',
              ariaLabel: `删除配置 ${row.mcpCode}`,
              danger: true,
              disabled: !row.config,
              icon: <DeleteOutlined />,
              onClick: () => row.config ? onDelete(row.config) : undefined,
            },
          ]}
        />
      ),
    },
  ], [onCreateWithCode, onDelete, onEdit, onPing, pingingToolId]);

  return (
    <AdminDataTable<UnifiedMcpRow>
      columns={columns}
      dataSource={rows}
      loading={loading}
      locale={{ emptyText: loading ? '加载中...' : '暂无 MCP 配置' }}
      pagination={{
        current: currentPage,
        pageSize: MCP_TABLE_PAGE_SIZE,
        total: rows.length,
        onChange: onPageChange,
      }}
      rowKey={(row) => String(row.config?.id ?? row.mcpCode)}
      scroll={{ x: 1280 }}
    />
  );
}
