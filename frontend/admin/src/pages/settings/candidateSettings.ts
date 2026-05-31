import type { AdminRuntimeSetting } from '@/api/adminChatApi';

export interface CandidateSlotSummary {
  slot: string;
  title: string;
  provider?: string;
  model?: string;
  priority?: string;
  enabled?: string;
  supportsThinking?: string;
  supportsVision?: string;
}

export interface CandidatePriorityUpdate {
  slot: string;
  priority: number;
}

export interface CandidateRow {
  slot: string;
  title: string;
  idSetting?: AdminRuntimeSetting;
  providerSetting?: AdminRuntimeSetting;
  modelSetting?: AdminRuntimeSetting;
  prioritySetting?: AdminRuntimeSetting;
  enabledSetting?: AdminRuntimeSetting;
  supportsThinkingSetting?: AdminRuntimeSetting;
  supportsVisionSetting?: AdminRuntimeSetting;
}

export interface ProviderRow {
  code: string;
  title: string;
  baseUrlSetting?: AdminRuntimeSetting;
  apiKeySetting?: AdminRuntimeSetting;
  chatEndpointSetting?: AdminRuntimeSetting;
}

export function extractCandidateSlot(settingKey: string): string | null {
  const matched = /^ai\.chat\.candidates\.(\d+)\./.exec(settingKey);
  return matched?.[1] ?? null;
}

export function buildCandidateRows(settings: AdminRuntimeSetting[]): CandidateRow[] {
  const rows = new Map<string, CandidateRow>();
  settings.forEach((setting) => {
    const slot = extractCandidateSlot(setting.settingKey);
    if (!slot) {
      return;
    }
    const row = rows.get(slot) ?? { slot, title: `候选槽位 ${slot}` };
    const field = setting.settingKey.split('.').pop();
    if (field === 'id') {
      row.idSetting = setting;
      row.title = setting.settingValue || row.title;
    }
    if (field === 'provider') {
      row.providerSetting = setting;
    }
    if (field === 'model') {
      row.modelSetting = setting;
    }
    if (field === 'priority') {
      row.prioritySetting = setting;
    }
    if (field === 'enabled') {
      row.enabledSetting = setting;
    }
    if (field === 'supports_thinking') {
      row.supportsThinkingSetting = setting;
    }
    if (field === 'supports_vision') {
      row.supportsVisionSetting = setting;
    }
    rows.set(slot, row);
  });
  return Array.from(rows.values()).sort((left, right) => {
    // 候选池的真实尝试顺序由优先级决定，槽位号只作为配置键稳定标识和兜底排序。
    const priorityCompare = candidatePriority(left) - candidatePriority(right);
    if (priorityCompare !== 0) {
      return priorityCompare;
    }
    return Number(left.slot) - Number(right.slot);
  });
}

function candidatePriority(row: CandidateRow): number {
  const priority = Number(row.prioritySetting?.settingValue);
  return Number.isFinite(priority) ? priority : Number.MAX_SAFE_INTEGER;
}

export function summarizeCandidateSlots(settings: AdminRuntimeSetting[]): CandidateSlotSummary[] {
  return buildCandidateRows(settings).map((row) => ({
    slot: row.slot,
    title: row.title,
    provider: row.providerSetting?.settingValue,
    model: row.modelSetting?.settingValue,
    priority: row.prioritySetting?.settingValue,
    enabled: row.enabledSetting?.settingValue,
    supportsThinking: row.supportsThinkingSetting?.settingValue,
    supportsVision: row.supportsVisionSetting?.settingValue,
  }));
}

export function buildProviderRows(settings: AdminRuntimeSetting[]): ProviderRow[] {
  const rows = new Map<string, ProviderRow>();
  settings.forEach((setting) => {
    const matched = /^ai\.providers\.([^.]+)\./.exec(setting.settingKey);
    const code = matched?.[1];
    if (!code) {
      return;
    }
    const row = rows.get(code) ?? { code, title: providerTitle(code) };
    if (setting.settingKey.endsWith('.base_url')) {
      row.baseUrlSetting = setting;
    }
    if (setting.settingKey.endsWith('.api_key')) {
      row.apiKeySetting = setting;
    }
    if (setting.settingKey.endsWith('.endpoints.chat')) {
      row.chatEndpointSetting = setting;
    }
    rows.set(code, row);
  });
  return Array.from(rows.values()).sort((left, right) => left.code.localeCompare(right.code));
}

function providerTitle(code: string): string {
  if (code === 'siliconflow') {
    return '硅基流动';
  }
  if (code === 'bailian') {
    return '百炼';
  }
  if (code === 'deepseek') {
    return 'DeepSeek';
  }
  if (code === 'stub') {
    return 'Stub';
  }
  return code;
}

export function normalizeCandidatePriorityUpdates(
  settings: AdminRuntimeSetting[],
  update: CandidatePriorityUpdate,
): AdminRuntimeSetting[] {
  const grouped = new Map<string, AdminRuntimeSetting[]>();
  settings.forEach((setting) => {
    const slot = extractCandidateSlot(setting.settingKey);
    if (!slot) {
      return;
    }
    const bucket = grouped.get(slot) ?? [];
    bucket.push(setting);
    grouped.set(slot, bucket);
  });

  if (!grouped.has(update.slot)) {
    return settings;
  }

  const slotEntries = Array.from(grouped.entries()).map(([slot, bucket]) => {
    const prioritySetting = bucket.find((item) => item.settingKey.endsWith('.priority'));
    const currentPriority = Number(prioritySetting?.settingValue ?? 999);
    return { slot, bucket, currentPriority };
  });

  slotEntries.sort((left, right) => left.currentPriority - right.currentPriority);
  const targetIndex = Math.max(0, Math.min(update.priority - 1, slotEntries.length - 1));
  const moving = slotEntries.find((entry) => entry.slot === update.slot);
  if (!moving) {
    return settings;
  }

  const rest = slotEntries.filter((entry) => entry.slot !== update.slot);
  rest.splice(targetIndex, 0, moving);

  const priorityByKey = new Map<string, string>();
  rest.forEach((entry, index) => {
    const nextPriority = String(index + 1);
    entry.bucket.forEach((setting) => {
      if (setting.settingKey.endsWith('.priority')) {
        priorityByKey.set(setting.settingKey, nextPriority);
      }
    });
  });

  return settings.map((setting) => {
    const nextPriority = priorityByKey.get(setting.settingKey);
    if (!nextPriority) {
      return setting;
    }
    return { ...setting, settingValue: nextPriority };
  });
}
