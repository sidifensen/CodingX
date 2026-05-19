import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import clsx from 'clsx';

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
      <div className="flex items-center justify-between gap-sm">
        <button
          onClick={() => navigate(-1)}
          className="group flex items-center gap-xs text-secondary transition-colors hover:text-ink active:scale-95"
        >
          <span className="material-symbols-outlined text-[18px] transition-transform group-hover:-translate-x-1">
            arrow_back
          </span>
          返回会话列表
        </button>
        <button
          type="button"
          onClick={() => void loadDetail()}
          className="h-10 rounded-lg border border-border-strong bg-surface-container-lowest px-lg text-button font-button text-ink transition-colors hover:bg-surface-container-low"
        >
          刷新
        </button>
      </div>

      {errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      {loading ? (
        <div className="rounded-xl border border-border-hairline bg-surface-container-lowest px-lg py-xl text-center text-secondary">
          会话详情加载中...
        </div>
      ) : null}

      {!loading && !detail ? (
        <div className="rounded-xl border border-border-hairline bg-surface-container-lowest px-lg py-xl text-center text-secondary">
          暂无会话详情
        </div>
      ) : null}

      {!loading && detail ? (
        <>
          <section className="rounded-2xl border border-border-hairline bg-surface-container-lowest p-xl shadow-sm">
            <div className="mb-lg flex flex-col gap-sm border-b border-border-hairline pb-lg lg:flex-row lg:items-start lg:justify-between">
              <div>
                <div className="mb-xs flex items-center gap-sm">
                  <span className="rounded border border-border-hairline bg-surface-container-low px-2 py-1 font-data-mono text-[12px] text-secondary">
                    #{detail.id}
                  </span>
                  <span
                    className={clsx(
                      'inline-flex items-center gap-1 rounded border px-2 py-0.5 text-[11px] font-bold',
                      detail.statusLabel === '活跃'
                        ? 'border-status-running-border bg-status-running-bg text-status-running'
                        : 'border-border-hairline bg-surface-container-low text-secondary',
                    )}
                  >
                    {detail.statusLabel}
                  </span>
                </div>
                <h2 className="font-headline-sm text-headline-sm text-ink">{detail.title || '未命名会话'}</h2>
              </div>
              <div className="grid grid-cols-2 gap-md text-[12px] text-secondary lg:grid-cols-3">
                <InfoBlock label="创建人" value={String(detail.createdBy)} />
                <InfoBlock label="创建时间" value={formatDateTime(detail.createdAt)} />
                <InfoBlock label="更新时间" value={formatDateTime(detail.updatedAt)} />
                <InfoBlock label="最近消息" value={formatDateTime(detail.lastMessageAt)} />
                <InfoBlock label="最近运行ID" value={detail.lastRunId ? String(detail.lastRunId) : '-'} />
                <InfoBlock label="消息数量" value={String(detail.messages?.length ?? 0)} />
              </div>
            </div>

            <div className="space-y-md">
              <h3 className="flex items-center gap-xs text-ink font-title-sm">
                <span className="material-symbols-outlined text-[20px] text-secondary">chat</span>
                会话消息
              </h3>
              {detail.messages.length === 0 ? (
                <div className="rounded-xl border border-border-hairline bg-surface-container-low px-lg py-xl text-center text-secondary">
                  当前会话暂无消息
                </div>
              ) : (
                <div className="space-y-sm">
                  {detail.messages.map((message) => (
                    <MessageCard key={message.id} message={message} />
                  ))}
                </div>
              )}
            </div>
          </section>
        </>
      ) : null}
    </div>
  );
}

function MessageCard({ message }: { message: AdminChatConversationMessage }) {
  const isAssistant = message.role === 'ASSISTANT';
  const isSystem = message.role === 'SYSTEM';
  const isUser = message.role === 'USER';
  const toneClassName = isAssistant
    ? 'border-border-strong bg-surface-container-low'
    : isSystem
      ? 'border-status-pending-border bg-status-pending-bg'
      : 'border-border-hairline bg-surface-container-lowest';

  return (
    <article className={clsx('rounded-xl border px-lg py-md', toneClassName)}>
      <header className="mb-sm flex flex-wrap items-center justify-between gap-sm">
        <div className="flex items-center gap-sm">
          <span className="font-data-mono text-[12px] text-tertiary-container">#{message.id}</span>
          <span
            className={clsx(
              'rounded px-2 py-0.5 text-[11px] font-bold',
              isUser
                ? 'bg-primary/10 text-primary'
                : isAssistant
                  ? 'bg-status-running-bg text-status-running'
                  : 'bg-status-pending-bg text-status-pending',
            )}
          >
            {toRoleLabel(message.role)}
          </span>
          <span className="text-[12px] text-secondary">{toMessageStatusLabel(message.status)}</span>
        </div>
        <div className="text-[12px] text-secondary">{formatDateTime(message.createdAt)}</div>
      </header>

      <div className="whitespace-pre-wrap break-words text-ink">{message.content || '-'}</div>

      {message.errorMessage ? (
        <div className="mt-sm rounded-lg border border-error bg-error-container px-sm py-xs text-[12px] text-on-error-container">
          {message.errorMessage}
        </div>
      ) : null}

      {message.attachments && message.attachments.length > 0 ? (
        <div className="mt-sm space-y-xs">
          <p className="text-[12px] text-secondary">附件</p>
          <ul className="space-y-1">
            {message.attachments.map((attachment) => (
              <li
                key={attachment.id}
                className="rounded-lg border border-border-hairline bg-surface-container-low px-sm py-xs text-[12px] text-ink"
              >
                {attachment.fileName}
                {attachment.fileSize ? (
                  <span className="ml-2 text-secondary">({formatFileSize(attachment.fileSize)})</span>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </article>
  );
}

function InfoBlock({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="mb-1 text-[12px] text-secondary">{label}</p>
      <p className="font-medium text-ink">{value}</p>
    </div>
  );
}

function toRoleLabel(role: string): string {
  if (role === 'USER') {
    return '用户';
  }
  if (role === 'ASSISTANT') {
    return '助手';
  }
  if (role === 'SYSTEM') {
    return '系统';
  }
  return role || '未知角色';
}

function toMessageStatusLabel(status: string): string {
  if (status === 'PENDING') {
    return '处理中';
  }
  if (status === 'COMPLETED') {
    return '完成';
  }
  if (status === 'FAILED') {
    return '失败';
  }
  if (status === 'CANCELLED') {
    return '已取消';
  }
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
  if (!Number.isFinite(size) || size <= 0) {
    return '0 B';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
