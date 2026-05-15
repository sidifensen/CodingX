import { ApiResponseParser } from './apiResponse';
import { AuthStorage } from '../utils/authStorage';

export interface AdminTraceRun {
  id: string;
  traceId: string;
  traceName: string;
  conversationId?: string;
  taskId?: string;
  userId?: string;
  status: string;
  errorMessage?: string;
  durationMs?: number;
  startedAt?: string;
  finishedAt?: string;
}

export interface AdminTraceNode {
  id: string;
  traceId: string;
  nodeType: string;
  nodeName: string;
  className?: string;
  methodName?: string;
  status: string;
  errorMessage?: string;
  durationMs?: number;
}

export interface AdminTraceDetail {
  traceRun: AdminTraceRun;
  nodes: AdminTraceNode[];
}

export interface AdminIntentNode {
  id?: string;
  intentCode: string;
  parentCode?: string;
  name: string;
  description?: string;
  intentType: string;
  promptTemplate?: string;
  mcpToolId?: string;
  paramPromptTemplate?: string;
  enabled?: number;
  sortNo?: number;
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

/**
 * 统一封装管理端聊天运行时后台接口。
 */
export class AdminChatApi {
  static async listTraces(): Promise<AdminTraceRun[]> {
    return this.request<AdminTraceRun[]>('/api/admin/chat/traces');
  }

  static async getTrace(traceId: string): Promise<AdminTraceDetail> {
    return this.request<AdminTraceDetail>(`/api/admin/chat/traces/${traceId}`);
  }

  static async listIntents(): Promise<AdminIntentNode[]> {
    return this.request<AdminIntentNode[]>('/api/admin/chat/intents');
  }

  static async saveIntent(payload: AdminIntentNode): Promise<AdminIntentNode> {
    return this.request<AdminIntentNode>('/api/admin/chat/intents', {
      method: 'POST',
      body: JSON.stringify(payload),
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
