import React from 'react';
import { Link, useParams } from 'react-router-dom';
import clsx from 'clsx';

import { AdminChatApi, type AdminTraceDetail } from '../../api/adminChatApi';
import {
  clamp,
  formatDateTime,
  formatDuration,
  normalizeStatus,
  resolveNodeDuration,
  statusBadgeClassName,
  statusLabel,
  toTimestamp,
} from './traceUtils';

const decodeTraceId = (value?: string): string => {
  if (!value) return '';
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
};

/**
 * 独立链路详情页：对齐 ragent 的信息结构，保留 CodingX 主题风格。
 */
export function TraceDetailPage() {
  const params = useParams<{ traceId: string }>();
  const traceId = decodeTraceId(params.traceId);
  const requestIdRef = React.useRef(0);
  const [detail, setDetail] = React.useState<AdminTraceDetail | null>(null);
  const [loading, setLoading] = React.useState(false);

  const loadDetail = React.useCallback(async (nextTraceId: string) => {
    if (!nextTraceId) return;
    const requestId = ++requestIdRef.current;
    setLoading(true);
    try {
      const result = await AdminChatApi.getTrace(nextTraceId);
      if (requestIdRef.current !== requestId) return;
      setDetail(result);
    } catch (error) {
      if (requestIdRef.current !== requestId) return;
      console.error(error);
      setDetail(null);
    } finally {
      if (requestIdRef.current !== requestId) return;
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    if (!traceId) {
      setDetail(null);
      setLoading(false);
      return;
    }
    void loadDetail(traceId);
  }, [loadDetail, traceId]);

  const selectedRun = detail?.traceRun || null;

  const timeline = React.useMemo(() => {
    const nodes = detail?.nodes || [];
    if (!nodes.length) {
      return { totalWindowMs: 0, rows: [] as Array<ReturnType<typeof buildTimelineRow>> };
    }

    const normalized = nodes.map((node) => {
      const startTs = toTimestamp(node.startedAt);
      const endTs = toTimestamp(node.finishedAt);
      const resolvedDurationMs = resolveNodeDuration(node);
      const depthValue = Math.max(0, Number(node.depth ?? 0));
      const resolvedStartTs = startTs ?? 0;
      const resolvedEndTs = endTs ?? (resolvedStartTs > 0 ? resolvedStartTs + resolvedDurationMs : 0);
      return { ...node, depthValue, resolvedDurationMs, startTs: resolvedStartTs, endTs: resolvedEndTs };
    });

    const withTime = normalized.filter((item) => item.startTs > 0);
    const baseStart = withTime.length
      ? withTime.reduce((min, item) => Math.min(min, item.startTs), withTime[0].startTs)
      : Date.now();
    const maxEnd = withTime.length
      ? withTime.reduce((max, item) => Math.max(max, item.endTs || item.startTs), withTime[0].endTs || withTime[0].startTs)
      : baseStart;
    const runDuration = Number(selectedRun?.durationMs ?? 0);
    const windowDuration = Math.max(runDuration > 0 ? runDuration : maxEnd - baseStart, 1);

    const rows = normalized
      .sort((a, b) => a.startTs - b.startTs || a.depthValue - b.depthValue)
      .map((node) => buildTimelineRow(node, baseStart, windowDuration));
    return { totalWindowMs: windowDuration, rows };
  }, [detail?.nodes, selectedRun?.durationMs]);

  const stats = React.useMemo(() => {
    const nodes = detail?.nodes || [];
    const total = nodes.length;
    const failed = nodes.filter((node) => {
      const status = normalizeStatus(node.status);
      return status === 'failed' || status === 'error';
    }).length;
    const success = nodes.filter((node) => normalizeStatus(node.status) === 'success').length;
    const running = nodes.filter((node) => normalizeStatus(node.status) === 'running').length;
    const durations = nodes.map(resolveNodeDuration);
    const avgDuration = total > 0 ? Math.round(durations.reduce((sum, value) => sum + value, 0) / total) : 0;
    return { total, failed, success, running, avgDuration };
  }, [detail?.nodes]);

  if (loading) {
    return (
      <div className="p-lg w-full">
        <div className="rounded-xl border border-border-hairline bg-surface-container-lowest px-lg py-xl text-center text-secondary">
          加载链路详情中...
        </div>
      </div>
    );
  }

  if (!traceId || !selectedRun) {
    return (
      <div className="p-lg w-full space-y-lg">
        <div className="flex items-center justify-between">
          <div className="text-secondary text-[12px]">首页 / 链路追踪 / 详情</div>
          <Link
            to="/traces"
            className="inline-flex items-center gap-xs rounded-lg border border-border-strong bg-surface-container-lowest px-md py-1.5 text-button font-button text-ink hover:bg-surface-container-low"
          >
            返回列表
          </Link>
        </div>
        <div className="rounded-xl border border-border-hairline bg-surface-container-lowest px-lg py-xl text-center text-secondary">
          {!traceId ? '缺少 Trace Id' : '暂无数据'}
        </div>
      </div>
    );
  }

  return (
    <div className="p-lg w-full space-y-md pb-xl">
      <div className="flex items-center justify-between gap-sm">
        <div className="space-y-xs">
          <p className="text-secondary text-[12px]">首页 / 链路追踪 / 链路详情</p>
          <div className="flex items-center gap-sm">
            <h2 className="font-headline-md text-headline-md text-ink">{selectedRun.traceName || '未命名链路'}</h2>
            <span
              className={clsx(
                'inline-flex rounded-full border px-2.5 py-1 text-[12px] font-semibold',
                statusBadgeClassName(selectedRun.status),
              )}
            >
              {statusLabel(selectedRun.status)}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-xs">
          <Link
            to="/traces"
            className="inline-flex items-center gap-xs rounded-lg border border-border-strong bg-surface-container-lowest px-md py-1.5 text-button font-button text-ink hover:bg-surface-container-low"
          >
            返回列表
          </Link>
          <button
            type="button"
            className="inline-flex items-center gap-xs rounded-lg border border-border-strong bg-surface-container-lowest px-md py-1.5 text-button font-button text-ink hover:bg-surface-container-low"
            onClick={() => void loadDetail(traceId)}
          >
            刷新
          </button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-md text-secondary text-[12px]">
        <span className="font-data-mono text-tertiary-container"># {traceId}</span>
        <span>{formatDateTime(selectedRun.startedAt)}</span>
        <span>{selectedRun.username || selectedRun.userId || '-'}</span>
      </div>

      {selectedRun.errorMessage ? (
        <div className="rounded-xl border border-status-failed-border bg-status-failed-bg px-md py-sm text-status-failed">
          <span className="font-semibold">执行出错：</span>
          <span className="ml-1">{selectedRun.errorMessage}</span>
        </div>
      ) : null}

      <section className="flex flex-wrap divide-x divide-border-hairline rounded-xl border border-border-hairline bg-surface-container-lowest">
        <MetricItem label="总耗时" value={formatDuration(selectedRun.durationMs)} />
        <MetricItem label="节点" value={String(stats.total)} />
        <MetricItem label="成功" value={String(stats.success)} className="text-status-running" />
        <MetricItem label="失败" value={String(stats.failed)} className={stats.failed > 0 ? 'text-status-failed' : 'text-secondary'} />
        {stats.running > 0 ? <MetricItem label="运行中" value={String(stats.running)} className="text-status-pending" /> : null}
        <MetricItem label="平均耗时" value={formatDuration(stats.avgDuration)} />
      </section>

      <section className="rounded-xl border border-border-hairline bg-surface-container-lowest overflow-hidden">
        <div className="flex items-center justify-between border-b border-border-hairline px-lg py-md">
          <h3 className="font-title-md text-title-md text-ink">执行时序</h3>
          <span className="text-[12px] text-secondary">窗口 {formatDuration(timeline.totalWindowMs)}</span>
        </div>

        {timeline.rows.length === 0 ? (
          <div className="px-lg py-xl text-center text-secondary">暂无节点记录</div>
        ) : (
          <div>
            <div className="grid grid-cols-[minmax(180px,1fr)_120px_2fr_100px] gap-md bg-surface-container-low border-b border-border-hairline px-lg py-sm text-[12px] text-secondary">
              <span>节点</span>
              <span>类型</span>
              <span>时间线</span>
              <span className="text-right">耗时</span>
            </div>
            <div className="grid grid-cols-[minmax(180px,1fr)_120px_2fr_100px] gap-md px-lg">
              <div />
              <div />
              <TimeScale totalMs={timeline.totalWindowMs} />
              <div />
            </div>
            <div className="divide-y divide-border-hairline">
              {timeline.rows.map((row) => (
                <WaterfallRow key={row.key} row={row} />
              ))}
            </div>
          </div>
        )}
      </section>
    </div>
  );
}

type WaterfallTimelineRow = ReturnType<typeof buildTimelineRow>;

function WaterfallRow({ row }: { row: WaterfallTimelineRow }) {
  return (
    <div className="grid grid-cols-[minmax(180px,1fr)_120px_2fr_100px] gap-md px-lg py-sm hover:bg-surface-container-low transition-colors">
      <div className="min-w-0 flex items-center gap-xs" style={{ paddingLeft: `${Math.min(row.depthValue, 6) * 16}px` }}>
        <span className="h-2 w-2 rounded-full bg-status-running shrink-0" />
        <span className="truncate text-ink" title={row.title}>
          {row.title}
        </span>
      </div>
      <div className="flex items-center">
        <span className="rounded bg-surface-container-low px-2 py-0.5 text-[12px] text-secondary truncate" title={row.nodeType}>
          {row.nodeType}
        </span>
      </div>
      <div className="flex items-center">
        <div className="relative h-6 w-full rounded bg-surface-container-low">
          {[25, 50, 75].map((p) => (
            <div key={p} className="absolute top-0 bottom-0 w-px bg-border-hairline" style={{ left: `${p}%` }} />
          ))}
          <div
            className={clsx(
              'absolute top-1 bottom-1 rounded',
              row.statusToneClassName,
            )}
            style={{
              left: `${row.leftPercent}%`,
              width: `${Math.max(row.widthPercent, 0.5)}%`,
              minWidth: '4px',
            }}
            title={`${row.title} - ${formatDuration(row.resolvedDurationMs)}`}
          />
        </div>
      </div>
      <div className="text-right">
        <p className="font-medium text-ink">{formatDuration(row.resolvedDurationMs)}</p>
        <p className="text-[10px] text-secondary">@{formatDuration(row.offsetMs)}</p>
      </div>
    </div>
  );
}

function TimeScale({ totalMs }: { totalMs: number }) {
  const ticks = [0, 25, 50, 75, 100];
  return (
    <div className="relative h-6 border-b border-border-hairline">
      {ticks.map((percent) => (
        <div
          key={percent}
          className="absolute top-0 bottom-0 flex flex-col items-center"
          style={{ left: `${percent}%`, transform: 'translateX(-50%)' }}
        >
          <div className="h-2 w-px bg-border-hairline" />
          <span className="mt-0.5 text-[10px] text-secondary">{formatDuration((totalMs * percent) / 100)}</span>
        </div>
      ))}
    </div>
  );
}

function MetricItem({ label, value, className }: { label: string; value: string; className?: string }) {
  return (
    <div className="px-md py-sm">
      <p className={clsx('text-[28px] leading-none font-semibold text-ink', className)}>{value}</p>
      <p className="mt-1 text-[12px] text-secondary">{label}</p>
    </div>
  );
}

function buildTimelineRow(
  node: {
    id: string;
    nodeId?: string;
    nodeName: string;
    methodName?: string;
    nodeType?: string;
    status?: string;
    depthValue: number;
    resolvedDurationMs: number;
    startTs: number;
  },
  baseStart: number,
  windowDuration: number,
) {
  const offsetMs = node.startTs > 0 ? Math.max(0, node.startTs - baseStart) : 0;
  const leftPercent = clamp((offsetMs / windowDuration) * 100, 0, 99.2);
  const widthPercent = clamp(
    (Math.max(node.resolvedDurationMs, 1) / windowDuration) * 100,
    0.8,
    100 - leftPercent,
  );
  const normalizedStatus = normalizeStatus(node.status);
  const statusToneClassName = normalizedStatus === 'failed' || normalizedStatus === 'error'
    ? 'bg-status-failed'
    : normalizedStatus === 'running'
      ? 'bg-status-pending'
      : 'bg-status-running';
  return {
    key: node.nodeId || node.id,
    title: node.nodeName || node.methodName || node.nodeId || node.id,
    nodeType: node.nodeType || '-',
    depthValue: node.depthValue,
    resolvedDurationMs: node.resolvedDurationMs,
    offsetMs,
    leftPercent,
    widthPercent,
    statusToneClassName,
  };
}
