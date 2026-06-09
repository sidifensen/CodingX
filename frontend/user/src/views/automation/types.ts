/**
 * 自动化任务计划类型，和后端 AutomationScheduleType 保持同名，避免前端二次映射。
 */
export type AutomationScheduleType = 'DAILY' | 'WEEKLY' | 'ONCE';

/**
 * 自动化任务创建来源，MANUAL 表示页面手动创建，CHAT 表示聊天会话中直接创建。
 */
export type AutomationTaskSourceType = 'MANUAL' | 'CHAT';

/**
 * 自动化任务列表项，字段来自后端 `/api/automation/tasks` 响应。
 */
export interface AutomationTask {
  /** 任务主键，前端统一转成字符串以兼容 Long/number 返回。 */
  id: string;
  /** 任务名称，用于列表主标题。 */
  name: string;
  /** 任务需求说明，调度执行时作为 AI 任务输入。 */
  prompt: string;
  /** 创建来源，用于区分手动任务和会话任务。 */
  sourceType: AutomationTaskSourceType;
  /** 来源会话 ID；手动任务为空，会话创建任务有值。 */
  sourceConversationId: string | null;
  /** 计划类型，决定页面的计划文案和创建请求参数。 */
  scheduleType: AutomationScheduleType;
  /** 固定执行时间，DAILY/WEEKLY 使用 HH:mm。 */
  scheduleTime: string | null;
  /** 每周执行星期，1 表示周一，7 表示周日。 */
  scheduleDayOfWeek: number | null;
  /** 一次性任务执行时间，ONCE 类型可用。 */
  onceExecuteAt: string | null;
  /** 下一次计划运行时间，用于列表排序说明。 */
  nextRunAt: string | null;
  /** 最近一次运行时间，尚未运行时为空。 */
  lastRunAt: string | null;
  /** 最近一次运行状态，后端当前返回 PENDING/TRIGGERED 等摘要。 */
  lastRunStatus: string | null;
  /** 是否启用，调度器只扫描启用任务。 */
  enabled: boolean;
  /** 归属工作空间 ID；手动全局任务可为空。 */
  workspaceId: string | null;
}

/**
 * 自动化任务保存请求体；创建和编辑使用同一组计划字段，避免两套表单协议漂移。
 */
export interface AutomationTaskSavePayload {
  /** 任务名称，不能为空。 */
  name: string;
  /** 任务需求说明，不能为空。 */
  prompt: string;
  /** 计划类型，默认 DAILY。 */
  scheduleType: AutomationScheduleType;
  /** 固定执行时间，DAILY/WEEKLY 必填。 */
  scheduleTime: string | null;
  /** WEEKLY 类型的执行星期，其他类型为空。 */
  scheduleDayOfWeek: number | null;
  /** ONCE 类型的一次性执行时间，其他类型为空。 */
  onceExecuteAt: string | null;
  /** 当前工作空间 ID，自动化页暂未绑定具体工作空间时为空。 */
  workspaceId: string | null;
}

/**
 * 手动创建自动化任务的请求体。
 */
export type AutomationTaskCreatePayload = AutomationTaskSavePayload;

/**
 * 编辑自动化任务的请求体。
 */
export type AutomationTaskUpdatePayload = AutomationTaskSavePayload;
