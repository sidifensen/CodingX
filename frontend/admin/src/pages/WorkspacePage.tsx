import React from 'react';
import { Link } from 'react-router-dom';
import { EyeOutlined, FilterOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Alert, Button, Input, Select, Space, Statistic, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import {
  AdminChatApi,
  type AdminPageResult,
  type AdminWorkspace,
} from '../api/adminChatApi';
import { AdminDataTable, AdminTableActions } from '../components/AdminDataTable';

const PAGE_SIZE = 10;

type RuntimeTargetFilter = 'ALL' | 'cloud' | 'local';

/**
 * 管理端工作空间管理页：只读展示空间归属、运行目标和关联会话规模。
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
  const showSkeletonRows = loading && records.length === 0;
  const cloudCount = records.filter((item) => item.runtimeTarget === 'cloud').length;
  const localCount = records.filter((item) => item.runtimeTarget === 'local').length;
  const conversationCount = records.reduce((sum, item) => sum + Number(item.conversationCount ?? 0), 0);

  const handleFilter = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
    setRuntimeTarget(runtimeTargetInput);
  };

  const columns = React.useMemo<ColumnsType<AdminWorkspace>>(() => [
    {
      title: '工作空间',
      dataIndex: 'name',
      fixed: 'left',
      width: 240,
      render: (_, item) => (
        <Link to={`/workspaces/${item.id}`} className="inline-flex flex-col">
          <Typography.Text strong>{item.name || '-'}</Typography.Text>
          <Typography.Text type="secondary" code>#{item.id}</Typography.Text>
        </Link>
      ),
    },
    {
      title: '运行目标',
      dataIndex: 'runtimeTargetLabel',
      width: 120,
      render: (_, item) => <RuntimeTargetBadge item={item} />,
    },
    {
      title: '创建人',
      dataIndex: 'createdBy',
      width: 120,
      render: (value) => value ?? '-',
    },
    {
      title: '会话数',
      dataIndex: 'conversationCount',
      width: 110,
      render: (value) => Number(value ?? 0).toLocaleString('zh-CN'),
    },
    {
      title: '仓库',
      dataIndex: 'repositoryUrl',
      width: 260,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '分支',
      dataIndex: 'branchName',
      width: 140,
      render: (value?: string) => value || '-',
    },
    {
      title: '本地目录',
      dataIndex: 'workingDirectory',
      width: 260,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 180,
      render: formatDate,
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
            href: `/workspaces/${item.id}`,
          }]}
        />
      ),
    },
  ], []);

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-col gap-md lg:flex-row lg:items-end lg:justify-between">
        <div>
          <Typography.Text type="secondary">Workspace Inventory</Typography.Text>
          <Typography.Title level={2} style={{ margin: 0 }}>工作空间管理</Typography.Title>
          <Typography.Text type="secondary">只读查看云端历史与本地目录空间，快速排查会话归属、仓库上下文和空间活跃度。</Typography.Text>
        </div>
        <Space wrap align="end">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            style={{ width: 260 }}
            value={keywordInput}
            placeholder="名称 / 仓库 / 目录 / ID"
            onChange={(event) => setKeywordInput(event.target.value)}
            onPressEnter={handleFilter}
          />
          <Select<RuntimeTargetFilter>
            aria-label="运行目标"
            style={{ width: 140 }}
            value={runtimeTargetInput}
            options={[
              { value: 'ALL', label: '全部' },
              { value: 'cloud', label: '云端' },
              { value: 'local', label: '本地' },
            ]}
            onChange={setRuntimeTargetInput}
          />
          <Button aria-label="筛选" icon={<FilterOutlined />} type="primary" onClick={handleFilter}>
            筛选
          </Button>
          <Button aria-label="刷新" icon={<ReloadOutlined />} onClick={() => void loadData(pageNo)}>
            刷新
          </Button>
        </Space>
      </header>

      <section className="grid gap-md md:grid-cols-3">
        <MetricItem title="当前页工作空间" value={records.length} suffix={`总计 ${total.toLocaleString('zh-CN')} 个`} />
        <MetricItem title="云端 / 本地" value={`${cloudCount} / ${localCount}`} suffix="按当前页记录统计" />
        <MetricItem title="关联会话" value={conversationCount} suffix="当前页未删除会话合计" />
      </section>

      {errorMessage ? <Alert showIcon type="error" message={errorMessage} /> : null}

      {showSkeletonRows ? (
        <div className="sr-only">
          {Array.from({ length: 10 }, (_, index) => <span key={index} data-testid="workspace-loading-skeleton-row" />)}
        </div>
      ) : null}

      <AdminDataTable<AdminWorkspace>
        columns={columns}
        dataSource={records}
        loading={loading}
        locale={{ emptyText: loading ? '加载中...' : '暂无工作空间' }}
        pagination={{
          current,
          pageSize: PAGE_SIZE,
          total,
          onChange: (nextPage) => setPageNo(nextPage),
        }}
        rowKey="id"
        scroll={{ x: 1440 }}
      />
    </div>
  );
}

/**
 * 顶部指标项使用 AntD Statistic，同时保留中性边界以适配深色主题。
 */
function MetricItem({ title, value, suffix }: { title: string; value: number | string; suffix: string }) {
  return (
    <div className="rounded-xl border border-border-hairline bg-surface-container-lowest p-md">
      <Statistic title={title} value={value} />
      <Typography.Text type="secondary">{suffix}</Typography.Text>
    </div>
  );
}

function RuntimeTargetBadge({ item }: { item: AdminWorkspace }) {
  return <Tag color={item.runtimeTarget === 'local' ? 'success' : undefined}>{item.runtimeTargetLabel || '未知'}</Tag>;
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
