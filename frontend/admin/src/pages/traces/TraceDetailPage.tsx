import React from 'react';
import { Link, useParams } from 'react-router-dom';
import clsx from 'clsx';

import { AdminChatApi, type AdminTraceDetail, type AdminTraceNode } from '../../api/adminChatApi';
import {
  clamp,
  formatDateTime,
  formatDuration,
  nodeTypeBadgeClassName,
  nodeTypeLabel,
  normalizeStatus,
  prettifyNodeName,
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
  const [activeNodeKey, setActiveNodeKey] = React.useState<string | null>(null);

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
      setActiveNodeKey(null);
      return;
    }
    setActiveNodeKey(null);
    void loadDetail(traceId);
  }, [loadDetail, traceId]);

  const selectedRun = detail?.traceRun || null;

  const timeline = React.useMemo(() => {
    // 节点时序计算统一转为毫秒时间轴，确保缺失 duration/结束时间时也可稳定绘制瀑布图。
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

  const activeRow = React.useMemo(
    () => timeline.rows.find((row) => row.key === activeNodeKey) || null,
    [activeNodeKey, timeline.rows],
  );

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
                <WaterfallRow
                  key={row.key}
                  row={row}
                  isSelected={activeNodeKey === row.key}
                  onSelect={() => {
                    setActiveNodeKey((current) => (current === row.key ? null : row.key));
                  }}
                />
              ))}
            </div>
          </div>
        )}
      </section>

      {activeRow ? (
        <NodeDetailPanel
          row={activeRow}
          onClose={() => setActiveNodeKey(null)}
        />
      ) : null}
    </div>
  );
}

type WaterfallTimelineRow = ReturnType<typeof buildTimelineRow>;

function WaterfallRow({
  row,
  isSelected,
  onSelect,
}: {
  row: WaterfallTimelineRow;
  isSelected: boolean;
  onSelect: () => void;
}) {
  const statusDotClassName = row.statusToneClassName === 'bg-status-failed'
    ? 'bg-status-failed'
    : row.statusToneClassName === 'bg-status-pending'
      ? 'bg-status-pending'
      : 'bg-status-running';

  return (
    <div
      onClick={onSelect}
      className={clsx(
        'grid cursor-pointer grid-cols-[minmax(180px,1fr)_120px_2fr_100px] gap-md px-lg py-sm transition-colors hover:bg-surface-container-low',
        isSelected ? 'bg-surface-container-low ring-1 ring-inset ring-border-strong' : '',
      )}
    >
      <div className="min-w-0 flex items-center gap-xs" style={{ paddingLeft: `${Math.min(row.depthValue, 6) * 16}px` }}>
        <span className={clsx('h-2 w-2 rounded-full shrink-0', statusDotClassName)} />
        <span className="truncate text-ink" title={row.title}>
          {row.displayTitle}
        </span>
      </div>
      <div className="flex items-center">
        <span
          className={clsx(
            'inline-flex rounded-full border px-2 py-0.5 text-[12px] font-medium truncate',
            nodeTypeBadgeClassName(row.nodeType),
          )}
          title={row.nodeType}
        >
          {row.displayNodeType}
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

function NodeDetailPanel({
  row,
  onClose,
}: {
  row: WaterfallTimelineRow;
  onClose: () => void;
}) {
  // 详情面板固定读取行内快照，避免刷新后 active key 失效导致渲染闪烁。
  const node = row.sourceNode;
  const hasError = ['error', 'failed'].includes(normalizeStatus(node.status));

  return (
    <section className="rounded-xl border border-border-hairline bg-surface-container-lowest overflow-hidden">
      <div className="flex items-center justify-between border-b border-border-hairline px-lg py-md">
        <div className="min-w-0">
          <h3 className="font-title-md text-title-md text-ink">节点详情</h3>
          <div className="mt-1 flex items-center gap-xs">
            <span className="truncate text-secondary" title={row.displayTitle}>{row.displayTitle}</span>
            <span className={clsx(
              'inline-flex rounded-full border px-2 py-0.5 text-[12px] font-semibold',
              statusBadgeClassName(node.status),
            )}
            >
              {statusLabel(node.status)}
            </span>
            <span className={clsx(
              'inline-flex rounded-full border px-2 py-0.5 text-[12px] font-medium',
              nodeTypeBadgeClassName(node.nodeType),
            )}
            >
              {row.displayNodeType}
            </span>
          </div>
        </div>
        <button
          type="button"
          className="inline-flex h-8 items-center justify-center rounded-md border border-border-hairline px-sm text-secondary hover:bg-surface-container-low hover:text-ink"
          onClick={onClose}
        >
          关闭
        </button>
      </div>
      <div className="space-y-md px-lg py-md">
        <div className="grid grid-cols-1 gap-sm text-[12px] text-secondary md:grid-cols-2">
          <DetailField label="Node Id" value={node.nodeId || '-'} mono />
          <DetailField label="Parent Node Id" value={node.parentNodeId || '-'} mono />
          <DetailField label="深度" value={String(node.depth ?? 0)} />
          <DetailField label="耗时" value={formatDuration(resolveNodeDuration(node))} highlight />
          <DetailField label="开始时间" value={formatDateTime(node.startedAt)} />
          <DetailField label="结束时间" value={formatDateTime(node.finishedAt)} />
          <DetailField label="类" value={node.className || '-'} mono />
          <DetailField label="方法" value={node.methodName || '-'} mono />
        </div>
        {hasError && node.errorMessage ? (
          <div className="rounded-lg border border-status-failed-border bg-status-failed-bg px-md py-sm text-status-failed">
            <p className="font-semibold">错误信息</p>
            <p className="mt-1 break-all whitespace-pre-wrap">{node.errorMessage}</p>
          </div>
        ) : null}
        {node.extraDataJson ? (
          <div>
            <p className="text-[12px] text-secondary">extraDataJson</p>
            <pre className="mt-1 max-h-[220px] overflow-auto rounded-lg border border-border-hairline bg-surface-container-low px-md py-sm text-[12px] text-ink">
              {prettyJson(node.extraDataJson)}
            </pre>
          </div>
        ) : null}
      </div>
    </section>
  );
}

function DetailField({
  label,
  value,
  mono = false,
  highlight = false,
}: {
  label: string;
  value: string;
  mono?: boolean;
  highlight?: boolean;
}) {
  return (
    <div className="flex items-center gap-xs min-w-0">
      <span className="shrink-0">{label}</span>
      <span className={clsx('truncate text-ink', mono ? 'font-data-mono' : '', highlight ? 'font-semibold text-primary' : '')}>
        {value}
      </span>
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
    traceId: string;
    nodeId?: string;
    nodeName: string;
    methodName?: string;
    nodeType?: string;
    status?: string;
    parentNodeId?: string | null;
    depth?: number;
    className?: string;
    errorMessage?: string;
    extraDataJson?: string;
    startedAt?: string;
    finishedAt?: string;
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

  const displayNodeType = nodeTypeLabel(node.nodeType);
  const displayTitle = prettifyNodeName(node.nodeName || node.methodName || node.nodeId || node.id);
  const sourceNode: AdminTraceNode = {
    id: node.id,
    traceId: node.traceId,
    nodeId: node.nodeId,
    parentNodeId: node.parentNodeId,
    depth: node.depth,
    nodeType: node.nodeType || '',
    nodeName: node.nodeName,
    className: node.className,
    methodName: node.methodName,
    status: node.status || '',
    errorMessage: node.errorMessage,
    durationMs: node.resolvedDurationMs,
    extraDataJson: node.extraDataJson,
    startedAt: node.startedAt,
    finishedAt: node.finishedAt,
  };

  return {
    key: node.nodeId || node.id,
    title: node.nodeName || node.methodName || node.nodeId || node.id,
    displayTitle,
    nodeType: node.nodeType || '-',
    displayNodeType,
    depthValue: node.depthValue,
    resolvedDurationMs: node.resolvedDurationMs,
    offsetMs,
    leftPercent,
    widthPercent,
    statusToneClassName,
    sourceNode,
  };
}

function prettyJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}
