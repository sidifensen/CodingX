import { AutomationScheduleType, AutomationTask } from './types';

const WEEKDAY_LABELS: Record<number, string> = {
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日',
};

/**
 * 将计划类型和时间字段转换成列表可读文案。
 * @param task 自动化任务。
 * @returns 面向用户展示的计划摘要。
 */
export function formatScheduleLabel(task: Pick<AutomationTask, 'scheduleType' | 'scheduleTime' | 'scheduleDayOfWeek' | 'onceExecuteAt'>): string {
  if (task.scheduleType === 'WEEKLY') {
    const weekday = WEEKDAY_LABELS[task.scheduleDayOfWeek ?? 1] ?? '周一';
    return `每${weekday} ${task.scheduleTime ?? '--:--'}`;
  }
  if (task.scheduleType === 'ONCE') {
    return `仅一次 ${formatDateTime(task.onceExecuteAt)}`;
  }
  return `每天 ${task.scheduleTime ?? '--:--'}`;
}

/**
 * 生成计划类型短标签，供表单和列表复用。
 * @param scheduleType 计划类型。
 * @returns 中文短标签。
 */
export function formatScheduleTypeLabel(scheduleType: AutomationScheduleType): string {
  switch (scheduleType) {
    case 'WEEKLY':
      return '每周';
    case 'ONCE':
      return '一次';
    case 'DAILY':
    default:
      return '每天';
  }
}

/**
 * 格式化后端 LocalDateTime 字符串，保留到分钟，空值显示待计算。
 * @param value 后端日期时间字符串。
 * @returns 列表展示文本。
 */
export function formatDateTime(value: string | null): string {
  if (!value) {
    return '待计算';
  }
  const normalizedValue = value.replace('T', ' ');
  return normalizedValue.length >= 16 ? normalizedValue.slice(0, 16) : normalizedValue;
}
