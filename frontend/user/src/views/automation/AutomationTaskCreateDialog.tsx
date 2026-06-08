import React, { FormEvent, useMemo, useState } from 'react';
import { CalendarClock, LoaderCircle, X } from 'lucide-react';

import { formatScheduleTypeLabel } from './formatters';
import { AutomationScheduleType, AutomationTaskCreatePayload } from './types';

interface AutomationTaskCreateDialogProps {
  /** 弹窗是否打开；关闭时不渲染遮罩，避免隐藏内容抢占焦点。 */
  isOpen: boolean;
  /** 创建接口是否正在提交，用于禁用重复点击。 */
  isSaving: boolean;
  /** 后端或本地校验错误文案，优先原样展示。 */
  errorMessage: string;
  /** 用户关闭弹窗时调用。 */
  onClose: () => void;
  /** 提交合法表单时调用，由上层完成 API 保存。 */
  onSubmit: (payload: AutomationTaskCreatePayload) => Promise<void>;
  /** 清理错误文案，用户再次编辑时避免旧错误残留。 */
  onClearError: () => void;
}

const scheduleOptions: AutomationScheduleType[] = ['DAILY', 'WEEKLY', 'ONCE'];

/**
 * 渲染自动化任务创建弹窗，使用项目内浮层替代浏览器原生弹窗。
 */
export function AutomationTaskCreateDialog({
  isOpen,
  isSaving,
  errorMessage,
  onClose,
  onSubmit,
  onClearError,
}: AutomationTaskCreateDialogProps) {
  const [name, setName] = useState('');
  const [prompt, setPrompt] = useState('');
  const [scheduleType, setScheduleType] = useState<AutomationScheduleType>('DAILY');
  const [scheduleTime, setScheduleTime] = useState('');
  const [scheduleDayOfWeek, setScheduleDayOfWeek] = useState(1);
  const [onceExecuteAt, setOnceExecuteAt] = useState('');
  const [localErrorMessage, setLocalErrorMessage] = useState('');

  const isFormReady = useMemo(() => {
    const hasBasicFields = name.trim().length > 0 && prompt.trim().length > 0;
    if (scheduleType === 'ONCE') {
      return hasBasicFields && onceExecuteAt.trim().length > 0;
    }
    return hasBasicFields && scheduleTime.trim().length > 0;
  }, [name, prompt, scheduleType, scheduleTime, onceExecuteAt]);

  if (!isOpen) {
    return null;
  }

  /**
   * 表单字段变化时同步清理本地与后端错误，避免用户修改后仍看到过期提示。
   */
  const handleFieldChange = (callback: () => void) => {
    callback();
    setLocalErrorMessage('');
    onClearError();
  };

  /**
   * 组装后端创建请求，ONCE 和周期任务只传各自需要的计划字段。
   */
  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!isFormReady) {
      setLocalErrorMessage('请填写任务名称、任务需求和执行时间');
      return;
    }

    await onSubmit({
      name: name.trim(),
      prompt: prompt.trim(),
      scheduleType,
      scheduleTime: scheduleType === 'ONCE' ? null : scheduleTime,
      scheduleDayOfWeek: scheduleType === 'WEEKLY' ? scheduleDayOfWeek : null,
      onceExecuteAt: scheduleType === 'ONCE' ? normalizeOnceExecuteAt(onceExecuteAt) : null,
      workspaceId: null,
    });
  };

  const visibleErrorMessage = localErrorMessage || errorMessage;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-background/82 px-4 py-6 backdrop-blur-sm"
      role="presentation"
    >
      <section
        aria-labelledby="automation-create-dialog-title"
        aria-modal="true"
        className="w-full max-w-[560px] overflow-hidden rounded-lg border border-border bg-surface text-foreground shadow-[0_24px_70px_rgba(0,0,0,0.34)]"
        role="dialog"
      >
        <header className="flex items-start justify-between gap-4 border-b border-border bg-surface-container px-5 py-4">
          <div className="flex min-w-0 gap-3">
            <span className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-border bg-surface">
              <CalendarClock size={20} className="text-foreground" />
            </span>
            <div className="min-w-0">
              <h2 id="automation-create-dialog-title" className="text-lg font-semibold text-foreground">
                新建定时任务
              </h2>
              <p className="mt-1 text-sm leading-6 text-muted">
                填写执行时间和需求，系统会按计划自动创建会话任务。
              </p>
            </div>
          </div>
          <button
            aria-label="关闭新建定时任务弹窗"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-border bg-surface text-muted transition-colors hover:border-border-active hover:text-foreground"
            onClick={onClose}
            type="button"
          >
            <X size={18} />
          </button>
        </header>

        <form className="space-y-5 px-5 py-5" onSubmit={handleSubmit}>
          <label className="block">
            <span className="text-sm font-medium text-foreground">任务名称</span>
            <input
              className="mt-2 h-11 w-full rounded-md border border-border bg-surface-container px-3 text-sm text-foreground outline-none transition-colors placeholder:text-muted focus:border-border-active"
              onChange={(event) => handleFieldChange(() => setName(event.target.value))}
              placeholder="例如：每日项目总结"
              value={name}
            />
          </label>

          <label className="block">
            <span className="text-sm font-medium text-foreground">任务需求</span>
            <textarea
              className="mt-2 min-h-28 w-full resize-none rounded-md border border-border bg-surface-container px-3 py-3 text-sm leading-6 text-foreground outline-none transition-colors placeholder:text-muted focus:border-border-active"
              onChange={(event) => handleFieldChange(() => setPrompt(event.target.value))}
              placeholder="写清楚希望 AI 到点完成的事情"
              value={prompt}
            />
          </label>

          <div className="grid gap-4 md:grid-cols-[1fr_1fr]">
            <label className="block">
              <span className="text-sm font-medium text-foreground">计划类型</span>
              <select
                className="mt-2 h-11 w-full rounded-md border border-border bg-surface-container px-3 text-sm text-foreground outline-none transition-colors focus:border-border-active"
                onChange={(event) =>
                  handleFieldChange(() => setScheduleType(event.target.value as AutomationScheduleType))
                }
                value={scheduleType}
              >
                {scheduleOptions.map((option) => (
                  <option key={option} value={option}>
                    {formatScheduleTypeLabel(option)}
                  </option>
                ))}
              </select>
            </label>

            {scheduleType === 'WEEKLY' && (
              <label className="block">
                <span className="text-sm font-medium text-foreground">执行星期</span>
                <select
                  className="mt-2 h-11 w-full rounded-md border border-border bg-surface-container px-3 text-sm text-foreground outline-none transition-colors focus:border-border-active"
                  onChange={(event) =>
                    handleFieldChange(() => setScheduleDayOfWeek(Number(event.target.value)))
                  }
                  value={scheduleDayOfWeek}
                >
                  <option value={1}>周一</option>
                  <option value={2}>周二</option>
                  <option value={3}>周三</option>
                  <option value={4}>周四</option>
                  <option value={5}>周五</option>
                  <option value={6}>周六</option>
                  <option value={7}>周日</option>
                </select>
              </label>
            )}

            {scheduleType === 'ONCE' ? (
              <label className="block">
                <span className="text-sm font-medium text-foreground">执行时间</span>
                <input
                  className="mt-2 h-11 w-full rounded-md border border-border bg-surface-container px-3 text-sm text-foreground outline-none transition-colors focus:border-border-active"
                  onChange={(event) => handleFieldChange(() => setOnceExecuteAt(event.target.value))}
                  type="datetime-local"
                  value={onceExecuteAt}
                />
              </label>
            ) : (
              <label className="block">
                <span className="text-sm font-medium text-foreground">执行时间</span>
                <input
                  className="mt-2 h-11 w-full rounded-md border border-border bg-surface-container px-3 text-sm text-foreground outline-none transition-colors focus:border-border-active"
                  onChange={(event) => handleFieldChange(() => setScheduleTime(event.target.value))}
                  type="time"
                  value={scheduleTime}
                />
              </label>
            )}
          </div>

          {visibleErrorMessage && (
            <div className="rounded-md border border-error/35 bg-error/10 px-3 py-2 text-sm text-error">
              {visibleErrorMessage}
            </div>
          )}

          <footer className="flex flex-col-reverse gap-3 border-t border-border pt-5 sm:flex-row sm:justify-end">
            <button
              className="h-10 rounded-md border border-border bg-surface px-4 text-sm font-medium text-muted transition-colors hover:border-border-active hover:text-foreground"
              onClick={onClose}
              type="button"
            >
              取消
            </button>
            <button
              className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground transition-opacity disabled:cursor-not-allowed disabled:opacity-45"
              disabled={!isFormReady || isSaving}
              type="submit"
            >
              {isSaving && <LoaderCircle size={16} className="animate-spin" />}
              创建任务
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}

/**
 * datetime-local 不带秒，后端 LocalDateTime 可直接接收补秒后的 ISO 字符串。
 */
function normalizeOnceExecuteAt(value: string): string {
  return value.length === 16 ? `${value}:00` : value;
}
