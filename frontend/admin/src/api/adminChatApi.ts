import { ApiResponseParser } from './apiResponse';
import { publishAdminAuthExpired } from '../auth/authEvents';
import { AdminErrorMessages } from '../constants/errorMessages';
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
  extraDataJson?: string;
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

export interface AdminChatConversationListItem {
  id: number;
  title: string;
  createdBy: number;
  status: string;
  statusLabel: string;
  lastMessageAt?: string;
  lastRunId?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AdminChatConversationMessageAttachment {
  id: number;
  conversationId: number;
  messageId: number;
  attachmentType: string;
  fileName: string;
  fileExt?: string;
  mimeType?: string;
  fileSize?: number;
  previewUrl?: string;
  contentSummary?: string;
  status?: string;
  createdAt?: string;
}

export interface AdminChatConversationMessage {
  id: number;
  conversationId: number;
  // 管理端排障需要展示消息所属运行记录；旧响应缺失时允许为空。
  runId?: number;
  role: string;
  content: string;
  thinkingContent?: string;
  thinkingDuration?: number;
  status: string;
  provider?: string;
  model?: string;
  errorMessage?: string;
  createdAt?: string;
  // 管理端兼容后端完整消息字段，旧响应缺失时展示为“-”。
  updatedAt?: string;
  deleted?: number;
  attachments?: AdminChatConversationMessageAttachment[];
  // 后端会从用户消息正文解析技能编码，管理端详情页直接展示解析结果。
  skillCodes?: string[];
  // 当前用户投票值：1 点赞、-1 点踩、null/undefined 表示未反馈。
  userVote?: number | null;
}

export interface AdminChatConversationDetail extends AdminChatConversationListItem {
  messages: AdminChatConversationMessage[];
}

export interface AdminChatConversationQuery {
  current?: number;
  size?: number;
  keyword?: string;
}

export interface AdminWorkspace {
  id: number;
  name: string;
  repositoryUrl?: string;
  branchName?: string;
  workingDirectory?: string;
  runtimeTarget: string;
  runtimeTargetLabel: string;
  createdBy?: number;
  conversationCount: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AdminWorkspaceQuery {
  current?: number;
  size?: number;
  keyword?: string;
  runtimeTarget?: string;
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
  id?: string | number;
  sourceTerm: string;
  targetTerm: string;
  matchType: number;
  priority: number;
  enabled: boolean;
  remark?: string | null;
  createTime?: string;
  updateTime?: string;
}

export interface AdminQueryTermMappingPayload {
  sourceTerm: string;
  targetTerm: string;
  matchType?: number;
  priority?: number;
  enabled?: boolean;
  remark?: string | null;
}

export interface AdminChatMessageFeedback {
  id: number;
  messageId: number;
  conversationId: number;
  userId?: number;
  vote: number;
  reason?: string;
  comment?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AdminChatMessageFeedbackDetail extends AdminChatMessageFeedback {
  conversationTitle?: string;
  messageRole?: string;
  messageContent?: string;
}

export interface AdminChatMessageReference {
  id: number;
  runId?: number;
  messageId?: number;
  conversationId?: number;
  sourceType?: string;
  title?: string;
  url?: string;
  siteName?: string;
  snippet?: string;
  rankNo?: number;
  createdAt?: string;
}

export interface AdminFeedbackQuery {
  current?: number;
  size?: number;
  keyword?: string;
  vote?: number | null;
}

export interface AdminPageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface AdminRuntimeSetting {
  id?: string;
  settingKey: string;
  settingValue: string;
  valueType: string;
  secret?: boolean;
  maskedValue?: string;
  categoryCode?: string;
  description?: string;
  sortNo?: number;
  restartRequired?: boolean;
}

export type AdminDashboardWindow = '24h' | '7d' | '30d';

export interface AdminDashboardKpis {
  activeUserCount: number;
  conversationCount: number;
  messageCount: number;
  workspaceCount: number;
  traceCount: number;
  runningTraceCount: number;
}

export interface AdminDashboardResources {
  skillCount: number;
  toolCount: number;
  expertCount: number;
  mcpCount: number;
  intentNodeCount: number;
  mappingCount: number;
  sampleQuestionCount: number;
}

export interface AdminDashboardPerformance {
  successRate: number;
  failureRate: number;
  runningRate: number;
  avgTraceDurationMs: number;
  p95TraceDurationMs: number;
}

export interface AdminDashboardTrendBucket {
  label: string;
  bucketStart: string;
  conversationCount: number;
  messageCount: number;
  activeUserCount: number;
  traceCount: number;
  successCount: number;
  failedCount: number;
  avgDurationMs: number;
}

export interface AdminDashboardView {
  window: AdminDashboardWindow;
  generatedAt: string;
  kpis: AdminDashboardKpis;
  resources: AdminDashboardResources;
  performance: AdminDashboardPerformance;
  trendBuckets: AdminDashboardTrendBucket[];
}

/**
 * 聊天运行时队列观测视图。
 */
export interface AdminChatRuntimeQueueView {
  mode: string;
  maxConcurrent: number;
  activeCount: number;
  waitingCount: number;
  availablePermits: number;
}

/**
 * 聊天运行时线程池观测视图。
 */
export interface AdminChatRuntimeExecutorView {
  streamActiveCount: number;
  streamPoolSize: number;
  streamQueueSize: number;
  streamQueueRemainingCapacity: number;
  searchActiveCount: number;
  searchPoolSize: number;
  searchQueueSize: number;
  searchQueueRemainingCapacity: number;
}

/**
 * 聊天运行时总览视图。
 */
export interface AdminChatRuntimeDashboardView {
  queue: AdminChatRuntimeQueueView;
  executor: AdminChatRuntimeExecutorView;
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
  storageKey?: string;
  packageFileName?: string;
  packageSize?: number;
  packageChecksum?: string;
  uploadedBy?: string | number;
  uploadedAt?: string;
}

export interface AdminSkillQuery {
  current?: number;
  size?: number;
}

export interface AdminSkillPackageEntry {
  path: string;
  name: string;
  directory: boolean;
  size?: number | null;
}

export interface AdminSkillPackageFileContent {
  path: string;
  content: string;
  truncated: boolean;
}

export interface AdminSkillPackageMigrationFailure {
  skillId?: number;
  skillCode: string;
  reason: string;
}

export interface AdminSkillPackageMigrationSummary {
  total: number;
  migrated: number;
  skipped: number;
  failures: AdminSkillPackageMigrationFailure[];
}

export interface AdminExpert {
  id?: string | number;
  expertCode: string;
  displayName: string;
  description?: string;
  category?: string;
  tagsJson?: string;
  avatarUrl?: string;
  presetQuestion?: string;
  systemPrompt?: string;
  enabled?: number;
  sortNo?: number;
}

export interface AdminExpertQuery {
  current?: number;
  size?: number;
}

export interface AdminChatTool {
  id?: string | number;
  toolCode: string;
  displayName: string;
  description?: string;
  category?: string;
  sourceType?: string;
  enabled?: number;
  sortNo?: number;
}

export interface AdminChatToolHealthView {
  toolCode: string;
  displayName: string;
  category?: string;
  source?: string;
  status: string;
  statusLabel: string;
  ok: boolean;
  message?: string;
  description?: string;
  sampleQuestion?: string;
  checkedAt?: string;
  durationMs?: number;
}

export interface AdminChatToolInvokeView {
  toolCode: string;
  displayName: string;
  ok: boolean;
  status: string;
  statusLabel: string;
  message?: string;
  requestQuestion?: string;
  content?: string;
  metadata?: Record<string, unknown>;
  checkedAt?: string;
  durationMs?: number;
}

/**
 * 治理中心权限策略配置，控制本地工具调用在执行前是否允许、确认或拒绝。
 */
export interface AdminGovernancePermissionPolicy {
  id?: string | number;
  policyCode: string;
  policyName: string;
  toolCode?: string;
  commandPattern?: string;
  pathPattern?: string;
  action: string;
  riskLevel: string;
  description?: string;
  enabled?: number;
  sortNo?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 治理中心权限判定审计记录，用于回看工具调用被哪条策略命中。
 */
export interface AdminGovernancePermissionAudit {
  id?: string | number;
  userId?: string | number;
  conversationId?: string | number;
  runId?: string | number;
  toolCode?: string;
  toolInput?: string;
  workingDirectory?: string;
  matchedPolicyCode?: string;
  decision?: string;
  riskLevel?: string;
  result?: string;
  message?: string;
  createdAt?: string;
}

/**
 * 治理中心 Hook 规则配置，用于管理任务生命周期事件上的自动化动作。
 */
export interface AdminGovernanceHookRule {
  id?: string | number;
  hookCode: string;
  hookName: string;
  triggerPoint: string;
  conditionKeyword?: string;
  actionType: string;
  actionConfigJson?: string;
  enabled?: number;
  sortNo?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 长期记忆治理记录，管理员可查看并启停项目级或用户级记忆。
 */
export interface AdminGovernanceLongTermMemory {
  id?: string | number;
  memoryScope: string;
  userId?: string | number;
  workspaceId?: string | number;
  memoryKey?: string;
  content: string;
  status: string;
  sourceType?: string;
  sourceConversationId?: string | number;
  sourceMessageId?: string | number;
  keywordJson?: string;
  confidenceScore?: number | string;
  lastUsedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Slash Command 配置，用户端聊天输入区只展示启用的命令。
 */
export interface AdminGovernanceSlashCommand {
  id?: string | number;
  commandCode: string;
  displayName: string;
  description?: string;
  commandType: string;
  promptTemplate?: string;
  enabled?: number;
  sortNo?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 统一封装管理端聊天运行时后台接口。
 */
export class AdminChatApi {
  /**
   * 分页查询会话列表，支持标题或会话 ID 关键字过滤。
   * @param query 分页与筛选参数。
   * @returns 会话分页结果。
   */
  static async listConversations(
    query: AdminChatConversationQuery = {},
  ): Promise<AdminPageResult<AdminChatConversationListItem>> {
    const searchParams = new URLSearchParams();
    searchParams.set('current', String(query.current ?? 1));
    searchParams.set('size', String(query.size ?? 10));
    if (query.keyword && query.keyword.trim()) {
      searchParams.set('keyword', query.keyword.trim());
    }
    return this.request<AdminPageResult<AdminChatConversationListItem>>(
      `/api/admin/chat/conversations?${searchParams.toString()}`,
    );
  }

  /**
   * 查询单条会话详情并附带消息列表。
   * @param conversationId 会话主键。
   * @returns 会话详情数据。
   */
  static async getConversationDetail(conversationId: string | number): Promise<AdminChatConversationDetail> {
    return this.request<AdminChatConversationDetail>(
      `/api/admin/chat/conversations/${encodeURIComponent(String(conversationId))}`,
    );
  }

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

  /**
   * 分页查询关键词映射规则。
   * @param current 当前页码（从 1 开始）
   * @param size 每页条数
   * @param keyword 搜索关键字（匹配原始词/目标词）
   * @returns 映射规则分页结果
   */
  static async listMappingsPage(
    current = 1,
    size = 10,
    keyword?: string,
  ): Promise<AdminPageResult<AdminQueryTermMapping>> {
    const searchParams = new URLSearchParams();
    searchParams.set('current', String(current));
    searchParams.set('size', String(size));
    if (keyword && keyword.trim()) {
      searchParams.set('keyword', keyword.trim());
    }
    const response = await this.request<AdminPageResult<AdminQueryTermMapping> | AdminQueryTermMapping[]>(
      `/api/admin/chat/query-term-mappings?${searchParams.toString()}`,
    );

    // 兼容旧接口直接返回数组的场景，避免分页组件出现 NaN。
    if (Array.isArray(response)) {
      const legacyRecords = response.map((item) => normalizeLegacyMapping(item));
      return {
        records: legacyRecords,
        total: legacyRecords.length,
        size: legacyRecords.length || size,
        current: 1,
        pages: 1,
      };
    }

    const records = Array.isArray(response?.records)
      ? response.records.map((item) => normalizeLegacyMapping(item))
      : [];
    const total = Number(response?.total ?? records.length ?? 0);
    const pageSize = Number(response?.size ?? size);
    const pageCurrent = Number(response?.current ?? current);
    const pageCount = Number(response?.pages ?? Math.max(1, Math.ceil(total / Math.max(1, pageSize))));
    return {
      records,
      total: Number.isFinite(total) ? total : records.length,
      size: Number.isFinite(pageSize) && pageSize > 0 ? pageSize : size,
      current: Number.isFinite(pageCurrent) && pageCurrent > 0 ? pageCurrent : 1,
      pages: Number.isFinite(pageCount) && pageCount > 0 ? pageCount : 1,
    };
  }

  /**
   * 根据主键查询关键词映射详情。
   * @param id 映射规则 ID
   * @returns 映射规则详情
   */
  static async getMappingById(id: string | number): Promise<AdminQueryTermMapping> {
    return this.request<AdminQueryTermMapping>(
      `/api/admin/chat/query-term-mappings/${encodeURIComponent(String(id))}`,
    );
  }

  /**
   * 新增关键词映射规则。
   * @param payload 映射规则创建参数
   * @returns 创建后的映射规则
   */
  static async createMapping(payload: AdminQueryTermMappingPayload): Promise<AdminQueryTermMapping> {
    return this.request<AdminQueryTermMapping>('/api/admin/chat/query-term-mappings', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  /**
   * 更新关键词映射规则。
   * @param id 映射规则 ID
   * @param payload 映射规则更新参数
   * @returns 更新后的映射规则
   */
  static async updateMapping(
    id: string | number,
    payload: AdminQueryTermMappingPayload,
  ): Promise<AdminQueryTermMapping> {
    return this.request<AdminQueryTermMapping>(
      `/api/admin/chat/query-term-mappings/${encodeURIComponent(String(id))}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
    );
  }

  /**
   * 删除关键词映射规则。
   * @param id 映射规则 ID
   */
  static async deleteMapping(id: string | number): Promise<void> {
    await this.request<void>(`/api/admin/chat/query-term-mappings/${encodeURIComponent(String(id))}`, {
      method: 'DELETE',
    });
  }

  /**
   * 分页查询聊天反馈记录，支持关键字与投票方向过滤。
   * @param query 分页与筛选参数。
   * @returns 反馈分页结果。
   */
  static async listFeedbacks(query: AdminFeedbackQuery = {}): Promise<AdminPageResult<AdminChatMessageFeedback>> {
    const searchParams = new URLSearchParams();
    searchParams.set('current', String(query.current ?? 1));
    searchParams.set('size', String(query.size ?? 10));
    if (query.keyword && query.keyword.trim()) {
      searchParams.set('keyword', query.keyword.trim());
    }
    if (typeof query.vote === 'number' && Number.isFinite(query.vote) && query.vote !== 0) {
      searchParams.set('vote', String(query.vote));
    }
    return this.request<AdminPageResult<AdminChatMessageFeedback>>(
      `/api/admin/chat/feedbacks?${searchParams.toString()}`,
    );
  }

  /**
   * 查询单条反馈详情，聚合消息角色与内容。
   * @param feedbackId 反馈主键。
   * @returns 反馈详情。
   */
  static async getFeedbackDetail(feedbackId: string | number): Promise<AdminChatMessageFeedbackDetail> {
    return this.request<AdminChatMessageFeedbackDetail>(
      `/api/admin/chat/feedbacks/${encodeURIComponent(String(feedbackId))}`,
    );
  }

  /**
   * 查询反馈关联消息的引用来源列表。
   * @param feedbackId 反馈主键。
   * @returns 引用来源列表。
   */
  static async listFeedbackReferences(feedbackId: string | number): Promise<AdminChatMessageReference[]> {
    return this.request<AdminChatMessageReference[]>(
      `/api/admin/chat/feedbacks/${encodeURIComponent(String(feedbackId))}/references`,
    );
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

  static async saveSettings(payload: AdminRuntimeSetting[]): Promise<AdminRuntimeSetting[]> {
    return this.request<AdminRuntimeSetting[]>('/api/admin/chat/settings/batch', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  static async getDashboard(window: AdminDashboardWindow = '24h'): Promise<AdminDashboardView> {
    return this.request<AdminDashboardView>(`/api/admin/chat/dashboard?window=${encodeURIComponent(window)}`);
  }

  /**
   * 获取聊天运行时观测面板数据。
   */
  static async getRuntimeDashboard(): Promise<AdminChatRuntimeDashboardView> {
    return this.request<AdminChatRuntimeDashboardView>('/api/admin/chat/runtime');
  }

  /**
   * 分页查询管理端工作空间列表，支持关键字与运行目标筛选。
   * @param query 分页与筛选参数。
   * @returns 工作空间分页结果。
   */
  static async listWorkspaces(query: AdminWorkspaceQuery = {}): Promise<AdminPageResult<AdminWorkspace>> {
    const searchParams = new URLSearchParams();
    searchParams.set('current', String(query.current ?? 1));
    searchParams.set('size', String(query.size ?? 10));
    if (query.keyword && query.keyword.trim()) {
      searchParams.set('keyword', query.keyword.trim());
    }
    if (query.runtimeTarget && query.runtimeTarget.trim() && query.runtimeTarget !== 'ALL') {
      searchParams.set('runtimeTarget', query.runtimeTarget.trim());
    }
    return this.request<AdminPageResult<AdminWorkspace>>(`/api/admin/workspaces?${searchParams.toString()}`);
  }

  /**
   * 分页查询指定工作空间下的会话，供工作空间详情页展示空间内部历史。
   * @param workspaceId 工作空间主键。
   * @param query 分页与筛选参数。
   * @returns 工作空间内会话分页结果。
   */
  static async listWorkspaceConversations(
    workspaceId: string | number,
    query: AdminChatConversationQuery = {},
  ): Promise<AdminPageResult<AdminChatConversationListItem>> {
    const searchParams = new URLSearchParams();
    searchParams.set('current', String(query.current ?? 1));
    searchParams.set('size', String(query.size ?? 10));
    if (query.keyword && query.keyword.trim()) {
      searchParams.set('keyword', query.keyword.trim());
    }
    return this.request<AdminPageResult<AdminChatConversationListItem>>(
      `/api/admin/workspaces/${encodeURIComponent(String(workspaceId))}/conversations?${searchParams.toString()}`,
    );
  }

  static async listMcpTools(): Promise<AdminMcpToolView[]> {
    return this.request<AdminMcpToolView[]>('/api/admin/mcps/tools');
  }

  static async pingMcpTool(toolId: string): Promise<AdminMcpToolView> {
    return this.request<AdminMcpToolView>(`/api/admin/mcps/tools/${encodeURIComponent(toolId)}/ping`);
  }

  static async listSkills(query: AdminSkillQuery = {}): Promise<AdminPageResult<AdminSkill>> {
    const searchParams = new URLSearchParams();
    searchParams.set('current', String(query.current ?? 1));
    searchParams.set('size', String(query.size ?? 10));
    return this.request<AdminPageResult<AdminSkill>>(`/api/admin/skills?${searchParams.toString()}`);
  }

  static async createSkill(payload: AdminSkill): Promise<AdminSkill> {
    return this.request<AdminSkill>('/api/admin/skills', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  static async updateSkill(id: string | number, payload: AdminSkill): Promise<AdminSkill> {
    return this.request<AdminSkill>(`/api/admin/skills/${encodeURIComponent(String(id))}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  }

  static async deleteSkill(id: string | number): Promise<void> {
    await this.request<void>(`/api/admin/skills/${encodeURIComponent(String(id))}`, {
      method: 'DELETE',
    });
  }

  /**
   * 上传技能包并由后端自动解析 SKILL.md 元信息。
   * @param file 技能包文件（zip 或 skill）。
   * @param category 可选分类。
   * @param directoryFiles 目录文件列表。
   * @param forceOverwrite 是否强制覆盖同名技能。
   * @returns 解析后的技能配置。
   */
  static async uploadSkillPackage(
    file: File | null,
    category?: string,
    directoryFiles?: File[],
    forceOverwrite?: boolean,
  ): Promise<AdminSkill> {
    const formData = new FormData();
    const hasDirectoryFiles = Array.isArray(directoryFiles) && directoryFiles.length > 0;
    if (hasDirectoryFiles) {
      directoryFiles.forEach((directoryFile) => {
        const relativePath = ((directoryFile as File & { webkitRelativePath?: string }).webkitRelativePath
          || directoryFile.name).replace(/^\/+/, '');
        formData.append('files', directoryFile, relativePath);
      });
    } else if (file) {
      formData.append('file', file);
    } else {
      throw new Error('请选择技能包文件');
    }
    if (category && category.trim()) {
      formData.append('category', category.trim());
    }
    if (forceOverwrite) {
      formData.append('forceOverwrite', 'true');
    }
    return this.request<AdminSkill>('/api/admin/skills/upload', {
      method: 'POST',
      body: formData,
    });
  }

  /**
   * 触发历史技能压缩包迁移，将对象存储格式统一转换为目录结构。
   * @returns 迁移统计摘要。
   */
  static async migrateSkillPackages(): Promise<AdminSkillPackageMigrationSummary> {
    return this.request<AdminSkillPackageMigrationSummary>('/api/admin/skills/migrate-packages', {
      method: 'POST',
    });
  }

  /**
   * 查询技能包目录树，用于资源管理器展示。
   * @param id 技能主键。
   * @returns 目录树条目列表。
   */
  static async listSkillPackageEntries(id: string | number): Promise<AdminSkillPackageEntry[]> {
    return this.request<AdminSkillPackageEntry[]>(
      `/api/admin/skills/${encodeURIComponent(String(id))}/package/entries`,
    );
  }

  /**
   * 按路径读取技能包中的文本文件内容。
   * @param id 技能主键。
   * @param path 归档内相对路径。
   * @returns 文件预览内容。
   */
  static async getSkillPackageFileContent(
    id: string | number,
    path: string,
  ): Promise<AdminSkillPackageFileContent> {
    const searchParams = new URLSearchParams();
    searchParams.set('path', path);
    return this.request<AdminSkillPackageFileContent>(
      `/api/admin/skills/${encodeURIComponent(String(id))}/package/file-content?${searchParams.toString()}`,
    );
  }

  static async listMcpConfigs(): Promise<AdminMcpConfig[]> {
    return this.request<AdminMcpConfig[]>('/api/admin/mcps');
  }

  static async listExperts(query: AdminExpertQuery = {}): Promise<AdminPageResult<AdminExpert>> {
    const searchParams = new URLSearchParams();
    searchParams.set('current', String(query.current ?? 1));
    searchParams.set('size', String(query.size ?? 10));
    return this.request<AdminPageResult<AdminExpert>>(`/api/admin/experts?${searchParams.toString()}`);
  }

  static async createExpert(payload: AdminExpert): Promise<AdminExpert> {
    return this.request<AdminExpert>('/api/admin/experts', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  static async updateExpert(id: string | number, payload: AdminExpert): Promise<AdminExpert> {
    return this.request<AdminExpert>(`/api/admin/experts/${encodeURIComponent(String(id))}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  }

  static async deleteExpert(id: string | number): Promise<void> {
    await this.request<void>(`/api/admin/experts/${encodeURIComponent(String(id))}`, {
      method: 'DELETE',
    });
  }

  static async createMcpConfig(payload: AdminMcpConfig): Promise<AdminMcpConfig> {
    return this.request<AdminMcpConfig>('/api/admin/mcps', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  static async updateMcpConfig(id: string | number, payload: AdminMcpConfig): Promise<AdminMcpConfig> {
    return this.request<AdminMcpConfig>(`/api/admin/mcps/${encodeURIComponent(String(id))}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  }

  static async deleteMcpConfig(id: string | number): Promise<void> {
    await this.request<void>(`/api/admin/mcps/${encodeURIComponent(String(id))}`, {
      method: 'DELETE',
    });
  }

  static async listTools(): Promise<AdminChatTool[]> {
    return this.request<AdminChatTool[]>('/api/admin/tools');
  }

  static async createTool(payload: AdminChatTool): Promise<AdminChatTool> {
    return this.request<AdminChatTool>('/api/admin/tools', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  static async updateTool(id: string | number, payload: AdminChatTool): Promise<AdminChatTool> {
    return this.request<AdminChatTool>(`/api/admin/tools/${encodeURIComponent(String(id))}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  }

  static async deleteTool(id: string | number): Promise<void> {
    await this.request<void>(`/api/admin/tools/${encodeURIComponent(String(id))}`, {
      method: 'DELETE',
    });
  }

  static async listToolHealthViews(): Promise<AdminChatToolHealthView[]> {
    return this.request<AdminChatToolHealthView[]>('/api/admin/tools/health');
  }

  static async pingTool(toolCode: string): Promise<AdminChatToolHealthView> {
    return this.request<AdminChatToolHealthView>(`/api/admin/tools/${encodeURIComponent(toolCode)}/ping`);
  }

  static async invokeTool(toolCode: string, question?: string): Promise<AdminChatToolInvokeView> {
    return this.request<AdminChatToolInvokeView>(`/api/admin/tools/${encodeURIComponent(toolCode)}/invoke`, {
      method: 'POST',
      body: JSON.stringify({ question }),
    });
  }

  /**
   * 查询治理中心权限策略列表。
   */
  static async listPermissionPolicies(): Promise<AdminGovernancePermissionPolicy[]> {
    return this.request<AdminGovernancePermissionPolicy[]>('/api/admin/governance/permission-policies');
  }

  /**
   * 新增权限策略。
   * @param payload 策略配置。
   */
  static async createPermissionPolicy(
    payload: AdminGovernancePermissionPolicy,
  ): Promise<AdminGovernancePermissionPolicy> {
    return this.request<AdminGovernancePermissionPolicy>('/api/admin/governance/permission-policies', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  /**
   * 更新权限策略。
   * @param id 策略主键。
   * @param payload 策略配置。
   */
  static async updatePermissionPolicy(
    id: string | number,
    payload: AdminGovernancePermissionPolicy,
  ): Promise<AdminGovernancePermissionPolicy> {
    return this.request<AdminGovernancePermissionPolicy>(
      `/api/admin/governance/permission-policies/${encodeURIComponent(String(id))}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
    );
  }

  /**
   * 删除权限策略。
   * @param id 策略主键。
   */
  static async deletePermissionPolicy(id: string | number): Promise<void> {
    await this.request<void>(`/api/admin/governance/permission-policies/${encodeURIComponent(String(id))}`, {
      method: 'DELETE',
    });
  }

  /**
   * 查询最近权限审计记录。
   * @param limit 最大返回条数。
   */
  static async listPermissionAudits(limit = 50): Promise<AdminGovernancePermissionAudit[]> {
    return this.request<AdminGovernancePermissionAudit[]>(
      `/api/admin/governance/permission-audits?limit=${encodeURIComponent(String(limit))}`,
    );
  }

  /**
   * 查询 Hook 规则列表。
   */
  static async listHookRules(): Promise<AdminGovernanceHookRule[]> {
    return this.request<AdminGovernanceHookRule[]>('/api/admin/governance/hook-rules');
  }

  /**
   * 新增 Hook 规则。
   * @param payload Hook 规则配置。
   */
  static async createHookRule(payload: AdminGovernanceHookRule): Promise<AdminGovernanceHookRule> {
    return this.request<AdminGovernanceHookRule>('/api/admin/governance/hook-rules', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  /**
   * 更新 Hook 规则。
   * @param id Hook 规则主键。
   * @param payload Hook 规则配置。
   */
  static async updateHookRule(
    id: string | number,
    payload: AdminGovernanceHookRule,
  ): Promise<AdminGovernanceHookRule> {
    return this.request<AdminGovernanceHookRule>(
      `/api/admin/governance/hook-rules/${encodeURIComponent(String(id))}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
    );
  }

  /**
   * 删除 Hook 规则。
   * @param id Hook 规则主键。
   */
  static async deleteHookRule(id: string | number): Promise<void> {
    await this.request<void>(`/api/admin/governance/hook-rules/${encodeURIComponent(String(id))}`, {
      method: 'DELETE',
    });
  }

  /**
   * 查询长期记忆列表，支持按状态筛选。
   * @param status 状态筛选，ALL 表示全部。
   * @param limit 最大返回数量。
   */
  static async listLongTermMemories(status = 'ALL', limit = 100): Promise<AdminGovernanceLongTermMemory[]> {
    const searchParams = new URLSearchParams();
    searchParams.set('status', status);
    searchParams.set('limit', String(limit));
    return this.request<AdminGovernanceLongTermMemory[]>(
      `/api/admin/governance/long-term-memories?${searchParams.toString()}`,
    );
  }

  /**
   * 更新长期记忆状态，供管理员启用或停用记忆。
   * @param id 长期记忆主键。
   * @param status 目标状态。
   */
  static async updateLongTermMemoryStatus(
    id: string | number,
    status: 'ACTIVE' | 'REJECTED',
  ): Promise<AdminGovernanceLongTermMemory> {
    return this.request<AdminGovernanceLongTermMemory>(
      `/api/admin/governance/long-term-memories/${encodeURIComponent(String(id))}/status`,
      {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      },
    );
  }

  /**
   * 查询治理中心 Slash Command 全量配置。
   */
  static async listGovernanceSlashCommands(): Promise<AdminGovernanceSlashCommand[]> {
    return this.request<AdminGovernanceSlashCommand[]>('/api/admin/governance/slash-commands');
  }

  /**
   * 新增 Slash Command 配置。
   * @param payload 命令配置。
   */
  static async createGovernanceSlashCommand(
    payload: AdminGovernanceSlashCommand,
  ): Promise<AdminGovernanceSlashCommand> {
    return this.request<AdminGovernanceSlashCommand>('/api/admin/governance/slash-commands', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  /**
   * 更新 Slash Command 配置。
   * @param id 命令主键。
   * @param payload 命令配置。
   */
  static async updateGovernanceSlashCommand(
    id: string | number,
    payload: AdminGovernanceSlashCommand,
  ): Promise<AdminGovernanceSlashCommand> {
    return this.request<AdminGovernanceSlashCommand>(
      `/api/admin/governance/slash-commands/${encodeURIComponent(String(id))}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
    );
  }

  /**
   * 删除 Slash Command 配置。
   * @param id 命令主键。
   */
  static async deleteGovernanceSlashCommand(id: string | number): Promise<void> {
    await this.request<void>(`/api/admin/governance/slash-commands/${encodeURIComponent(String(id))}`, {
      method: 'DELETE',
    });
  }

  private static async request<T>(path: string, init?: RequestInit): Promise<T> {
    const token = AuthStorage.getSession()?.token ?? '';
    const headers = new Headers(init?.headers ?? {});
    headers.set('satoken', token);
    const hasMultipartBody = typeof FormData !== 'undefined' && init?.body instanceof FormData;
    if (!hasMultipartBody && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
    }
    const response = await fetch(path, {
      ...init,
      headers,
    });
    const envelope = await ApiResponseParser.parseEnvelope<T>(response, AdminErrorMessages.API_REQUEST_FAILED);
    if (isUnauthorizedResponse(response, envelope)) {
      // 步骤：统一派发会话失效事件，让认证层集中处理“清会话 + 提示 + 跳登录页”。
      publishAdminAuthExpired(envelope.message || AdminErrorMessages.AUTH_SESSION_EXPIRED);
    }
    ApiResponseParser.assertSuccess(response, envelope, AdminErrorMessages.API_REQUEST_FAILED);
    return envelope.data;
  }
}

/**
 * 判断当前响应是否为登录失效语义。
 * @param response Fetch 响应对象。
 * @param envelope 统一响应包裹结构。
 * @returns true 表示应按未登录处理。
 */
function isUnauthorizedResponse<T>(
  response: Response,
  envelope: { code?: string; message?: string; success?: boolean },
): boolean {
  if (response.status === 401) {
    return true;
  }
  const normalizedCode = String(envelope.code || '').toUpperCase();
  if (normalizedCode === 'UNAUTHORIZED' || normalizedCode === 'NOT_LOGIN') {
    return true;
  }
  const normalizedMessage = String(envelope.message || '');
  return normalizedMessage.includes('未登录') || normalizedMessage.includes('登录已失效');
}

function normalizeLegacyMapping(input: unknown): AdminQueryTermMapping {
  const item = (input ?? {}) as Record<string, unknown>;
  const matchTypeValue = Number(item.matchType ?? convertLegacyMatchType(item.mappingType));
  const priorityValue = Number(item.priority ?? item.sortNo ?? 0);
  return {
    id: (item.id as string | number | undefined) ?? undefined,
    sourceTerm: String(item.sourceTerm ?? ''),
    targetTerm: String(item.targetTerm ?? ''),
    matchType: Number.isFinite(matchTypeValue) && matchTypeValue > 0 ? matchTypeValue : 1,
    priority: Number.isFinite(priorityValue) ? priorityValue : 0,
    enabled: toBooleanEnabled(item.enabled),
    remark: (item.remark as string | null | undefined) ?? null,
    createTime: (item.createTime as string | undefined) ?? (item.createdAt as string | undefined),
    updateTime: (item.updateTime as string | undefined) ?? (item.updatedAt as string | undefined),
  };
}

function convertLegacyMatchType(mappingType: unknown): number {
  const normalized = String(mappingType ?? '').toLowerCase();
  if (normalized === 'prefix') {
    return 2;
  }
  if (normalized === 'regex') {
    return 3;
  }
  if (normalized === 'word') {
    return 4;
  }
  return 1;
}

function toBooleanEnabled(enabled: unknown): boolean {
  if (typeof enabled === 'boolean') {
    return enabled;
  }
  if (typeof enabled === 'number') {
    return enabled === 1;
  }
  if (typeof enabled === 'string') {
    const normalized = enabled.toLowerCase();
    return normalized === '1' || normalized === 'true';
  }
  return true;
}
