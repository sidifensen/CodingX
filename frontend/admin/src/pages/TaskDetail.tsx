import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Descriptions, Empty, List, Space, Spin, Tag, Typography } from 'antd';

import {
  AdminChatApi,
  type AdminChatConversationDetail,
  type AdminChatConversationMessage,
} from '../api/adminChatApi';

/**
 * 管理端会话详情页：展示会话元信息与消息历史。
 */
export function TaskDetail() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = React.useState<AdminChatConversationDetail | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');

  const loadDetail = React.useCallback(async () => {
    if (!id) {
      return;
    }
    setLoading(true);
    setErrorMessage('');
    try {
      const data = await AdminChatApi.getConversationDetail(id);
      setDetail(data);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '会话详情加载失败'));
      setDetail(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  React.useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex items-center justify-between gap-sm">
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
          返回会话列表
        </Button>
        <Button icon={<ReloadOutlined />} onClick={() => void loadDetail()}>
          刷新
        </Button>
      </header>

      {errorMessage ? <Alert showIcon type="error" message={errorMessage} /> : null}
      {loading ? <Spin tip="会话详情加载中..." /> : null}
      {!loading && !detail ? <Empty description="暂无会话详情" /> : null}

      {!loading && detail ? (
        <section className="space-y-lg rounded-2xl border border-border-hairline bg-surface-container-lowest p-xl">
          <div className="flex flex-col gap-sm lg:flex-row lg:items-start lg:justify-between">
            <div>
              <Space className="mb-sm">
                <Typography.Text code>#{detail.id}</Typography.Text>
                <Tag color={detail.statusLabel === '活跃' ? 'success' : undefined}>{detail.statusLabel}</Tag>
              </Space>
              <Typography.Title level={2} style={{ margin: 0 }}>{detail.title || '未命名会话'}</Typography.Title>
            </div>
          </div>

          <Descriptions
            bordered
            column={{ xs: 1, md: 2, xl: 3 }}
            items={[
              { key: 'createdBy', label: '创建人', children: String(detail.createdBy) },
              { key: 'createdAt', label: '创建时间', children: formatDateTime(detail.createdAt) },
              { key: 'updatedAt', label: '更新时间', children: formatDateTime(detail.updatedAt) },
              { key: 'lastMessageAt', label: '最近消息', children: formatDateTime(detail.lastMessageAt) },
              { key: 'lastRunId', label: '最近运行ID', children: detail.lastRunId ? String(detail.lastRunId) : '-' },
              { key: 'messageCount', label: '消息数量', children: String(detail.messages?.length ?? 0) },
            ]}
          />

          <div>
            <Typography.Title level={3}>会话消息</Typography.Title>
            <List
              dataSource={detail.messages}
              locale={{ emptyText: <Empty description="当前会话暂无消息" /> }}
              renderItem={(message) => <MessageItem message={message} />}
            />
          </div>
        </section>
      ) : null}
    </div>
  );
}

function MessageItem({ message }: { message: AdminChatConversationMessage }) {
  return (
    <List.Item className="rounded-xl border border-border-hairline bg-surface-container-lowest px-lg">
      <List.Item.Meta
        title={(
          <Space wrap>
            <Typography.Text code>#{message.id}</Typography.Text>
            <Tag color={toRoleColor(message.role)}>{toRoleLabel(message.role)}</Tag>
            <Typography.Text type="secondary">{toMessageStatusLabel(message.status)}</Typography.Text>
            <Typography.Text type="secondary">{formatDateTime(message.createdAt)}</Typography.Text>
          </Space>
        )}
        description={(
          <div className="space-y-sm">
            <Typography.Paragraph className="whitespace-pre-wrap break-words text-ink">
              {message.content || '-'}
            </Typography.Paragraph>
            {message.errorMessage ? <Alert showIcon type="error" message={message.errorMessage} /> : null}
            {message.attachments && message.attachments.length > 0 ? (
              <List
                size="small"
                dataSource={message.attachments}
                renderItem={(attachment) => (
                  <List.Item>
                    {attachment.fileName}
                    {attachment.fileSize ? <Typography.Text type="secondary"> ({formatFileSize(attachment.fileSize)})</Typography.Text> : null}
                  </List.Item>
                )}
              />
            ) : null}
          </div>
        )}
      />
    </List.Item>
  );
}

function toRoleLabel(role: string): string {
  if (role === 'USER') return '用户';
  if (role === 'ASSISTANT') return '助手';
  if (role === 'SYSTEM') return '系统';
  return role || '未知角色';
}

function toRoleColor(role: string): string | undefined {
  if (role === 'USER') return 'default';
  if (role === 'ASSISTANT') return 'success';
  if (role === 'SYSTEM') return 'warning';
  return undefined;
}

function toMessageStatusLabel(status: string): string {
  if (status === 'PENDING') return '处理中';
  if (status === 'COMPLETED') return '完成';
  if (status === 'FAILED') return '失败';
  if (status === 'CANCELLED') return '已取消';
  return status || '未知状态';
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

function formatFileSize(size: number): string {
  if (!Number.isFinite(size) || size <= 0) return '0 B';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
