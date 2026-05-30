import React from 'react';
import { Alert, Button, Card, Space, Table, Typography } from 'antd';
import type { TableProps } from 'antd';
import { useParams } from 'react-router-dom';

import { AdminChatApi, type AdminChatMessageReference } from '../api/adminChatApi';

/**
 * 管理端反馈引用页：使用 Ant Design Table 展示反馈消息对应的参考来源证据。
 */
export function FeedbackReferencePage() {
  const { feedbackId = '' } = useParams();
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [references, setReferences] = React.useState<AdminChatMessageReference[]>([]);

  React.useEffect(() => {
    if (!feedbackId) {
      return;
    }
    const loadReferences = async () => {
      setLoading(true);
      setErrorMessage('');
      try {
        const data = await AdminChatApi.listFeedbackReferences(feedbackId);
        setReferences(data);
      } catch (error) {
        setErrorMessage(extractErrorMessage(error, '加载引用来源失败'));
      } finally {
        setLoading(false);
      }
    };
    void loadReferences();
  }, [feedbackId]);

  const columns: TableProps<AdminChatMessageReference>['columns'] = [
    {
      title: '序号',
      dataIndex: 'rankNo',
      width: 100,
      render: (value: number | undefined, _record, index) => value ?? index + 1,
    },
    {
      title: '来源标题',
      dataIndex: 'title',
      width: 260,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '站点',
      dataIndex: 'siteName',
      width: 180,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '来源类型',
      dataIndex: 'sourceType',
      width: 120,
      render: (value?: string) => value || '-',
    },
    {
      title: '摘要',
      dataIndex: 'snippet',
      width: 320,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '链接',
      dataIndex: 'url',
      fixed: 'right',
      width: 120,
      align: 'right',
      render: (value?: string, item?: AdminChatMessageReference) => (
        value ? (
          <Button
            aria-label={`打开来源 ${item?.title || value}`}
            href={value}
            rel="noreferrer"
            size="small"
            target="_blank"
            type="link"
          >
            打开来源
          </Button>
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        )
      ),
    },
  ];

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-wrap items-center justify-between gap-sm">
        <Typography.Title level={2} style={{ margin: 0 }}>
          反馈引用来源
        </Typography.Title>
        <Space wrap>
          <Button href={`/feedbacks/${feedbackId}`}>
            返回反馈详情
          </Button>
          <Button href="/feedbacks">
            返回列表
          </Button>
        </Space>
      </header>

      {errorMessage ? (
        <Alert showIcon type="error" message={errorMessage} />
      ) : null}

      <Card>
        <Table<AdminChatMessageReference>
          bordered
          columns={columns}
          dataSource={references}
          loading={loading}
          locale={{ emptyText: loading ? '加载中...' : '暂无引用来源' }}
          pagination={false}
          rowKey={(item) => String(item.id ?? `${item.messageId ?? 'reference'}-${item.rankNo ?? item.url ?? ''}`)}
          scroll={{ x: 1100 }}
        />
      </Card>
    </div>
  );
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
