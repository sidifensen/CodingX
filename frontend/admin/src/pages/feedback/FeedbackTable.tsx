import React from 'react';
import { EyeOutlined } from '@ant-design/icons';
import { Tag } from 'antd';
import type { TableProps } from 'antd';

import type { AdminChatMessageFeedback } from '../../api/adminChatApi';
import { AdminDataTable, AdminTableActions } from '../../components/AdminDataTable';
import type { FeedbackPaginationState } from './feedbackPagination';

interface FeedbackTableProps {
  records: AdminChatMessageFeedback[];
  loading: boolean;
  pagination: FeedbackPaginationState;
  onPageChange: (page: number) => void;
}

/**
 * 反馈表格：集中定义列、投票标签、日期展示和 AntD 分页文本。
 */
export function FeedbackTable({ records, loading, pagination, onPageChange }: FeedbackTableProps) {
  const { current, pages, pageSize, total } = pagination;
  const columns = React.useMemo<TableProps<AdminChatMessageFeedback>['columns']>(() => [
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
        <AdminTableActions
          actions={[
            {
              key: 'view',
              label: '查看',
              ariaLabel: `查看 ${item.id}`,
              href: `/feedbacks/${item.id}`,
              icon: <EyeOutlined />,
              type: 'link',
            },
          ]}
        />
      ),
    },
  ], []);

  return (
    <AdminDataTable<AdminChatMessageFeedback>
      columns={columns}
      dataSource={records}
      loading={loading}
      locale={{ emptyText: loading ? '加载中...' : '暂无反馈记录' }}
      pagination={{
        current,
        pageSize,
        total,
        onChange: onPageChange,
      }}
      rowKey="id"
      scroll={{ x: 1100 }}
    />
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
