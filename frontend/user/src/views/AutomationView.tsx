import React, { useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { CalendarClock, MessageSquareText, Plus, RotateCcw } from 'lucide-react';

import { AutomationTaskCreateDialog } from './automation/AutomationTaskCreateDialog';
import { AutomationTaskList } from './automation/AutomationTaskList';
import { useAutomationTasks } from './automation/useAutomationTasks';

/**
 * 渲染自动化定时任务页面，支持手动创建和查看聊天创建的任务。
 */
export default function AutomationView() {
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const {
    tasks,
    isLoading,
    isSaving,
    errorMessage,
    setErrorMessage,
    reloadTasks,
    createTask,
  } = useAutomationTasks();

  const activeTaskCount = useMemo(() => tasks.filter((task) => task.enabled).length, [tasks]);
  const chatTaskCount = useMemo(() => tasks.filter((task) => task.sourceType === 'CHAT').length, [tasks]);
  const nextRunLabel = useMemo(() => {
    const nextTask = tasks.find((task) => task.enabled && task.nextRunAt);
    return nextTask?.nextRunAt ? nextTask.nextRunAt.replace('T', ' ').slice(0, 16) : '待创建';
  }, [tasks]);

  /**
   * 提交创建表单，成功后关闭弹窗；失败时保留弹窗并原样展示后端 message。
   */
  const handleCreateSubmit: Parameters<typeof AutomationTaskCreateDialog>[0]['onSubmit'] = async (payload) => {
    try {
      await createTask(payload);
      setIsCreateDialogOpen(false);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '自动化任务请求失败');
    }
  };

  return (
    <motion.div
      animate={{ opacity: 1, x: 0 }}
      className="h-full overflow-y-auto bg-background px-5 py-6 text-foreground md:px-10 md:py-8"
      exit={{ opacity: 0, x: -20 }}
      initial={{ opacity: 0, x: 20 }}
    >
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-6">
        <header className="flex flex-col gap-5 border-b border-border pb-6 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-2xl">
            <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-border bg-surface px-3 py-1 text-xs font-medium text-muted">
              <CalendarClock size={14} />
              自动执行
            </div>
            <h1 className="text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
              定时任务
            </h1>
            <p className="mt-3 text-sm leading-6 text-muted">
              手动创建周期任务，或在聊天里直接说出执行时间和需求，系统会在当前会话内完成创建并留下任务消息。
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <button
              className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-surface px-4 text-sm font-medium text-muted transition-colors hover:border-border-active hover:text-foreground"
              onClick={() => void reloadTasks()}
              type="button"
            >
              <RotateCcw size={16} />
              刷新
            </button>
            <button
              className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
              onClick={() => {
                setErrorMessage('');
                setIsCreateDialogOpen(true);
              }}
              type="button"
            >
              <Plus size={16} />
              新建定时任务
            </button>
          </div>
        </header>

        <section className="grid gap-3 md:grid-cols-3">
          <AutomationMetric label="运行中" value={String(activeTaskCount)} />
          <AutomationMetric label="会话创建" value={String(chatTaskCount)} icon={<MessageSquareText size={16} />} />
          <AutomationMetric label="下次运行" value={nextRunLabel} />
        </section>

        {errorMessage && !isCreateDialogOpen && (
          <div className="rounded-md border border-error/35 bg-error/10 px-4 py-3 text-sm text-error">
            {errorMessage}
          </div>
        )}

        <AutomationTaskList
          isLoading={isLoading}
          onCreate={() => {
            setErrorMessage('');
            setIsCreateDialogOpen(true);
          }}
          tasks={tasks}
        />
      </div>

      <AutomationTaskCreateDialog
        errorMessage={errorMessage}
        isOpen={isCreateDialogOpen}
        isSaving={isSaving}
        onClearError={() => setErrorMessage('')}
        onClose={() => {
          setErrorMessage('');
          setIsCreateDialogOpen(false);
        }}
        onSubmit={handleCreateSubmit}
      />
    </motion.div>
  );
}

/**
 * 页面顶部指标块，使用轻量边框分区展示任务统计，避免把页面拆成多层卡片。
 */
function AutomationMetric({
  label,
  value,
  icon,
}: {
  label: string;
  value: string;
  icon?: React.ReactNode;
}) {
  return (
    <div className="min-w-0 rounded-lg border border-border bg-surface px-4 py-3">
      <div className="flex items-center gap-2 text-xs font-medium text-muted">
        {icon}
        {label}
      </div>
      <div className="mt-2 truncate text-lg font-semibold text-foreground">{value}</div>
    </div>
  );
}
