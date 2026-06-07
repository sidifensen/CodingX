import { ApiResponseEnvelope } from '../../types/auth';
import { ApiResponseParser, ApiUnauthorizedError } from '../../api/apiResponse';
import { UserErrorMessages } from '../../constants/errorMessages';
import {
  ArtifactItem,
  ChatAttachmentItem,
  ChatExpertItem,
  ChatSkillItem,
  ChatMessageItem,
  ConversationItem,
  CurrentExpertItem,
  CurrentMcpItem,
  CurrentSkillItem,
  ExecutionStepItem,
  LongTermMemoryItem,
  LongTermMemoryStatus,
  McpItem,
  ProjectProfileView,
  ReferenceItem,
  SharedConversationPayload,
  SampleQuestionItem,
  ShareConversationOptions,
  SlashCommandItem,
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
  static async listConversations(token: string, workspaceId?: string | null): Promise<ConversationItem[]> {
    const searchParams = new URLSearchParams();
    if (workspaceId && workspaceId.trim().length > 0) {
      searchParams.set('workspaceId', workspaceId);
    }
    const queryString = searchParams.toString();
    const envelope = await this.request<ConversationItem[]>(
      queryString ? `/api/chat/conversations?${queryString}` : '/api/chat/conversations',
      token
    );
    return envelope.data.map((item) => ({
      id: String(item.id ?? ''),
      title: String(item.title ?? ''),
      status: String(item.status ?? ''),
      lastMessageAt: item.lastMessageAt,
      lastRunId: item.lastRunId == null ? undefined : String(item.lastRunId),
      activeTaskId: item.activeTaskId == null ? undefined : String(item.activeTaskId),
      activeTaskStatus: item.activeTaskStatus == null ? undefined : String(item.activeTaskStatus),
      lastTaskId: item.lastTaskId == null ? undefined : String(item.lastTaskId),
      lastTaskStatus: item.lastTaskStatus == null ? undefined : String(item.lastTaskStatus),
      lastTaskFinishedAt: item.lastTaskFinishedAt,
      taskCompletionRead:
        item.taskCompletionRead == null
          ? undefined
          : Boolean(item.taskCompletionRead),
      workspaceId: item.workspaceId == null ? null : String(item.workspaceId),
      workspaceType: item.workspaceType,
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
   * 加载指定会话当前运行绑定的专家列表。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 当前专家列表。
   */
  static async listCurrentExperts(
    token: string,
    conversationId: string,
  ): Promise<CurrentExpertItem[]> {
    const envelope = await this.request<CurrentExpertItem[]>(
      `/api/chat/conversations/${conversationId}/current-experts`,
      token,
    );
    return envelope.data.map((item) => ({
      ...item,
      id: String(item.id ?? ''),
      expertCode: String(item.expertCode ?? ''),
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
      enabled:
        item.enabled == null
          ? undefined
          : Number(item.enabled) === 0
            ? 0
            : 1,
      available:
        item.available == null
          ? undefined
          : Boolean(item.available),
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
   * 加载用户侧可选 Slash Command 列表。
   * @param token 当前登录令牌。
   * @returns Slash Command 列表。
   */
  static async listSlashCommands(token: string): Promise<SlashCommandItem[]> {
    const envelope = await this.request<SlashCommandItem[]>('/api/chat/slash-commands', token);
    return envelope.data.map((item) => ({
      ...item,
      id: String(item.id ?? ''),
      commandCode: String(item.commandCode ?? ''),
      displayName: String(item.displayName ?? (item.commandCode ? `/${item.commandCode}` : '')),
      commandType: String(item.commandType ?? 'BUILTIN'),
      enabled:
        item.enabled == null
          ? undefined
          : Number(item.enabled) === 0
            ? 0
            : 1,
    }));
  }

  /**
   * 加载用户侧可选专家列表。
   * @param token 当前登录令牌。
   * @returns 专家列表。
   */
  static async listExperts(token: string): Promise<ChatExpertItem[]> {
    const envelope = await this.request<ChatExpertItem[]>('/api/chat/experts', token);
    return envelope.data.map((item) => ({
      ...item,
      id: String(item.id ?? ''),
      expertCode: String(item.expertCode ?? ''),
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

  /**
   * 标记指定会话的任务完成提醒为已读，服务端字段是刷新后圆点状态的权威来源。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   */
  static async markTaskCompletionRead(token: string, conversationId: string): Promise<void> {
    await this.request<void>(
      `/api/chat/conversations/${conversationId}/task-completion-read`,
      token,
      {
        method: 'PATCH',
      },
    );
  }

  /**
   * 删除会话中的指定消息，空值和重复值会在前端先规整，避免后端收到脏 ID 列表。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @param messageIds 待删除消息标识。
   */
  static async deleteConversationMessages(
    token: string,
    conversationId: string,
    messageIds: string[],
  ): Promise<void> {
    const normalizedMessageIds = normalizeMessageIds(messageIds);
    if (normalizedMessageIds.length === 0) {
      return;
    }
    await this.request<void>(`/api/chat/conversations/${conversationId}/messages`, token, {
      method: 'DELETE',
      body: JSON.stringify({ messageIds: normalizedMessageIds }),
    });
  }

  /**
   * 为指定会话生成分享链接，供前端复制公开只读地址。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 分享令牌与分享路径。
   */
  static async shareConversation(
    token: string,
    conversationId: string,
    options?: ShareConversationOptions,
  ): Promise<{ shareToken: string; shareUrl: string }> {
    const normalizedMessageIds = normalizeMessageIds(options?.messageIds);
    const envelope = await this.request<{ shareToken: string; shareUrl: string }>(
      `/api/chat/conversations/${conversationId}/share`,
      token,
      {
        method: 'POST',
        ...(normalizedMessageIds.length > 0
          ? { body: JSON.stringify({ messageIds: normalizedMessageIds }) }
          : {}),
      },
    );
    return {
      shareToken: String(envelope.data.shareToken ?? ''),
      shareUrl: String(envelope.data.shareUrl ?? ''),
    };
  }

  /**
   * 加载公开分享会话，只读页面可选按消息 ID 过滤展示范围。
   * @param shareToken 分享令牌。
   * @param messageIds 需要展示的消息标识集合。
   * @returns 公开分享会话与消息列表。
   */
  static async getSharedConversation(
    shareToken: string,
    messageIds: string[] = [],
  ): Promise<SharedConversationPayload> {
    const searchParams = new URLSearchParams();
    const normalizedMessageIds = normalizeMessageIds(messageIds);
    if (normalizedMessageIds.length > 0) {
      searchParams.set('messages', normalizedMessageIds.join(','));
    }
    const queryString = searchParams.toString();
    const envelope = await this.requestPublic<SharedConversationPayload>(
      `/api/chat/conversations/shared/${shareToken}${queryString ? `?${queryString}` : ''}`,
    );
    return {
      conversation: {
        ...envelope.data.conversation,
        id: String(envelope.data.conversation.id ?? ''),
        title: String(envelope.data.conversation.title ?? ''),
        status: String(envelope.data.conversation.status ?? ''),
      },
      messages: (envelope.data.messages ?? []).map((message) => ({
        ...message,
        id: String(message.id ?? ''),
        conversationId: String(message.conversationId ?? ''),
        skillCodes: (message.skillCodes ?? []).map((skillCode) => String(skillCode ?? '')).filter(Boolean),
        attachments: (message.attachments ?? []).map((attachment) => this.normalizeAttachment(attachment)),
      })),
    };
  }

  /**
   * 重新生成指定会话最后一条助手回复。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   */
  static async regenerateConversation(token: string, conversationId: string): Promise<void> {
    await this.request<void>(`/api/chat/conversations/${conversationId}/regenerate`, token, {
      method: 'POST',
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
  ): Promise<{
    repositoryPath: string;
    workspaceId: string;
    workspaceName: string;
    projectProfile: ProjectProfileView | null;
    activeMemoryCount: number;
  }> {
    const envelope = await this.request<{
      repositoryPath: string;
      workspaceId: string;
      workspaceName: string;
      projectProfile?: ProjectProfileView | null;
      activeMemoryCount?: number | null;
    }>(
      '/api/chat/workspace/bind-repository',
      token,
      {
        method: 'POST',
        body: JSON.stringify({ repositoryPath }),
      },
    );
    return {
      repositoryPath: String(envelope.data.repositoryPath ?? ''),
      workspaceId: String(envelope.data.workspaceId ?? ''),
      workspaceName: String(envelope.data.workspaceName ?? ''),
      projectProfile: envelope.data.projectProfile ?? null,
      activeMemoryCount: Number(envelope.data.activeMemoryCount ?? 0),
    };
  }

  /**
   * 查询当前用户可见的已生效长期记忆，供聊天页展示当前会进入上下文的偏好和约定。
   * @param token 当前登录令牌。
   * @param workspaceId 当前工作空间 ID，可为空。
   * @param status 可选状态筛选。
   * @returns 归一化后的长期记忆列表。
   */
  static async listLongTermMemories(
    token: string,
    workspaceId?: string | null,
    status?: LongTermMemoryStatus | 'ALL' | null,
  ): Promise<LongTermMemoryItem[]> {
    const searchParams = new URLSearchParams();
    if (workspaceId && workspaceId.trim().length > 0) {
      searchParams.set('workspaceId', workspaceId.trim());
    }
    const effectiveStatus = status == null ? 'ACTIVE' : status;
    if (effectiveStatus && effectiveStatus.trim().length > 0 && effectiveStatus !== 'ALL') {
      searchParams.set('status', effectiveStatus);
    }
    const queryString = searchParams.toString();
    const envelope = await this.request<LongTermMemoryItem[]>(
      queryString ? `/api/chat/memories?${queryString}` : '/api/chat/memories',
      token,
    );
    return (envelope.data ?? []).map((item) => this.normalizeLongTermMemory(item));
  }

  /**
   * 更新长期记忆状态，供未来用户侧记忆管理入口启用或停用记忆。
   * @param token 当前登录令牌。
   * @param memoryId 长期记忆 ID。
   * @param status 目标状态。
   * @returns 更新后的长期记忆。
   */
  static async updateLongTermMemoryStatus(
    token: string,
    memoryId: string,
    status: LongTermMemoryStatus,
  ): Promise<LongTermMemoryItem> {
    const envelope = await this.request<LongTermMemoryItem>(
      `/api/chat/memories/${encodeURIComponent(memoryId)}/status`,
      token,
      {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      },
    );
    return this.normalizeLongTermMemory(envelope.data);
  }

  /**
   * 更新长期记忆正文，供用户端记忆管理页修正已生效或已停用记忆。
   * @param token 当前登录令牌。
   * @param memoryId 长期记忆 ID。
   * @param content 新记忆正文。
   * @returns 更新后的长期记忆。
   */
  static async updateLongTermMemoryContent(
    token: string,
    memoryId: string,
    content: string,
  ): Promise<LongTermMemoryItem> {
    const envelope = await this.request<LongTermMemoryItem>(
      `/api/chat/memories/${encodeURIComponent(memoryId)}`,
      token,
      {
        method: 'PATCH',
        body: JSON.stringify({ content }),
      },
    );
    return this.normalizeLongTermMemory(envelope.data);
  }

  /**
   * 删除长期记忆，后端执行逻辑删除并保留来源审计链路。
   * @param token 当前登录令牌。
   * @param memoryId 长期记忆 ID。
   */
  static async deleteLongTermMemory(token: string, memoryId: string): Promise<void> {
    await this.request<void>(
      `/api/chat/memories/${encodeURIComponent(memoryId)}`,
      token,
      {
        method: 'DELETE',
      },
    );
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
    const envelope = await ApiResponseParser.parseEnvelope<ChatAttachmentItem>(
      response,
      UserErrorMessages.CHAT_ATTACHMENT_UPLOAD_FAILED,
    );
    ApiResponseParser.assertSuccess(response, envelope, UserErrorMessages.CHAT_ATTACHMENT_UPLOAD_FAILED);
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
   * 统一归一化长期记忆结构，避免后端 Long 主键和可空字段直接泄漏到页面层。
   * @param memory 原始长期记忆。
   * @returns 前端可直接消费的记忆对象。
   */
  private static normalizeLongTermMemory(memory: LongTermMemoryItem): LongTermMemoryItem {
    return {
      ...memory,
      id: String(memory.id ?? ''),
      userId: memory.userId == null ? null : String(memory.userId),
      workspaceId: memory.workspaceId == null ? null : String(memory.workspaceId),
      sourceConversationId:
        memory.sourceConversationId == null ? null : String(memory.sourceConversationId),
      sourceMessageId: memory.sourceMessageId == null ? null : String(memory.sourceMessageId),
      content: String(memory.content ?? ''),
      status: String(memory.status ?? 'ACTIVE'),
      memoryScope: String(memory.memoryScope ?? 'USER'),
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
    const envelope = await ApiResponseParser.parseEnvelope<T>(response, UserErrorMessages.CHAT_REQUEST_FAILED);
    ApiResponseParser.assertSuccess(response, envelope, UserErrorMessages.CHAT_REQUEST_FAILED);
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
    const envelope = await ApiResponseParser.parseEnvelope<null>(response, UserErrorMessages.CHAT_REQUEST_FAILED);
    if (ApiResponseParser.isUnauthorized(response, envelope)) {
      throw new ApiUnauthorizedError(
        envelope.message || UserErrorMessages.AUTH_SESSION_EXPIRED,
        response.status,
        envelope.code ?? '',
      );
    }
    throw new Error(envelope.message || UserErrorMessages.CHAT_REQUEST_FAILED);
  }

  /**
   * 公开接口不需要 satoken，但仍复用 ApiResponse 解析，保证错误语义一致。
   * @param path 接口路径。
   * @returns 统一响应包。
   */
  private static async requestPublic<T>(
    path: string,
  ): Promise<ApiResponseEnvelope<T>> {
    const response = await fetch(path, {
      headers: {
        'Content-Type': 'application/json',
      },
    });
    const envelope = await ApiResponseParser.parseEnvelope<T>(response, UserErrorMessages.CHAT_REQUEST_FAILED);
    ApiResponseParser.assertSuccess(response, envelope, UserErrorMessages.CHAT_REQUEST_FAILED);
    return envelope;
  }
}

/**
 * 规范化分享消息范围，过滤空值并保持用户选择顺序去重。
 * @param messageIds 原始消息标识。
 * @returns 可发送给后端的消息标识列表。
 */
function normalizeMessageIds(messageIds: string[] | undefined) {
  return Array.from(
    new Set(
      (messageIds ?? [])
        .map((messageId) => String(messageId ?? '').trim())
        .filter(Boolean),
    ),
  );
}
