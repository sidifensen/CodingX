import React from 'react';
import clsx from 'clsx';
import { Pagination } from './Pagination';

interface DataTableCardProps {
  title?: string;
  description?: string;
  /**
   * 表格头部右侧操作区：用于放置刷新、新增、筛选等动作按钮。
   */
  headerActions?: React.ReactNode;
  scrollTestId?: string;
  tableContent: React.ReactNode;
  summaryText: string;
  paginationCurrent: number;
  paginationPages: number;
  onPaginationChange: (page: number) => void;
  loading?: boolean;
  loadingText?: string;
  className?: string;
}

/**
 * 通用数据表卡片容器：统一头部说明、滚动区域与底部分页/操作区布局。
 */
export function DataTableCard({
  title,
  description,
  headerActions,
  scrollTestId,
  tableContent,
  summaryText,
  paginationCurrent,
  paginationPages,
  onPaginationChange,
  loading = false,
  loadingText = '加载中...',
  className,
}: DataTableCardProps) {
  const showHeader = Boolean(title || description || headerActions);

  return (
    <section
      className={clsx(
        'flex min-h-0 max-h-full flex-col overflow-hidden rounded-xl border border-border-hairline bg-surface-container-lowest shadow-sm',
        className,
      )}
    >
      {showHeader ? (
        <div className="shrink-0 border-b border-border-hairline px-lg py-md">
          <div className="flex flex-wrap items-start justify-between gap-md">
            {title || description ? (
              <div>
                {title ? <h2 className="font-title-md text-title-md text-ink">{title}</h2> : null}
                {description ? <p className="mt-1 text-secondary">{description}</p> : null}
              </div>
            ) : null}
            {headerActions ? (
              <div
                className={clsx(
                  'flex flex-wrap items-center gap-sm',
                  title || description ? 'ml-auto' : 'w-full justify-end',
                )}
              >
                {headerActions}
              </div>
            ) : null}
          </div>
        </div>
      ) : null}

      <div data-testid={scrollTestId} className="relative min-h-0 flex-1 overflow-auto">
        {tableContent}

        {loading ? (
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-surface-container-lowest/50">
            <span className="rounded-lg border border-border-hairline bg-surface-container-lowest px-md py-xs text-[12px] text-secondary">
              {loadingText}
            </span>
          </div>
        ) : null}
      </div>

      <div className="shrink-0 flex items-center justify-between gap-sm border-t border-border-hairline bg-surface-container-low px-lg py-sm">
        <p className="text-secondary text-[12px]">{summaryText}</p>
        <Pagination
          current={paginationCurrent}
          pages={Math.max(1, paginationPages)}
          loading={loading}
          onChange={onPaginationChange}
        />
      </div>
    </section>
  );
}
