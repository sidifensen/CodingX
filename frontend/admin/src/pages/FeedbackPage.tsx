import React from 'react';
import { Link } from 'react-router-dom';

import { AdminChatApi, type AdminChatMessageFeedback, type AdminPageResult } from '../api/adminChatApi';
import { DataTableCard } from '../components/DataTableCard';

const PAGE_SIZE = 10;

/**
 * 管理端反馈列表页：用于检索点赞/点踩反馈并跳转到详情分析。
 */
export function FeedbackPage() {
  const [pageNo, setPageNo] = React.useState(1);
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const [voteFilter, setVoteFilter] = React.useState<0 | 1 | -1>(0);
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [pageData, setPageData] = React.useState<AdminPageResult<AdminChatMessageFeedback> | null>(null);

  /**
   * 拉取反馈分页数据，集中处理筛选条件与异常文案。
   * @param current 当前页码。
   */
  const loadData = React.useCallback(async (current = pageNo) => {
    setLoading(true);
    setErrorMessage('');
    try {
      const data = await AdminChatApi.listFeedbacks({
        current,
        size: PAGE_SIZE,
        keyword: keyword || undefined,
        vote: voteFilter === 0 ? null : voteFilter,
      });
      setPageData(data);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '加载反馈失败'));
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, voteFilter]);

  React.useEffect(() => {
    void loadData(pageNo);
  }, [loadData, pageNo]);

  const records = pageData?.records ?? [];
  const total = pageData?.total ?? 0;
  const current = pageData?.current ?? pageNo;
  const pages = pageData?.pages ?? 1;
  // 对齐 Trace 管理：加载阶段在表格体渲染骨架占位。
  const showEmptyState = !loading && records.length === 0;
  const showSkeletonRows = loading && records.length === 0;

  const handleFilter = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">反馈管理</h2>
          <p className="mt-1 text-secondary">统一查看用户点赞/点踩反馈，并支持按反馈记录进入详情页排查</p>
        </div>
        <div className="flex flex-wrap items-end gap-sm">
          <label className="flex flex-col gap-1 text-[12px] text-secondary">
            关键词
            <input
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              placeholder="原因或评论"
              className="h-10 w-[220px] rounded-lg border border-border-hairline bg-surface-container-lowest px-3 text-ink outline-none transition-colors focus:border-border-strong"
            />
          </label>
          <label className="flex flex-col gap-1 text-[12px] text-secondary">
            投票筛选
            <select
              aria-label="投票筛选"
              value={String(voteFilter)}
              onChange={(event) => setVoteFilter(Number(event.target.value) as 0 | 1 | -1)}
              className="h-10 w-[140px] rounded-lg border border-border-hairline bg-surface-container-lowest px-3 text-ink outline-none transition-colors focus:border-border-strong"
            >
              <option value="0">全部</option>
              <option value="1">仅点赞</option>
              <option value="-1">仅点踩</option>
            </select>
          </label>
          <button
            type="button"
            onClick={handleFilter}
            className="h-10 rounded-lg bg-ink px-lg text-button font-button text-on-ink transition-opacity hover:opacity-90"
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
      </header>

      {errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      <section className="space-y-md rounded-2xl border border-border-hairline bg-surface-container-lowest p-lg shadow-sm">
        <DataTableCard
          title="反馈列表"
          description="按反馈记录查看点赞/点踩详情，并支持跳转详情页排查上下文。"
          scrollTestId="feedback-table-scroll"
          loading={loading}
          loadingText="加载中..."
          summaryText={`第 ${current} / ${Math.max(1, pages)} 页，共 ${total.toLocaleString('zh-CN')} 条`}
          paginationCurrent={current}
          paginationPages={pages}
          onPaginationChange={setPageNo}
          tableContent={(
          <table className="min-w-[980px] w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-border-hairline bg-surface-container-low">
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">反馈ID</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">消息ID</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">会话ID</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">投票</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">原因</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">评论</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">创建时间</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md text-right font-label-caps text-label-caps text-secondary">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {showEmptyState ? (
                <tr>
                  <td colSpan={8} className="px-lg py-xl text-center text-secondary">暂无反馈记录</td>
                </tr>
              ) : records.map((item) => (
                <tr key={item.id} className="transition-colors hover:bg-surface-container-low">
                  <td className="px-lg py-md text-ink">{item.id}</td>
                  <td className="px-lg py-md text-secondary">{item.messageId}</td>
                  <td className="px-lg py-md text-secondary">{item.conversationId}</td>
                  <td className="px-lg py-md">
                    <span className={item.vote === 1 ? 'text-status-running' : 'text-error'}>
                      {item.vote === 1 ? '点赞' : '点踩'}
                    </span>
                  </td>
                  <td className="max-w-[180px] truncate px-lg py-md text-secondary" title={item.reason || ''}>
                    {item.reason || '-'}
                  </td>
                  <td className="max-w-[220px] truncate px-lg py-md text-secondary" title={item.comment || ''}>
                    {item.comment || '-'}
                  </td>
                  <td className="px-lg py-md text-[12px] text-secondary">{formatDate(item.createdAt)}</td>
                  <td className="px-lg py-md text-right">
                    <Link
                      to={`/feedbacks/${item.id}`}
                      aria-label={`查看 ${item.id}`}
                      className="rounded-lg border border-border-strong bg-surface-container-lowest px-sm py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
                    >
                      查看
                    </Link>
                  </td>
                </tr>
              ))}
              {showSkeletonRows
                ? Array.from({ length: 10 }, (_, index) => (
                  <tr key={`feedback-loading-row-${index}`} data-testid="feedback-loading-skeleton-row">
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
      </section>
    </div>
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
