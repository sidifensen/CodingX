import { ApiResponseParser } from './apiResponse';
import { ApiResponseEnvelope } from '../types/auth';
import {
  AutomationTask,
  AutomationTaskCreatePayload,
  AutomationTaskUpdatePayload,
} from '../views/automation/types';

const AUTOMATION_REQUEST_FAILED = '自动化任务请求失败';

/**
 * 封装用户端自动化任务接口，页面只消费领域化结果与统一错误。
 */
export class AutomationApi {
  /**
   * 查询当前用户的自动化任务列表。
   * @param token 当前登录令牌。
   * @returns 已归一化的任务列表。
   */
  static async listTasks(token: string): Promise<AutomationTask[]> {
    const envelope = await this.request<AutomationTask[]>('/api/automation/tasks', token);
    return envelope.data.map((task) => normalizeAutomationTask(task));
  }

  /**
   * 手动创建自动化任务。
   * @param token 当前登录令牌。
   * @param payload 创建表单请求体。
   * @returns 创建后的任务快照。
   */
  static async createTask(
    token: string,
    payload: AutomationTaskCreatePayload,
  ): Promise<AutomationTask> {
    const envelope = await this.request<AutomationTask>('/api/automation/tasks', token, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    return normalizeAutomationTask(envelope.data);
  }

  /**
   * 编辑当前用户自己的自动化任务。
   * @param token 当前登录令牌。
   * @param taskId 任务主键。
   * @param payload 编辑后的任务表单请求体。
   * @returns 更新后的任务快照。
   */
  static async updateTask(
    token: string,
    taskId: string,
    payload: AutomationTaskUpdatePayload,
  ): Promise<AutomationTask> {
    const envelope = await this.request<AutomationTask>(
      `/api/automation/tasks/${encodeURIComponent(taskId)}`,
      token,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
    );
    return normalizeAutomationTask(envelope.data);
  }

  /**
   * 更新当前用户自己的自动化任务启停状态。
   * @param token 当前登录令牌。
   * @param taskId 任务主键。
   * @param enabled true 表示开启调度，false 表示暂停调度。
   * @returns 更新后的任务快照。
   */
  static async updateTaskEnabled(
    token: string,
    taskId: string,
    enabled: boolean,
  ): Promise<AutomationTask> {
    const envelope = await this.request<AutomationTask>(
      `/api/automation/tasks/${encodeURIComponent(taskId)}/enabled`,
      token,
      {
        method: 'PATCH',
        body: JSON.stringify({ enabled }),
      },
    );
    return normalizeAutomationTask(envelope.data);
  }

  /**
   * 删除当前用户自己的自动化任务；后端执行逻辑删除。
   * @param token 当前登录令牌。
   * @param taskId 任务主键。
   */
  static async deleteTask(token: string, taskId: string): Promise<void> {
    await this.request<void>(`/api/automation/tasks/${encodeURIComponent(taskId)}`, token, {
      method: 'DELETE',
    });
  }

  /**
   * 统一执行自动化 JSON 请求，复用 ApiResponseParser 以优先透传后端中文 message。
   */
  private static async request<T>(
    path: string,
    token: string,
    init?: RequestInit,
  ): Promise<ApiResponseEnvelope<T>> {
    const response = await fetch(path, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        satoken: token,
        ...init?.headers,
      },
    });
    const envelope = await ApiResponseParser.parseEnvelope<T>(response, AUTOMATION_REQUEST_FAILED);
    ApiResponseParser.assertSuccess(response, envelope, AUTOMATION_REQUEST_FAILED);
    return envelope;
  }
}

/**
 * 归一化后端任务响应，兼容 Long 数值、空字段和旧快照缺省字段。
 */
function normalizeAutomationTask(rawTask: AutomationTask): AutomationTask {
  return {
    id: String(rawTask.id ?? ''),
    name: String(rawTask.name ?? ''),
    prompt: String(rawTask.prompt ?? ''),
    sourceType: rawTask.sourceType ?? 'MANUAL',
    sourceConversationId:
      rawTask.sourceConversationId == null ? null : String(rawTask.sourceConversationId),
    scheduleType: rawTask.scheduleType ?? 'DAILY',
    scheduleTime: rawTask.scheduleTime ?? null,
    scheduleDayOfWeek: rawTask.scheduleDayOfWeek ?? null,
    onceExecuteAt: rawTask.onceExecuteAt ?? null,
    nextRunAt: rawTask.nextRunAt ?? null,
    lastRunAt: rawTask.lastRunAt ?? null,
    lastRunStatus: rawTask.lastRunStatus ?? null,
    enabled: rawTask.enabled !== false,
    workspaceId: rawTask.workspaceId == null ? null : String(rawTask.workspaceId),
  };
}
