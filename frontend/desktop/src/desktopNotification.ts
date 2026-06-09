import { DesktopNotificationPayload } from './types';

const DEFAULT_NOTIFICATION_TITLE = 'CodingX 通知';
const DEFAULT_NOTIFICATION_BODY = '有一项后台任务状态已更新';

/**
 * 描述主进程真正交给 Electron Notification 的安全载荷。
 */
export interface NormalizedDesktopNotificationPayload {
  title: string;
  body: string;
  conversationId?: string;
  runId?: string;
  hookCode?: string;
  hookName?: string;
  triggerPoint?: string;
  actionType?: string;
  contextText?: string;
  toolCode?: string;
}

/**
 * 将渲染层传入的 Hook 通知载荷清洗成系统通知可直接展示的文本。
 * @param payload 渲染层或后端 SSE 传入的通知载荷。
 * @returns 带中文兜底文案的系统通知载荷。
 */
export function normalizeDesktopNotificationPayload(
  payload: DesktopNotificationPayload | null | undefined,
): NormalizedDesktopNotificationPayload {
  const source = payload ?? {};
  return {
    title: normalizeText(source.title, DEFAULT_NOTIFICATION_TITLE),
    body: normalizeText(source.body, DEFAULT_NOTIFICATION_BODY),
    ...optionalTextFields(source),
  };
}

/**
 * 归一化可展示文本，避免空字符串进入 Windows 通知中心。
 * @param value 原始字段值。
 * @param fallback 中文兜底文案。
 * @returns 可展示文本。
 */
function normalizeText(value: unknown, fallback: string): string {
  if (typeof value !== 'string' && typeof value !== 'number') {
    return fallback;
  }
  const text = String(value).trim();
  return text.length > 0 ? text : fallback;
}

/**
 * 只保留用于点击聚焦和后续排查的上下文字段，避免把未知对象透传给系统通知。
 * @param payload 原始通知载荷。
 * @returns 已清洗的可选上下文字段。
 */
function optionalTextFields(
  payload: DesktopNotificationPayload,
): Omit<NormalizedDesktopNotificationPayload, 'title' | 'body'> {
  const fields: Omit<NormalizedDesktopNotificationPayload, 'title' | 'body'> = {};
  assignOptionalText(fields, 'conversationId', payload.conversationId);
  assignOptionalText(fields, 'runId', payload.runId);
  assignOptionalText(fields, 'hookCode', payload.hookCode);
  assignOptionalText(fields, 'hookName', payload.hookName);
  assignOptionalText(fields, 'triggerPoint', payload.triggerPoint);
  assignOptionalText(fields, 'actionType', payload.actionType);
  assignOptionalText(fields, 'contextText', payload.contextText);
  assignOptionalText(fields, 'toolCode', payload.toolCode);
  return fields;
}

/**
 * 写入非空上下文字段，保持通知载荷紧凑且避免空字符串覆盖默认值。
 * @param fields 目标字段集合。
 * @param key 字段名。
 * @param value 原始字段值。
 */
function assignOptionalText(
  fields: Omit<NormalizedDesktopNotificationPayload, 'title' | 'body'>,
  key: keyof Omit<NormalizedDesktopNotificationPayload, 'title' | 'body'>,
  value: unknown,
) {
  if (typeof value !== 'string' && typeof value !== 'number') {
    return;
  }
  const text = String(value).trim();
  if (text.length > 0) {
    fields[key] = text;
  }
}
