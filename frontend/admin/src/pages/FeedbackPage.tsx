import React from 'react';
import { Alert, Typography } from 'antd';

import { AdminChatApi, type AdminChatMessageFeedback, type AdminPageResult } from '../api/adminChatApi';
import { FeedbackFilterBar, type FeedbackVoteFilter } from './feedback/FeedbackFilterBar';
import { FeedbackTable } from './feedback/FeedbackTable';
import { normalizeFeedbackPagination } from './feedback/feedbackPagination';

const PAGE_SIZE = 10;

/**
 * 管理端反馈列表页：使用 Ant Design 表单与表格统一承载筛选、分页和只读排查入口。
 */
export function FeedbackPage() {
  const [pageNo, setPageNo] = React.useState(1);
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const [voteFilterInput, setVoteFilterInput] = React.useState<FeedbackVoteFilter>(0);
  const [voteFilter, setVoteFilter] = React.useState<FeedbackVoteFilter>(0);
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [pageData, setPageData] = React.useState<AdminPageResult<AdminChatMessageFeedback> | null>(null);

  /**
   * 拉取反馈分页数据，集中处理筛选条件、分页和后端错误文案。
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
  const paginationState = normalizeFeedbackPagination(pageData, records.length, PAGE_SIZE, pageNo);

  const handleFilter = ({ keyword: nextKeyword, vote: nextVote }: { keyword: string; vote: FeedbackVoteFilter }) => {
    setPageNo(1);
    setKeyword(nextKeyword);
    setVoteFilter(nextVote);
  };

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <Typography.Title level={2} style={{ margin: 0 }}>
            反馈管理
          </Typography.Title>
          <Typography.Text type="secondary">
            统一查看用户点赞/点踩反馈，并支持按反馈记录进入详情页排查
          </Typography.Text>
        </div>
        <FeedbackFilterBar
          keywordValue={keywordInput}
          voteValue={voteFilterInput}
          loading={loading}
          onKeywordChange={setKeywordInput}
          onVoteChange={setVoteFilterInput}
          onFilter={handleFilter}
          onRefresh={() => void loadData(pageNo)}
        />
      </header>

      {errorMessage ? (
        <Alert showIcon type="error" message={errorMessage} />
      ) : null}

      <FeedbackTable
        records={records}
        loading={loading}
        pagination={paginationState}
        onPageChange={setPageNo}
      />
    </div>
  );
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
