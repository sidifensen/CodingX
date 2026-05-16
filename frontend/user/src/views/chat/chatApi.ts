import { ApiResponseEnvelope } from '../../types/auth';
import { ApiResponseParser } from '../../api/apiResponse';
import {
  ArtifactItem,
  ChatMessageItem,
  ConversationItem,
  ExecutionStepItem,
  McpItem,
  ReferenceItem,
  SampleQuestionItem,
} from './types';

/**
 * 统一封装聊天工作区的 HTTP 请求，确保会话、消息和右栏回放共享同一套鉴权与错误处理逻辑。
 */
export class ChatApi {
  /**
   * 加载当前用户可见的会话列表。
   * @param token 当前登录令牌。
   * @returns 会话列表。
   */
  static async listConversations(token: string): Promise<ConversationItem[]> {
    const envelope = await this.request<ConversationItem[]>('/api/chat/conversations', token);
    return envelope.data.map((item) => ({
      ...item,
      lastRunId: item.lastRunId,
    }));
  }

  /**
   * 加载指定会话的消息列表。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 消息列表。
   */
  static async listMessages(token: string, conversationId: string): Promise<ChatMessageItem[]> {
    const envelope = await this.request<ChatMessageItem[]>(`/api/chat/conversations/${conversationId}/messages`, token);
    return envelope.data;
  }

  /**
   * 加载指定会话的步骤回放。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 步骤列表。
   */
  static async listSteps(token: string, conversationId: string): Promise<ExecutionStepItem[]> {
    const envelope = await this.request<ExecutionStepItem[]>(`/api/chat/conversations/${conversationId}/steps`, token);
    return envelope.data;
  }

  /**
   * 加载指定会话的来源回放。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 来源列表。
   */
  static async listReferences(token: string, conversationId: string): Promise<ReferenceItem[]> {
    const envelope = await this.request<ReferenceItem[]>(`/api/chat/conversations/${conversationId}/references`, token);
    return envelope.data;
  }

  /**
   * 加载指定会话的产物回放。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 产物列表。
   */
  static async listArtifacts(token: string, conversationId: string): Promise<ArtifactItem[]> {
    const envelope = await this.request<ArtifactItem[]>(`/api/chat/conversations/${conversationId}/artifacts`, token);
    return envelope.data;
  }

  /**
   * 加载首页欢迎区示例问题。
   * @param token 当前登录令牌。
   * @returns 示例问题列表。
   */
  static async listSampleQuestions(token: string): Promise<SampleQuestionItem[]> {
    const envelope = await this.request<SampleQuestionItem[]>('/api/chat/sample-questions', token);
    return envelope.data.map((item) => ({
      ...item,
      id: String(item.id),
    }));
  }

  /**
   * 加载用户侧可选 MCP 列表。
   * @param token 当前登录令牌。
   * @returns MCP 列表。
   */
  static async listMcps(token: string): Promise<McpItem[]> {
    const envelope = await this.request<McpItem[]>('/api/chat/mcps', token);
    return envelope.data.map((item) => ({
      ...item,
      id: String(item.id),
      mcpCode: String(item.mcpCode ?? ''),
    }));
  }

  /**
   * 提交会话重命名请求。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @param title 新会话标题。
   */
  static async renameConversation(token: string, conversationId: string, title: string): Promise<void> {
    await this.request<void>(`/api/chat/conversations/${conversationId}`, token, {
      method: 'PATCH',
      body: JSON.stringify({ title }),
    });
  }

  /**
   * 删除指定会话。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   */
  static async deleteConversation(token: string, conversationId: string): Promise<void> {
    await this.request<void>(`/api/chat/conversations/${conversationId}`, token, {
      method: 'DELETE',
    });
  }

  static async cancelConversation(token: string, conversationId: string): Promise<void> {
    await this.request<void>(`/api/chat/conversations/${conversationId}/cancel`, token, {
      method: 'POST',
    });
  }

  /**
   * 统一执行带鉴权的 JSON 请求。
   * @param path 接口路径。
   * @param token 当前登录令牌。
   * @param init 可选请求参数。
   * @returns 统一响应包。
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
        ...(init?.headers ?? {}),
      },
    });
    const envelope = await ApiResponseParser.parseEnvelope<T>(response, '聊天请求失败');
    ApiResponseParser.assertSuccess(response, envelope, '聊天请求失败');
    return envelope;
  }
}
