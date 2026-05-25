import React from 'react';
import { Link, useParams } from 'react-router-dom';
import clsx from 'clsx';

import {
  AdminChatApi,
  type AdminChatConversationListItem,
  type AdminPageResult,
} from '../api/adminChatApi';
import { DataTableCard } from '../components/DataTableCard';

const PAGE_SIZE = 10;

/**
 * 管理端工作空间详情页：从空间维度查看内部会话，便于排查会话归属和本地目录绑定关系。
 */
export function WorkspaceDetailPage() {
  const { workspaceId = '' } = useParams();
  const [pageNo, setPageNo] = React.useState(1);
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [pageData, setPageData] = React.useState<AdminPageResult<AdminChatConversationListItem> | null>(null);

  /**
   * 拉取当前工作空间下的会话，所有错误文案保持后端语义。
   * @param current 当前页码。
   */
  const loadConversations = React.useCallback(async (current = pageNo) => {
    if (!workspaceId) {
      return;
    }
    setLoading(true);
    setErrorMessage('');
    try {
      const data = await AdminChatApi.listWorkspaceConversations(workspaceId, {
        current,
        size: PAGE_SIZE,
        keyword: keyword || undefined,
      });
      setPageData(data);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '加载工作空间会话失败'));
      setPageData(null);
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, workspaceId]);

  React.useEffect(() => {
    void loadConversations(pageNo);
  }, [loadConversations, pageNo]);

  const records = pageData?.records ?? [];
  const total = pageData?.total ?? 0;
  const current = pageData?.current ?? pageNo;
  const pages = pageData?.pages ?? 1;
  const showEmptyState = !loading && records.length === 0;
  const showSkeletonRows = loading && records.length === 0;
  const activeCount = records.filter((item) => item.statusLabel === '活跃').length;

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="relative overflow-hidden rounded-2xl border border-border-hairline bg-surface-container-lowest p-lg shadow-sm">
        <div className="pointer-events-none absolute -left-20 top-0 h-52 w-52 rounded-full bg-primary/10 blur-3xl" />
        <div className="relative flex flex-col gap-md lg:flex-row lg:items-end lg:justify-between">
          <div className="space-y-sm">
            <Link
              to="/workspaces"
              className="group inline-flex items-center gap-xs text-secondary transition-colors hover:text-ink"
            >
              <span className="material-symbols-outlined text-[18px] transition-transform group-hover:-translate-x-1">
                arrow_back
              </span>
              返回工作空间列表
            </Link>
            <div>
              <p className="font-label-caps text-label-caps uppercase tracking-widest text-secondary">
                Workspace Conversations
              </p>
              <h2 className="mt-xs font-headline-md text-headline-md text-ink">工作空间 #{workspaceId || '-'}</h2>
              <p className="mt-1 max-w-3xl text-secondary">
                查看该工作空间内的会话列表，并继续进入单个会话详情排查消息历史。
              </p>
            </div>
          </div>
          <div className="flex flex-wrap items-end gap-sm">
            <label className="flex flex-col gap-1 text-[12px] text-secondary">
              关键词
              <input
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                placeholder="搜索会话标题或 ID"
                className="h-10 w-[260px] rounded-lg border border-border-hairline bg-surface-container-low px-3 text-ink outline-none transition-colors placeholder:text-secondary/70 focus:border-border-strong"
              />
            </label>
            <button
              type="button"
              onClick={handleSearch}
              className="h-10 rounded-lg bg-primary px-lg text-button font-button text-on-primary transition-opacity hover:opacity-90"
            >
              查询
            </button>
            <button
              type="button"
              onClick={() => void loadConversations(pageNo)}
              className="h-10 rounded-lg border border-border-strong bg-surface-container-lowest px-lg text-button font-button text-ink transition-colors hover:bg-surface-container-low"
            >
              刷新
            </button>
          </div>
        </div>
      </header>

      <section className="grid gap-md md:grid-cols-3">
        <MetricCard label="工作空间" value={`#${workspaceId || '-'}`} hint="当前查看对象" />
        <MetricCard label="会话总数" value={total.toLocaleString('zh-CN')} hint="按后端分页结果统计" />
        <MetricCard label="当前页活跃" value={activeCount} hint="仅统计当前页记录" />
      </section>

      {errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      <DataTableCard
        scrollTestId="workspace-conversation-table-scroll"
        loading={loading}
        loadingText="会话加载中..."
        summaryText={`第 ${current} / ${Math.max(1, pages)} 页，共 ${total.toLocaleString('zh-CN')} 条`}
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
                    当前工作空间暂无会话
                  </td>
                </tr>
              ) : records.map((item) => (
                <tr key={item.id} className="transition-colors hover:bg-surface-container-low">
                  <td className="px-lg py-md font-data-mono text-[12px] text-tertiary-container">#{item.id}</td>
                  <td className="max-w-[360px] truncate px-lg py-md font-medium text-ink" title={item.title}>
                    {item.title || '-'}
                  </td>
                  <td className="px-lg py-md text-secondary">{item.createdBy ?? '-'}</td>
                  <td className="px-lg py-md">
                    <ConversationStatusBadge item={item} />
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
                  <tr key={`workspace-conversation-loading-row-${index}`} data-testid="workspace-conversation-loading-skeleton-row">
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

/**
 * 详情页指标卡，集中展示当前空间会话规模和当前页状态。
 */
function MetricCard({ label, value, hint }: { label: string; value: number | string; hint: string }) {
  return (
    <div className="rounded-2xl border border-border-hairline bg-surface-container-lowest p-md shadow-sm">
      <p className="font-label-caps text-label-caps uppercase tracking-widest text-secondary">{label}</p>
      <p className="mt-sm font-metric-lg text-metric-lg text-ink">{value}</p>
      <p className="mt-1 text-[12px] text-secondary">{hint}</p>
    </div>
  );
}

/**
 * 会话状态标签与会话管理页保持同一视觉语义。
 */
function ConversationStatusBadge({ item }: { item: AdminChatConversationListItem }) {
  const isActive = item.statusLabel === '活跃';
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[12px] font-semibold',
        isActive
          ? 'border-status-running-border bg-status-running-bg text-status-running'
          : 'border-border-hairline bg-surface-container-low text-secondary',
      )}
    >
      {isActive ? <span className="h-1.5 w-1.5 rounded-full bg-status-running" /> : null}
      {item.statusLabel || item.status || '未知'}
    </span>
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
