import React from 'react';
import clsx from 'clsx';

export type TraceStatTone = 'emerald' | 'cyan' | 'indigo' | 'amber';

interface TraceStatCardProps {
  title: string;
  value: string;
  unit?: string;
  icon: React.ReactNode;
  tone: TraceStatTone;
}

/**
 * 列表页统计卡片：复用管理端主题色并保持亮暗模式可读性。
 */
export function TraceStatCard({ title, value, unit, icon, tone }: TraceStatCardProps) {
  return (
    <article className="rounded-xl border border-border-hairline bg-surface-container-lowest p-md shadow-sm">
      <div className="flex items-center gap-sm">
        <div
          className={clsx(
            'flex h-10 w-10 items-center justify-center rounded-lg border text-[16px]',
            toneClassName(tone),
          )}
        >
          {icon}
        </div>
        <div className="min-w-0">
          <p className="text-[12px] text-secondary">{title}</p>
          <div className="mt-1 flex items-baseline gap-1">
            <p className="text-[36px] leading-none font-semibold text-ink">{value}</p>
            {unit ? <span className="text-[12px] text-secondary">{unit}</span> : null}
          </div>
        </div>
      </div>
    </article>
  );
}

/**
 * 统计卡强调色映射。
 */
function toneClassName(tone: TraceStatTone): string {
  if (tone === 'emerald') return 'border-status-running-border bg-status-running-bg text-status-running';
  if (tone === 'cyan') return 'border-border-hairline bg-surface-container-low text-tertiary-container';
  if (tone === 'indigo') return 'border-border-hairline bg-surface-container-low text-primary';
  return 'border-status-pending-border bg-status-pending-bg text-status-pending';
}
