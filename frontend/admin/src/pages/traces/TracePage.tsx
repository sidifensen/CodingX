import React from 'react';

import { AdminChatApi, type AdminTraceRun, type AdminTraceRunPageResult } from '../../api/adminChatApi';
import { TRACE_PAGE_SIZE, normalizeStatus, percentile } from './traceUtils';
import { TraceStatCard, type TraceStatTone } from './components/TraceStatCard';
import { TraceRunsTable } from './components/TraceRunsTable';

type DurationMetric = { value: string; unit: string };

/**
 * 管理端链路追踪列表页：聚焦检索、统计与跳转详情。
 */
export function TracePage() {
  const [traceIdFilter, setTraceIdFilter] = React.useState('');
  const [queryTraceId, setQueryTraceId] = React.useState('');
  const [pageNo, setPageNo] = React.useState(1);
  const [pageData, setPageData] = React.useState<AdminTraceRunPageResult | null>(null);
  const [loading, setLoading] = React.useState(false);
  const requestIdRef = React.useRef(0);

  const runs = pageData?.records || [];

  const loadRuns = React.useCallback(async (current = pageNo, nextTraceId = queryTraceId) => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    try {
      const result = await AdminChatApi.listTraces({
        current,
        size: TRACE_PAGE_SIZE,
        traceId: nextTraceId.trim() || undefined,
      });
      if (requestIdRef.current !== requestId) return;
      setPageData(result);
    } catch (error) {
      if (requestIdRef.current !== requestId) return;
      console.error(error);
    } finally {
      if (requestIdRef.current !== requestId) return;
      setLoading(false);
    }
  }, [pageNo, queryTraceId]);

  React.useEffect(() => {
    void loadRuns();
  }, [loadRuns]);

  const traceStats = React.useMemo(() => {
    const durations = runs
      .map((item) => Number(item.durationMs ?? 0))
      .filter((value) => Number.isFinite(value) && value > 0);
    const successCount = runs.filter((item) => normalizeStatus(item.status) === 'success').length;
    const failedCount = runs.filter((item) => {
      const status = normalizeStatus(item.status);
      return status === 'failed' || status === 'error';
    }).length;
    const runningCount = runs.filter((item) => normalizeStatus(item.status) === 'running').length;
    const avgDuration = durations.length
      ? Math.round(durations.reduce((sum, value) => sum + value, 0) / durations.length)
      : 0;
    const p95Duration = Math.round(percentile(durations, 0.95));
    const successRate = runs.length ? Math.round((successCount / runs.length) * 1000) / 10 : 0;
    return {
      successCount,
      failedCount,
      runningCount,
      avgDuration,
      p95Duration,
      successRate,
    };
  }, [runs]);

  const avgDurationMetric = formatDurationMetric(traceStats.avgDuration);
  const p95DurationMetric = formatDurationMetric(traceStats.p95Duration);
  const current = pageData?.current || pageNo;
  const pages = pageData?.pages || 0;
  const total = pageData?.total || 0;

  const statCards: {
    key: string;
    title: string;
    value: string;
    unit?: string;
    icon: React.ReactNode;
    tone: TraceStatTone;
  }[] = [
    {
      key: 'status',
      title: '成功 / 失败 / 运行中',
      value: `${traceStats.successCount} / ${traceStats.failedCount} / ${traceStats.runningCount}`,
      icon: <span className="material-symbols-outlined text-[18px]">monitor_heart</span>,
      tone: 'emerald',
    },
    {
      key: 'successRate',
      title: '成功率',
      value: `${traceStats.successRate}%`,
      icon: <span className="material-symbols-outlined text-[18px]">trending_up</span>,
      tone: 'cyan',
    },
    {
      key: 'avg',
      title: '平均耗时',
      value: avgDurationMetric.value,
      unit: avgDurationMetric.unit,
      icon: <span className="material-symbols-outlined text-[18px]">schedule</span>,
      tone: 'indigo',
    },
    {
      key: 'p95',
      title: 'P95 耗时',
      value: p95DurationMetric.value,
      unit: p95DurationMetric.unit,
      icon: <span className="material-symbols-outlined text-[18px]">layers</span>,
      tone: 'amber',
    },
  ];

  const handleSearch = () => {
    setPageNo(1);
    setQueryTraceId(traceIdFilter.trim());
  };

  const handleRefresh = () => {
    void loadRuns(pageNo, queryTraceId);
  };

  return (
    <div className="p-lg w-full space-y-lg">
      <header className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">链路追踪</h2>
          <p className="text-secondary mt-1">
            独立列表页聚焦运行检索，点击任意运行记录进入详情页分析慢节点与失败节点
          </p>
        </div>
        <div className="flex items-center gap-xs">
          <input
            value={traceIdFilter}
            onChange={(event) => setTraceIdFilter(event.target.value)}
            placeholder="搜索 Trace Id"
            className="h-10 w-[320px] rounded-lg border border-border-hairline bg-surface-container-lowest px-3 text-ink outline-none transition-colors focus:border-border-strong"
          />
          <button
            type="button"
            className="h-10 rounded-lg bg-ink px-lg text-button font-button text-on-ink hover:opacity-90 transition-opacity"
            onClick={handleSearch}
          >
            查询
          </button>
          <button
            type="button"
            className="h-10 rounded-lg border border-border-strong bg-surface-container-lowest px-lg text-button font-button text-ink hover:bg-surface-container-low transition-colors"
            onClick={handleRefresh}
          >
            刷新
          </button>
        </div>
      </header>

      <section className="grid grid-cols-1 gap-sm xl:grid-cols-4">
        {statCards.map((item) => (
          <TraceStatCard
            key={item.key}
            title={item.title}
            value={item.value}
            unit={item.unit}
            icon={item.icon}
            tone={item.tone}
          />
        ))}
      </section>

      <TraceRunsTable
        runs={runs}
        loading={loading}
        current={current}
        pages={pages}
        total={total}
        onPrevPage={() => setPageNo((previous) => Math.max(1, previous - 1))}
        onNextPage={() => setPageNo((previous) => previous + 1)}
      />
    </div>
  );
}

/**
 * 指标卡使用的耗时单位格式化。
 */
function formatDurationMetric(durationMs: number): DurationMetric {
  const duration = Number.isFinite(durationMs) && durationMs > 0 ? durationMs : 0;
  if (duration < 1000) {
    return { value: `${Math.round(duration)}`, unit: 'ms' };
  }
  if (duration < 60_000) {
    return { value: (duration / 1000).toFixed(2), unit: 's' };
  }
  return { value: (duration / 1000).toFixed(1), unit: 's' };
}
