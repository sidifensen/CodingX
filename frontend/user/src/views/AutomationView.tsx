import React, { useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { CalendarClock, MessageSquareText, Plus, RotateCcw } from 'lucide-react';

import { Button } from '../components/ui/Button';
import { AutomationTaskCreateDialog } from './automation/AutomationTaskCreateDialog';
import { AutomationTaskList } from './automation/AutomationTaskList';
import { AutomationTask, AutomationTaskSavePayload } from './automation/types';
import { useAutomationTasks } from './automation/useAutomationTasks';

/**
 * 渲染自动化定时任务页面，支持手动创建、会话创建任务查看，以及任务编辑/启停/删除。
 */
export default function AutomationView() {
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [editingTask, setEditingTask] = useState<AutomationTask | null>(null);
  const [deletingTask, setDeletingTask] = useState<AutomationTask | null>(null);
  const {
    tasks,
    isLoading,
    isSaving,
    errorMessage,
    setErrorMessage,
    reloadTasks,
    createTask,
    updateTask,
    updateTaskEnabled,
    deleteTask,
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
  const handleCreateSubmit = async (payload: AutomationTaskSavePayload) => {
    try {
      await createTask(payload);
      setIsCreateDialogOpen(false);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '自动化任务请求失败');
    }
  };

  /**
   * 编辑任务成功后关闭弹窗；失败时保留弹窗并展示后端中文 message。
   */
  const handleEditSubmit = async (payload: AutomationTaskSavePayload) => {
    if (!editingTask) {
      return;
    }

    try {
      await updateTask(editingTask.id, payload);
      setEditingTask(null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '自动化任务请求失败');
    }
  };

  /**
   * 启停任务时不做本地乐观更新，避免 nextRunAt 与服务端重新计算结果不一致。
   */
  const handleToggleEnabled = async (task: AutomationTask) => {
    try {
      await updateTaskEnabled(task.id, !task.enabled);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '自动化任务请求失败');
    }
  };

  /**
   * 删除确认后由后端逻辑删除任务，刷新列表为空时回到空态。
   */
  const handleDeleteConfirm = async () => {
    if (!deletingTask) {
      return;
    }

    try {
      await deleteTask(deletingTask.id);
      setDeletingTask(null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '自动化任务请求失败');
    }
  };

  const isAnyDialogOpen = isCreateDialogOpen || editingTask !== null || deletingTask !== null;

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
            <Button
              onClick={() => void reloadTasks()}
              variant="outline"
            >
              <RotateCcw size={16} />
              刷新
            </Button>
            <Button
              onClick={() => {
                setErrorMessage('');
                setIsCreateDialogOpen(true);
              }}
            >
              <Plus size={16} />
              新建定时任务
            </Button>
          </div>
        </header>

        <section className="grid gap-3 md:grid-cols-3">
          <AutomationMetric label="运行中" value={String(activeTaskCount)} />
          <AutomationMetric label="会话创建" value={String(chatTaskCount)} icon={<MessageSquareText size={16} />} />
          <AutomationMetric label="下次运行" value={nextRunLabel} />
        </section>

        {errorMessage && !isAnyDialogOpen && (
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
          onDelete={(task) => {
            setErrorMessage('');
            setDeletingTask(task);
          }}
          onEdit={(task) => {
            setErrorMessage('');
            setEditingTask(task);
          }}
          onToggleEnabled={(task) => {
            setErrorMessage('');
            void handleToggleEnabled(task);
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
      <AutomationTaskCreateDialog
        errorMessage={errorMessage}
        initialTask={editingTask}
        isOpen={editingTask !== null}
        isSaving={isSaving}
        mode="edit"
        onClearError={() => setErrorMessage('')}
        onClose={() => {
          setErrorMessage('');
          setEditingTask(null);
        }}
        onSubmit={handleEditSubmit}
      />
      <AutomationTaskDeleteDialog
        isOpen={deletingTask !== null}
        isSaving={isSaving}
        onClose={() => {
          setErrorMessage('');
          setDeletingTask(null);
        }}
        onConfirm={handleDeleteConfirm}
        task={deletingTask}
      />
    </motion.div>
  );
}

/**
 * 删除确认弹窗使用项目内浮层，避免浏览器原生 confirm 破坏主题与交互一致性。
 */
function AutomationTaskDeleteDialog({
  isOpen,
  isSaving,
  task,
  onClose,
  onConfirm,
}: {
  isOpen: boolean;
  isSaving: boolean;
  task: AutomationTask | null;
  onClose: () => void;
  onConfirm: () => Promise<void>;
}) {
  if (!isOpen || !task) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-background/82 px-4 py-6 backdrop-blur-sm"
      role="presentation"
    >
      <section
        aria-labelledby="automation-delete-dialog-title"
        aria-modal="true"
        className="w-full max-w-[420px] rounded-lg border border-border bg-surface p-5 text-foreground shadow-[0_24px_70px_rgba(0,0,0,0.34)]"
        role="dialog"
      >
        <h2 id="automation-delete-dialog-title" className="text-lg font-semibold text-foreground">
          删除定时任务
        </h2>
        <p className="mt-3 text-sm leading-6 text-muted">
          确认删除
          <span className="mx-1 font-medium text-foreground">{task.name}</span>
          后，系统将停止后续调度，并从任务列表中移除。
        </p>
        <footer className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button onClick={onClose} variant="outline">
            取消
          </Button>
          <Button disabled={isSaving} onClick={() => void onConfirm()} variant="danger">
            删除
          </Button>
        </footer>
      </section>
    </div>
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
