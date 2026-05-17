import React from 'react';
import { Link, useParams } from 'react-router-dom';

import { AdminChatApi, type AdminChatMessageFeedbackDetail } from '../api/adminChatApi';

/**
 * 管理端反馈详情页：聚合展示反馈本体与关联消息上下文。
 */
export function FeedbackDetailPage() {
  const { feedbackId = '' } = useParams();
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [detail, setDetail] = React.useState<AdminChatMessageFeedbackDetail | null>(null);

  React.useEffect(() => {
    if (!feedbackId) {
      return;
    }
    const loadDetail = async () => {
      setLoading(true);
      setErrorMessage('');
      try {
        const data = await AdminChatApi.getFeedbackDetail(feedbackId);
        setDetail(data);
      } catch (error) {
        setErrorMessage(extractErrorMessage(error, '加载反馈详情失败'));
      } finally {
        setLoading(false);
      }
    };
    void loadDetail();
  }, [feedbackId]);

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-wrap items-center justify-between gap-sm">
        <h2 className="font-headline-md text-headline-md text-ink">反馈详情 #{feedbackId || '-'}</h2>
        <div className="flex items-center gap-sm">
          <Link
            to="/feedbacks"
            className="rounded-lg border border-border-strong bg-surface-container-lowest px-md py-2 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
          >
            返回列表
          </Link>
          <Link
            to={`/feedbacks/${feedbackId}/references`}
            className="rounded-lg bg-primary px-md py-2 text-[12px] text-on-primary transition-opacity hover:opacity-90"
          >
            查看引用来源
          </Link>
        </div>
      </header>

      {loading ? <div className="text-secondary">加载中...</div> : null}
      {errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      {detail ? (
        <section className="space-y-md rounded-2xl border border-border-hairline bg-surface-container-lowest p-lg shadow-sm">
          <InfoRow label="会话ID" value={String(detail.conversationId ?? '-')} />
          <InfoRow label="会话标题" value={detail.conversationTitle || '-'} />
          <InfoRow label="消息ID" value={String(detail.messageId ?? '-')} />
          <InfoRow label="消息角色" value={detail.messageRole || '-'} />
          <InfoRow label="反馈投票" value={detail.vote === 1 ? '点赞' : detail.vote === -1 ? '点踩' : '-'} />
          <InfoRow label="反馈原因" value={detail.reason || '-'} />
          <InfoRow label="反馈评论" value={detail.comment || '-'} />
          <div className="space-y-xs rounded-xl border border-border-hairline bg-surface-container-low p-md">
            <h3 className="text-[12px] font-medium text-secondary">关联消息内容</h3>
            <p className="whitespace-pre-wrap text-body-sm text-ink">{detail.messageContent || '-'}</p>
          </div>
        </section>
      ) : null}
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-sm border-b border-border-hairline pb-sm last:border-b-0 last:pb-0 md:grid-cols-[160px_1fr]">
      <span className="text-[12px] text-secondary">{label}</span>
      <span className="text-body-sm text-ink">{value}</span>
    </div>
  );
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

