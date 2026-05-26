/**
 * 统一定义聊天工作区前端状态模型，保证三栏面板和数据请求共享同一份类型约束。
 */
export interface ConversationItem {
  id: string;
  title: string;
  status: string;
  /**
   * 仅供前端本地侧栏排序使用，表示该会话已被当前用户置顶。
   * 关键约束：该字段不依赖后端持久化，刷新后由本地快照回填。
   */
  isPinned?: boolean;
  lastMessageAt?: string;
  lastRunId?: string;
  activeTaskId?: string;
  activeTaskStatus?: string;
  lastTaskId?: string;
  lastTaskStatus?: string;
  lastTaskFinishedAt?: string;
  hasUnreadTaskCompletion?: boolean;
  workspaceId?: string | null;
  workspaceType?: 'CLOUD' | 'LOCAL' | string;
}

/**
 * 描述聊天消息回放与流式拼接需要的字段。
 */
export interface ChatMessageItem {
  id: string;
  conversationId: string;
  runId?: string;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  skillCodes?: string[];
  attachments?: ChatAttachmentItem[];
  thinkingContent?: string;
  thinkingDuration?: number;
  mcpCalls?: McpCallItem[];
  processCards?: ProcessCardItem[];
  searchProgress?: MessageSearchProgress;
  status: string;
  provider?: string;
  model?: string;
  errorMessage?: string;
  createdAt?: string;
  userVote?: number | null; // 当前用户对该消息的投票值（1 点赞，-1 点踩，null/undefined 表示未投票）
}

/**
 * 描述助手消息中的联网搜索进度，支持在流式阶段实时回显已检索站点。
 */
export interface MessageSearchProgress {
  status: 'running' | 'completed' | 'cancelled' | 'error';
  items: MessageSearchProgressItem[];
}

/**
 * 描述单条联网检索结果在消息内进度面板中的展示字段。
 */
export interface MessageSearchProgressItem {
  id: string;
  title: string;
  url?: string;
  siteName?: string;
}

/**
 * 描述主消息区中的单条过程卡片细节项，用于参数与结果的折叠展示。
 */
export interface ProcessCardDetailItem {
  label: string;
  content: string;
}

/**
 * 描述主消息区中统一的过程链路节点。
 */
export interface ProcessCardItem {
  id: string;
  type: 'analysis' | 'tool_call' | 'tool_result' | 'synthesis';
  title: string;
  summary: string;
  status: 'running' | 'completed' | 'error' | 'cancelled';
  toolId?: string;
  displayName?: string;
  details?: ProcessCardDetailItem[];
}

/**
 * 描述聊天附件在“输入中/消息中”两个阶段都可复用的结构。
 */
export interface ChatAttachmentItem {
  id: string;
  conversationId?: string;
  messageId?: string;
  attachmentType: 'image' | 'file';
  fileName: string;
  fileExt?: string;
  mimeType?: string;
  fileSize: number;
  previewUrl?: string;
  contentSummary?: string;
  status: string;
  createdAt?: string;
}

/**
 * 描述输入区待发送附件的前端运行时状态。
 */
export interface PendingAttachmentItem {
  clientId: string;
  file: File;
  previewUrl: string;
  uploadStatus: 'pending' | 'uploading' | 'uploaded' | 'failed';
  uploadError?: string;
  attachment?: ChatAttachmentItem;
}

/**
 * 描述右栏执行步骤项目。
 */
export interface ExecutionStepItem {
  id: string;
  runId: string;
  stepType: string;
  stepTitle: string;
  stepStatus: string;
  sequenceNo: number;
  content?: string;
}

/**
 * 描述右栏来源项目。
 */
export interface ReferenceItem {
  id: string;
  runId: string;
  messageId?: string;
  conversationId: string;
  sourceType?: string;
  title: string;
  url?: string;
  siteName?: string;
  snippet?: string;
  rankNo?: number;
}

/**
 * 描述右栏产物项目。
 */
export interface ArtifactItem {
  id: string;
  runId: string;
  messageId?: string;
  conversationId: string;
  artifactType: string;
  name: string;
  mimeType?: string;
  storagePath: string;
  contentPreview?: string;
}

/**
 * 描述首页欢迎区示例问题。
 */
export interface SampleQuestionItem {
  id: string;
  questionText: string;
  category?: string;
}

/**
 * 描述用户可选技能项。
 */
export interface ChatSkillItem {
  id: string;
  skillCode: string;
  displayName: string;
  description?: string;
  category?: string;
  sourceType?: string;
  enabled?: number;
  sortNo?: number;
}

/**
 * 描述会话当前运行绑定的技能项。
 */
export interface CurrentSkillItem {
  id: string;
  skillCode: string;
  displayName: string;
  description?: string;
  category?: string;
}

/**
 * 描述用户可选专家项。
 */
export interface ChatExpertItem {
  id: string;
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

/**
 * 描述会话当前运行绑定的专家项。
 */
export interface CurrentExpertItem {
  id: string;
  expertCode: string;
  displayName: string;
  description?: string;
  category?: string;
  presetQuestion?: string;
}

/**
 * 描述用户可选 MCP 项。
 */
export interface McpItem {
  id: string;
  mcpCode: string;
  displayName: string;
  description?: string;
  category?: string;
  /**
   * 管理端配置启用状态：1 启用，0 禁用。
   * 约束：用户端若收到 0，必须禁止用户手动开启该 MCP。
   */
  enabled?: number;
  /**
   * MCP 当前是否可用（例如执行器已注册且健康探测通过）。
   * 约束：显式为 false 时，前端只能展示为不可选状态。
   */
  available?: boolean;
}

/**
 * 描述会话当前运行绑定的 MCP 项。
 */
export interface CurrentMcpItem {
  id: string;
  mcpCode: string;
  displayName: string;
  description?: string;
  category?: string;
}

/**
 * 描述一次 MCP 调用信息，供消息区折叠面板展示。
 */
export interface McpCallItem {
  /**
   * 单次 MCP 调用标识，用于把 start/complete 两阶段事件合并为同一条记录。
   */
  callId?: string;
  toolId: string;
  displayName: string;
  input: string;
  content: string;
  metadata?: Record<string, unknown>;
  /**
   * 调用阶段，来自后端 SSE 事件。
   */
  phase?: 'start' | 'progress' | 'complete' | 'error';
  /**
   * 调用状态，供前端面板直接驱动“调用中/完成/异常”。
   */
  status?: 'running' | 'completed' | 'error';
  /**
   * 工具阶段进度标识与文案，用于真实进度展示。
   */
  progressStage?: string;
  progressText?: string;
  progressDetail?: Record<string, unknown>;
  /**
   * MCP 调用参数，优先展示真实结构化入参。
   */
  params?: Record<string, unknown> | string;
  /**
   * MCP 原始返回结果，优先展示工具原始输出。
   */
  rawResult?: unknown;
  /**
   * 调用完成态返回的元数据。
   */
  resultMetadata?: Record<string, unknown>;
  startedAt?: string;
  finishedAt?: string;
  errorMessage?: string;
}

/**
 * 记录当前 SSE 会话的流式上下文。
 */
export interface ActiveStreamState {
  conversationId: string;
  activeMessageId: string;
}

/**
 * 统一描述后端 `meta` 事件目前会返回的会话上下文。
 */
export interface StreamMetaPayload {
  conversationId: string;
  deepThinking?: boolean;
  taskId?: number;
  traceId?: string;
}

/**
 * 描述当前流式会话的排队状态，用于前端展示排队提示条。
 */
export interface StreamQueueState {
  position: number;
  message: string;
}

/**
 * 描述左侧工作空间树中的单个分组项。
 */
export interface WorkspaceConversationGroup {
  partitionKey: string;
  groupType?: 'workspace' | 'history';
  workspacePath: string | null;
  workspaceLabel: string;
  runtimeTarget: 'cloud' | 'local';
  lastOpenedAt: number;
  activeConversationId: string | null;
  /**
   * 当前分组内被用户置顶的会话标识列表，按显示优先级排序。
   */
  pinnedConversationIds?: string[];
  conversations: ConversationItem[];
}

/**
 * 统一描述会话动作所需的分组上下文，确保非当前分组菜单动作仍能落到正确分区。
 */
export type ConversationActionContext = WorkspaceConversationSelectionContext;

/**
 * 描述用户端支持的对话导出格式。
 */
export type ConversationExportFormat = 'word' | 'pdf' | 'txt' | 'json' | 'markdown';

/**
 * 描述分享会话时的可选范围，messageIds 为空时表示分享整段会话。
 */
export interface ShareConversationOptions {
  messageIds?: string[];
}

/**
 * 描述重新生成助手消息时的定位参数，避免前端只能重试会话末尾。
 */
export interface RegenerateConversationOptions {
  assistantMessageId?: string;
}

/**
 * 公开分享页使用的只读回放数据。
 */
export interface SharedConversationPayload {
  conversation: ConversationItem;
  messages: ChatMessageItem[];
}

/**
 * 描述侧边栏会话选择时携带的分组上下文，确保会话总是在所属空间内打开。
 */
export interface WorkspaceConversationSelectionContext {
  partitionKey: string;
  runtimeTarget: 'cloud' | 'local';
  workspacePath: string | null;
  groupType?: 'workspace' | 'history';
}

/**
 * 描述侧边栏“新建会话”时携带的空间上下文，用于在创建前先切到目标环境和工作空间。
 */
export type WorkspaceConversationCreateContext = WorkspaceConversationSelectionContext;

/**
 * 统一描述聊天工作区对页面和侧边栏暴露的状态与动作。
 */
export interface ChatWorkspaceController {
  runtimeTargets: Array<'cloud' | 'local'>;
  activeRuntimeTarget: 'cloud' | 'local';
  workspaceGroups: WorkspaceConversationGroup[];
  activeWorkspacePartitionKey: string | null;
  workspacePath: string | null;
  workspaceLabel: string;
  workspaceRuntimeTarget: 'cloud' | 'local';
  conversations: ConversationItem[];
  activeConversationId: string | null;
  messages: ChatMessageItem[];
  executionSteps: ExecutionStepItem[];
  references: ReferenceItem[];
  artifacts: ArtifactItem[];
  sampleQuestions: SampleQuestionItem[];
  availableExperts: ChatExpertItem[];
  selectedExpertCode: string | null;
  currentExperts: CurrentExpertItem[];
  availableSkills: ChatSkillItem[];
  selectedSkillCodes: string[];
  currentSkills: CurrentSkillItem[];
  availableMcps: McpItem[];
  selectedMcpCodes: string[];
  currentMcps: CurrentMcpItem[];
  mcpConnected: boolean;
  isStreaming: boolean;
  isCancelling: boolean;
  deepThinkingEnabled: boolean;
  streamQueueState: StreamQueueState | null;
  streamError: string;
  inputValue: string;
  pendingAttachments: PendingAttachmentItem[];
  isBootstrapping: boolean;
  setInputValue: (value: string) => void;
  setSelectedExpertCode: (expertCode: string | null) => void;
  addPendingAttachments: (files: File[]) => Promise<void>;
  removePendingAttachment: (clientId: string) => void;
  clearPendingAttachments: () => void;
  setDeepThinkingEnabled: (value: boolean) => void;
  setSelectedSkillCodes: (skillCodes: string[] | ((previous: string[]) => string[])) => void;
  setSelectedMcpCodes: (mcpCodes: string[] | ((previous: string[]) => string[])) => void;
  setMcpConnected: (value: boolean) => void;
  setActiveRuntimeTarget: (runtimeTarget: 'cloud' | 'local') => Promise<void>;
  pickRepositoryDirectory: () => Promise<void>;
  setActiveWorkspacePath: (workspacePath: string | null) => Promise<void>;
  submitMessage: () => Promise<void>;
  cancelCurrentStream: () => Promise<void>;
  selectConversation: (
    conversationId: string,
    sourceConversations?: ConversationItem[],
  ) => Promise<void>;
  selectConversationInWorkspace: (
    conversationId: string,
    selectionContext: WorkspaceConversationSelectionContext,
  ) => Promise<void>;
  startNewConversation: (
    createContext?: WorkspaceConversationCreateContext,
  ) => Promise<void>;
  renameConversation: (
    conversationId: string,
    title: string,
    actionContext?: ConversationActionContext,
  ) => Promise<void>;
  deleteConversation: (
    conversationId: string,
    actionContext?: ConversationActionContext,
  ) => Promise<void>;
  shareConversation: (
    conversationId: string,
    options?: ShareConversationOptions,
  ) => Promise<string>;
  regenerateConversation: (
    conversationId: string,
    options?: RegenerateConversationOptions,
  ) => Promise<void>;
  toggleConversationPin: (
    conversationId: string,
    actionContext: ConversationActionContext,
  ) => Promise<boolean>;
  exportConversation: (
    conversationId: string,
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  exportConversations: (
    conversationIds: string[],
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  deleteConversations: (
    conversationIds: string[],
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  renameDialog: {
    conversationId: string | null;
    initialTitle: string;
    actionContext?: ConversationActionContext;
    isOpen: boolean;
    open: (
      conversationId: string,
      initialTitle: string,
      actionContext?: ConversationActionContext,
    ) => void;
    close: () => void;
  };
  deleteDialog: {
    conversationId: string | null;
    title: string;
    actionContext?: ConversationActionContext;
    isOpen: boolean;
    open: (
      conversationId: string,
      title: string,
      actionContext?: ConversationActionContext,
    ) => void;
    close: () => void;
  };
}

/**
 * 聊天工作区可选的鉴权失效回调。
 */
export interface UseChatWorkspaceOptions {
  onUnauthorized?: () => void;
  hostContext?: import('../../host/types').HostContext | null;
  pickRepositoryDirectory?: () => Promise<string | null>;
  bindWorkspacePath?: (
    workspacePath: string,
  ) => Promise<WorkspaceBindingSyncResult | null | void>;
}

/**
 * 描述桌面端目录绑定后返回的工作空间上下文，供前端立即同步 workspaceId。
 */
export interface WorkspaceBindingSyncResult {
  workspaceId?: string | null;
  workspaceName?: string | null;
  repositoryPath?: string | null;
}
