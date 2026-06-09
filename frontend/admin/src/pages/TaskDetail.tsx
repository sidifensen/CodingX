import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Empty, List, Space, Spin, Tag, Typography } from 'antd';

import {
  AdminChatApi,
  type AdminChatConversationDetail,
  type AdminChatConversationMessage,
} from '../api/adminChatApi';

interface MetadataItem {
  key: string;
  label: string;
  value: React.ReactNode;
}

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
    <div className="w-full min-w-0 space-y-xl overflow-x-hidden p-lg">
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
        <section className="space-y-xl rounded-2xl border border-border-hairline bg-surface-container-lowest p-lg sm:p-xl">
          <div className="flex flex-col gap-md lg:flex-row lg:items-start lg:justify-between">
            <div>
              <Space className="mb-sm">
                <Typography.Text code>#{detail.id}</Typography.Text>
                <Tag color={detail.statusLabel === '活跃' ? 'success' : undefined}>{detail.statusLabel}</Tag>
              </Space>
              <Typography.Title level={2} style={{ margin: 0 }}>{detail.title || '未命名会话'}</Typography.Title>
            </div>
          </div>

          <MetadataStrip items={buildConversationMetadataItems(detail)} />

          <div
            className="border-t border-border-hairline pt-xl"
            style={{ marginTop: 40 }}
          >
            <Typography.Title level={3} style={{ margin: 0, marginBottom: 24, lineHeight: 1.35 }}>
              会话消息
            </Typography.Title>
            <List
              className="[&_.ant-list-items]:space-y-sm"
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
 * 会话消息项显式覆盖 AntD List.Item 默认 padding，在排查密度和正文可读性之间保持平衡。
 */
function MessageItem({ message }: { message: AdminChatConversationMessage }) {
  const attachments = message.attachments ?? [];
  const headerMetadataItems = buildMessageHeaderMetadataItems(message, attachments.length);

  return (
    <List.Item className="rounded-xl border border-border-hairline bg-surface-container-lowest !px-lg !py-md sm:!px-xl">
      <List.Item.Meta
        className="admin-task-message-meta"
        title={(
          <Space className="w-full" size={[8, 8]} wrap>
            <Typography.Text code>#{message.id}</Typography.Text>
            <Tag color={toRoleColor(message.role)}>{toRoleLabel(message.role)}</Tag>
            <Typography.Text type="secondary">{toMessageStatusLabel(message.status)}</Typography.Text>
            <Typography.Text type="secondary">{formatDateTime(message.createdAt)}</Typography.Text>
            {headerMetadataItems.map((item) => (
              <InlineHeaderMeta key={item.key} item={item} />
            ))}
          </Space>
        )}
        description={(
          <div className="pt-md">
            <div className="space-y-md border-t border-border-hairline pt-md">
              <MessageTextBlock title="消息正文" content={message.content} />
              {hasValue(message.thinkingContent) ? (
                <MessageTextBlock title="深度思考内容" content={message.thinkingContent} />
              ) : null}
              {message.errorMessage ? <Alert showIcon type="error" message={message.errorMessage} /> : null}
              {attachments.length > 0 ? <AttachmentDetails attachments={attachments} /> : null}
            </div>
          </div>
        )}
      />
    </List.Item>
  );
}

/**
 * 会话概要只展示管理员排查最常用字段，避免旧表格把正文内容挤到首屏之外。
 */
function buildConversationMetadataItems(detail: AdminChatConversationDetail): MetadataItem[] {
  return [
    { key: 'createdBy', label: '创建人', value: String(detail.createdBy) },
    { key: 'createdAt', label: '创建时间', value: formatDateTime(detail.createdAt) },
    { key: 'lastMessageAt', label: '最近消息', value: formatDateTime(detail.lastMessageAt) },
    { key: 'lastRunId', label: '最近运行ID', value: <InlineCode value={detail.lastRunId} /> },
    { key: 'updatedAt', label: '更新时间', value: formatDateTime(detail.updatedAt) },
    { key: 'messageCount', label: '消息数量', value: String(detail.messages?.length ?? 0) },
  ];
}

/**
 * 消息头部承载运行与模型信息，让管理员在一行内完成消息身份和执行上下文识别。
 */
function buildMessageHeaderMetadataItems(message: AdminChatConversationMessage, attachmentCount: number): MetadataItem[] {
  const items: Array<MetadataItem | false> = [
    { key: 'conversationId', label: '会话ID', value: <InlineCode value={message.conversationId} /> },
    { key: 'runId', label: '运行ID', value: <InlineCode value={message.runId} /> },
    hasValue(message.model) && { key: 'model', label: '模型', value: <DetailText value={message.model} /> },
    hasValue(message.provider) && { key: 'provider', label: '供应商', value: <DetailText value={message.provider} /> },
    hasFiniteNumber(message.thinkingDuration) && {
      key: 'thinkingDuration',
      label: '思考耗时',
      value: formatDuration(message.thinkingDuration),
    },
    { key: 'attachmentCount', label: '附件', value: String(attachmentCount) },
    { key: 'userVote', label: '反馈', value: formatVote(message.userVote) },
    { key: 'deleted', label: '逻辑删除', value: formatDeleted(message.deleted) },
    hasValue(message.updatedAt) && message.updatedAt !== message.createdAt && {
      key: 'updatedAt',
      label: '更新时间',
      value: formatDateTimeFromUnknown(message.updatedAt),
    },
  ];
  return items.filter(Boolean) as MetadataItem[];
}

/**
 * 消息头部的次级信息采用行内弱标签，避免再生成占高度的详情区。
 */
function InlineHeaderMeta({ item }: { item: MetadataItem }) {
  return (
    <span className="inline-flex min-w-0 items-center gap-xs rounded-md border border-border-hairline bg-surface-container-low px-sm py-xs text-[12px] leading-none text-secondary">
      <span className="shrink-0">{item.label}</span>
      <span className="min-w-0 font-semibold text-ink">{item.value}</span>
    </span>
  );
}

/**
 * 用紧凑的 label/value 信息条替代表格，保留扫描效率并降低纵向占用。
 */
function MetadataStrip({ items, compact = false }: { items: MetadataItem[]; compact?: boolean }) {
  return (
    <dl
      className={
        compact
          ? 'grid grid-cols-1 gap-x-xl gap-y-sm border-y border-border-hairline py-md sm:grid-cols-2 xl:grid-cols-4'
          : 'grid grid-cols-2 gap-x-xl gap-y-md border-y border-border-hairline py-lg md:grid-cols-3 xl:grid-cols-6'
      }
    >
      {items.map((item) => (
        <div key={item.key} className="min-w-0 space-y-xs">
          <dt className="text-[12px] leading-none text-secondary">{item.label}</dt>
          <dd className="m-0 min-w-0 text-[13px] font-semibold leading-relaxed text-ink">
            {item.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}

/**
 * 消息长文本区块保留换行和 Markdown 原文，便于管理员核对持久化内容。
 */
function MessageTextBlock({ title, content }: { title: string; content?: string | null }) {
  return (
    <div className="space-y-sm rounded-lg bg-surface-container-low px-md py-sm">
      <Typography.Text strong className="text-ink">{title}</Typography.Text>
      <Typography.Paragraph className="whitespace-pre-wrap break-words text-ink leading-relaxed" style={{ marginBottom: 0 }}>
        {formatNullable(content)}
      </Typography.Paragraph>
    </div>
  );
}

/**
 * 附件明细只在存在附件时展示，避免无附件状态反复占用消息正文后的空间。
 */
function AttachmentDetails({ attachments }: { attachments: NonNullable<AdminChatConversationMessage['attachments']> }) {
  return (
    <div className="space-y-sm rounded-lg bg-surface-container-low px-md py-sm">
      <Typography.Text strong className="text-ink">附件明细</Typography.Text>
      <List
        className="[&_.ant-list-items]:space-y-sm"
        dataSource={attachments}
        renderItem={(attachment, index) => (
          <List.Item className="rounded-lg border border-border-hairline bg-surface-container-lowest !p-md">
            <MetadataStrip compact items={buildAttachmentMetadataItems(attachment, index)} />
          </List.Item>
        )}
        size="small"
        split={false}
      />
    </div>
  );
}

function buildAttachmentMetadataItems(
  attachment: NonNullable<AdminChatConversationMessage['attachments']>[number],
  index: number,
): MetadataItem[] {
  return [
    { key: 'index', label: '序号', value: String(index + 1) },
    { key: 'id', label: '附件ID', value: <InlineCode value={attachment.id} /> },
    { key: 'fileName', label: '文件名', value: <DetailText value={attachment.fileName} /> },
    { key: 'attachmentType', label: '附件类型', value: <DetailText value={attachment.attachmentType} /> },
    { key: 'fileSize', label: '文件大小', value: formatOptionalFileSize(attachment.fileSize) },
    { key: 'status', label: '状态', value: <DetailText value={attachment.status} /> },
    hasValue(attachment.mimeType) && { key: 'mimeType', label: 'MIME', value: <DetailText value={attachment.mimeType} /> },
    hasValue(attachment.contentSummary) && {
      key: 'contentSummary',
      label: '内容摘要',
      value: <DetailText value={attachment.contentSummary} />,
    },
    { key: 'createdAt', label: '创建时间', value: formatDateTimeFromUnknown(attachment.createdAt) },
  ].filter(Boolean) as MetadataItem[];
}

function InlineCode({ value }: { value: unknown }) {
  const text = formatNullable(value);
  if (text === '-') {
    return <Typography.Text className="text-ink">{text}</Typography.Text>;
  }
  return <Typography.Text code>{text}</Typography.Text>;
}

function DetailText({ value }: { value: unknown }) {
  const text = formatNullable(value);
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

function hasValue(value: unknown): boolean {
  return value !== null && value !== undefined && value !== '';
}

function hasFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function hasVote(value: number | null | undefined): value is number {
  return typeof value === 'number';
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
