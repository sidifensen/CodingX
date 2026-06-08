import React from 'react';
import { Bot, CalendarClock, Clock3, LoaderCircle, MessageSquareText, Plus } from 'lucide-react';

import { formatDateTime, formatScheduleLabel } from './formatters';
import { AutomationTask } from './types';

interface AutomationTaskListProps {
  /** 当前用户任务列表。 */
  tasks: AutomationTask[];
  /** 是否正在加载首屏数据。 */
  isLoading: boolean;
  /** 打开创建弹窗的回调。 */
  onCreate: () => void;
}

/**
 * 渲染自动化任务列表、加载态和空态。
 */
export function AutomationTaskList({ tasks, isLoading, onCreate }: AutomationTaskListProps) {
  if (isLoading) {
    return (
      <div className="flex min-h-[360px] items-center justify-center rounded-lg border border-border bg-surface">
        <div className="flex items-center gap-3 text-sm text-muted">
          <LoaderCircle size={18} className="animate-spin" />
          正在加载定时任务
        </div>
      </div>
    );
  }

  if (tasks.length === 0) {
    return <AutomationTaskEmptyState onCreate={onCreate} />;
  }

  return (
    <section className="overflow-hidden rounded-lg border border-border bg-surface">
      <header className="grid grid-cols-[1fr_auto] gap-4 border-b border-border bg-surface-container px-5 py-3 text-xs font-medium text-muted md:grid-cols-[1.4fr_0.8fr_0.8fr_auto]">
        <span>任务</span>
        <span className="hidden md:block">计划</span>
        <span className="hidden md:block">下次运行</span>
        <span className="text-right">状态</span>
      </header>
      <div className="divide-y divide-border">
        {tasks.map((task) => (
          <article
            className="grid gap-4 px-5 py-4 transition-colors hover:bg-surface-container md:grid-cols-[1.4fr_0.8fr_0.8fr_auto] md:items-center"
            key={task.id}
          >
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-border bg-surface-container">
                  {task.sourceType === 'CHAT' ? (
                    <MessageSquareText size={16} className="text-accent-breeze" />
                  ) : (
                    <CalendarClock size={16} className="text-foreground" />
                  )}
                </span>
                <h3 className="min-w-0 truncate text-base font-semibold text-foreground">{task.name}</h3>
              </div>
              <p className="mt-2 line-clamp-2 text-sm leading-6 text-muted">{task.prompt}</p>
            </div>

            <div className="flex items-center gap-2 text-sm text-foreground">
              <Clock3 size={15} className="text-muted" />
              <span>{formatScheduleLabel(task)}</span>
            </div>

            <div className="text-sm text-muted">{formatDateTime(task.nextRunAt)}</div>

            <div className="flex items-center justify-between gap-3 md:justify-end">
              <span className="md:hidden text-xs text-muted">状态</span>
              <span
                className={`inline-flex h-7 items-center rounded-full border px-3 text-xs font-medium ${
                  task.enabled
                    ? 'border-success/35 bg-success/10 text-success'
                    : 'border-border bg-surface-container text-muted'
                }`}
              >
                {task.enabled ? '运行中' : '已停用'}
              </span>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

/**
 * 空态保持任务管理语义，提供创建入口和聊天创建提示。
 */
function AutomationTaskEmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <section className="flex min-h-[420px] flex-col items-center justify-center rounded-lg border border-dashed border-border bg-surface px-6 py-12 text-center">
      <span className="flex h-14 w-14 items-center justify-center rounded-lg border border-border bg-surface-container">
        <Bot size={26} className="text-foreground" />
      </span>
      <h2 className="mt-5 text-xl font-semibold text-foreground">暂无定时任务</h2>
      <p className="mt-2 max-w-md text-sm leading-6 text-muted">
        可以在这里手动创建，也可以直接在聊天里说“每天 18:11 帮我总结项目状态”。
      </p>
      <button
        className="mt-6 inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
        onClick={onCreate}
        type="button"
      >
        <Plus size={16} />
        创建第一个任务
      </button>
    </section>
  );
}
