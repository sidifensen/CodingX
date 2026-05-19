import React from 'react';
import { Link } from 'react-router-dom';
import clsx from 'clsx';

import {
  AdminChatApi,
  type AdminChatConversationListItem,
  type AdminPageResult,
} from '../api/adminChatApi';
import { DataTableCard } from '../components/DataTableCard';

const PAGE_SIZE = 10;

/**
 * 管理端会话列表页：承载会话检索、状态筛选与详情跳转。
 */
export function Tasks() {
  const [statusFilter, setStatusFilter] = React.useState<'全部' | '活跃' | '已归档'>('全部');
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const [pageNo, setPageNo] = React.useState(1);
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [pageData, setPageData] = React.useState<AdminPageResult<AdminChatConversationListItem> | null>(null);

  /**
   * 拉取会话分页数据，并在前端执行状态筛选。
   * @param current 当前页码。
   */
  const loadConversations = React.useCallback(async (current = pageNo) => {
    setLoading(true);
    setErrorMessage('');
    try {
      const data = await AdminChatApi.listConversations({
        current,
        size: PAGE_SIZE,
        keyword: keyword || undefined,
      });
      setPageData(data);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '会话列表加载失败'));
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo]);

  React.useEffect(() => {
    void loadConversations(pageNo);
  }, [loadConversations, pageNo]);

  const records = React.useMemo(() => {
    const source = pageData?.records ?? [];
    if (statusFilter === '全部') {
      return source;
    }
    return source.filter((item) => item.statusLabel === statusFilter);
  }, [pageData?.records, statusFilter]);
  const total = pageData?.total ?? 0;
  const current = pageData?.current ?? pageNo;
  const pages = pageData?.pages ?? 1;
  const showEmptyState = !loading && records.length === 0;
  const showSkeletonRows = loading && records.length === 0;

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  const summaryText = `第 ${current} / ${Math.max(1, pages)} 页，共 ${total.toLocaleString('zh-CN')} 条`;

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">会话管理</h2>
          <p className="mt-1 text-secondary">查看系统会话状态、最后消息时间与消息详情。</p>
        </div>
        <div className="flex items-center gap-xs">
          <button
            type="button"
            className="h-10 rounded-lg border border-border-strong bg-surface-container-lowest px-lg text-button font-button text-ink transition-colors hover:bg-surface-container-low"
            onClick={() => void loadConversations(pageNo)}
          >
            刷新
          </button>
        </div>
      </header>

      {errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      <div className="rounded-xl border border-border-hairline bg-surface-container-lowest p-md">
        <div className="flex flex-wrap items-center justify-between gap-sm">
          <div className="flex gap-2">
            {(['全部', '活跃', '已归档'] as const).map((tab) => (
              <button
                key={tab}
                type="button"
                onClick={() => setStatusFilter(tab)}
                className={clsx(
                  'rounded-md border px-md py-1.5 text-body-sm font-medium transition-colors',
                  statusFilter === tab
                    ? 'border-border-strong bg-surface-container text-ink shadow-sm'
                    : 'border-transparent bg-transparent text-secondary hover:bg-surface-container-low hover:text-ink',
                )}
              >
                {tab}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-xs">
            <div className="relative w-72">
              <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-[18px] text-secondary">
                search
              </span>
              <input
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                placeholder="搜索会话标题或 ID"
                className="h-10 w-full rounded-md border border-border-hairline bg-surface-container-low pl-9 pr-3 text-body-sm text-ink outline-none transition-colors focus:border-ink focus:ring-1 focus:ring-ink"
              />
            </div>
            <button
              type="button"
              onClick={handleSearch}
              className="h-10 rounded-lg bg-ink px-lg text-button font-button text-on-ink transition-opacity hover:opacity-90"
            >
              查询
            </button>
          </div>
        </div>
      </div>

      <DataTableCard
        scrollTestId="conversation-table-scroll"
        loading={loading}
        loadingText="会话加载中..."
        summaryText={summaryText}
        paginationCurrent={current}
        paginationPages={pages}
        onPaginationChange={setPageNo}
        tableContent={(
          <table className="w-full min-w-[1120px] border-collapse text-left">
            <thead>
              <tr className="border-b border-border-hairline bg-surface-container-low">
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                  会话ID
                </th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                  标题
                </th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                  创建人
                </th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                  状态
                </th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                  最近消息时间
                </th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">
                  更新时间
                </th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md text-right font-label-caps text-label-caps text-secondary">
                  操作
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {showEmptyState ? (
                <tr>
                  <td colSpan={7} className="px-lg py-xl text-center text-secondary">
                    暂无会话数据
                  </td>
                </tr>
              ) : records.map((item) => (
                <tr key={item.id} className="transition-colors hover:bg-surface-container-low">
                  <td className="px-lg py-md font-data-mono text-[12px] text-tertiary-container">#{item.id}</td>
                  <td className="max-w-[360px] truncate px-lg py-md text-ink font-medium" title={item.title}>
                    {item.title}
                  </td>
                  <td className="px-lg py-md text-secondary">{item.createdBy}</td>
                  <td className="px-lg py-md">
                    <span
                      className={clsx(
                        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[12px] font-semibold',
                        item.statusLabel === '活跃'
                          ? 'border-status-running-border bg-status-running-bg text-status-running'
                          : 'border-border-hairline bg-surface-container-low text-secondary',
                      )}
                    >
                      {item.statusLabel === '活跃' ? (
                        <span className="h-1.5 w-1.5 rounded-full bg-status-running" />
                      ) : null}
                      {item.statusLabel}
                    </span>
                  </td>
                  <td className="px-lg py-md text-secondary">{formatDateTime(item.lastMessageAt)}</td>
                  <td className="px-lg py-md text-secondary">{formatDateTime(item.updatedAt)}</td>
                  <td className="px-lg py-md text-right">
                    <Link
                      to={`/tasks/${item.id}`}
                      className="inline-flex items-center gap-xs rounded-lg border border-border-strong bg-surface-container-lowest px-sm py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
                    >
                      <span className="material-symbols-outlined text-[16px]">visibility</span>
                      查看详情
                    </Link>
                  </td>
                </tr>
              ))}
              {showSkeletonRows
                ? Array.from({ length: PAGE_SIZE }, (_, index) => (
                  <tr key={`conversation-loading-row-${index}`}>
                    <td colSpan={7} className="px-lg py-md">
                      <div className="h-6 w-full animate-pulse rounded bg-surface-container-low" />
                    </td>
                  </tr>
                ))
                : null}
            </tbody>
          </table>
        )}
      />
    </div>
  );
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN');
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
