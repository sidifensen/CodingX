import React from 'react';
import { EyeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Alert, Button, Input, Segmented, Space, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';

import {
  AdminChatApi,
  type AdminChatConversationListItem,
  type AdminPageResult,
} from '../api/adminChatApi';
import { AdminDataTable, AdminTableActions } from '../components/AdminDataTable';

const PAGE_SIZE = 10;

/**
 * 管理端会话列表页：承载会话检索、状态筛选与详情跳转。
 */
export function Tasks() {
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = React.useState<'全部' | '活跃' | '已归档'>('全部');
  const [keywordInput, setKeywordInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');
  const [pageNo, setPageNo] = React.useState(1);
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [pageData, setPageData] = React.useState<AdminPageResult<AdminChatConversationListItem> | null>(null);

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
      render: (value: string) => value || '-',
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
      render: (value: string) => <Tag color={value === '活跃' ? 'success' : undefined}>{value || '未知'}</Tag>,
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
          actions={[
            {
              key: 'detail',
              label: '查看详情',
              icon: <EyeOutlined />,
              onClick: () => navigate(`/tasks/${item.id}`),
            },
          ]}
        />
      ),
    },
  ], [navigate]);

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <Typography.Title level={2} style={{ margin: 0 }}>会话管理</Typography.Title>
          <Typography.Text type="secondary">查看系统会话状态、最后消息时间与消息详情。</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => void loadConversations(pageNo)}>
          刷新
        </Button>
      </header>

      {errorMessage ? <Alert showIcon type="error" message={errorMessage} /> : null}

      <section className="rounded-xl border border-border-hairline bg-surface-container-lowest p-md">
        <div className="flex flex-wrap items-center justify-between gap-sm">
          <Segmented
            options={['全部', '活跃', '已归档']}
            value={statusFilter}
            onChange={(value) => setStatusFilter(value as typeof statusFilter)}
          />
          <Space.Compact>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              style={{ width: 280 }}
              value={keywordInput}
              placeholder="搜索会话标题或 ID"
              onChange={(event) => setKeywordInput(event.target.value)}
              onPressEnter={handleSearch}
            />
            <Button icon={<SearchOutlined />} type="primary" onClick={handleSearch}>
              查询
            </Button>
          </Space.Compact>
        </div>
      </section>

      <AdminDataTable<AdminChatConversationListItem>
        columns={columns}
        dataSource={records}
        loading={loading}
        locale={{ emptyText: loading ? '会话加载中...' : '暂无会话数据' }}
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
