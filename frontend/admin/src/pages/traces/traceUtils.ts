import type { AdminTraceNode } from '../../api/adminChatApi';

export const TRACE_PAGE_SIZE = 10;

export type TraceStatus = '' | 'success' | 'failed' | 'running';

export type TimelineNode = AdminTraceNode & {
  depthValue: number;
  resolvedDurationMs: number;
  offsetMs: number;
  leftPercent: number;
  widthPercent: number;
};

/**
 * 统一归一化链路状态值，避免前后端大小写差异影响展示逻辑。
 *
 * @param status 原始状态值。
 * @returns 归一化后的状态值。
 */
export const normalizeStatus = (status?: string | null): string => (status || '').trim().toLowerCase();

/**
 * 将状态转换为列表/详情统一展示文案。
 *
 * @param status 原始状态值。
 * @returns 状态展示文案。
 */
export const statusLabel = (status?: string | null): string => {
  const normalized = normalizeStatus(status);
  if (!normalized) return 'UNKNOWN';
  if (normalized === 'success') return 'SUCCESS';
  if (normalized === 'failed') return 'FAILED';
  if (normalized === 'running') return 'RUNNING';
  if (normalized === 'error') return 'ERROR';
  return normalized.toUpperCase();
};

/**
 * 根据状态输出与现有主题风格兼容的标签样式。
 *
 * @param status 原始状态值。
 * @returns 标签样式类名。
 */
export const statusBadgeClassName = (status?: string | null): string => {
  const normalized = normalizeStatus(status);
  if (normalized === 'failed' || normalized === 'error') {
    return 'border-status-failed-border bg-status-failed-bg text-status-failed';
  }
  if (normalized === 'running') {
    return 'border-status-pending-border bg-status-pending-bg text-status-pending';
  }
  if (normalized === 'success') {
    return 'border-status-running-border bg-status-running-bg text-status-running';
  }
  return 'border-border-strong bg-surface-container-low text-secondary';
};

/**
 * 将节点类型统一展示为大写标签，并兼容 entry => ROOT 的历史数据。
 *
 * @param nodeType 原始节点类型。
 * @returns 展示用节点类型。
 */
export const nodeTypeLabel = (nodeType?: string | null): string => {
  const normalized = (nodeType || '').trim().toUpperCase();
  if (!normalized) return '-';
  if (normalized === 'ENTRY') return 'ROOT';
  return normalized;
};

/**
 * 根据节点类型输出类型标签样式，保持亮暗主题下可读。
 *
 * @param nodeType 原始节点类型。
 * @returns 标签样式类名。
 */
export const nodeTypeBadgeClassName = (nodeType?: string | null): string => {
  const normalized = nodeTypeLabel(nodeType);
  if (normalized === 'ROOT') {
    return 'border-border-strong bg-surface-container-low text-primary';
  }
  if (normalized === 'INTENT') {
    return 'border-status-running-border bg-status-running-bg text-status-running';
  }
  if (normalized === 'REWRITE') {
    return 'border-status-pending-border bg-status-pending-bg text-status-pending';
  }
  if (normalized === 'SEARCH') {
    return 'border-border-strong bg-surface-container-low text-tertiary-container';
  }
  if (normalized === 'ARTIFACT') {
    return 'border-status-failed-border bg-status-failed-bg text-status-failed';
  }
  return 'border-border-hairline bg-surface-container-low text-secondary';
};

const NODE_NAME_DISPLAY: Record<string, string> = {
  'chat-entry': '聊天入口',
  'rag-stream-chat': 'RAG 流式对话',
  'intent-resolve': '意图识别',
  'intent-classify': '意图识别',
  'intent-guidance': '意图引导',
  'rewrite-question': '问题改写',
  'rewrite-with-split': '问题改写与拆分',
  'artifact-generate': '产物生成',
  'web-search': '联网搜索',
  'reference-collect': '参考资料整理',
  'llm-chat-routing': 'LLM 路由调度',
  'llm-stream-routing': 'LLM 流式路由',
  'user-first-packet': '用户感知首包',
  'llm-first-packet': 'LLM 首包',
};

const toTitleCase = (value: string): string => value
  .split(/[-_\s]+/)
  .filter(Boolean)
  .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
  .join(' ');

/**
 * 将技术节点名转换为可读中文名，未知节点保留英文标题化展示。
 *
 * @param nodeName 原始节点名称。
 * @returns 前端展示名称。
 */
export const prettifyNodeName = (nodeName?: string | null): string => {
  const trimmed = (nodeName || '').trim();
  if (!trimmed) return '-';
  if (NODE_NAME_DISPLAY[trimmed]) return NODE_NAME_DISPLAY[trimmed];
  return toTitleCase(trimmed);
};

/**
 * 解析字符串/数字时间到毫秒时间戳。
 *
 * @param value 时间值。
 * @returns 时间戳，不可解析时返回 null。
 */
export const toTimestamp = (value?: string | number | null): number | null => {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  const parsedByDate = new Date(value).getTime();
  if (!Number.isNaN(parsedByDate)) return parsedByDate;
  const parsedByNumber = Number(value);
  return Number.isFinite(parsedByNumber) ? parsedByNumber : null;
};

/**
 * 格式化本地时间字符串用于管理端展示。
 *
 * @param value 时间值。
 * @returns 本地化时间文案。
 */
export const formatDateTime = (value?: string | number | null): string => {
  const timestamp = toTimestamp(value);
  if (timestamp === null) return '-';
  return new Date(timestamp).toLocaleString('zh-CN');
};

/**
 * 格式化耗时展示文案，自动在 ms/s 之间切换。
 *
 * @param value 耗时毫秒值。
 * @returns 可读耗时文案。
 */
export const formatDuration = (value?: number | null): string => {
  if (value === null || value === undefined || Number.isNaN(value)) return '-';
  if (value < 1000) return `${Math.round(value)}ms`;
  if (value < 60_000) return `${(value / 1000).toFixed(2)}s`;
  const minutes = Math.floor(value / 60_000);
  const seconds = ((value % 60_000) / 1000).toFixed(1);
  return `${minutes}m ${seconds}s`;
};

/**
 * 计算分位值（如 P95）用于列表页指标统计。
 *
 * @param values 输入样本。
 * @param ratio 分位比例。
 * @returns 分位结果。
 */
export const percentile = (values: number[], ratio: number): number => {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.max(0, Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1));
  return sorted[index];
};

/**
 * 钳制数值范围，避免时序条越界。
 *
 * @param value 原始值。
 * @param min 最小值。
 * @param max 最大值。
 * @returns 钳制后的值。
 */
export const clamp = (value: number, min: number, max: number): number => Math.min(max, Math.max(min, value));

/**
 * 节点耗时兜底：优先使用 durationMs，缺失时由开始/结束时间反推。
 *
 * @param node 节点记录。
 * @returns 节点耗时（毫秒）。
 */
export const resolveNodeDuration = (node: AdminTraceNode): number => {
  const durationMs = Number(node.durationMs ?? 0);
  if (Number.isFinite(durationMs) && durationMs > 0) return durationMs;
  const start = toTimestamp(node.startedAt);
  const end = toTimestamp(node.finishedAt);
  if (start !== null && end !== null && end >= start) return end - start;
  return 0;
};
