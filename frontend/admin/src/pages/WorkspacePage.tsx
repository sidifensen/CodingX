import React from 'react';
import { Link } from 'react-router-dom';

import {
  AdminChatApi,
  type AdminPageResult,
  type AdminWorkspace,
} from '../api/adminChatApi';
import { DataTableCard } from '../components/DataTableCard';

const PAGE_SIZE = 10;

type RuntimeTargetFilter = 'ALL' | 'cloud' | 'local';

/**
 * 管理端工作空间管理页：只读展示空间归属、运行目标和关联会话规模，避免误触发用户侧写入流程。
 */
export function WorkspacePage() {
  const [pageNo, setPageNo] = React.useState(1);
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const [runtimeTargetInput, setRuntimeTargetInput] = React.useState<RuntimeTargetFilter>('ALL');
  const [runtimeTarget, setRuntimeTarget] = React.useState<RuntimeTargetFilter>('ALL');
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [pageData, setPageData] = React.useState<AdminPageResult<AdminWorkspace> | null>(null);

  /**
   * 拉取工作空间分页数据，集中处理筛选条件和异常文案。
   * @param current 当前页码。
   */
  const loadData = React.useCallback(async (current = pageNo) => {
    setLoading(true);
    setErrorMessage('');
    try {
      const data = await AdminChatApi.listWorkspaces({
        current,
        size: PAGE_SIZE,
        keyword: keyword || undefined,
        runtimeTarget,
      });
      setPageData(data);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '加载工作空间失败'));
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, runtimeTarget]);

  React.useEffect(() => {
    void loadData(pageNo);
  }, [loadData, pageNo]);

  const records = pageData?.records ?? [];
  const total = pageData?.total ?? 0;
  const current = pageData?.current ?? pageNo;
  const pages = pageData?.pages ?? 1;
  const showEmptyState = !loading && records.length === 0;
  const showSkeletonRows = loading && records.length === 0;
  const cloudCount = records.filter((item) => item.runtimeTarget === 'cloud').length;
  const localCount = records.filter((item) => item.runtimeTarget === 'local').length;
  const conversationCount = records.reduce((sum, item) => sum + Number(item.conversationCount ?? 0), 0);

  const handleFilter = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
    setRuntimeTarget(runtimeTargetInput);
  };

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="relative overflow-hidden rounded-2xl border border-border-hairline bg-surface-container-lowest p-lg shadow-sm">
        <div className="pointer-events-none absolute -right-16 -top-24 h-56 w-56 rounded-full bg-primary/10 blur-3xl" />
        <div className="pointer-events-none absolute bottom-0 left-1/3 h-px w-1/2 bg-gradient-to-r from-transparent via-border-strong to-transparent" />
        <div className="relative flex flex-col gap-md lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="font-label-caps text-label-caps uppercase tracking-widest text-secondary">Workspace Inventory</p>
            <h2 className="mt-xs font-headline-md text-headline-md text-ink">工作空间管理</h2>
            <p className="mt-1 max-w-3xl text-secondary">
              只读查看云端历史与本地目录空间，快速排查会话归属、仓库上下文和空间活跃度。
            </p>
          </div>
          <div className="flex flex-wrap items-end gap-sm">
            <label className="flex flex-col gap-1 text-[12px] text-secondary">
              关键词
              <input
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                placeholder="名称 / 仓库 / 目录 / ID"
                className="h-10 w-[240px] rounded-lg border border-border-hairline bg-surface-container-low px-3 text-ink outline-none transition-colors placeholder:text-secondary/70 focus:border-border-strong"
              />
            </label>
            <label className="flex flex-col gap-1 text-[12px] text-secondary">
              运行目标
              <select
                aria-label="运行目标"
                value={runtimeTargetInput}
                onChange={(event) => setRuntimeTargetInput(event.target.value as RuntimeTargetFilter)}
                className="h-10 w-[140px] rounded-lg border border-border-hairline bg-surface-container-low px-3 text-ink outline-none transition-colors focus:border-border-strong"
              >
                <option value="ALL">全部</option>
                <option value="cloud">云端</option>
                <option value="local">本地</option>
              </select>
            </label>
            <button
              type="button"
              onClick={handleFilter}
              className="h-10 rounded-lg bg-primary px-lg text-button font-button text-on-primary transition-opacity hover:opacity-90"
            >
              筛选
            </button>
            <button
              type="button"
              onClick={() => void loadData(pageNo)}
              className="h-10 rounded-lg border border-border-strong bg-surface-container-lowest px-lg text-button font-button text-ink transition-colors hover:bg-surface-container-low"
            >
              刷新
            </button>
          </div>
        </div>
      </header>

      <section className="grid gap-md md:grid-cols-3">
        <MetricCard label="当前页工作空间" value={records.length} hint={`总计 ${total.toLocaleString('zh-CN')} 个`} />
        <MetricCard label="云端 / 本地" value={`${cloudCount} / ${localCount}`} hint="按当前页记录统计" />
        <MetricCard label="关联会话" value={conversationCount} hint="当前页未删除会话合计" />
      </section>

      {errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      <DataTableCard
        scrollTestId="workspace-table-scroll"
        loading={loading}
        loadingText="加载中..."
        summaryText={`第 ${current} / ${Math.max(1, pages)} 页，共 ${total.toLocaleString('zh-CN')} 条`}
        paginationCurrent={current}
        paginationPages={pages}
        onPaginationChange={setPageNo}
        tableContent={(
          <table className="w-full min-w-[1280px] border-collapse text-left">
            <thead>
              <tr className="border-b border-border-hairline bg-surface-container-low">
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">工作空间</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">运行目标</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">创建人</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">会话数</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">仓库</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">分支</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">本地目录</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">更新时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {showEmptyState ? (
                <tr>
                  <td colSpan={8} className="px-lg py-xl text-center text-secondary">暂无工作空间</td>
                </tr>
              ) : records.map((item) => (
                <tr key={item.id} className="transition-colors hover:bg-surface-container-low">
                  <td className="px-lg py-md">
                    <Link
                      to={`/workspaces/${item.id}`}
                      className="group inline-flex flex-col gap-1 rounded-lg px-2 py-1 -mx-2 -my-1 transition-colors hover:bg-surface-container-low"
                    >
                      <span className="font-medium text-ink transition-colors group-hover:text-primary">
                        {item.name || '-'}
                      </span>
                      <span className="font-data-mono text-[12px] text-secondary transition-colors group-hover:text-primary">
                        #{item.id}
                      </span>
                    </Link>
                  </td>
                  <td className="px-lg py-md">
                    <RuntimeTargetBadge item={item} />
                  </td>
                  <td className="px-lg py-md text-secondary">{item.createdBy ?? '-'}</td>
                  <td className="px-lg py-md text-ink">{Number(item.conversationCount ?? 0).toLocaleString('zh-CN')}</td>
                  <td className="max-w-[260px] truncate px-lg py-md text-secondary" title={item.repositoryUrl || ''}>
                    {item.repositoryUrl || '-'}
                  </td>
                  <td className="px-lg py-md text-secondary">{item.branchName || '-'}</td>
                  <td className="max-w-[260px] truncate px-lg py-md text-secondary" title={item.workingDirectory || ''}>
                    {item.workingDirectory || '-'}
                  </td>
                  <td className="px-lg py-md text-[12px] text-secondary">{formatDate(item.updatedAt)}</td>
                </tr>
              ))}
              {showSkeletonRows
                ? Array.from({ length: 10 }, (_, index) => (
                  <tr key={`workspace-loading-row-${index}`} data-testid="workspace-loading-skeleton-row">
                    <td colSpan={8} className="px-lg py-md">
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
 * 顶部指标卡片，保持页面信息密度同时避免表格成为唯一入口。
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
 * 根据运行目标渲染强调程度不同的标签，方便快速区分云端历史与本地目录。
 */
function RuntimeTargetBadge({ item }: { item: AdminWorkspace }) {
  const isLocal = item.runtimeTarget === 'local';
  return (
    <span
      className={[
        'inline-flex items-center rounded-full border px-2 py-0.5 text-[12px] font-medium',
        isLocal
          ? 'border-status-running-border bg-status-running-bg text-status-running'
          : 'border-border-strong bg-surface-container text-ink',
      ].join(' ')}
    >
      {item.runtimeTargetLabel || '未知'}
    </span>
  );
}

function formatDate(value?: string) {
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
