import { ApiResponseParser } from './apiResponse';
import { AuthStorage } from '../utils/authStorage';

export interface AdminTraceRun {
  id?: string;
  traceId: string;
  traceName: string;
  conversationId?: string | number;
  taskId?: string | number;
  userId?: string | number;
  username?: string;
  status: string;
  errorMessage?: string;
  durationMs?: number;
  startedAt?: string;
  finishedAt?: string;
}

export interface AdminTraceNode {
  id: string;
  traceId: string;
  nodeId?: string;
  parentNodeId?: string;
  depth?: number;
  nodeType: string;
  nodeName: string;
  className?: string;
  methodName?: string;
  status: string;
  errorMessage?: string;
  durationMs?: number;
  startedAt?: string;
  finishedAt?: string;
}

export interface AdminTraceDetail {
  traceRun: AdminTraceRun;
  nodes: AdminTraceNode[];
}

export interface AdminTraceRunPageResult {
  records: AdminTraceRun[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface AdminTraceRunQuery {
  current?: number;
  size?: number;
  traceId?: string;
}

export interface AdminIntentNode {
  id?: string;
  intentCode: string;
  parentCode?: string | null;
  name: string;
  description?: string;
  intentType: string;
  // 意图配置台字段与旧 intentType/sortNo 并存，保证管理端升级时不破坏运行时分流。
  kbId?: string;
  level?: number;
  examples?: string | string[];
  collectionName?: string;
  topK?: number;
  kind?: number;
  promptTemplate?: string;
  promptSnippet?: string;
  mcpToolId?: string;
  paramPromptTemplate?: string;
  enabled?: number;
  sortNo?: number;
  sortOrder?: number;
  children?: AdminIntentNode[];
}

export interface AdminQueryTermMapping {
  id?: string;
  sourceTerm: string;
  targetTerm: string;
  mappingType: string;
  enabled?: number;
  sortNo?: number;
}

export interface AdminRuntimeSetting {
  id?: string;
  settingKey: string;
  settingValue: string;
  valueType: string;
  description?: string;
}

export interface AdminDashboardView {
  traceCount: number;
  runningTraceCount: number;
  intentNodeCount: number;
  mappingCount: number;
  sampleQuestionCount: number;
}

export interface AdminMcpToolView {
  toolId: string;
  displayName: string;
  category: string;
  source: string;
  status: string;
  statusLabel: string;
  ok: boolean;
  message?: string;
  description?: string;
  sampleQuestion?: string;
  checkedAt?: string;
  durationMs?: number;
}

export interface AdminMcpConfig {
  id?: string | number;
  mcpCode: string;
  displayName: string;
  description?: string;
  category?: string;
  sourceType?: string;
  enabled?: number;
  sortNo?: number;
}

export interface AdminSkill {
  id?: string | number;
  skillCode: string;
  displayName: string;
  description?: string;
  category?: string;
  sourceType?: string;
  enabled?: number;
  sortNo?: number;
}

/**
 * 统一封装管理端聊天运行时后台接口。
 */
export class AdminChatApi {
  static async listTraces(query: AdminTraceRunQuery = {}): Promise<AdminTraceRunPageResult> {
    const searchParams = new URLSearchParams();
    searchParams.set('current', String(query.current ?? 1));
    searchParams.set('size', String(query.size ?? 10));
    if (query.traceId && query.traceId.trim()) {
      searchParams.set('traceId', query.traceId.trim());
    }
    return this.request<AdminTraceRunPageResult>(`/api/admin/chat/traces?${searchParams.toString()}`);
  }

  static async getTrace(traceId: string): Promise<AdminTraceDetail> {
    return this.request<AdminTraceDetail>(`/api/admin/chat/traces/${traceId}`);
  }

  static async listIntents(): Promise<AdminIntentNode[]> {
    return this.request<AdminIntentNode[]>('/api/admin/chat/intents');
  }

  static async listIntentTree(): Promise<AdminIntentNode[]> {
    return this.request<AdminIntentNode[]>('/api/admin/chat/intents/tree');
  }

  static async saveIntent(payload: AdminIntentNode): Promise<AdminIntentNode> {
    return this.createIntent(payload);
  }

  static async createIntent(payload: AdminIntentNode): Promise<AdminIntentNode> {
    return this.request<AdminIntentNode>('/api/admin/chat/intents', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  static async updateIntent(id: string | number, payload: AdminIntentNode): Promise<AdminIntentNode> {
    return this.request<AdminIntentNode>(`/api/admin/chat/intents/${encodeURIComponent(String(id))}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  }

  static async deleteIntent(id: string | number): Promise<void> {
    return this.request<void>(`/api/admin/chat/intents/${encodeURIComponent(String(id))}`, {
      method: 'DELETE',
    });
  }

  static async listMappings(): Promise<AdminQueryTermMapping[]> {
    return this.request<AdminQueryTermMapping[]>('/api/admin/chat/query-term-mappings');
  }

  static async saveMapping(payload: AdminQueryTermMapping): Promise<AdminQueryTermMapping> {
    return this.request<AdminQueryTermMapping>('/api/admin/chat/query-term-mappings', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  static async listSettings(): Promise<AdminRuntimeSetting[]> {
    return this.request<AdminRuntimeSetting[]>('/api/admin/chat/settings');
  }

  static async saveSetting(payload: AdminRuntimeSetting): Promise<AdminRuntimeSetting> {
    return this.request<AdminRuntimeSetting>('/api/admin/chat/settings', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  static async getDashboard(): Promise<AdminDashboardView> {
    return this.request<AdminDashboardView>('/api/admin/chat/dashboard');
  }

  static async listMcpTools(): Promise<AdminMcpToolView[]> {
    return this.request<AdminMcpToolView[]>('/api/admin/chat/mcp-tools');
  }

  static async pingMcpTool(toolId: string): Promise<AdminMcpToolView> {
    return this.request<AdminMcpToolView>(`/api/admin/chat/mcp-tools/${encodeURIComponent(toolId)}/ping`);
  }

  static async listSkills(): Promise<AdminSkill[]> {
    return this.request<AdminSkill[]>('/api/admin/chat/skills');
  }

  static async createSkill(payload: AdminSkill): Promise<AdminSkill> {
    return this.request<AdminSkill>('/api/admin/chat/skills', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  static async updateSkill(id: string | number, payload: AdminSkill): Promise<AdminSkill> {
    return this.request<AdminSkill>(`/api/admin/chat/skills/${encodeURIComponent(String(id))}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  }

  static async deleteSkill(id: string | number): Promise<void> {
    await this.request<void>(`/api/admin/chat/skills/${encodeURIComponent(String(id))}`, {
      method: 'DELETE',
    });
  }

  static async listMcpConfigs(): Promise<AdminMcpConfig[]> {
    return this.request<AdminMcpConfig[]>('/api/admin/chat/mcps');
  }

  static async createMcpConfig(payload: AdminMcpConfig): Promise<AdminMcpConfig> {
    return this.request<AdminMcpConfig>('/api/admin/chat/mcps', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  static async updateMcpConfig(id: string | number, payload: AdminMcpConfig): Promise<AdminMcpConfig> {
    return this.request<AdminMcpConfig>(`/api/admin/chat/mcps/${encodeURIComponent(String(id))}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  }

  static async deleteMcpConfig(id: string | number): Promise<void> {
    await this.request<void>(`/api/admin/chat/mcps/${encodeURIComponent(String(id))}`, {
      method: 'DELETE',
    });
  }

  private static async request<T>(path: string, init?: RequestInit): Promise<T> {
    const token = AuthStorage.getSession()?.token ?? '';
    const response = await fetch(path, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        satoken: token,
        ...(init?.headers ?? {}),
      },
    });
    const envelope = await ApiResponseParser.parseEnvelope<T>(response, '管理端请求失败');
    ApiResponseParser.assertSuccess(response, envelope, '管理端请求失败');
    return envelope.data;
  }
}
