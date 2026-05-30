import React from 'react';
import { Alert, Button, Form, Input, Select, Space, Table, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';

import { AdminChatApi, type AdminChatMessageFeedback, type AdminPageResult } from '../api/adminChatApi';

const PAGE_SIZE = 10;

/**
 * 管理端反馈列表页：使用 Ant Design 表单与表格统一承载筛选、分页和只读排查入口。
 */
export function FeedbackPage() {
  const [pageNo, setPageNo] = React.useState(1);
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const [voteFilterInput, setVoteFilterInput] = React.useState<0 | 1 | -1>(0);
  const [voteFilter, setVoteFilter] = React.useState<0 | 1 | -1>(0);
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
  const { current, pages, pageSize, total } = paginationState;

  const handleFilter = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
    setVoteFilter(voteFilterInput);
  };

  const columns: TableProps<AdminChatMessageFeedback>['columns'] = [
    {
      title: '反馈ID',
      dataIndex: 'id',
      width: 120,
    },
    {
      title: '消息ID',
      dataIndex: 'messageId',
      width: 140,
    },
    {
      title: '会话ID',
      dataIndex: 'conversationId',
      width: 140,
    },
    {
      title: '投票',
      dataIndex: 'vote',
      width: 110,
      render: (vote: number) => renderVoteTag(vote),
    },
    {
      title: '原因',
      dataIndex: 'reason',
      width: 180,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '评论',
      dataIndex: 'comment',
      width: 220,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 190,
      render: (value?: string) => formatDate(value),
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 100,
      align: 'right',
      render: (_, item) => (
        <Button
          aria-label={`查看 ${item.id}`}
          href={`/feedbacks/${item.id}`}
          size="small"
          type="link"
        >
          查看
        </Button>
      ),
    },
  ];

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
        <Form layout="inline" className="ant-feedback-filter-form">
          <Form.Item label="关键词">
            <Input
              allowClear
              aria-label="关键词"
              placeholder="原因或评论"
              style={{ width: 220 }}
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              onPressEnter={handleFilter}
            />
          </Form.Item>
          <Form.Item label="投票筛选">
            <Select
              aria-label="投票筛选"
              options={[
                { label: '全部', value: 0 },
                { label: '仅点赞', value: 1 },
                { label: '仅点踩', value: -1 },
              ]}
              style={{ width: 140 }}
              value={voteFilterInput}
              onChange={(value) => setVoteFilterInput(value as 0 | 1 | -1)}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" onClick={handleFilter}>
                筛选
              </Button>
              <Button onClick={() => void loadData(pageNo)}>
                刷新
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </header>

      {errorMessage ? (
        <Alert showIcon type="error" message={errorMessage} />
      ) : null}

      <Table<AdminChatMessageFeedback>
        bordered
        columns={columns}
        dataSource={records}
        loading={loading}
        locale={{ emptyText: loading ? '加载中...' : '暂无反馈记录' }}
        pagination={{
          current,
          pageSize,
          showSizeChanger: false,
          showTotal: () => `第 ${current} / ${Math.max(1, pages)} 页，共 ${total.toLocaleString('zh-CN')} 条`,
          total,
          onChange: setPageNo,
        }}
        rowKey="id"
        scroll={{ x: 1100 }}
      />
    </div>
  );
}

function renderVoteTag(vote: number) {
  if (vote === 1) {
    return <Tag color="success">点赞</Tag>;
  }
  if (vote === -1) {
    return <Tag color="error">点踩</Tag>;
  }
  return <Tag>-</Tag>;
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

function normalizeFeedbackPagination(
  pageData: AdminPageResult<AdminChatMessageFeedback> | null,
  recordCount: number,
  fallbackSize: number,
  fallbackCurrent: number,
): { current: number; pages: number; pageSize: number; total: number } {
  if (!pageData) {
    return { current: fallbackCurrent, pages: 1, pageSize: fallbackSize, total: 0 };
  }
  const rawTotal = Number(pageData.total);
  const rawPages = Number(pageData.pages);
  const rawSize = Number(pageData.size);
  const rawCurrent = Number(pageData.current);
  const safeSize = Number.isFinite(rawSize) && rawSize > 0 ? rawSize : fallbackSize;

  /**
   * 部分历史/降级接口会返回 records，但 total/pages 都是 0；此时按“一页完整数据”
   * 展示，避免 AntD 根据 dataSource 长度在前端拆出并不存在的第 2 页。
   */
  if (recordCount > 0 && (!Number.isFinite(rawTotal) || rawTotal <= 0) && (!Number.isFinite(rawPages) || rawPages <= 0)) {
    return {
      current: 1,
      pages: 1,
      pageSize: Math.max(safeSize, recordCount),
      total: recordCount,
    };
  }

  const total = Number.isFinite(rawTotal) && rawTotal >= 0 ? rawTotal : recordCount;
  const pageSize = safeSize;
  const pages = Number.isFinite(rawPages) && rawPages > 0
    ? rawPages
    : Math.max(1, Math.ceil(Math.max(total, recordCount) / Math.max(1, pageSize)));
  // 后端分页结果可能在筛选收窄后短暂返回越界页码，展示层统一夹到有效范围。
  const current = Math.min(Math.max(1, Number.isFinite(rawCurrent) ? rawCurrent : fallbackCurrent), Math.max(1, pages));
  return {
    current,
    pages,
    pageSize,
    total,
  };
}
