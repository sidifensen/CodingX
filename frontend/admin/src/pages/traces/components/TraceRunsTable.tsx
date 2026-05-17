import React from 'react';
import { Link } from 'react-router-dom';
import clsx from 'clsx';

import type { AdminTraceRun } from '../../../api/adminChatApi';
import { DataTableCard } from '../../../components/DataTableCard';
import { Pagination } from '../../../components/Pagination';
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

  return (
    <DataTableCard
      title="运行列表"
      description="按时间倒序查看运行记录，通过操作按钮进入独立详情页"
      scrollTestId="trace-runs-scroll"
      loading={loading}
      tableContent={(
        <table className="w-full min-w-[1180px] border-collapse text-left">
          <thead>
            <tr className="bg-surface-container-low border-b border-border-hairline">
              <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                Trace Name
              </th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                Trace Id
              </th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                会话ID / TaskID
              </th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                用户名
              </th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                耗时
              </th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                状态
              </th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                执行时间
              </th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary text-right">
                操作
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-hairline">
            {showEmptyState ? (
              <tr>
                <td colSpan={8} className="px-lg py-xl text-center text-secondary">
                  暂无链路数据
                </td>
              </tr>
            ) : (
              runs.map((run) => (
                <tr key={run.traceId} className="hover:bg-surface-container-low transition-colors">
                  <td className="px-lg py-md text-ink font-medium max-w-[240px] truncate" title={run.traceName || '-'}>
                    {run.traceName || '-'}
                  </td>
                  <td className="px-lg py-md font-data-mono text-[12px] text-tertiary-container">
                    <span title={run.traceId}>{run.traceId}</span>
                  </td>
                  <td className="px-lg py-md">
                    <p className="font-data-mono text-[12px] text-tertiary-container">{run.conversationId ?? '-'}</p>
                    <p className="font-data-mono text-[12px] text-secondary mt-1">{run.taskId ?? '-'}</p>
                  </td>
                  <td className="px-lg py-md text-secondary">{run.username || run.userId || '-'}</td>
                  <td className="px-lg py-md text-ink font-medium">{formatDuration(run.durationMs)}</td>
                  <td className="px-lg py-md">
                    <span
                      className={clsx(
                        'inline-flex rounded-full border px-2.5 py-1 text-[12px] font-semibold',
                        statusBadgeClassName(run.status),
                      )}
                    >
                      {statusLabel(run.status)}
                    </span>
                  </td>
                  <td className="px-lg py-md text-secondary">{formatDateTime(run.startedAt)}</td>
                  <td className="px-lg py-md text-right">
                    <Link
                      to={`/traces/${encodeURIComponent(run.traceId)}`}
                      className="inline-flex items-center gap-xs rounded-lg border border-border-hairline bg-surface-container-lowest px-sm py-1.5 text-secondary hover:bg-surface-container-low hover:text-ink transition-colors"
                    >
                      <span className="material-symbols-outlined text-[16px]">visibility</span>
                      查看链路
                    </Link>
                  </td>
                </tr>
              ))
            )}
            {showSkeletonRows
              ? Array.from({ length: 10 }, (_, index) => (
                <tr key={`trace-loading-row-${index}`}>
                  <td colSpan={8} className="px-lg py-md">
                    <div className="h-6 w-full animate-pulse rounded bg-surface-container-low" />
                  </td>
                </tr>
              ))
              : null}
          </tbody>
        </table>
      )}
      footerContent={(
        <>
        <p className="text-secondary text-[12px]">
          第 {current} / {Math.max(pages, 1)} 页，共 {total.toLocaleString('zh-CN')} 条
        </p>
        <Pagination
          current={current}
          pages={Math.max(1, pages)}
          loading={loading}
          onChange={onChangePage}
        />
        </>
      )}
    />
  );
}
