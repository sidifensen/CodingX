import { ApiResponseEnvelope } from '../../types/auth';
import { ApiResponseParser, ApiUnauthorizedError } from '../../api/apiResponse';
import {
  ArtifactItem,
  ChatAttachmentItem,
  ChatSkillItem,
  ChatMessageItem,
  ConversationItem,
  CurrentMcpItem,
  CurrentSkillItem,
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
   * 复用统一鉴权异常类型，供上层快速判断是否需要回退到登录页。
   */
  static UnauthorizedError = ApiUnauthorizedError;

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
    const envelope = await this.request<ChatMessageItem[]>(
      `/api/chat/conversations/${conversationId}/messages`,
      token,
    );
    return envelope.data.map((item) => ({
      ...item,
      skillCodes: (item.skillCodes ?? []).map((skillCode) => String(skillCode ?? '')).filter(Boolean),
      attachments: (item.attachments ?? []).map((attachment) => this.normalizeAttachment(attachment)),
    }));
  }

  /**
   * 加载指定会话的步骤回放。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 步骤列表。
   */
  static async listSteps(token: string, conversationId: string): Promise<ExecutionStepItem[]> {
    const envelope = await this.request<ExecutionStepItem[]>(
      `/api/chat/conversations/${conversationId}/steps`,
      token,
    );
    return envelope.data;
  }

  /**
   * 加载指定会话的来源回放。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 来源列表。
   */
  static async listReferences(token: string, conversationId: string): Promise<ReferenceItem[]> {
    const envelope = await this.request<ReferenceItem[]>(
      `/api/chat/conversations/${conversationId}/references`,
      token,
    );
    return envelope.data;
  }

  /**
   * 加载指定会话的产物回放。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 产物列表。
   */
  static async listArtifacts(token: string, conversationId: string): Promise<ArtifactItem[]> {
    const envelope = await this.request<ArtifactItem[]>(
      `/api/chat/conversations/${conversationId}/artifacts`,
      token,
    );
    return envelope.data;
  }

  /**
   * 加载指定会话当前运行绑定的技能列表。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 当前技能列表。
   */
  static async listCurrentSkills(
    token: string,
    conversationId: string,
  ): Promise<CurrentSkillItem[]> {
    const envelope = await this.request<CurrentSkillItem[]>(
      `/api/chat/conversations/${conversationId}/current-skills`,
      token,
    );
    return envelope.data.map((item) => ({
      ...item,
      id: String(item.id ?? ''),
      skillCode: String(item.skillCode ?? ''),
    }));
  }

  /**
   * 加载指定会话当前运行绑定的 MCP 列表。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 当前 MCP 列表。
   */
  static async listCurrentMcps(token: string, conversationId: string): Promise<CurrentMcpItem[]> {
    const envelope = await this.request<CurrentMcpItem[]>(
      `/api/chat/conversations/${conversationId}/current-mcps`,
      token,
    );
    return envelope.data.map((item) => ({
      ...item,
      id: String(item.id ?? ''),
      mcpCode: String(item.mcpCode ?? ''),
    }));
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
   * 加载用户侧可选技能列表。
   * @param token 当前登录令牌。
   * @returns 技能列表。
   */
  static async listSkills(token: string): Promise<ChatSkillItem[]> {
    const envelope = await this.request<ChatSkillItem[]>('/api/chat/skills', token);
    return envelope.data.map((item) => ({
      ...item,
      id: String(item.id ?? ''),
      skillCode: String(item.skillCode ?? ''),
    }));
  }

  /**
   * 提交会话重命名请求。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @param title 新会话标题。
   */
  static async renameConversation(
    token: string,
    conversationId: string,
    title: string,
  ): Promise<void> {
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
   * 提交指定消息的点赞/点踩反馈，确保用户操作写入后端反馈表。
   * @param token 当前登录令牌。
   * @param messageId 消息标识。
   * @param payload 反馈请求体。
   */
  static async submitMessageFeedback(
    token: string,
    messageId: string,
    payload: { conversationId: string; vote: 1 | -1; reason?: string; comment?: string },
  ): Promise<void> {
    await this.request<void>(`/api/chat/messages/${messageId}/feedback`, token, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  /**
   * 绑定当前用户在桌面端选中的本地仓库目录，供后端工具执行链路复用。
   * @param token 当前登录令牌。
   * @param repositoryPath 本地仓库绝对路径。
   * @returns 规范化后的仓库路径。
   */
  static async bindWorkspaceRepository(
    token: string,
    repositoryPath: string,
  ): Promise<{ repositoryPath: string }> {
    const envelope = await this.request<{ repositoryPath: string }>(
      '/api/chat/workspace/bind-repository',
      token,
      {
        method: 'POST',
        body: JSON.stringify({ repositoryPath }),
      },
    );
    return envelope.data;
  }

  /**
   * 上传聊天附件并返回附件元数据，供发送消息时携带 attachmentIds。
   * @param token 当前登录令牌。
   * @param file 上传文件。
   * @param conversationId 会话标识，可为空。
   * @returns 附件信息。
   */
  static async uploadAttachment(
    token: string,
    file: File,
    conversationId?: string | null,
  ): Promise<ChatAttachmentItem> {
    const formData = new FormData();
    formData.append('file', file);
    if (conversationId != null) {
      formData.append('conversationId', conversationId);
    }
    const response = await fetch('/api/chat/attachments/upload', {
      method: 'POST',
      headers: {
        satoken: token,
      },
      body: formData,
    });
    const envelope = await ApiResponseParser.parseEnvelope<ChatAttachmentItem>(response, '附件上传失败');
    ApiResponseParser.assertSuccess(response, envelope, '附件上传失败');
    return this.normalizeAttachment(envelope.data);
  }

  /**
   * 统一归一化附件结构，避免接口数字主键在前端出现精度问题。
   * @param attachment 原始附件数据。
   * @returns 归一化后附件。
   */
  private static normalizeAttachment(attachment: ChatAttachmentItem): ChatAttachmentItem {
    return {
      ...attachment,
      id: String(attachment.id ?? ''),
      conversationId: attachment.conversationId == null ? undefined : String(attachment.conversationId),
      messageId: attachment.messageId == null ? undefined : String(attachment.messageId),
      attachmentType: attachment.attachmentType === 'image' ? 'image' : 'file',
      fileName: String(attachment.fileName ?? ''),
      fileSize: Number(attachment.fileSize ?? 0),
      status: String(attachment.status ?? ''),
    };
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
        ...init?.headers,
      },
    });
    const envelope = await ApiResponseParser.parseEnvelope<T>(response, '聊天请求失败');
    ApiResponseParser.assertSuccess(response, envelope, '聊天请求失败');
    return envelope;
  }

  /**
   * 统一解析聊天流（SSE）请求的授权失败，避免 UI 把 JSON 错误体直接展示给用户。
   * @param response SSE 响应对象。
   */
  static async assertStreamAuthorized(response: Response): Promise<void> {
    if (response.ok) {
      return;
    }
    const envelope = await ApiResponseParser.parseEnvelope<null>(response, '聊天请求失败');
    if (ApiResponseParser.isUnauthorized(response, envelope)) {
      throw new ApiUnauthorizedError(
        envelope.message || '登录已失效，请重新登录',
        response.status,
        envelope.code ?? '',
      );
    }
    throw new Error(envelope.message || '聊天请求失败');
  }
}
