import React from 'react';
import { Area, Column, Line, Pie } from '@ant-design/plots';

import {
  AdminChatApi,
  type AdminChatRuntimeDashboardView,
  type AdminDashboardTrendBucket,
  type AdminDashboardView,
  type AdminDashboardWindow,
} from '../api/adminChatApi';

const WINDOW_OPTIONS: AdminDashboardWindow[] = ['24h', '7d', '30d'];
const MILLISECONDS_PER_SECOND = 1000;
const LATENCY_WARNING_SECONDS = 15;

/**
 * 管理端控制台首页：参考 ragent 的信息架构，但只展示当前仓库真实可得的数据域。
 */
export function Dashboard() {
  const [windowValue, setWindowValue] = React.useState<AdminDashboardWindow>('24h');
  const [dashboard, setDashboard] = React.useState<AdminDashboardView | null>(null);
  const [runtimeDashboard, setRuntimeDashboard] = React.useState<AdminChatRuntimeDashboardView | null>(null);
  const chartTheme = useChartTheme();

  React.useEffect(() => {
    void AdminChatApi.getDashboard(windowValue).then(setDashboard);
  }, [windowValue]);

  React.useEffect(() => {
    void AdminChatApi.getRuntimeDashboard().then(setRuntimeDashboard);
  }, []);

  const trendBuckets = dashboard?.trendBuckets ?? [];
  const mainTrafficData = trendBuckets.map((bucket) => ({
    label: bucket.label,
    value: bucket.messageCount,
  }));
  const conversationTrendData = trendBuckets.map((bucket) => ({
    label: bucket.label,
    value: bucket.conversationCount,
  }));
  const activeUserTrendData = trendBuckets.map((bucket) => ({
    label: bucket.label,
    value: bucket.activeUserCount,
  }));
  const latencyTrendData = trendBuckets.map((bucket) => ({
    label: bucket.label,
    // 后端趋势桶仍返回毫秒，工作台图表按秒展示，便于管理员直接阅读耗时量级。
    value: toLatencySeconds(bucket.avgDurationMs),
  }));
  const qualityTrendData = trendBuckets.flatMap((bucket) => ([
    { label: bucket.label, type: '成功', value: bucket.successCount },
    { label: bucket.label, type: '失败', value: bucket.failedCount },
  ]));

  const successRate = dashboard?.performance.successRate ?? 0;
  const ringData = [
    { type: '成功率', value: successRate },
    { type: '剩余', value: Math.max(0, 100 - successRate) },
  ];

  return (
    <div className="px-6 py-6">
      <div className="mx-auto max-w-[1500px] space-y-5">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
          <div>
            <h1 className="text-[44px] font-semibold tracking-[-0.04em] text-ink">工作台</h1>
            <p className="mt-2 text-body-sm text-secondary">当前窗口下的运营态快照、运行健康和配置资产全览。</p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <div className="inline-flex rounded-2xl border border-border-hairline bg-surface-container-lowest p-1 shadow-sm">
              {WINDOW_OPTIONS.map((option) => (
                <button
                  key={option}
                  type="button"
                  onClick={() => setWindowValue(option)}
                  className={[
                    'rounded-2xl px-4 py-2 text-[13px] font-semibold transition-colors',
                    windowValue === option ? 'bg-[#121826] text-white' : 'text-secondary hover:text-ink',
                  ].join(' ')}
                >
                  {option}
                </button>
              ))}
            </div>
            <div className="inline-flex items-center gap-2 rounded-2xl border border-border-hairline bg-surface-container-lowest px-4 py-2 text-[12px] text-secondary shadow-sm">
              <span className="h-2 w-2 rounded-full bg-emerald-500" />
              {formatDateTime(dashboard?.generatedAt)}
            </div>
            <button
              type="button"
              onClick={() => {
                void AdminChatApi.getDashboard(windowValue).then(setDashboard);
                void AdminChatApi.getRuntimeDashboard().then(setRuntimeDashboard);
              }}
              className="inline-flex h-11 w-11 items-center justify-center rounded-2xl border border-border-hairline bg-surface-container-lowest text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
              title="刷新"
            >
              <span className="material-symbols-outlined text-[19px]">refresh</span>
            </button>
          </div>
        </div>

        <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
          <div className="space-y-5">
            <section className="rounded-[28px] border border-border-hairline bg-surface-container-lowest p-5 shadow-[0_18px_40px_rgba(15,23,42,0.06)]">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-[16px] font-semibold text-ink">核心指标</h2>
                <span className="text-[12px] text-secondary">窗口 {windowValue}</span>
              </div>
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <MetricCard
                  label="活跃用户"
                  value={dashboard?.kpis.activeUserCount ?? '-'}
                  icon="monitoring"
                  toneLight="bg-[#8fcfff]"
                  toneDark="dark:bg-sky-500/16"
                />
                <MetricCard
                  label="会话数"
                  value={dashboard?.kpis.conversationCount ?? '-'}
                  icon="chat_bubble"
                  toneLight="bg-[#c9b8ff]"
                  toneDark="dark:bg-indigo-500/16"
                />
                <MetricCard
                  label="消息数"
                  value={dashboard?.kpis.messageCount ?? '-'}
                  icon="bolt"
                  toneLight="bg-[#ffc86f]"
                  toneDark="dark:bg-amber-500/16"
                />
                <MetricCard
                  label="工作空间"
                  value={dashboard?.kpis.workspaceCount ?? '-'}
                  icon="deployed_code"
                  toneLight="bg-[#87e7bb]"
                  toneDark="dark:bg-emerald-500/16"
                />
              </div>
            </section>

            <section className="rounded-[28px] border border-border-hairline bg-surface-container-lowest p-5 shadow-[0_18px_40px_rgba(15,23,42,0.06)]">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-[16px] font-semibold text-ink">流量概览</h2>
                <span className="text-[12px] text-secondary">消息趋势</span>
              </div>
              <div data-testid="dashboard-traffic-chart" className="h-[300px]">
                <Area
                  {...buildAreaConfig(mainTrafficData, '--chart-primary', chartTheme, '消息数', '条')}
                  data-testid="dashboard-traffic-plot"
                />
              </div>
            </section>

            <section className="rounded-[28px] border border-border-hairline bg-surface-container-lowest p-5 shadow-[0_18px_40px_rgba(15,23,42,0.06)]">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-[16px] font-semibold text-ink">趋势分析</h2>
                <span className="text-[12px] text-secondary">窗口 {windowValue}</span>
              </div>
              <div className="grid gap-4 lg:grid-cols-2">
                <TrendChartCard
                  title="会话趋势"
                  meta="单位：次"
                  legendLabel="会话数"
                  legendColor="#22c55e"
                  testId="dashboard-conversation-chart"
                >
                  <Line
                    {...buildLineConfig(conversationTrendData, '#22c55e', chartTheme, '次', '会话数')}
                    data-testid="dashboard-conversation-plot"
                  />
                </TrendChartCard>
                <TrendChartCard
                  title="活跃用户趋势"
                  meta="单位：人"
                  legendLabel="活跃用户"
                  legendColor="#8b5cf6"
                  testId="dashboard-active-user-chart"
                >
                  <Line
                    {...buildLineConfig(activeUserTrendData, '#8b5cf6', chartTheme, '人', '活跃用户')}
                    data-testid="dashboard-active-user-plot"
                  />
                </TrendChartCard>
                <TrendChartCard
                  title="响应耗时趋势"
                  meta="单位：秒"
                  legendLabel="平均响应时间"
                  legendColor="#f59e0b"
                  annotation={`警告 > ${formatSeconds(LATENCY_WARNING_SECONDS)}`}
                  testId="dashboard-latency-chart"
                >
                  <Line
                    {...buildLineConfig(latencyTrendData, '#f59e0b', chartTheme, '秒', '平均响应时间', { domainMin: 0 })}
                    data-testid="dashboard-latency-plot"
                  />
                </TrendChartCard>
                <TrendChartCard
                  title="质量趋势"
                  meta="单位：次"
                  legendItems={[
                    { label: '成功', color: '#22c55e' },
                    { label: '失败', color: '#ef4444' },
                  ]}
                  testId="dashboard-quality-chart"
                >
                  <Column {...buildColumnConfig(qualityTrendData, chartTheme)} data-testid="dashboard-quality-plot" />
                </TrendChartCard>
              </div>
            </section>
          </div>

          <aside className="space-y-5">
            <section className="rounded-[28px] border border-border-hairline bg-surface-container-lowest p-5 shadow-[0_18px_40px_rgba(15,23,42,0.06)]">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-[16px] font-semibold text-ink">AI 性能</h2>
                <span className="rounded-full bg-amber-100 px-2.5 py-1 text-[11px] font-semibold text-amber-700 dark:bg-amber-500/12 dark:text-amber-300">
                  需要关注
                </span>
              </div>
              <div data-testid="dashboard-success-ring" className="mx-auto h-[190px] max-w-[240px]">
                <Pie {...buildRingConfig(ringData)} data-testid="dashboard-success-ring-plot" />
              </div>
              <div className="space-y-3 text-[13px]">
                <StatRow label="平均响应" value={formatDuration(dashboard?.performance.avgTraceDurationMs)} accent="text-emerald-500" />
                <StatRow label="P95 响应" value={formatDuration(dashboard?.performance.p95TraceDurationMs)} accent="text-rose-500" />
                <StatRow label="运行中占比" value={`${dashboard?.performance.runningRate ?? 0}%`} accent="text-sky-500" />
                <StatRow label="链路总数" value={`${dashboard?.kpis.traceCount ?? 0}`} />
              </div>
            </section>

            <SidebarCard title="质量快照">
              <QualityBar label="失败率" value={dashboard?.performance.failureRate ?? 0} colorClass="bg-rose-500" />
              <QualityBar label="成功率" value={dashboard?.performance.successRate ?? 0} colorClass="bg-amber-500" />
              <QualityBar
                label="排队压力"
                value={Math.min(100, ((runtimeDashboard?.queue.waitingCount ?? 0) / Math.max(1, runtimeDashboard?.queue.maxConcurrent ?? 1)) * 100)}
                colorClass="bg-sky-500"
              />
              <StatRow label="运行中链路" value={`${dashboard?.kpis.runningTraceCount ?? 0}`} />
            </SidebarCard>

            <SidebarCard title="运营效率">
              <StatRow label="人均会话" value={formatRatio(resolveAverage(dashboard?.kpis.conversationCount, dashboard?.kpis.activeUserCount))} />
              <StatRow label="单会话消息" value={formatRatio(resolveAverage(dashboard?.kpis.messageCount, dashboard?.kpis.conversationCount))} />
              <StatRow label="每空间会话" value={formatRatio(resolveAverage(dashboard?.kpis.conversationCount, dashboard?.kpis.workspaceCount))} />
              <StatRow label="技能总数" value={`${dashboard?.resources.skillCount ?? 0}`} />
            </SidebarCard>

            <SidebarCard title="运营洞察">
              <InsightItem
                badge="趋势"
                badgeTone="text-sky-600 bg-sky-50 dark:text-sky-300 dark:bg-sky-500/12"
                title="控制台首页已切换到新结构"
                body={`当前窗口共记录 ${dashboard?.kpis.traceCount ?? 0} 条链路，资源侧统计技能 ${dashboard?.resources.skillCount ?? 0} 个。`}
              />
              <InsightItem
                badge="建议"
                badgeTone="text-amber-600 bg-amber-50 dark:text-amber-300 dark:bg-amber-500/12"
                title="关注排队与失败率"
                body={`队列等待 ${runtimeDashboard?.queue.waitingCount ?? 0}，失败率 ${(dashboard?.performance.failureRate ?? 0).toFixed(1)}%。`}
              />
            </SidebarCard>
          </aside>
        </div>
      </div>
    </div>
  );
}

function MetricCard({
  label,
  value,
  icon,
  toneLight,
  toneDark,
}: {
  label: string;
  value: number | string;
  icon: string;
  toneLight: string;
  toneDark: string;
}) {
  return (
    <div className="rounded-[24px] bg-surface-container-low p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-[30px] font-semibold tracking-[-0.04em] text-ink">{value}</div>
          <div className="mt-1 text-[13px] text-secondary">{label}</div>
        </div>
        <div className={`flex h-12 w-12 items-center justify-center rounded-2xl ${toneLight} ${toneDark}`}>
          <span
            className="material-symbols-outlined text-[22px]"
            style={{
              color: '#ffffff',
              fontVariationSettings: "'FILL' 1, 'wght' 700, 'GRAD' 0, 'opsz' 24",
              textShadow: '0 1px 1px rgba(0, 0, 0, 0.08)',
            }}
          >
            {icon}
          </span>
        </div>
      </div>
      <div className="mt-4 text-[12px] text-secondary">数据来自当前控制台真实运行与配置快照</div>
    </div>
  );
}

function TrendChartCard({
  title,
  meta,
  legendLabel,
  legendColor,
  legendItems,
  annotation,
  testId,
  children,
}: {
  title: string;
  meta?: string;
  legendLabel?: string;
  legendColor?: string;
  legendItems?: Array<{ label: string; color: string }>;
  annotation?: string;
  testId: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-[24px] bg-surface-container-low p-4">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <div className="text-[14px] font-medium text-ink">{title}</div>
          {meta ? <div className="mt-1 text-[12px] text-secondary">{meta}</div> : null}
          {legendItems?.length ? (
            <div className="mt-2 flex flex-wrap items-center gap-3">
              {legendItems.map((item) => (
                <LegendChip key={item.label} label={item.label} color={item.color} />
              ))}
            </div>
          ) : legendLabel && legendColor ? (
            <div className="mt-2">
              <LegendChip label={legendLabel} color={legendColor} />
            </div>
          ) : null}
        </div>
        {annotation ? <span className="text-[11px] text-secondary">{annotation}</span> : null}
      </div>
      <div data-testid={testId} className="h-[220px]">
        {children}
      </div>
    </div>
  );
}

function LegendChip({ label, color }: { label: string; color: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-[12px] text-secondary">
      <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: color }} />
      {label}
    </span>
  );
}

function SidebarCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-[28px] border border-border-hairline bg-surface-container-lowest p-5 shadow-[0_18px_40px_rgba(15,23,42,0.06)]">
      <h2 className="mb-4 text-[16px] font-semibold text-ink">{title}</h2>
      <div className="space-y-4">{children}</div>
    </section>
  );
}

function StatRow({
  label,
  value,
  accent,
}: {
  label: string;
  value: string;
  accent?: string;
}) {
  return (
    <div className="flex items-center justify-between gap-3 text-[13px]">
      <span className="text-secondary">{label}</span>
      <span className={['font-semibold text-ink', accent ?? ''].join(' ')}>{value}</span>
    </div>
  );
}

function QualityBar({
  label,
  value,
  colorClass,
}: {
  label: string;
  value: number;
  colorClass: string;
}) {
  const normalizedValue = Math.max(0, Math.min(100, value));
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-3 text-[13px]">
        <span className="text-secondary">{label}</span>
        <span className="font-semibold text-ink">{normalizedValue.toFixed(1)}%</span>
      </div>
      <div className="h-3 rounded-full bg-surface-container-low">
        <div className={`h-3 rounded-full ${colorClass}`} style={{ width: `${normalizedValue}%` }} />
      </div>
    </div>
  );
}

function InsightItem({
  badge,
  badgeTone,
  title,
  body,
}: {
  badge: string;
  badgeTone: string;
  title: string;
  body: string;
}) {
  return (
    <div className="rounded-[22px] bg-surface-container-low p-4">
      <span className={`inline-flex rounded-full px-2.5 py-1 text-[11px] font-semibold ${badgeTone}`}>{badge}</span>
      <div className="mt-3 text-[14px] font-semibold text-ink">{title}</div>
      <p className="mt-2 text-[13px] leading-6 text-secondary">{body}</p>
    </div>
  );
}

function buildAreaConfig(
  data: Array<{ label: string; value: number }>,
  colorToken: string,
  chartTheme: ChartTheme,
  seriesLabel: string,
  unit: string,
) {
  return {
    data,
    xField: 'label',
    yField: 'value',
    height: 300,
    smooth: true,
    color: `var(${colorToken})`,
    line: {
      style: {
        lineWidth: 2.5,
      },
    },
    areaStyle: {
      fill: 'l(270) 0:rgba(59,130,246,0.26) 1:rgba(59,130,246,0.02)',
    },
    scale: {
      y: {
        type: 'linear',
        nice: true,
        domainMin: 0,
      },
    },
    axis: buildAxisTheme(chartTheme, unit),
    tooltip: buildTooltipTheme({
      title: { field: 'label' },
      items: [buildSingleSeriesTooltipItem(seriesLabel, unit)],
    }),
    legend: false,
    padding: [16, 16, 40, 52],
  };
}

function buildLineConfig(
  data: Array<{ label: string; value: number }>,
  color: string,
  chartTheme: ChartTheme,
  unit: string,
  seriesLabel: string,
  yScaleOverrides?: Record<string, unknown>,
) {
  const xLabelFormatter = buildSparseLabelFormatter(data.map((item) => item.label));
  return {
    data,
    xField: 'label',
    yField: 'value',
    height: 220,
    smooth: true,
    color,
    scale: {
      y: {
        type: 'linear',
        nice: true,
        domainMin: 0,
        ...yScaleOverrides,
      },
    },
    point: {
      size: 3,
      shape: 'circle',
      style: {
        fill: color,
        stroke: '#ffffff',
        lineWidth: 1.5,
      },
    },
    axis: buildAxisTheme(chartTheme, unit, xLabelFormatter),
    tooltip: buildTooltipTheme({
      title: { field: 'label' },
      items: [buildSingleSeriesTooltipItem(seriesLabel, unit)],
    }),
    legend: false,
    padding: [16, 16, 40, 52],
  };
}

function buildColumnConfig(data: Array<{ label: string; type: string; value: number }>, chartTheme: ChartTheme) {
  const xLabelFormatter = buildSparseLabelFormatter(
    Array.from(new Set(data.map((item) => item.label))),
  );
  return {
    data,
    xField: 'label',
    yField: 'value',
    seriesField: 'type',
    height: 220,
    color: ['#22c55e', '#ef4444'],
    scale: {
      y: {
        type: 'linear',
        nice: true,
        domainMin: 0,
      },
    },
    axis: buildAxisTheme(chartTheme, '次', xLabelFormatter),
    tooltip: buildTooltipTheme({
      title: { field: 'label' },
      items: [
        (datum: { type: string; value: number }) => ({
          name: datum.type,
          value: formatTooltipValue(datum.value, '次'),
        }),
      ],
    }),
    legend: {
      position: 'top-left' as const,
      itemLabelFill: chartTheme.axisLabelColor,
    },
    padding: [16, 16, 40, 52],
  };
}

function buildRingConfig(data: Array<{ type: string; value: number }>) {
  return {
    data,
    angleField: 'value',
    colorField: 'type',
    innerRadius: 0.74,
    color: ['#10b981', 'rgba(148,163,184,0.18)'],
    legend: false,
    tooltip: buildTooltipTheme({
      items: [
        // 环图默认 tooltip 在当前主题覆盖下只露出色块，显式输出名称和值保证悬浮层可读。
        (datum: { type: string; value: number }) => ({
          name: datum.type,
          value: `${datum.value.toFixed(1)}%`,
        }),
      ],
    }),
    label: {
      position: 'center',
      text: `${data[0]?.value.toFixed(1) ?? '0.0'}%`,
      style: {
        fontSize: 28,
        fontWeight: 700,
        fill: 'var(--theme-ink)',
      },
    },
    annotations: [
      {
        type: 'text',
        style: {
          text: '成功率',
          x: '50%',
          y: '58%',
          textAlign: 'center',
          fill: 'var(--theme-secondary)',
          fontSize: 12,
        },
      },
    ],
  };
}

function buildAxisTheme(
  chartTheme: ChartTheme,
  unit: string,
  xLabelFormatter?: (value: string) => string,
) {
  return {
    x: {
      labelFill: chartTheme.axisLabelColor,
      labelOpacity: 1,
      labelFormatter: xLabelFormatter,
      lineStroke: chartTheme.axisStrokeColor,
      tickStroke: chartTheme.axisStrokeColor,
      tickLength: 4,
      grid: false,
    },
    y: {
      title: unit ? unit : false,
      titleFill: chartTheme.axisLabelColor,
      labelFill: chartTheme.axisLabelColor,
      labelOpacity: 1,
      lineStroke: chartTheme.axisStrokeColor,
      tickStroke: chartTheme.axisStrokeColor,
      tickLength: 4,
      gridStroke: chartTheme.gridStrokeColor,
      gridStrokeOpacity: 1,
    },
  };
}

function buildTooltipTheme(overrides?: Record<string, unknown>) {
  return {
    titleFill: 'var(--theme-ink)',
    marker: true,
    ...overrides,
    domStyles: {
      'g2-tooltip': {
        borderRadius: '16px',
        border: '1px solid var(--theme-border-hairline)',
        background: 'var(--theme-surface-container-lowest)',
        color: 'var(--theme-ink)',
        padding: '10px 12px',
        boxShadow: '0 18px 42px rgba(15,23,42,0.18)',
      },
      'g2-tooltip-title': {
        color: 'var(--theme-ink)',
      },
      'g2-tooltip-list-item': {
        color: 'var(--theme-ink)',
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
      },
      'g2-tooltip-list-item-name': {
        color: 'var(--theme-secondary)',
      },
      'g2-tooltip-list-item-value': {
        color: 'var(--theme-ink)',
        fontWeight: 700,
      },
      'g2-tooltip-name': {
        color: 'var(--theme-secondary)',
      },
      'g2-tooltip-value': {
        color: 'var(--theme-ink)',
        fontWeight: 700,
      },
    },
  };
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

function formatDuration(value?: number) {
  if (!value || value <= 0) {
    return '-';
  }
  if (value < 1000) {
    return `${Math.round(value)}ms`;
  }
  return `${(value / 1000).toFixed(2)}s`;
}

function resolveAverage(numerator?: number, denominator?: number) {
  if (!numerator || !denominator) {
    return 0;
  }
  return numerator / denominator;
}

function formatRatio(value: number) {
  if (!value || Number.isNaN(value)) {
    return '-';
  }
  return value.toFixed(2);
}

function buildSingleSeriesTooltipItem(seriesLabel: string, unit: string) {
  return (datum: { value: number }) => ({
    name: seriesLabel,
    value: formatTooltipValue(datum.value, unit),
  });
}

function formatTooltipValue(value: number, unit: string) {
  if (unit === '秒') {
    return formatSeconds(value);
  }
  if (unit === '人') {
    return `${value} 人`;
  }
  if (unit === '次' || unit === '条') {
    return `${value} ${unit}`;
  }
  return String(value);
}

function toLatencySeconds(value: number) {
  return Number((value / MILLISECONDS_PER_SECOND).toFixed(1));
}

function formatSeconds(value: number) {
  return `${value.toFixed(1)} 秒`;
}

function buildSparseLabelFormatter(labels: string[], maxVisible = 5) {
  if (labels.length <= maxVisible) {
    return (value: string) => value;
  }
  const lastIndex = labels.length - 1;
  const visibleIndexes = new Set<number>();
  const step = lastIndex / Math.max(1, maxVisible - 1);
  for (let index = 0; index < maxVisible; index += 1) {
    visibleIndexes.add(Math.round(index * step));
  }
  visibleIndexes.add(lastIndex);
  const indexMap = new Map<string, number>();
  labels.forEach((label, index) => {
    if (!indexMap.has(label)) {
      indexMap.set(label, index);
    }
  });
  return (value: string) => {
    const index = indexMap.get(value);
    if (index === undefined) {
      return value;
    }
    return visibleIndexes.has(index) ? value : '';
  };
}

interface ChartTheme {
  axisLabelColor: string;
  axisStrokeColor: string;
  gridStrokeColor: string;
}

function useChartTheme(): ChartTheme {
  const resolveTheme = React.useCallback(() => {
    const root = document.documentElement;
    if (root.classList.contains('dark')) {
      return {
        axisLabelColor: '#a8b0bd',
        axisStrokeColor: '#4b5563',
        gridStrokeColor: 'rgba(107, 114, 128, 0.32)',
      };
    }
    return {
      axisLabelColor: '#6b7280',
      axisStrokeColor: '#cbd5e1',
      gridStrokeColor: 'rgba(148, 163, 184, 0.22)',
    };
  }, []);

  const [theme, setTheme] = React.useState<ChartTheme>(() => resolveTheme());

  React.useEffect(() => {
    const root = document.documentElement;
    const observer = new MutationObserver(() => {
      setTheme(resolveTheme());
    });
    observer.observe(root, { attributes: true, attributeFilter: ['class'] });
    return () => observer.disconnect();
  }, [resolveTheme]);

  return theme;
}
