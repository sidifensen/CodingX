import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeftOutlined, EyeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Alert, Button, Input, Space, Statistic, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import {
  AdminChatApi,
  type AdminChatConversationListItem,
  type AdminPageResult,
} from '../api/adminChatApi';
import { AdminDataTable, AdminTableActions } from '../components/AdminDataTable';

const PAGE_SIZE = 10;

/**
 * 管理端工作空间详情页：从空间维度查看内部会话，便于排查会话归属和本地目录绑定关系。
 */
export function WorkspaceDetailPage() {
  const { workspaceId = '' } = useParams();
  const navigate = useNavigate();
  const [pageNo, setPageNo] = React.useState(1);
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [pageData, setPageData] = React.useState<AdminPageResult<AdminChatConversationListItem> | null>(null);

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
  const activeCount = records.filter((item) => item.statusLabel === '活跃').length;
  const showSkeletonRows = loading && records.length === 0;

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  const columns = React.useMemo<ColumnsType<AdminChatConversationListItem>>(() => [
    {
      title: '会话ID',
      dataIndex: 'id',
      width: 140,
      render: (value) => <Typography.Text code>#{value}</Typography.Text>,
    },
    {
      title: '标题',
      dataIndex: 'title',
      width: 320,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '创建人',
      dataIndex: 'createdBy',
      width: 120,
      render: (value) => value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'statusLabel',
      width: 120,
      render: (_, item) => <ConversationStatusBadge item={item} />,
    },
    {
      title: '最近消息时间',
      dataIndex: 'lastMessageAt',
      width: 190,
      render: formatDateTime,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 190,
      render: formatDateTime,
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 140,
      align: 'right',
      render: (_, item) => (
        <AdminTableActions
          actions={[{
            key: 'detail',
            label: '查看详情',
            icon: <EyeOutlined />,
            onClick: () => navigate(`/tasks/${item.id}`),
          }]}
        />
      ),
    },
  ], [navigate]);

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-col gap-md lg:flex-row lg:items-end lg:justify-between">
        <div className="space-y-sm">
          <Button icon={<ArrowLeftOutlined />} type="link" onClick={() => navigate('/workspaces')}>
            返回工作空间列表
          </Button>
          <div>
            <Typography.Text type="secondary">Workspace Conversations</Typography.Text>
            <Typography.Title level={2} style={{ margin: 0 }}>工作空间 #{workspaceId || '-'}</Typography.Title>
            <Typography.Text type="secondary">查看该工作空间内的会话列表，并继续进入单个会话详情排查消息历史。</Typography.Text>
          </div>
        </div>
        <Space wrap>
          <Input
            allowClear
            prefix={<SearchOutlined />}
            style={{ width: 280 }}
            value={keywordInput}
            placeholder="搜索会话标题或 ID"
            onChange={(event) => setKeywordInput(event.target.value)}
            onPressEnter={handleSearch}
          />
          <Button aria-label="查询" icon={<SearchOutlined />} type="primary" onClick={handleSearch}>
            查询
          </Button>
          <Button aria-label="刷新" icon={<ReloadOutlined />} onClick={() => void loadConversations(pageNo)}>
            刷新
          </Button>
        </Space>
      </header>

      <section className="grid gap-md md:grid-cols-3">
        <MetricItem title="工作空间" value={`#${workspaceId || '-'}`} hint="当前查看对象" />
        <MetricItem title="会话总数" value={total.toLocaleString('zh-CN')} hint="按后端分页结果统计" />
        <MetricItem title="当前页活跃" value={activeCount} hint="仅统计当前页记录" />
      </section>

      {errorMessage ? <Alert showIcon type="error" message={errorMessage} /> : null}

      {showSkeletonRows ? (
        <div className="sr-only">
          {Array.from({ length: 10 }, (_, index) => <span key={index} data-testid="workspace-conversation-loading-skeleton-row" />)}
        </div>
      ) : null}

      <AdminDataTable<AdminChatConversationListItem>
        columns={columns}
        dataSource={records}
        loading={loading}
        locale={{ emptyText: loading ? '会话加载中...' : '当前工作空间暂无会话' }}
        pagination={{
          current,
          pageSize: PAGE_SIZE,
          total,
          onChange: (nextPage) => setPageNo(nextPage),
        }}
        rowKey="id"
        scroll={{ x: 1120 }}
      />
    </div>
  );
}

function MetricItem({ title, value, hint }: { title: string; value: number | string; hint: string }) {
  return (
    <div className="rounded-xl border border-border-hairline bg-surface-container-lowest p-md">
      <Statistic title={title} value={value} />
      <Typography.Text type="secondary">{hint}</Typography.Text>
    </div>
  );
}

function ConversationStatusBadge({ item }: { item: AdminChatConversationListItem }) {
  const isActive = item.statusLabel === '活跃';
  return <Tag color={isActive ? 'success' : undefined}>{item.statusLabel || item.status || '未知'}</Tag>;
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
