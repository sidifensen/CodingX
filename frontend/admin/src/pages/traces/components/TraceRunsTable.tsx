import React from 'react';
import { EyeOutlined } from '@ant-design/icons';
import { Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import type { AdminTraceRun } from '../../../api/adminChatApi';
import { AdminDataTable, AdminTableActions } from '../../../components/AdminDataTable';
import { formatDateTime, formatDuration, statusBadgeClassName, statusLabel } from '../traceUtils';

interface TraceRunsTableProps {
  runs: AdminTraceRun[];
  loading: boolean;
  current: number;
  pages: number;
  total: number;
  onChangePage: (page: number) => void;
}

/**
 * Trace 运行记录表格，承载列表页主信息与分页导航。
 */
export function TraceRunsTable({
  runs,
  loading,
  current,
  pages,
  total,
  onChangePage,
}: TraceRunsTableProps) {
  const showEmptyState = !loading && runs.length === 0;
  const showSkeletonRows = loading && runs.length === 0;

  const columns = React.useMemo<ColumnsType<AdminTraceRun>>(() => [
    {
      title: 'Trace Name',
      dataIndex: 'traceName',
      width: 240,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: 'Trace Id',
      dataIndex: 'traceId',
      width: 180,
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: '会话ID / TaskID',
      width: 180,
      render: (_, run) => (
        <div>
          <Typography.Text code>{run.conversationId ?? '-'}</Typography.Text>
          <div><Typography.Text type="secondary" code>{run.taskId ?? '-'}</Typography.Text></div>
        </div>
      ),
    },
    {
      title: '用户名',
      dataIndex: 'username',
      width: 140,
      render: (_, run) => run.username || run.userId || '-',
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      width: 120,
      render: (_, run) => formatDuration(run.durationMs),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (_, run) => <Tag className={statusBadgeClassName(run.status)}>{statusLabel(run.status)}</Tag>,
    },
    {
      title: '执行时间',
      dataIndex: 'startedAt',
      width: 180,
      render: formatDateTime,
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 150,
      align: 'right',
      render: (_, run) => (
        <AdminTableActions
          actions={[{
            key: 'detail',
            label: '查看链路',
            icon: <EyeOutlined />,
            href: `/traces/${encodeURIComponent(run.traceId)}`,
          }]}
        />
      ),
    },
  ], []);

  return (
    <div data-testid="trace-runs-scroll" className="relative min-h-0 flex-1 overflow-auto">
      <AdminDataTable<AdminTraceRun>
        columns={columns}
        dataSource={runs}
        loading={false}
        locale={{ emptyText: showEmptyState ? '暂无链路数据' : '加载中...' }}
        pagination={{
          current,
          pageSize: 10,
          total,
          onChange: onChangePage,
        }}
        rowKey="traceId"
        scroll={{ x: 1180 }}
      />

      {loading ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-surface-container-lowest/60">
          <span className="rounded-lg border border-border-hairline bg-surface-container-lowest px-md py-xs text-[12px] text-secondary">
            加载中...
          </span>
        </div>
      ) : null}

      {showSkeletonRows ? (
        <div className="sr-only">
          {Array.from({ length: 10 }, (_, index) => <span key={index} data-testid="trace-loading-row" />)}
        </div>
      ) : null}
    </div>
  );
}
