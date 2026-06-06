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
              className="[&_.ant-list-items]:space-y-md"
              dataSource={detail.messages}
              locale={{ emptyText: <Empty description="当前会话暂无消息" /> }}
              split={false}
              renderItem={(message) => <MessageItem message={message} />}
            />
          </div>
        </section>
      ) : null}
    </div>
  );
}

/**
 * 会话消息项显式覆盖 AntD List.Item 默认 padding，避免长文本、Markdown 表格和附件列表贴边显示。
 */
function MessageItem({ message }: { message: AdminChatConversationMessage }) {
  const attachments = message.attachments ?? [];

  return (
    <List.Item className="rounded-xl border border-border-hairline bg-surface-container-lowest !p-md sm:!p-lg">
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
          <div className="space-y-md">
            <Descriptions
              bordered
              column={{ xs: 1, md: 2, xl: 3 }}
              items={buildMessageMetadataItems(message, attachments.length)}
              size="small"
            />
            <MessageTextBlock title="消息正文" content={message.content} />
            <MessageTextBlock title="深度思考内容" content={message.thinkingContent} />
            {message.errorMessage ? <Alert showIcon type="error" message={message.errorMessage} /> : null}
            <AttachmentDetails attachments={attachments} />
          </div>
        )}
      />
    </List.Item>
  );
}

/**
 * 消息元信息固定展示接口返回的所有轻量字段，长文本和附件交给独立区块避免挤压表格。
 */
function buildMessageMetadataItems(message: AdminChatConversationMessage, attachmentCount: number) {
  return [
    { key: 'id', label: '消息ID', children: <DetailText code value={message.id} /> },
    { key: 'conversationId', label: '会话ID', children: <DetailText code value={message.conversationId} /> },
    { key: 'runId', label: '运行ID', children: <DetailText code value={message.runId} /> },
    { key: 'role', label: '角色', children: `${toRoleLabel(message.role)} (${formatNullable(message.role)})` },
    { key: 'status', label: '状态', children: `${toMessageStatusLabel(message.status)} (${formatNullable(message.status)})` },
    { key: 'createdAt', label: '创建时间', children: formatDateTimeFromUnknown(message.createdAt) },
    { key: 'updatedAt', label: '更新时间', children: formatDateTimeFromUnknown(message.updatedAt) },
    { key: 'deleted', label: '逻辑删除', children: formatDeleted(message.deleted) },
    { key: 'provider', label: '供应商', children: <DetailText value={message.provider} /> },
    { key: 'model', label: '模型', children: <DetailText value={message.model} /> },
    { key: 'thinkingDuration', label: '思考耗时', children: formatDuration(message.thinkingDuration) },
    { key: 'userVote', label: '用户反馈', children: formatVote(message.userVote) },
    { key: 'attachmentCount', label: '附件数量', children: String(attachmentCount) },
    { key: 'errorMessage', label: '错误信息', children: <DetailText value={message.errorMessage} /> },
  ];
}

/**
 * 消息长文本区块保留换行和 Markdown 原文，便于管理员核对持久化内容。
 */
function MessageTextBlock({ title, content }: { title: string; content?: string | null }) {
  return (
    <div className="space-y-xs">
      <Typography.Text strong className="text-ink">{title}</Typography.Text>
      <Typography.Paragraph className="whitespace-pre-wrap break-words text-ink" style={{ marginBottom: 0 }}>
        {formatNullable(content)}
      </Typography.Paragraph>
    </div>
  );
}

/**
 * 附件明细展示附件响应中的全部字段；无附件时也保留空状态，避免误判为加载失败。
 */
function AttachmentDetails({ attachments }: { attachments: NonNullable<AdminChatConversationMessage['attachments']> }) {
  return (
    <div className="space-y-sm">
      <Typography.Text strong className="text-ink">附件明细</Typography.Text>
      {attachments.length > 0 ? (
        <List
          className="[&_.ant-list-items]:space-y-sm"
          dataSource={attachments}
          renderItem={(attachment, index) => (
            <List.Item className="rounded-lg border border-border-hairline bg-surface-container-low !p-sm">
              <Descriptions
                bordered
                column={{ xs: 1, md: 2, xl: 3 }}
                items={buildAttachmentMetadataItems(attachment, index)}
                size="small"
              />
            </List.Item>
          )}
          size="small"
          split={false}
        />
      ) : (
        <Typography.Text type="secondary">无附件</Typography.Text>
      )}
    </div>
  );
}

function buildAttachmentMetadataItems(
  attachment: NonNullable<AdminChatConversationMessage['attachments']>[number],
  index: number,
) {
  return [
    { key: 'index', label: '序号', children: String(index + 1) },
    { key: 'id', label: '附件ID', children: <DetailText code value={attachment.id} /> },
    { key: 'conversationId', label: '会话ID', children: <DetailText code value={attachment.conversationId} /> },
    { key: 'messageId', label: '消息ID', children: <DetailText code value={attachment.messageId} /> },
    { key: 'attachmentType', label: '附件类型', children: <DetailText value={attachment.attachmentType} /> },
    { key: 'fileName', label: '文件名', children: <DetailText value={attachment.fileName} /> },
    { key: 'fileExt', label: '扩展名', children: <DetailText value={attachment.fileExt} /> },
    { key: 'mimeType', label: 'MIME', children: <DetailText value={attachment.mimeType} /> },
    { key: 'fileSize', label: '文件大小', children: formatOptionalFileSize(attachment.fileSize) },
    { key: 'previewUrl', label: '预览地址', children: <DetailText value={attachment.previewUrl} /> },
    { key: 'contentSummary', label: '内容摘要', children: <DetailText value={attachment.contentSummary} /> },
    { key: 'status', label: '状态', children: <DetailText value={attachment.status} /> },
    { key: 'createdAt', label: '创建时间', children: formatDateTimeFromUnknown(attachment.createdAt) },
  ];
}

function DetailText({ value, code = false }: { value: unknown; code?: boolean }) {
  const text = formatNullable(value);
  if (code && text !== '-') {
    return <Typography.Text code>{text}</Typography.Text>;
  }
  return <Typography.Text className="whitespace-pre-wrap break-words text-ink">{text}</Typography.Text>;
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

function formatNullable(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  if (Array.isArray(value)) {
    return value.length > 0 ? value.map((item) => String(item)).join(', ') : '-';
  }
  if (typeof value === 'boolean') {
    return value ? 'true' : 'false';
  }
  return String(value);
}

function formatDateTimeFromUnknown(value: unknown): string {
  if (typeof value !== 'string') {
    return formatNullable(value);
  }
  return formatDateTime(value);
}

function formatDuration(value?: number): string {
  if (!Number.isFinite(value)) {
    return '-';
  }
  return `${value} 秒`;
}

function formatVote(value?: number | null): string {
  if (value === 1) {
    return '点赞 (1)';
  }
  if (value === -1) {
    return '点踩 (-1)';
  }
  if (typeof value === 'number') {
    return String(value);
  }
  return '未反馈';
}

function formatDeleted(value?: number): string {
  if (value === 0) {
    return '未删除 (0)';
  }
  if (value === 1) {
    return '已删除 (1)';
  }
  return formatNullable(value);
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

function formatOptionalFileSize(size?: number): string {
  if (size === null || size === undefined) {
    return '-';
  }
  return formatFileSize(size);
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
