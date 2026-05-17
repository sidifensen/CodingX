import React from 'react';
import clsx from 'clsx';
import { Pagination } from './Pagination';

interface DataTableCardProps {
  title: string;
  description?: string;
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
  return (
    <section
      className={clsx(
        'flex min-h-0 max-h-full flex-col overflow-hidden rounded-xl border border-border-hairline bg-surface-container-lowest shadow-sm',
        className,
      )}
    >
      <div className="shrink-0 border-b border-border-hairline px-lg py-md">
        <h2 className="font-title-md text-title-md text-ink">{title}</h2>
        {description ? <p className="mt-1 text-secondary">{description}</p> : null}
      </div>

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
