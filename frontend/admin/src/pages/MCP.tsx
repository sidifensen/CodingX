import React from 'react';
import clsx from 'clsx';

import { AdminChatApi, AdminMcpToolView } from '../api/adminChatApi';

/**
 * 管理端 MCP 工具页：展示后端注册工具并支持在线探测。
 */
export function MCP() {
  const [tools, setTools] = React.useState<AdminMcpToolView[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [pingingToolId, setPingingToolId] = React.useState<string | null>(null);
  const [dialogResult, setDialogResult] = React.useState<AdminMcpToolView | null>(null);

  const reload = React.useCallback(async () => {
    setLoading(true);
    setErrorMessage('');
    try {
      const result = await AdminChatApi.listMcpTools();
      setTools(result ?? []);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '加载 MCP 工具失败');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void reload();
  }, [reload]);

  const handlePing = async (toolId: string) => {
    setPingingToolId(toolId);
    try {
      const result = await AdminChatApi.pingMcpTool(toolId);
      setDialogResult(result);
    } catch (error) {
      setDialogResult({
        toolId,
        displayName: toolId,
        category: '自定义',
        source: '内置后端',
        status: 'failed',
        statusLabel: '异常',
        ok: false,
        message: error instanceof Error ? error.message : '探测失败',
      });
    } finally {
      setPingingToolId(null);
    }
  };

  return (
    <div className="p-lg w-full space-y-lg">
      <div className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">MCP 工具管理</h2>
          <p className="text-secondary mt-1">展示当前后端已注册的 MCP 工具，并支持在线探测可用性。</p>
        </div>
        <button
          type="button"
          className="border border-border-strong text-ink bg-surface-container-lowest shadow-sm px-lg py-2 rounded-lg font-button text-button flex items-center gap-xs active:scale-95 transition-transform hover:bg-surface-container-low"
          onClick={() => void reload()}
        >
          <span className="material-symbols-outlined text-[18px]">refresh</span>
          刷新
        </button>
      </div>

      {errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      <div className="bg-surface-container-lowest border border-border-hairline rounded-xl overflow-hidden shadow-sm">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-surface-container-low border-b border-border-hairline">
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">工具标识</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">工具名称</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">分类</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">状态</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">最近探测</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary text-right">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-hairline">
            {loading ? (
              <tr>
                <td className="px-lg py-xl text-center text-secondary" colSpan={6}>
                  加载中...
                </td>
              </tr>
            ) : tools.length === 0 ? (
              <tr>
                <td className="px-lg py-xl text-center text-secondary" colSpan={6}>
                  暂无 MCP 工具
                </td>
              </tr>
            ) : (
              tools.map((tool) => (
                <tr key={tool.toolId} className="hover:bg-surface-container-low transition-colors">
                  <td className="px-lg py-md font-data-mono text-[12px] text-tertiary-container">{tool.toolId}</td>
                  <td className="px-lg py-md font-medium text-ink">{tool.displayName}</td>
                  <td className="px-lg py-md text-secondary text-body-sm">{tool.category}</td>
                  <td className="px-lg py-md">
                    <div className="flex items-center gap-xs">
                      <span className={clsx('w-2 h-2 rounded-full', statusDotClass(tool.status))} />
                      <span className={clsx('text-body-sm font-medium', statusTextClass(tool.status))}>
                        {tool.statusLabel}
                      </span>
                    </div>
                  </td>
                  <td className="px-lg py-md text-secondary text-[12px]">{tool.checkedAt || '-'}</td>
                  <td className="px-lg py-md text-right">
                    <button
                      type="button"
                      aria-label={`测试 ${tool.toolId}`}
                      className="inline-flex items-center gap-xs rounded-lg border border-border-hairline bg-surface-container-lowest px-sm py-1.5 text-secondary hover:text-ink hover:bg-surface-container-low transition-colors disabled:opacity-60"
                      onClick={() => void handlePing(tool.toolId)}
                      disabled={pingingToolId === tool.toolId}
                    >
                      <span className="material-symbols-outlined text-[18px]">network_ping</span>
                      {pingingToolId === tool.toolId ? '探测中...' : '测试连接'}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {dialogResult ? (
        <PingResultDialog result={dialogResult} onClose={() => setDialogResult(null)} />
      ) : null}
    </div>
  );
}

/**
 * MCP 状态点颜色映射。
 *
 * @param status 状态码。
 * @returns 对应样式。
 */
function statusDotClass(status: string): string {
  switch (status) {
    case 'healthy':
      return 'bg-status-running';
    case 'degraded':
      return 'bg-status-pending';
    default:
      return 'bg-status-failed';
  }
}

/**
 * MCP 状态文本颜色映射。
 *
 * @param status 状态码。
 * @returns 对应样式。
 */
function statusTextClass(status: string): string {
  switch (status) {
    case 'healthy':
      return 'text-status-running';
    case 'degraded':
      return 'text-status-pending';
    default:
      return 'text-status-failed';
  }
}

function PingResultDialog({
  result,
  onClose,
}: {
  result: AdminMcpToolView;
  onClose: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="工具探测结果"
        className="w-full max-w-[42rem] rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
      >
        <div className="flex items-start justify-between gap-md border-b border-border-hairline px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">工具探测结果</h3>
            <p className="mt-1 break-all font-data-mono text-[12px] text-secondary">{result.toolId}</p>
          </div>
          <button
            type="button"
            className="rounded-lg px-sm py-xs text-secondary hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭结果弹窗"
            onClick={onClose}
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        <div className="space-y-md p-lg">
          <div className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
            <p className="text-[12px] text-secondary">探测消息</p>
            <p className="mt-1 break-words text-body-sm text-ink">{result.message || '无返回消息'}</p>
          </div>
          <div className="grid grid-cols-1 gap-sm md:grid-cols-3">
            <ResultMeta label="状态" value={result.statusLabel || '-'} />
            <ResultMeta
              label="耗时"
              value={typeof result.durationMs === 'number' ? `${result.durationMs} ms` : '-'}
            />
            <ResultMeta label="时间" value={result.checkedAt || '-'} />
          </div>
          <div className="flex justify-end">
            <button
              type="button"
              className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 text-button font-button text-ink hover:bg-surface-container-low"
              onClick={onClose}
            >
              知道了
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * 探测结果元信息展示块，统一字段视觉密度。
 */
function ResultMeta({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border-hairline bg-surface-container-low px-md py-sm">
      <p className="text-[12px] text-secondary">{label}</p>
      <p className="mt-1 break-words text-body-sm text-ink">{value}</p>
    </div>
  );
}
