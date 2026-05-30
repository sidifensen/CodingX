import type { AdminMcpConfig, AdminMcpToolView } from '../../api/adminChatApi';
import type { McpFormValues, UnifiedMcpRow } from './mcpTypes';

export const emptyMcpFormValues: McpFormValues = {
  mcpCode: '',
  displayName: '',
  description: '',
  category: '',
  sourceType: 'built-in',
  sortNo: 0,
  enabled: true,
};

/**
 * 把后端异常归一成页面可展示中文文案，优先保留接口层传出的 message。
 */
export function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

/**
 * 合并数据库配置与执行器探测结果，统一输出页面展示行。
 */
export function mergeConfigAndTools(configs: AdminMcpConfig[], tools: AdminMcpToolView[]): UnifiedMcpRow[] {
  const configByCode = new Map<string, AdminMcpConfig>();
  const toolByCode = new Map<string, AdminMcpToolView>();
  const codeSet = new Set<string>();

  for (const config of configs) {
    const normalizedCode = normalizeMcpCode(config.mcpCode);
    if (!normalizedCode) {
      continue;
    }
    configByCode.set(normalizedCode, config);
    codeSet.add(normalizedCode);
  }

  for (const tool of tools) {
    const normalizedCode = normalizeMcpCode(tool.toolId);
    if (!normalizedCode) {
      continue;
    }
    toolByCode.set(normalizedCode, tool);
    codeSet.add(normalizedCode);
  }

  return Array.from(codeSet)
    .map((code) => {
      const config = configByCode.get(code) ?? null;
      const tool = toolByCode.get(code) ?? null;
      return {
        mcpCode: config?.mcpCode ?? tool?.toolId ?? code,
        config,
        tool,
      };
    })
    .sort((left, right) => {
      const leftSort = left.config?.sortNo ?? Number.MAX_SAFE_INTEGER;
      const rightSort = right.config?.sortNo ?? Number.MAX_SAFE_INTEGER;
      if (leftSort !== rightSort) {
        return leftSort - rightSort;
      }
      return left.mcpCode.localeCompare(right.mcpCode);
    });
}

/**
 * 规范化 MCP 编码用于配置与执行器结果的大小写无关匹配。
 */
export function normalizeMcpCode(code?: string): string {
  return (code ?? '').trim().toLowerCase();
}

/**
 * 将配置对象转为 AntD 表单可消费的字段值。
 */
export function toMcpFormValues(config: AdminMcpConfig | null): McpFormValues {
  if (!config) {
    return emptyMcpFormValues;
  }
  return {
    mcpCode: config.mcpCode ?? '',
    displayName: config.displayName ?? '',
    description: config.description ?? '',
    category: config.category ?? '',
    sourceType: config.sourceType ?? 'built-in',
    sortNo: Number(config.sortNo ?? 0),
    enabled: config.enabled !== 0,
  };
}

/**
 * 将 AntD 表单值转为后端配置载荷，保留编辑时的配置主键。
 */
export function toMcpPayload(values: McpFormValues, editingConfig: AdminMcpConfig | null): AdminMcpConfig {
  const parsedSortNo = Number(values.sortNo ?? 0);
  return {
    id: editingConfig?.id,
    mcpCode: values.mcpCode.trim(),
    displayName: values.displayName.trim(),
    description: values.description?.trim() || undefined,
    category: values.category?.trim() || undefined,
    sourceType: values.sourceType?.trim() || 'built-in',
    sortNo: Number.isFinite(parsedSortNo) ? parsedSortNo : 0,
    enabled: values.enabled ? 1 : 0,
  };
}

/**
 * 映射执行器状态到 AntD Badge 的语义状态。
 */
export function toBadgeStatus(status?: string): 'success' | 'warning' | 'error' {
  if (status === 'healthy') {
    return 'success';
  }
  if (status === 'degraded') {
    return 'warning';
  }
  return 'error';
}
