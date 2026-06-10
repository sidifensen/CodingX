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
  /**
   * 后端持久化的任务完成提醒已读状态；云端会话刷新后的提醒圆点以该字段为准。
   */
  taskCompletionRead?: boolean;
  hasUnreadTaskCompletion?: boolean;
  workspaceId?: string | null;
  workspaceType?: 'CLOUD' | 'LOCAL' | string;
}

/**
 * 描述后端 cursor 分页返回的下一页起点；会话分页使用 updatedAt，消息分页使用 createdAt。
 */
export interface CursorPageCursor {
  cursorPinned?: boolean | null;
  cursorUpdatedAt?: string | null;
  cursorCreatedAt?: string | null;
  cursorId?: string | null;
}

/**
 * 描述后端统一 cursor 分页响应，items 已由 API 层归一化为前端可消费结构。
 */
export interface CursorPage<T> {
  items: T[];
  hasMore: boolean;
  nextCursor: CursorPageCursor | null;
}

/**
 * 会话列表分页查询参数，Sidebar 加载更多时会带上上一页游标。
 */
export interface ConversationPageQuery {
  workspaceId?: string | null;
  pageSize?: number;
  cursor?: CursorPageCursor | null;
}

/**
 * 消息列表分页查询参数，加载更旧消息时会带上当前最旧消息游标。
 */
export interface MessagePageQuery {
  pageSize?: number;
  before?: CursorPageCursor | null;
}

export type ConversationPage = CursorPage<ConversationItem>;
export type MessagePage = CursorPage<ChatMessageItem>;

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
  /**
   * 助手消息内按 SSE 到达顺序记录的正文与过程片段；用于 Codex 风格穿插展示。
   */
  timelineItems?: MessageTimelineItem[];
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
 * 描述单个文件的 unified diff，供消息内编辑列表和右侧代码审查栏共同展示。
 */
export interface FileDiffItem {
  /**
   * 展示用文件路径，优先使用工作区相对路径。
   */
  path: string;
  /**
   * 修改前路径；重命名或删除场景可能与 path 不一致。
   */
  oldPath?: string;
  /**
   * 修改后路径；新增或重命名场景用于定位最终文件。
   */
  newPath?: string;
  /**
   * 文件状态，例如 added、modified、deleted、renamed。
   */
  status?: string;
  /**
   * 新增行数，来自后端 diffSummary/fileDiffs 统计。
   */
  additions: number;
  /**
   * 删除行数，来自后端 diffSummary/fileDiffs 统计。
   */
  deletions: number;
  /**
   * 标准 unified diff 文本，用于弹窗和侧栏按行高亮。
   */
  diff: string;
}

/**
 * 描述一组文件差异的汇总数据，避免 UI 每次渲染都重新遍历 diff。
 */
export interface DiffSummary {
  filesChanged: number;
  additions: number;
  deletions: number;
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
  /**
   * 标记特殊展示形态；ReAct 过程需要按时间线直接露出，不能折叠进工具汇总。
   */
  presentation?: 'react';
  toolId?: string;
  displayName?: string;
  details?: ProcessCardDetailItem[];
  /**
   * 工具编辑文件时产生的结构化差异；实时过程和历史回放都通过该字段恢复。
   */
  fileDiffs?: FileDiffItem[];
  /**
   * 文件差异汇总，用于快速展示“已编辑 N 个文件 +x -y”。
   */
  diffSummary?: DiffSummary;
}

/**
 * 描述单条助手消息内的混合时间线片段，保留正文 token 与工具过程的相对顺序。
 */
export type MessageTimelineItem =
  | {
      id: string;
      type: 'content';
      content: string;
    }
  | {
      id: string;
      type: 'process';
      card: ProcessCardItem;
    };

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
  /**
   * 后端步骤附加元数据；历史工具回放优先从这里恢复工具名、入参与原始结果。
   */
  metadataJson?: string;
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
 * 描述用户输入区可选择的 Slash Command 命令。
 */
export interface SlashCommandItem {
  id: string;
  commandCode: string;
  displayName: string;
  description?: string;
  commandType?: string;
  promptTemplate?: string;
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
  /**
   * 从 resultMetadata.fileDiffs 归一化得到的文件差异，供消息区和侧栏复用。
   */
  fileDiffs?: FileDiffItem[];
  /**
   * 从 resultMetadata.diffSummary 归一化得到的差异汇总。
   */
  diffSummary?: DiffSummary;
  /**
   * 后端基于工具调用公开生成的 ReAct 摘要，不承载模型私有思维链。
   */
  reactThought?: string;
  reactAction?: string;
  reactObservation?: string;
  startedAt?: string;
  finishedAt?: string;
  errorMessage?: string;
}

/**
 * 长期记忆状态：ACTIVE 参与后续模型上下文回注，REJECTED 表示已停用。
 */
export type LongTermMemoryStatus = 'ACTIVE' | 'REJECTED';

/**
 * 描述用户可见的长期记忆，ID 在前端统一用字符串避免 Long 精度问题。
 */
export interface LongTermMemoryItem {
  id: string;
  memoryScope: 'USER' | 'PROJECT' | string;
  userId?: string | null;
  workspaceId?: string | null;
  workspaceName?: string | null;
  memoryKey?: string | null;
  content: string;
  status: LongTermMemoryStatus | string;
  sourceType?: string | null;
  sourceConversationId?: string | null;
  sourceMessageId?: string | null;
  keywordJson?: string | null;
  confidenceScore?: number | string | null;
  lastUsedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
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
  hasMore?: boolean;
  isLoadingMore?: boolean;
}

/**
 * 描述后端返回的当前用户工作区库存项，供侧栏补齐没有会话的空工作区。
 */
export interface WorkspaceInventoryItem {
  id: string;
  name: string;
  runtimeTarget: 'cloud' | 'local';
  workingDirectory: string | null;
  repositoryUrl?: string | null;
  branchName?: string | null;
}

/**
 * 描述真实目标模式中的单个目标步骤；状态来自后端 goal 快照，不再由执行过程步骤推导。
 */
export interface ChatGoalStepItem {
  id: string;
  goalId?: string;
  stepKey?: string;
  title: string;
  status: string;
  sortNo: number;
  detail?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  updatedAt?: string | null;
}

/**
 * 描述当前会话的后端 active goal；仅存在真实目标时页面才展示目标进度浮窗。
 */
export interface ChatGoalItem {
  id: string;
  conversationId: string;
  goalKey?: string;
  title: string;
  description?: string | null;
  status: string;
  progressSummary?: string | null;
  createdRunId?: string | null;
  updatedRunId?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  completedAt?: string | null;
  steps: ChatGoalStepItem[];
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
  workspaceId: string | null;
  workspaceLabel: string;
  workspaceRuntimeTarget: 'cloud' | 'local';
  activeMemoryCount: number;
  longTermMemories: LongTermMemoryItem[];
  isMemoryLoading: boolean;
  conversations: ConversationItem[];
  activeConversationId: string | null;
  messages: ChatMessageItem[];
  hasMoreMessagesBefore: boolean;
  isLoadingOlderMessages: boolean;
  executionSteps: ExecutionStepItem[];
  activeGoal: ChatGoalItem | null;
  references: ReferenceItem[];
  artifacts: ArtifactItem[];
  sampleQuestions: SampleQuestionItem[];
  availableExperts: ChatExpertItem[];
  selectedExpertCode: string | null;
  currentExperts: CurrentExpertItem[];
  availableSkills: ChatSkillItem[];
  selectedSkillCodes: string[];
  currentSkills: CurrentSkillItem[];
  availableSlashCommands: SlashCommandItem[];
  selectedSlashCommand: SlashCommandItem | null;
  availableMcps: McpItem[];
  selectedMcpCodes: string[];
  currentMcps: CurrentMcpItem[];
  mcpConnected: boolean;
  isStreaming: boolean;
  isCancelling: boolean;
  deepThinkingEnabled: boolean;
  /**
   * 桌面目标模式开关；开启后后续流请求会进入目标跟进语境。
   */
  goalModeEnabled: boolean;
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
  setGoalModeEnabled: (value: boolean) => void;
  setSelectedSkillCodes: (skillCodes: string[] | ((previous: string[]) => string[])) => void;
  setSelectedSlashCommand: (command: SlashCommandItem | null) => void;
  setSelectedMcpCodes: (mcpCodes: string[] | ((previous: string[]) => string[])) => void;
  setMcpConnected: (value: boolean) => void;
  setActiveRuntimeTarget: (runtimeTarget: 'cloud' | 'local') => Promise<void>;
  pickRepositoryDirectory: () => Promise<void>;
  setActiveWorkspacePath: (workspacePath: string | null) => Promise<void>;
  refreshLongTermMemories: () => Promise<void>;
  updateLongTermMemoryStatus: (
    memoryId: string,
    status: LongTermMemoryStatus,
  ) => Promise<void>;
  /**
   * 提交当前输入；歧义引导按钮等快捷入口可传入显式文本，避免等待 React 输入状态刷新。
   */
  submitMessage: (overrideInputValue?: string) => Promise<void>;
  cancelCurrentStream: () => Promise<void>;
  selectConversation: (
    conversationId: string,
    sourceConversations?: ConversationItem[],
  ) => Promise<void>;
  selectConversationInWorkspace: (
    conversationId: string,
    selectionContext: WorkspaceConversationSelectionContext,
  ) => Promise<void>;
  loadMoreConversations: (selectionContext: WorkspaceConversationSelectionContext) => Promise<void>;
  loadOlderMessages: () => Promise<void>;
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
  /**
   * 删除当前会话中的指定消息；用于消息级选择删除与编辑重发前清理旧上下文。
   */
  deleteConversationMessages: (
    conversationId: string,
    messageIds: string[],
  ) => Promise<void>;
  shareConversation: (
    conversationId: string,
    options?: ShareConversationOptions,
  ) => Promise<string>;
  regenerateConversation: (
    conversationId: string,
    options?: RegenerateConversationOptions,
  ) => Promise<void>;
  /**
   * 从一条用户消息重新发送；旧消息段落应被替换，避免编辑后的问题与旧回复同时进入上下文。
   */
  resendUserMessage: (
    messageId: string,
    content: string,
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
  /**
   * 控制是否启动聊天首屏重型初始化；独立功能页只需要稳定空状态，不需要预取聊天数据。
   */
  shouldBootstrap?: boolean;
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
  activeMemoryCount?: number | null;
}
