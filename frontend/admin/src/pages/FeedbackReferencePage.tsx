import React from 'react';
import { Link, useParams } from 'react-router-dom';

import { AdminChatApi, type AdminChatMessageReference } from '../api/adminChatApi';

/**
 * 管理端反馈引用页：展示反馈消息对应的参考来源证据。
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

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-wrap items-center justify-between gap-sm">
        <h2 className="font-headline-md text-headline-md text-ink">反馈引用来源</h2>
        <div className="flex items-center gap-sm">
          <Link
            to={`/feedbacks/${feedbackId}`}
            className="rounded-lg border border-border-strong bg-surface-container-lowest px-md py-2 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
          >
            返回反馈详情
          </Link>
          <Link
            to="/feedbacks"
            className="rounded-lg border border-border-strong bg-surface-container-lowest px-md py-2 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
          >
            返回列表
          </Link>
        </div>
      </header>

      {loading ? <div className="text-secondary">加载中...</div> : null}
      {errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      <section className="space-y-md rounded-2xl border border-border-hairline bg-surface-container-lowest p-lg shadow-sm">
        <div className="overflow-hidden rounded-xl border border-border-hairline">
          <table className="min-w-[980px] w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-border-hairline bg-surface-container-low">
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">序号</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">来源标题</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">站点</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">来源类型</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">摘要</th>
                <th className="px-lg py-md text-right font-label-caps text-label-caps text-secondary">链接</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {references.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-lg py-xl text-center text-secondary">暂无引用来源</td>
                </tr>
              ) : references.map((item, index) => (
                <tr key={item.id ?? index} className="transition-colors hover:bg-surface-container-low">
                  <td className="px-lg py-md text-secondary">{item.rankNo ?? index + 1}</td>
                  <td className="max-w-[260px] truncate px-lg py-md text-ink" title={item.title || ''}>
                    {item.title || '-'}
                  </td>
                  <td className="max-w-[180px] truncate px-lg py-md text-secondary" title={item.siteName || ''}>
                    {item.siteName || '-'}
                  </td>
                  <td className="px-lg py-md text-secondary">{item.sourceType || '-'}</td>
                  <td className="max-w-[320px] truncate px-lg py-md text-secondary" title={item.snippet || ''}>
                    {item.snippet || '-'}
                  </td>
                  <td className="px-lg py-md text-right">
                    {item.url ? (
                      <a
                        href={item.url}
                        target="_blank"
                        rel="noreferrer"
                        aria-label={`打开来源 ${item.title || item.url}`}
                        className="rounded-lg border border-border-strong bg-surface-container-lowest px-sm py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
                      >
                        打开来源
                      </a>
                    ) : (
                      <span className="text-[12px] text-secondary">-</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

