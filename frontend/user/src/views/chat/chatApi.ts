import { ApiResponseEnvelope } from '../../types/auth';
import { ApiResponseParser, ApiUnauthorizedError } from '../../api/apiResponse';
import { UserErrorMessages } from '../../constants/errorMessages';
import {
  ArtifactItem,
  ChatAttachmentItem,
  ChatExpertItem,
  ChatGoalItem,
  ChatGoalStepItem,
  ChatSkillItem,
  ChatMessageItem,
  ConversationPage,
  ConversationPageQuery,
  ConversationItem,
  CursorPageCursor,
  CurrentExpertItem,
  CurrentMcpItem,
  CurrentSkillItem,
  ExecutionStepItem,
  LongTermMemoryItem,
  LongTermMemoryStatus,
  MessagePage,
  MessagePageQuery,
  McpItem,
  ReferenceItem,
  SharedConversationPayload,
  SampleQuestionItem,
  ShareConversationOptions,
  SlashCommandItem,
  WorkspaceInventoryItem,
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
    return envelope.data.map((item) => this.normalizeConversation(item));
  }

  /**
   * 分页加载当前用户可见的会话列表，首屏和“加载更多”都走该入口，避免前端一次性接收全量会话。
   * @param token 当前登录令牌。
   * @param query 分页查询参数，缺省时读取第一页。
   * @returns 会话分页响应。
   */
  static async listConversationPage(
    token: string,
    query: ConversationPageQuery = {},
  ): Promise<ConversationPage> {
    const searchParams = new URLSearchParams();
    if (query.workspaceId && query.workspaceId.trim().length > 0) {
      searchParams.set('workspaceId', query.workspaceId);
    }
    searchParams.set('pageSize', String(query.pageSize ?? 30));
    appendCursorSearchParams(searchParams, query.cursor, 'conversation');
    const envelope = await this.request<ConversationPage>(
      `/api/chat/conversations?${searchParams.toString()}`,
      token,
    );
    // 兼容约束：分页后端已返回 CursorPage；旧测试桩或旧后端快照仍可能返回数组，按单页处理即可。
    const pageData = normalizeCursorPageData(envelope.data);
    return {
      items: pageData.items.map((item) => this.normalizeConversation(item)),
      hasMore: pageData.hasMore,
      nextCursor: pageData.nextCursor,
    };
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
    return envelope.data.map((item) => this.normalizeMessage(item));
  }

  /**
   * 读取当前会话的真实 active goal；没有目标时返回 null，页面不得自行制造占位进度。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @returns 归一化后的 active goal，或 null。
   */
  static async getActiveGoal(
    token: string,
    conversationId: string,
  ): Promise<ChatGoalItem | null> {
    const envelope = await this.request<ChatGoalItem | null>(
      `/api/chat/conversations/${encodeURIComponent(conversationId)}/goal/active`,
      token,
    );
    if (envelope.data == null) {
      return null;
    }
    return this.normalizeGoal(envelope.data);
  }

  /**
   * 分页加载指定会话消息，首页返回最近一页，before cursor 用于继续读取更旧消息。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @param query 分页查询参数。
   * @returns 消息分页响应。
   */
  static async listMessagePage(
    token: string,
    conversationId: string,
    query: MessagePageQuery = {},
  ): Promise<MessagePage> {
    const searchParams = new URLSearchParams();
    searchParams.set('pageSize', String(query.pageSize ?? 30));
    appendCursorSearchParams(searchParams, query.before, 'message');
    const envelope = await this.request<MessagePage>(
      `/api/chat/conversations/${conversationId}/messages?${searchParams.toString()}`,
      token,
    );
    // 兼容约束：分页后端已返回 CursorPage；旧测试桩或旧后端快照仍可能返回数组，按单页处理即可。
    const pageData = normalizeCursorPageData(envelope.data);
    return {
      items: pageData.items.map((item) => this.normalizeMessage(item)),
      hasMore: pageData.hasMore,
      nextCursor: pageData.nextCursor,
    };
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
   * 加载当前用户拥有的工作区库存，供侧栏补齐没有会话的空工作区分组。
   * @param token 当前登录令牌。
   * @returns 当前用户工作区列表。
   */
  static async listWorkspaces(token: string): Promise<WorkspaceInventoryItem[]> {
    const envelope = await this.request<WorkspaceInventoryItem[]>('/api/chat/workspaces', token);
    return (envelope.data ?? []).map((item) => this.normalizeWorkspace(item));
  }

  /**
   * 调用用户态聊天工具，供页面内轻量工具面板读取结构化结果。
   * @param token 当前登录令牌。
   * @param toolCode 工具编码。
   * @param payload 工具输入对象，会序列化到 question 字段。
   * @param options 页面当前工作区上下文，可为空；侧栏读取 git diff 时用于后端定位当前仓库。
   * @returns 工具执行结果。
   */
  static async invokeTool(
    token: string,
    toolCode: string,
    payload: Record<string, unknown>,
    options?: {
      workspaceId?: string | null;
      repositoryPath?: string | null;
    },
  ): Promise<{
    toolCode: string;
    content: string;
    metadata?: Record<string, unknown>;
  }> {
    const envelope = await this.request<{
      toolCode?: string;
      content?: string;
      metadata?: Record<string, unknown>;
    }>(
      `/api/chat/tools/${encodeURIComponent(toolCode)}/invoke`,
      token,
      {
        method: 'POST',
        body: JSON.stringify({
          question: JSON.stringify(payload),
          confirmHighRisk: false,
          workspaceId: normalizeOptionalString(options?.workspaceId),
          repositoryPath: normalizeOptionalString(options?.repositoryPath),
        }),
      },
    );
    return {
      toolCode: String(envelope.data.toolCode ?? toolCode),
      content: String(envelope.data.content ?? ''),
      metadata: envelope.data.metadata,
    };
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
    activeMemoryCount: number;
  }> {
    const envelope = await this.request<{
      repositoryPath: string;
      workspaceId: string;
      workspaceName: string;
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
      activeMemoryCount: Number(envelope.data.activeMemoryCount ?? 0),
    };
  }

  /**
   * 查询当前用户可见的长期记忆，默认按当前工作空间约束；管理页可显式查询全部工作空间。
   * @param token 当前登录令牌。
   * @param workspaceId 当前工作空间 ID，可为空。
   * @param status 可选状态筛选。
   * @param options 管理页查询选项。
   * @returns 归一化后的长期记忆列表。
   */
  static async listLongTermMemories(
    token: string,
    workspaceId?: string | null,
    status?: LongTermMemoryStatus | 'ALL' | null,
    options?: { includeAllWorkspaces?: boolean },
  ): Promise<LongTermMemoryItem[]> {
    const searchParams = new URLSearchParams();
    if (workspaceId && workspaceId.trim().length > 0) {
      searchParams.set('workspaceId', workspaceId.trim());
    }
    if (options?.includeAllWorkspaces) {
      // 该参数只用于记忆管理页总览，聊天上下文仍按 workspaceId 默认语义检索。
      searchParams.set('includeAllWorkspaces', 'true');
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
   * 统一归一化会话结构，分页和旧列表接口共用，避免 Long 主键和可空状态在两个路径出现差异。
   * @param item 后端返回的会话项。
   * @returns 前端可直接使用的会话项。
   */
  private static normalizeConversation(item: ConversationItem): ConversationItem {
    return {
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
    };
  }

  /**
   * 统一归一化目标快照，保证 Long 主键和步骤顺序进入 React 状态前稳定。
   * @param goal 后端返回或 SSE 下发的目标快照。
   * @returns 前端可直接展示的目标状态。
   */
  static normalizeGoal(goal: ChatGoalItem): ChatGoalItem {
    return {
      ...goal,
      id: String(goal.id ?? ''),
      conversationId: String(goal.conversationId ?? ''),
      goalKey: goal.goalKey == null ? undefined : String(goal.goalKey),
      title: String(goal.title ?? ''),
      description: goal.description == null ? null : String(goal.description),
      status: String(goal.status ?? ''),
      progressSummary: goal.progressSummary == null ? null : String(goal.progressSummary),
      createdRunId: goal.createdRunId == null ? null : String(goal.createdRunId),
      updatedRunId: goal.updatedRunId == null ? null : String(goal.updatedRunId),
      createdAt: goal.createdAt == null ? null : String(goal.createdAt),
      updatedAt: goal.updatedAt == null ? null : String(goal.updatedAt),
      completedAt: goal.completedAt == null ? null : String(goal.completedAt),
      steps: (goal.steps ?? []).map((step) => this.normalizeGoalStep(step)),
    };
  }

  /**
   * 统一归一化目标步骤，兼容后端使用 id/key/title/content 的不同快照字段。
   * @param step 后端返回或 SSE 下发的步骤快照。
   * @returns 前端展示所需步骤。
   */
  private static normalizeGoalStep(step: ChatGoalStepItem): ChatGoalStepItem {
    const record = step as ChatGoalStepItem & {
      key?: unknown;
      content?: unknown;
      sequenceNo?: unknown;
    };
    return {
      id: String(step.id ?? record.key ?? ''),
      goalId: step.goalId == null ? undefined : String(step.goalId),
      stepKey: step.stepKey == null && record.key == null ? undefined : String(step.stepKey ?? record.key),
      title: String(step.title ?? record.content ?? ''),
      status: String(step.status ?? ''),
      sortNo: Number(step.sortNo ?? record.sequenceNo ?? 0),
      detail: step.detail == null ? null : String(step.detail),
      startedAt: step.startedAt == null ? null : String(step.startedAt),
      completedAt: step.completedAt == null ? null : String(step.completedAt),
      updatedAt: step.updatedAt == null ? null : String(step.updatedAt),
    };
  }

  /**
   * 统一归一化用户工作区库存，避免运行目标大小写和 Long 主键影响侧栏分组。
   * @param item 后端返回的工作区项。
   * @returns 前端可直接合并的工作区项。
   */
  private static normalizeWorkspace(item: WorkspaceInventoryItem): WorkspaceInventoryItem {
    const runtimeTarget = String(item.runtimeTarget ?? '').trim().toLowerCase();
    const workingDirectory = String(item.workingDirectory ?? '').trim();
    const repositoryUrl = String(item.repositoryUrl ?? '').trim();
    const branchName = String(item.branchName ?? '').trim();
    return {
      id: String(item.id ?? ''),
      name: String(item.name ?? ''),
      runtimeTarget: runtimeTarget === 'local' ? 'local' : 'cloud',
      workingDirectory: workingDirectory.length === 0 ? null : workingDirectory,
      repositoryUrl: repositoryUrl.length === 0 ? null : repositoryUrl,
      branchName: branchName.length === 0 ? null : branchName,
    };
  }

  /**
   * 统一归一化消息结构，分页和旧列表接口共用，保证附件与技能标记始终是前端安全类型。
   * @param item 后端返回的消息项。
   * @returns 前端可直接使用的消息项。
   */
  private static normalizeMessage(item: ChatMessageItem): ChatMessageItem {
    return {
      ...item,
      id: String(item.id ?? ''),
      conversationId: String(item.conversationId ?? ''),
      runId: item.runId == null ? undefined : String(item.runId),
      skillCodes: (item.skillCodes ?? []).map((skillCode) => String(skillCode ?? '')).filter(Boolean),
      attachments: (item.attachments ?? []).map((attachment) => this.normalizeAttachment(attachment)),
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
      workspaceName: memory.workspaceName == null ? null : String(memory.workspaceName),
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
   * 打开聊天 SSE 流请求，集中设置鉴权头与 abort signal，Hook 只负责构造业务 URL 和消费流内容。
   * @param path 已构造好的流式请求路径。
   * @param token 当前登录令牌。
   * @param signal 取消信号。
   * @returns 已通过授权检查的响应对象。
   */
  static async openChatStream(path: string, token: string, signal: AbortSignal): Promise<Response> {
    const response = await fetch(path, {
      headers: {
        satoken: token,
      },
      signal,
    });
    await this.assertStreamAuthorized(response);
    return response;
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
 * 归一化可选字符串字段；空白值视为未传，避免后端把空路径当作显式上下文。
 * @param value 原始可选字段。
 * @returns 去空白后的字符串，或 undefined。
 */
function normalizeOptionalString(value: string | null | undefined) {
  const normalizedValue = value == null ? '' : String(value).trim();
  return normalizedValue.length > 0 ? normalizedValue : undefined;
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

/**
 * 将前端 cursor 映射为后端分页查询参数；会话和消息分页使用不同的时间字段名。
 * @param searchParams 正在拼装的 URL 参数。
 * @param cursor 上一页返回的 cursor。
 * @param mode 分页目标类型。
 */
function appendCursorSearchParams(
  searchParams: URLSearchParams,
  cursor: CursorPageCursor | null | undefined,
  mode: 'conversation' | 'message',
) {
  if (!cursor) {
    return;
  }
  if (mode === 'conversation') {
    if (cursor.cursorPinned != null) {
      searchParams.set('cursorPinned', String(Boolean(cursor.cursorPinned)));
    }
    if (cursor.cursorUpdatedAt) {
      searchParams.set('cursorUpdatedAt', cursor.cursorUpdatedAt);
    }
    if (cursor.cursorId) {
      searchParams.set('cursorId', cursor.cursorId);
    }
    return;
  }
  if (cursor.cursorCreatedAt) {
    searchParams.set('beforeCreatedAt', cursor.cursorCreatedAt);
  }
  if (cursor.cursorId) {
    searchParams.set('beforeId', cursor.cursorId);
  }
}

/**
 * 归一化后端返回的分页 cursor，保证 Snowflake 主键不会在前端被 Number 化。
 * @param cursor 后端返回的原始 cursor。
 * @returns 字符串化后的 cursor，没有下一页时返回 null。
 */
function normalizeCursor(cursor: CursorPageCursor | null | undefined): CursorPageCursor | null {
  if (!cursor) {
    return null;
  }
  return {
    cursorPinned: cursor.cursorPinned == null ? null : Boolean(cursor.cursorPinned),
    cursorUpdatedAt: cursor.cursorUpdatedAt == null ? null : String(cursor.cursorUpdatedAt),
    cursorCreatedAt: cursor.cursorCreatedAt == null ? null : String(cursor.cursorCreatedAt),
    cursorId: cursor.cursorId == null ? null : String(cursor.cursorId),
  };
}

/**
 * 归一化分页响应数据；旧数组响应按无下一页的单页数据处理，保障兼容路径不会中断页面初始化。
 * @param data 后端或测试桩返回的原始 data。
 * @returns 标准分页数据。
 */
function normalizeCursorPageData<T>(
  data: CursorPage<T> | T[] | null | undefined,
): CursorPage<T> {
  if (Array.isArray(data)) {
    return {
      items: data,
      hasMore: false,
      nextCursor: null,
    };
  }
  return {
    items: data?.items ?? [],
    hasMore: Boolean(data?.hasMore),
    nextCursor: normalizeCursor(data?.nextCursor),
  };
}
