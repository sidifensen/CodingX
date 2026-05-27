import { useEffect, useMemo, useRef, useState } from 'react';
import { AuthStorage } from '../../utils/authStorage';
import { UserErrorMessages } from '../../constants/errorMessages';
import { ChatApi } from './chatApi';
import { extractSseEvents } from './sse';
import {
  ActiveStreamState,
  ArtifactItem,
  ChatAttachmentItem,
  ChatExpertItem,
  ChatSkillItem,
  CurrentExpertItem,
  CurrentMcpItem,
  CurrentSkillItem,
  ChatMessageItem,
  ChatWorkspaceController,
  ConversationActionContext,
  ConversationExportFormat,
  ConversationItem,
  ExecutionStepItem,
  McpCallItem,
  McpItem,
  MessageTimelineItem,
  MessageSearchProgress,
  MessageSearchProgressItem,
  PendingAttachmentItem,
  ProcessCardItem,
  ReferenceItem,
  RegenerateConversationOptions,
  SampleQuestionItem,
  ShareConversationOptions,
  StreamQueueState,
  UseChatWorkspaceOptions,
  WorkspaceConversationCreateContext,
  WorkspaceConversationGroup,
} from './types';
import {
  buildWorkspacePartitionKey,
  filterUnassignedConversations,
  isWorkspaceHistoryPartitionKey,
  getWorkspaceLabel,
  findWorkspacePartitionByConversationId,
  listWorkspaceGroups,
  markConversationTaskCompletionSeen,
  markWorkspaceConversationOwnership,
  readWorkspaceSnapshot,
  saveConversationRecordToWorkspace,
  upsertWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from './localConversationStorage';

const DEFAULT_CLOUD_WORKSPACE_LABEL = '云端历史记录';
const DEFAULT_LOCAL_WORKSPACE_LABEL = '本地历史记录';
const CONVERSATION_ID_QUERY_KEY = 'conversationId';
const STREAM_QUEUE_BANNER_DELAY_MS = 250;

/**
 * 根据运行环境返回默认工作空间标题，避免本地/云端标签混淆。
 * @param runtimeTarget 运行环境。
 * @returns 默认工作空间标题。
 */
function getDefaultWorkspaceLabel(runtimeTarget: 'cloud' | 'local') {
  return runtimeTarget === 'local' ? DEFAULT_LOCAL_WORKSPACE_LABEL : DEFAULT_CLOUD_WORKSPACE_LABEL;
}

/**
 * 判断会话是否明确归属本地工作空间。
 * 业务约束：Web / 云端历史页只应展示云端会话，显式标记为 LOCAL 的记录必须在前端侧拦截。
 * @param conversation 会话项。
 * @returns 是否为本地会话。
 */
function isLocalWorkspaceConversation(conversation: ConversationItem) {
  return String(conversation.workspaceType ?? '').trim().toUpperCase() === 'LOCAL';
}

/**
 * 按当前运行环境裁剪会话可见范围，避免不同运行环境的历史记录互相串线。
 * @param conversations 原始会话列表。
 * @param runtimeTarget 当前运行环境。
 * @returns 可见会话列表。
 */
function filterWorkspaceConversationsByRuntimeTarget(
  conversations: ConversationItem[],
  runtimeTarget: 'cloud' | 'local',
) {
  if (runtimeTarget !== 'cloud') {
    return conversations;
  }
  return conversations.filter((conversation) => !isLocalWorkspaceConversation(conversation));
}

/**
 * 按会话 ID 去重合并列表，保留已有顺序并将新增项追加到末尾。
 * @param primary 主列表。
 * @param secondary 待合并列表。
 * @returns 合并后的会话列表。
 */
function mergeConversationListById(
  primary: ConversationItem[],
  secondary: ConversationItem[],
  options?: { mergeExisting?: boolean },
) {
  const mergedConversations = [...primary];
  const conversationIndexById = new Map(
    primary.map((conversation, index) => [conversation.id, index]),
  );
  for (const conversation of secondary) {
    const existingIndex = conversationIndexById.get(conversation.id);
    if (existingIndex != null) {
      if (options?.mergeExisting) {
        mergedConversations[existingIndex] = mergeConversationFromAuthoritativeSource(
          mergedConversations[existingIndex],
          conversation,
        );
      }
      continue;
    }
    conversationIndexById.set(conversation.id, mergedConversations.length);
    mergedConversations.push(conversation);
  }
  return mergedConversations;
}

/**
 * 判断权威会话数据是否仍声明存在运行中的后台任务。
 * @param conversation 权威会话数据。
 * @returns 是否存在运行中任务。
 */
function hasRunningTaskProjection(conversation: ConversationItem) {
  return String(conversation.activeTaskStatus ?? '').trim().toUpperCase() === 'RUNNING';
}

/**
 * 使用权威会话数据覆盖本地快照，同时清理权威终态下已经过期的运行中字段。
 * @param existingConversation 本地已有会话。
 * @param authoritativeConversation 后到权威会话。
 * @returns 合并后的会话。
 */
function mergeConversationFromAuthoritativeSource(
  existingConversation: ConversationItem,
  authoritativeConversation: ConversationItem,
): ConversationItem {
  const mergedConversation = {
    ...existingConversation,
    ...authoritativeConversation,
  };
  if (hasRunningTaskProjection(authoritativeConversation)) {
    return mergedConversation;
  }
  // 权威列表未再声明运行中任务时，必须清理本地旧快照中的 active 投影，避免侧栏 spinner 残留。
  return {
    ...mergedConversation,
    activeTaskId: undefined,
    activeTaskStatus: undefined,
  };
}

/**
 * 使用分区置顶列表重新装饰会话顺序，确保内存态与本地快照读取行为保持一致。
 * @param conversations 原始会话列表。
 * @param pinnedConversationIds 分区置顶列表。
 * @returns 已按置顶规则排序的会话列表。
 */
function applyPinnedConversationOrder(
  conversations: ConversationItem[],
  pinnedConversationIds: string[],
) {
  const normalizedPinnedConversationIds = Array.from(
    new Set(
      pinnedConversationIds
        .map((conversationId) => String(conversationId ?? '').trim())
        .filter(Boolean),
    ),
  );
  const pinnedConversationMap = new Map(
    conversations.map((conversation) => [
      conversation.id,
      {
        ...conversation,
        isPinned: normalizedPinnedConversationIds.includes(conversation.id),
      },
    ]),
  );
  const pinnedConversations = normalizedPinnedConversationIds
    .map((conversationId) => pinnedConversationMap.get(conversationId))
    .filter((conversation): conversation is ConversationItem => conversation != null);
  const unpinnedConversations = conversations
    .filter((conversation) => !normalizedPinnedConversationIds.includes(conversation.id))
    .map((conversation) => ({
      ...conversation,
      isPinned: false,
    }));
  return [...pinnedConversations, ...unpinnedConversations];
}

/**
 * 过滤置顶列表中已不存在的会话标识，避免删除或远端收敛后残留脏数据。
 * @param pinnedConversationIds 原始置顶列表。
 * @param conversations 当前会话列表。
 * @returns 有效置顶列表。
 */
function sanitizePinnedConversationIds(
  pinnedConversationIds: string[],
  conversations: ConversationItem[],
) {
  const availableConversationIds = new Set(conversations.map((conversation) => conversation.id));
  return pinnedConversationIds.filter((conversationId) => availableConversationIds.has(conversationId));
}

/**
 * 将会话插入到列表头部；若已存在则提升到头部并合并字段。
 * @param conversations 原始会话列表。
 * @param nextConversation 目标会话。
 * @returns 新会话列表。
 */
function upsertConversationToTop(
  conversations: ConversationItem[],
  nextConversation: ConversationItem,
) {
  const existingConversation = conversations.find(
    (conversation) => conversation.id === nextConversation.id,
  );
  const normalizedConversation = existingConversation
    ? {
        ...existingConversation,
        ...nextConversation,
      }
    : nextConversation;
  return [
    normalizedConversation,
    ...conversations.filter((conversation) => conversation.id !== normalizedConversation.id),
  ];
}

/**
 * 生成前端本地会话标识，避免新建本地对话在 SSE meta 返回前使用固定 pending 键互相覆盖。
 * @returns 本地临时会话标识。
 */
function createLocalConversationId() {
  return `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * 判断后端任务状态是否仍处于执行中，驱动侧栏 spinner 展示。
 * @param conversation 会话项。
 * @returns 是否运行中。
 */
function isConversationTaskRunning(conversation: ConversationItem) {
  return String(conversation.activeTaskStatus ?? '').trim().toUpperCase() === 'RUNNING';
}

/**
 * 根据本地已读时间戳计算会话是否有新的后台任务完成提醒。
 * @param conversation 会话项。
 * @param seenMap 本地已读任务完成时间。
 * @param activeConversationId 当前打开会话。
 * @returns 合并后的会话项。
 */
function applyTaskCompletionReminder(
  conversation: ConversationItem,
  seenMap: Record<string, string>,
  activeConversationId: string | null,
): ConversationItem {
  if (isConversationTaskRunning(conversation)) {
    return {
      ...conversation,
      hasUnreadTaskCompletion: false,
    };
  }
  const finishedAt = conversation.lastTaskFinishedAt;
  const isTerminalTask = String(conversation.lastTaskStatus ?? '').trim().length > 0;
  if (typeof conversation.taskCompletionRead === 'boolean') {
    return {
      ...conversation,
      hasUnreadTaskCompletion:
        isTerminalTask &&
        Boolean(finishedAt) &&
        conversation.id !== activeConversationId &&
        !conversation.taskCompletionRead,
    };
  }
  const seenFinishedAt = seenMap[conversation.id];
  return {
    ...conversation,
    hasUnreadTaskCompletion:
      isTerminalTask &&
      Boolean(finishedAt) &&
      conversation.id !== activeConversationId &&
      finishedAt !== seenFinishedAt,
  };
}

/**
 * 批量合并任务完成提醒状态，确保刷新后侧栏能恢复圆点。
 * @param conversations 会话列表。
 * @param seenMap 本地已读任务完成时间。
 * @param activeConversationId 当前打开会话。
 * @returns 合并后的会话列表。
 */
function applyTaskCompletionReminders(
  conversations: ConversationItem[],
  seenMap: Record<string, string>,
  activeConversationId: string | null,
) {
  return conversations.map((conversation) =>
    applyTaskCompletionReminder(conversation, seenMap, activeConversationId),
  );
}

/**
 * 当前打开会话的任务完成即视为已读，避免用户切走或刷新后又看到完成圆点。
 * @param conversations 会话列表。
 * @param seenMap 本地已读任务完成时间。
 * @param activeConversationId 当前打开会话。
 * @returns 合并后的已读任务完成时间。
 */
function markActiveTaskCompletionSeen(
  conversations: ConversationItem[],
  seenMap: Record<string, string>,
  activeConversationId: string | null,
) {
  if (!activeConversationId) {
    return seenMap;
  }
  const activeConversation = conversations.find((conversation) => conversation.id === activeConversationId);
  if (
    !activeConversation ||
    isConversationTaskRunning(activeConversation) ||
    !activeConversation.lastTaskFinishedAt ||
    String(activeConversation.lastTaskStatus ?? '').trim().length === 0 ||
    typeof activeConversation.taskCompletionRead === 'boolean' ||
    seenMap[activeConversation.id] === activeConversation.lastTaskFinishedAt
  ) {
    return seenMap;
  }
  return {
    ...seenMap,
    [activeConversation.id]: activeConversation.lastTaskFinishedAt,
  };
}

type WorkspaceGroupQueryMode = 'runtime-only' | 'all';

/**
 * 统一判断 MCP 是否允许用户在会话中启用。
 * 关键约束：后端显式返回禁用或不可用时，前端必须强制剔除，不允许进入可选列表。
 * @param mcp MCP 配置。
 * @returns 是否可选。
 */
function isSelectableMcp(mcp: McpItem): boolean {
  if (mcp.enabled === 0) {
    return false;
  }
  if (mcp.available === false) {
    return false;
  }
  return true;
}

/**
 * 判断一个工作空间快照是否包含足够的可恢复状态。
 * 仅依赖最近访问时间会把空壳默认分区误判成有效分区，
 * 所以这里必须至少命中会话、回放记录或任务已读映射中的一种。
 * @param snapshot 工作空间快照。
 * @returns 是否存在真实可恢复状态。
 */
function hasMeaningfulWorkspaceSnapshot(
  snapshot: Pick<
    WorkspaceConversationGroup,
    'activeConversationId' | 'conversations' | 'conversationRecords' | 'seenTaskFinishedAtByConversationId' | 'pinnedConversationIds'
  >,
) {
  return (
    String(snapshot.activeConversationId ?? '').trim().length > 0 ||
    (snapshot.conversations?.length ?? 0) > 0 ||
    Object.keys(snapshot.conversationRecords ?? {}).length > 0 ||
    Object.keys(snapshot.seenTaskFinishedAtByConversationId ?? {}).length > 0 ||
    (snapshot.pinnedConversationIds?.length ?? 0) > 0
  );
}

/**
 * 从本地快照里挑出最近访问且包含真实状态的工作空间分区。
 * 这里明确忽略默认空壳分区，避免刷新后把“云端首页”误当成最近工作上下文。
 * @param runtimeTargets 当前允许的运行目标。
 * @param hostBoundRepositoryPath 宿主绑定的本地仓库路径。
 * @param hostWorkspaceId 宿主绑定的本地 workspaceId。
 * @returns 可恢复的分区信息或 null。
 */
function resolveMostRecentWorkspacePartition(
  runtimeTargets: Array<'cloud' | 'local'>,
  hostBoundRepositoryPath: string | null,
  hostWorkspaceId: string | null,
) {
  const normalizedHostBoundRepositoryPath = (hostBoundRepositoryPath ?? '').trim().toLowerCase();
  const candidates = listWorkspaceGroups()
    .filter((group) => runtimeTargets.includes(group.runtimeTarget))
    .filter((group) => hasMeaningfulWorkspaceSnapshot(group))
    .filter((group) => {
      if (group.runtimeTarget !== 'local') {
        return true;
      }
      if (!normalizedHostBoundRepositoryPath) {
        return (group.workspacePath ?? '').trim().length === 0;
      }
      return (group.workspacePath ?? '').trim().toLowerCase() === normalizedHostBoundRepositoryPath;
    })
    .sort((left, right) => {
      if (left.lastOpenedAt !== right.lastOpenedAt) {
        return right.lastOpenedAt - left.lastOpenedAt;
      }
      if (left.runtimeTarget !== right.runtimeTarget) {
        return left.runtimeTarget === 'cloud' ? -1 : 1;
      }
      return left.partitionKey.localeCompare(right.partitionKey, 'zh-Hans-CN');
    });
  if (candidates.length === 0) {
    return null;
  }
  const latest = candidates[0];
  return {
    partitionKey: latest.partitionKey,
    snapshot: readWorkspaceSnapshot(latest.partitionKey),
    workspaceId:
      latest.runtimeTarget === 'local' && (latest.workspacePath ?? '').trim().length > 0
        ? hostWorkspaceId
        : null,
  };
}

/**
 * 聚合聊天页三栏所需的真实状态、接口请求与 SSE 流式控制。
 */
export function useChatWorkspace(
  isAuthenticated: boolean,
  options?: UseChatWorkspaceOptions,
) {
  const initialUrlConversationIdRef = useRef<string | null>(null);
  const hasHydratedInitialConversationRef = useRef(false);
  if (initialUrlConversationIdRef.current == null && typeof window !== 'undefined') {
    initialUrlConversationIdRef.current = readConversationIdFromUrl();
  }
  const onUnauthorizedRef = useRef(options?.onUnauthorized);
  onUnauthorizedRef.current = options?.onUnauthorized;
  const hostContext = options?.hostContext ?? null;
  const hostType = hostContext?.hostType ?? 'web';
  const hostBoundRepositoryPath = hostContext?.localResource?.boundRepositoryPath ?? null;
  const hostWorkspaceId = normalizeWorkspaceId(hostContext?.localResource?.workspaceId);
  const executionTargetsSignature = hostContext?.executionTargets?.join('|') ?? '';
  const bindWorkspacePath =
    options?.bindWorkspacePath ??
    (async () => {
      return null;
    });
  const openRepositoryPicker =
    options?.pickRepositoryDirectory ??
    (async () => {
      return null;
    });
  const runtimeTargets = useMemo(
    () =>
      hostContext?.executionTargets && hostContext.executionTargets.length
        ? hostContext.executionTargets
        : ['cloud'],
    [executionTargetsSignature],
  );
  const runtimeTargetsSignature = useMemo(() => runtimeTargets.join('|'), [runtimeTargets]);
  const [activeRuntimeTarget, setActiveRuntimeTargetState] = useState<'cloud' | 'local'>(
    runtimeTargets[0] ?? 'cloud',
  );
  const [workspaceGroups, setWorkspaceGroups] = useState<WorkspaceConversationGroup[]>([]);
  const [activeWorkspacePartitionKey, setActiveWorkspacePartitionKey] = useState<string | null>(
    null,
  );
  const [workspacePath, setWorkspacePath] = useState<string | null>(null);
  const [workspaceId, setWorkspaceId] = useState<string | null>(null);
  const [workspaceLabel, setWorkspaceLabel] = useState(
    getDefaultWorkspaceLabel(activeRuntimeTarget),
  );
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [messages, setMessagesState] = useState<ChatMessageItem[]>([]);
  const [executionSteps, setExecutionSteps] = useState<ExecutionStepItem[]>([]);
  const [references, setReferences] = useState<ReferenceItem[]>([]);
  const [artifacts, setArtifacts] = useState<ArtifactItem[]>([]);
  const [sampleQuestions, setSampleQuestions] = useState<SampleQuestionItem[]>([]);
  const [availableExperts, setAvailableExperts] = useState<ChatExpertItem[]>([]);
  const [currentExperts, setCurrentExperts] = useState<CurrentExpertItem[]>([]);
  const [selectedExpertCode, setSelectedExpertCode] = useState<string | null>(null);
  const [availableSkills, setAvailableSkills] = useState<ChatSkillItem[]>([]);
  const [currentSkills, setCurrentSkills] = useState<CurrentSkillItem[]>([]);
  const [selectedSkillCodes, setSelectedSkillCodesState] = useState<string[]>([]);
  const [availableMcps, setAvailableMcps] = useState<McpItem[]>([]);
  const [currentMcps, setCurrentMcps] = useState<CurrentMcpItem[]>([]);
  const [selectedMcpCodes, setSelectedMcpCodesState] = useState<string[]>([]);
  const [mcpConnected, setMcpConnected] = useState(true);
  const [isStreaming, setIsStreaming] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [deepThinkingEnabled, setDeepThinkingEnabled] = useState(false);
  const [streamQueueState, setStreamQueueState] = useState<StreamQueueState | null>(null);
  const [streamError, setStreamError] = useState('');
  const [inputValue, setInputValue] = useState('');
  const [pendingAttachments, setPendingAttachments] = useState<PendingAttachmentItem[]>([]);
  const [isBootstrapping, setIsBootstrapping] = useState(false);
  const [renameDialogState, setRenameDialogState] = useState<{
    isOpen: boolean;
    conversationId: string | null;
    initialTitle: string;
    actionContext?: ConversationActionContext;
  }>({
    isOpen: false,
    conversationId: null,
    initialTitle: '',
    actionContext: undefined,
  });
  const [deleteDialogState, setDeleteDialogState] = useState<{
    isOpen: boolean;
    conversationId: string | null;
    title: string;
    actionContext?: ConversationActionContext;
  }>({
    isOpen: false,
    conversationId: null,
    title: '',
    actionContext: undefined,
  });
  const streamStateRef = useRef<ActiveStreamState | null>(null);
  const activeConversationIdRef = useRef<string | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const streamMcpCallsRef = useRef<Record<string, McpCallItem[]>>({});
  const streamSessionSeedRef = useRef(0);
  const activeStreamSessionIdRef = useRef<number | null>(null);
  const streamQueueTimerRef = useRef<number | null>(null);
  const skipNextRuntimeSyncRef = useRef(false);
  const messagesRef = useRef<ChatMessageItem[]>([]);
  messagesRef.current = messages;

  /**
   * 统一写入消息列表，并同步维护最新引用，避免流结束后紧跟的会话回放读到旧闭包。
   * @param nextValue 目标消息列表或基于前值的更新函数。
   */
  const setMessages = (
    nextValue: ChatMessageItem[] | ((previous: ChatMessageItem[]) => ChatMessageItem[]),
  ) => {
    setMessagesState((previousMessages) => {
      const rawNextMessages =
        typeof nextValue === 'function' ? nextValue(previousMessages) : nextValue;
      const nextMessages = sanitizeMessagesForProcessDisplay(rawNextMessages);
      messagesRef.current = nextMessages;
      return nextMessages;
    });
  };

  /**
   * 刷新命中 URL 会话时，优先解析该会话真实归属分区，避免默认工作空间误吞历史会话恢复。
   * @returns 会话归属分区及其快照；不存在时返回 null。
   */
  const resolveInitialConversationPartition = () => {
    if (hasHydratedInitialConversationRef.current) {
      return null;
    }
    const initialConversationId = initialUrlConversationIdRef.current;
    if (!initialConversationId) {
      return null;
    }
    const partitionKey = findWorkspacePartitionByConversationId(initialConversationId);
    if (!partitionKey) {
      return null;
    }
    const snapshot = readWorkspaceSnapshot(partitionKey);
    if (!runtimeTargets.includes(snapshot.runtimeTarget)) {
      return null;
    }
    return {
      partitionKey,
      snapshot,
      workspaceId:
        snapshot.runtimeTarget === 'local' && snapshot.workspacePath === hostBoundRepositoryPath
          ? hostWorkspaceId
          : null,
    };
  };

  /**
   * 解析初始化时应恢复的工作空间分区。
   * URL 中如果显式带了会话标识，必须优先恢复对应会话所在分区；
   * 否则退回到最近一次打开且包含真实状态的分区，避免刷新后回到空壳默认分区。
   * @returns 应恢复的分区信息或 null。
   */
  const resolveInitialWorkspacePartition = () => {
    const conversationPartition = resolveInitialConversationPartition();
    if (conversationPartition) {
      return conversationPartition;
    }
    if (hasHydratedInitialConversationRef.current) {
      return null;
    }
    return resolveMostRecentWorkspacePartition(runtimeTargets, hostBoundRepositoryPath, hostWorkspaceId);
  };

  /**
   * 维护当前激活会话的最新引用，避免异步动作在闭包里读到过期会话。
   */
  useEffect(() => {
    activeConversationIdRef.current = activeConversationId;
  }, [activeConversationId]);

  /**
   * 清理排队提示延迟任务，防止旧流事件在新流阶段误触发提示。
   */
  const clearStreamQueueTimer = () => {
    if (streamQueueTimerRef.current == null) {
      return;
    }
    window.clearTimeout(streamQueueTimerRef.current);
    streamQueueTimerRef.current = null;
  };

  /**
   * 立即隐藏排队提示并取消延迟任务。
   */
  const hideStreamQueueState = () => {
    clearStreamQueueTimer();
    setStreamQueueState(null);
  };

  /**
   * 延迟展示排队提示，避免 queued 紧接 queue-accepted 时出现黄色提示闪烁。
   * @param position 当前排队位置。
   */
  const scheduleStreamQueueState = (position: number) => {
    clearStreamQueueTimer();
    streamQueueTimerRef.current = window.setTimeout(() => {
      streamQueueTimerRef.current = null;
      setStreamQueueState({
        position,
        message: `请求排队中，前方还有 ${position} 个会话`,
      });
    }, STREAM_QUEUE_BANNER_DELAY_MS);
  };

  /**
   * 统一更新 MCP 选择列表，支持直接赋值与函数式更新。
   * @param nextValue 目标值或计算函数。
   */
  const setSelectedMcpCodes = (nextValue: string[] | ((previous: string[]) => string[])) => {
    const selectableMcpCodeSet = new Set(
      availableMcps.filter((mcp) => isSelectableMcp(mcp)).map((mcp) => mcp.mcpCode),
    );
    const normalizeSelectedCodes = (codes: string[]) =>
      codes.filter((code) => selectableMcpCodeSet.has(code));
    if (typeof nextValue === 'function') {
      setSelectedMcpCodesState((previous) => normalizeSelectedCodes(nextValue(previous)));
      return;
    }
    setSelectedMcpCodesState(normalizeSelectedCodes(nextValue));
  };

  /**
   * 当后端 MCP 可用集合变化时，自动剔除已失效选项，避免把不可用 MCP 带入发送参数。
   */
  useEffect(() => {
    const selectableMcpCodeSet = new Set(
      availableMcps.filter((mcp) => isSelectableMcp(mcp)).map((mcp) => mcp.mcpCode),
    );
    setSelectedMcpCodesState((previous) => {
      const filtered = previous.filter((code) => selectableMcpCodeSet.has(code));
      if (filtered.length === previous.length) {
        return previous;
      }
      return filtered;
    });
  }, [availableMcps]);

  /**
   * 统一更新技能选择列表，支持直接赋值与函数式更新。
   * @param nextValue 目标值或计算函数。
   */
  const setSelectedSkillCodes = (nextValue: string[] | ((previous: string[]) => string[])) => {
    if (typeof nextValue === 'function') {
      setSelectedSkillCodesState((previous) => nextValue(previous));
      return;
    }
    setSelectedSkillCodesState(nextValue);
  };

  /**
   * 生成单调递增的流会话编号，用于拦截过期流的状态回写。
   * @returns 新流会话编号。
   */
  const createStreamSessionId = () => {
    streamSessionSeedRef.current += 1;
    return streamSessionSeedRef.current;
  };

  /**
   * 判断当前回调是否仍属于激活中的流会话，避免旧流覆盖新流状态。
   * @param streamSessionId 流会话编号。
   * @returns 是否为当前激活会话。
   */
  const isActiveStreamSession = (streamSessionId: number) =>
    activeStreamSessionIdRef.current === streamSessionId;

  /**
   * 当后端通过 meta 下发新会话 ID 时，立即写入当前分区会话列表，避免列表依赖后续刷新才出现。
   * @param conversationId 会话标识。
   */
  const upsertConversationFromStreamMeta = (conversationId: string, conversationTitle?: string) => {
    if (!conversationId) {
      return;
    }
    const previousConversationId = streamStateRef.current?.conversationId;
    const shouldRenameLocalConversation =
      activeRuntimeTarget === 'local' &&
      previousConversationId != null &&
      previousConversationId !== conversationId;
    const existingConversation = conversations.find((conversation) => conversation.id === conversationId);
    const nextConversation: ConversationItem =
      existingConversation
        ? {
            ...existingConversation,
            title: conversationTitle && conversationTitle.trim().length > 0
              ? conversationTitle
              : existingConversation.title,
          }
        : {
            id: conversationId,
            title:
              conversationTitle && conversationTitle.trim().length > 0
                ? conversationTitle
                : '新会话',
            status: 'ACTIVE',
            workspaceType: activeRuntimeTarget === 'local' ? 'LOCAL' : undefined,
          };
    const nextConversations = upsertConversationToTop(
      shouldRenameLocalConversation
        ? conversations.filter((conversation) => conversation.id !== previousConversationId)
        : conversations,
      nextConversation,
    );
    setConversations(nextConversations);
    upsertWorkspaceSnapshot(activeRuntimeTarget, workspacePath ?? null, {
      conversations: nextConversations,
      activeConversationId: conversationId,
      workspaceLabel:
        workspacePath != null
          ? getWorkspaceLabel(workspacePath)
          : getDefaultWorkspaceLabel(activeRuntimeTarget),
    });
    refreshWorkspaceGroups('all');
  };

  /**
   * 统一刷新左侧分组数据：桌面端展示云端历史 + 本地历史 + 本地工作空间，网页端保持当前环境过滤。
   * @param mode 过滤模式。
   */
  const refreshWorkspaceGroups = (mode: WorkspaceGroupQueryMode = 'runtime-only') => {
    const shouldShowAllWorkspaceGroups = hostType === 'desktop' && mode === 'all';
    // 关键约束：侧栏分组始终基于本地快照重建，避免会话刷新的短窗口期出现“暂无会话”闪烁。
    if (shouldShowAllWorkspaceGroups) {
      setWorkspaceGroups(listWorkspaceGroups());
      return;
    }
    setWorkspaceGroups(listWorkspaceGroups(activeRuntimeTarget));
  };

  /**
   * 当前工作空间分区刷新后，重新读取对应快照，保证切换目录时左侧与主区同步。
   */
  useEffect(() => {
    if (skipNextRuntimeSyncRef.current) {
      skipNextRuntimeSyncRef.current = false;
      return;
    }
    if (!runtimeTargets.length) {
      return;
    }
    if (!runtimeTargets.includes(activeRuntimeTarget)) {
      setActiveRuntimeTargetState(runtimeTargets[0] ?? 'cloud');
      return;
    }
    const preferredInitialConversationPartition = resolveInitialWorkspacePartition();
    if (
      preferredInitialConversationPartition &&
      preferredInitialConversationPartition.snapshot.runtimeTarget !== activeRuntimeTarget
    ) {
      setActiveRuntimeTargetState(preferredInitialConversationPartition.snapshot.runtimeTarget);
      refreshWorkspaceGroups('all');
      return;
    }
    // 业务约束：云端环境不绑定本地目录，避免与本地工作空间混淆。
    const nextWorkspacePath = preferredInitialConversationPartition
      ? preferredInitialConversationPartition.snapshot.workspacePath ?? null
      : activeRuntimeTarget === 'local'
        ? hostBoundRepositoryPath
        : null;
    const nextWorkspaceId = preferredInitialConversationPartition
      ? preferredInitialConversationPartition.workspaceId
      : activeRuntimeTarget === 'local'
        ? hostWorkspaceId
        : null;
    const nextPartitionKey =
      preferredInitialConversationPartition?.partitionKey ??
      buildWorkspacePartitionKey(activeRuntimeTarget, nextWorkspacePath);
    const isSamePartition = nextPartitionKey === activeWorkspacePartitionKey;
    const nextSnapshot =
      preferredInitialConversationPartition?.snapshot ?? readWorkspaceSnapshot(nextPartitionKey);
    const nextWorkspaceLabel = nextWorkspacePath
      ? getWorkspaceLabel(nextWorkspacePath)
      : getDefaultWorkspaceLabel(nextSnapshot.runtimeTarget);
    if (activeStreamSessionIdRef.current != null || abortControllerRef.current != null || isStreaming) {
      // 关键约束：流式生成期间仅允许用户显式切换分区，宿主被动上下文刷新不得打断当前会话。
      // 否则会导致消息区被清空但输入栏仍显示生成中，形成“内容消失”的错位体验。
      if (!isSamePartition) {
        refreshWorkspaceGroups('all');
        return;
      }
    }
    setWorkspacePath(nextWorkspacePath);
    setWorkspaceId(nextWorkspaceId);
    setWorkspaceLabel(nextWorkspaceLabel);
    // 关键约束：宿主仅刷新引用且语义未变化时不重置会话，避免发送中闪回首页与侧栏闪空。
    if (isSamePartition) {
      refreshWorkspaceGroups('all');
      return;
    }
    setActiveWorkspacePartitionKey(nextPartitionKey);
    setConversations(nextSnapshot.conversations);
    refreshWorkspaceGroups('all');
    // 业务约束：仅首次进入默认停留首页；已有分区内激活会话时保留会话上下文以支持刷新恢复。
    if (!nextSnapshot.activeConversationId) {
      clearConversationPlayback(true);
    }
  }, [
    hostType,
    hostBoundRepositoryPath,
    hostWorkspaceId,
    runtimeTargetsSignature,
    runtimeTargets,
    activeRuntimeTarget,
    activeWorkspacePartitionKey,
    isStreaming,
  ]);

  useEffect(() => {
    refreshWorkspaceGroups('all');
  }, [activeRuntimeTarget]);

  useEffect(() => {
    const hasPersistedToken = currentToken() != null;
    // 关键约束：登录态校验尚未返回但本地仍有 token 时，不能提前按“未登录”重置，
    // 否则会清空 URL 会话参数并打断刷新恢复链路。
    if (!isAuthenticated && !hasPersistedToken) {
      resetWorkspace();
      return;
    }
    if (!activeWorkspacePartitionKey) {
      return;
    }
    void bootstrapWorkspace();
  }, [isAuthenticated, activeWorkspacePartitionKey]);

  useEffect(() => {
    return () => {
      clearStreamQueueTimer();
    };
  }, []);

  /**
   * 加载初始会话列表并恢复显式指定会话，默认保持首页新建态。
   */
  const bootstrapWorkspace = async () => {
    const token = currentToken();
    if (!token) {
      return;
    }
    setIsBootstrapping(true);
    try {
      const preferredInitialConversationPartition = resolveInitialWorkspacePartition();
      if (
        preferredInitialConversationPartition &&
        preferredInitialConversationPartition.partitionKey !== activeWorkspacePartitionKey
      ) {
        setWorkspacePath(preferredInitialConversationPartition.snapshot.workspacePath ?? null);
        setWorkspaceId(preferredInitialConversationPartition.workspaceId);
        setWorkspaceLabel(
          preferredInitialConversationPartition.snapshot.workspacePath != null
            ? getWorkspaceLabel(preferredInitialConversationPartition.snapshot.workspacePath)
            : getDefaultWorkspaceLabel(
                preferredInitialConversationPartition.snapshot.runtimeTarget,
              ),
        );
        setActiveWorkspacePartitionKey(preferredInitialConversationPartition.partitionKey);
        setConversations(preferredInitialConversationPartition.snapshot.conversations);
        refreshWorkspaceGroups('all');
        return;
      }
      const shouldKeepLandingState =
        activeConversationId == null && messages.length === 0 && !readConversationIdFromUrl();
      // 性能约束：会话恢复是首屏关键路径；技能/MCP/示例题/专家属于旁路信息，不应阻塞会话正文渲染。
      const nextConversationsPromise = loadConversations(token);
      const nextSampleQuestionsPromise = ChatApi.listSampleQuestions(token);
      const nextExpertsPromise = ChatApi.listExperts(token);
      const nextSkillsPromise = ChatApi.listSkills(token);
      const nextMcpsPromise = ChatApi.listMcps(token);
      const preferredConversationIdFromSnapshot = resolvePreferredConversationIdFromSnapshot();
      const didHydrateConversationFromSnapshot = Boolean(preferredConversationIdFromSnapshot);
      if (preferredConversationIdFromSnapshot && activeWorkspacePartitionKey) {
        hasHydratedInitialConversationRef.current = true;
        // 刷新命中 URL 会话时优先恢复本地快照，避免慢接口期间闪回首页空态。
        await restoreWorkspaceSnapshot(activeWorkspacePartitionKey, preferredConversationIdFromSnapshot);
      }

      const nextConversations = await nextConversationsPromise;
      refreshWorkspaceGroups('all');
      const shouldProtectActiveStream = hasActiveStreamPlayback();
      const preferredConversationId = didHydrateConversationFromSnapshot || shouldProtectActiveStream
        ? null
        : resolvePreferredConversationId(nextConversations);
      if (preferredConversationId) {
        hasHydratedInitialConversationRef.current = true;
        if (hasPersistedConversationRecord(preferredConversationId)) {
          // URL 命中本地快照时优先恢复缓存，避免接口空回放覆盖已有消息。
          if (activeWorkspacePartitionKey) {
            await restoreWorkspaceSnapshot(activeWorkspacePartitionKey, preferredConversationId);
          }
        } else {
          await selectConversation(preferredConversationId, nextConversations, undefined, false);
        }
      } else if (!shouldKeepLandingState && !didHydrateConversationFromSnapshot && !shouldProtectActiveStream) {
        const hasRecoveredConversation =
          activeConversationIdRef.current != null || messagesRef.current.length > 0;
        // 关键约束：鉴权完成或严格模式带来的后续初始化可能晚于首轮恢复；
        // 只有当前仍然真的是空态时，才允许回退到首页，避免把刚恢复好的会话再次清空。
        if (!hasRecoveredConversation) {
          clearConversationPlayback(true);
        }
      }

      const [nextSampleQuestionsResult, nextExpertsResult, nextSkillsResult, nextMcpsResult] =
        await Promise.allSettled([
          nextSampleQuestionsPromise,
          nextExpertsPromise,
          nextSkillsPromise,
          nextMcpsPromise,
        ]);

      if (nextSampleQuestionsResult.status === 'fulfilled') {
        setSampleQuestions(nextSampleQuestionsResult.value);
      }
      if (nextExpertsResult.status === 'fulfilled') {
        setAvailableExperts(nextExpertsResult.value);
      }
      if (nextSkillsResult.status === 'fulfilled') {
        setAvailableSkills(nextSkillsResult.value);
      }
      const nextMcps = nextMcpsResult.status === 'fulfilled' ? nextMcpsResult.value : [];
      setAvailableMcps(nextMcps);
      const selectableMcps = nextMcps.filter((item) => isSelectableMcp(item));
      if (selectedSkillCodes.length === 0) {
        setSelectedSkillCodes([]);
      }
      if (selectedMcpCodes.length === 0) {
        setSelectedMcpCodesState(selectableMcps.map((item) => item.mcpCode));
      }
      setMcpConnected(selectableMcps.length > 0);
      if (!hasHydratedInitialConversationRef.current) {
        hasHydratedInitialConversationRef.current = true;
      }
    } catch (error) {
      if (error instanceof ChatApi.UnauthorizedError) {
        onUnauthorizedRef.current?.();
        return;
      }
      throw error;
    } finally {
      setIsBootstrapping(false);
    }
  };

  /**
   * 切换执行环境后立即回到新会话，避免不同环境共享同一上下文。
   * @param runtimeTarget 目标环境。
   */
  const setActiveRuntimeTarget = async (runtimeTarget: 'cloud' | 'local') => {
    if (!runtimeTargets.includes(runtimeTarget) || runtimeTarget === activeRuntimeTarget) {
      return;
    }
    // 环境切换后按该环境默认工作空间重新开新会话，避免上下文串线。
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    activeStreamSessionIdRef.current = null;
    streamStateRef.current = null;
    setIsStreaming(false);
    setIsCancelling(false);
    setStreamError('');
    hideStreamQueueState();
    setInputValue('');
    clearPendingAttachments();
    if (runtimeTarget === 'cloud') {
      await switchWorkspacePartition('cloud', null, false, false);
      return;
    }
    const fallbackWorkspacePath = hostContext?.localResource?.boundRepositoryPath ?? workspacePath ?? null;
    await switchWorkspacePartition('local', fallbackWorkspacePath, false, false);
  };

  /**
   * 按指定运行环境切换工作空间分区，可被环境切换与文件夹选择复用。
   * @param runtimeTarget 目标运行环境。
   * @param nextWorkspacePath 目标工作空间路径。
   * @param shouldBindDesktopPath 是否写回桌面宿主绑定目录。
   * @param shouldRestoreConversation 是否恢复目标分区最后一次会话。
   */
  const switchWorkspacePartition = async (
    runtimeTarget: 'cloud' | 'local',
    nextWorkspacePath: string | null,
    shouldBindDesktopPath: boolean,
    shouldRestoreConversation = true,
  ) => {
    const normalizedWorkspacePath = nextWorkspacePath ? nextWorkspacePath.trim() : null;
    let normalizedWorkspaceId =
      runtimeTarget === 'local'
        ? normalizeWorkspaceId(hostContext?.localResource?.workspaceId)
        : null;
    if (
      shouldBindDesktopPath &&
      normalizedWorkspacePath &&
      hostContext?.hostType === 'desktop'
    ) {
      const bindingResult = await bindWorkspacePath(normalizedWorkspacePath);
      normalizedWorkspaceId =
        normalizeWorkspaceId(bindingResult?.workspaceId) ?? normalizedWorkspaceId;
    }
    const nextPartitionKey = buildWorkspacePartitionKey(runtimeTarget, normalizedWorkspacePath);
    activeStreamSessionIdRef.current = null;
    // 用户显式切换空间后优先采用本次选择，避免宿主上下文异步回写前被旧值覆盖。
    skipNextRuntimeSyncRef.current = true;
    const nextSnapshot = readWorkspaceSnapshot(nextPartitionKey);
    setActiveRuntimeTargetState(runtimeTarget);
    setWorkspacePath(normalizedWorkspacePath);
    setWorkspaceId(normalizedWorkspaceId);
    setWorkspaceLabel(
      normalizedWorkspacePath
        ? getWorkspaceLabel(normalizedWorkspacePath)
        : getDefaultWorkspaceLabel(runtimeTarget),
    );
    setActiveWorkspacePartitionKey(nextPartitionKey);
    setConversations(nextSnapshot.conversations);
    clearConversationPlayback(true);
    upsertWorkspaceSnapshot(runtimeTarget, normalizedWorkspacePath, {
      conversations: nextSnapshot.conversations,
      activeConversationId: null,
      workspaceLabel: normalizedWorkspacePath
        ? getWorkspaceLabel(normalizedWorkspacePath)
        : getDefaultWorkspaceLabel(runtimeTarget),
    });
    refreshWorkspaceGroups('all');
    // 空间切换后默认恢复该空间已有会话；新建会话场景会显式关闭恢复，避免误带旧上下文。
    const nextActiveConversationId = nextSnapshot.activeConversationId;
    if (shouldRestoreConversation && nextActiveConversationId) {
      await restoreWorkspaceSnapshot(nextPartitionKey, nextActiveConversationId);
    }
    return normalizedWorkspaceId;
  };

  /**
   * 切换到指定工作空间目录。
   * @param nextWorkspacePath 目标工作空间路径。
   */
  const setActiveWorkspacePath = async (nextWorkspacePath: string | null) => {
    if (activeRuntimeTarget !== 'local') {
      return;
    }
    if ((nextWorkspacePath ?? null) === (workspacePath ?? null)) {
      return;
    }
    const nextWorkspaceId = await switchWorkspacePartition('local', nextWorkspacePath, true, false);
    const token = currentToken();
    if (token) {
      await loadConversations(token, nextWorkspaceId);
    }
  };

  /**
   * 在指定工作空间上下文中打开会话，确保“先切空间再选会话”，避免跨空间串线。
   * @param conversationId 会话标识。
   * @param selectionContext 侧栏会话携带的空间上下文。
   */
  const selectConversationInWorkspace = async (
    conversationId: string,
    selectionContext: {
      partitionKey: string;
      runtimeTarget: 'cloud' | 'local';
      workspacePath: string | null;
      groupType?: 'workspace' | 'history';
    },
  ) => {
    const normalizedSelectionWorkspacePath =
      selectionContext.runtimeTarget === 'local' ? selectionContext.workspacePath : null;
    const selectionSnapshotKey = isWorkspaceHistoryPartitionKey(selectionContext.partitionKey)
      ? buildWorkspacePartitionKey(selectionContext.runtimeTarget, normalizedSelectionWorkspacePath)
      : selectionContext.partitionKey;
    if (selectionContext.groupType === 'history' || isWorkspaceHistoryPartitionKey(selectionContext.partitionKey)) {
      const latestSnapshot = readWorkspaceSnapshot(selectionSnapshotKey);
      await selectConversation(
        conversationId,
        latestSnapshot.conversations,
        undefined,
        true,
        false,
        {
          runtimeTarget: selectionContext.runtimeTarget,
          workspacePath: normalizedSelectionWorkspacePath,
        },
      );
      return;
    }
    const isSamePartition =
      activeRuntimeTarget === selectionContext.runtimeTarget &&
      buildWorkspacePartitionKey(activeRuntimeTarget, workspacePath ?? null) === selectionContext.partitionKey;
    if (!isSamePartition) {
      await switchWorkspacePartition(
        selectionContext.runtimeTarget,
        selectionContext.runtimeTarget === 'local' ? selectionContext.workspacePath : null,
        false,
        false,
      );
    }
    const latestSnapshot = readWorkspaceSnapshot(selectionSnapshotKey);
    await selectConversation(
      conversationId,
      latestSnapshot.conversations,
      undefined,
      true,
      false,
      {
        runtimeTarget: selectionContext.runtimeTarget,
        workspacePath: normalizedSelectionWorkspacePath,
      },
    );
  };

  /**
   * 打开文件夹选择器并将结果切换为当前工作空间。
   */
  const pickRepositoryDirectory = async () => {
    const selectedPath = await openRepositoryPicker();
    if (!selectedPath) {
      return;
    }
    const nextWorkspaceId = await switchWorkspacePartition('local', selectedPath, true, false);
    const token = currentToken();
    if (token) {
      await loadConversations(token, nextWorkspaceId);
    }
  };

  /**
   * 切换到指定会话并回放消息与右栏数据。
   * @param conversationId 会话标识。
   * @param sourceConversations 可选的最新会话列表，避免重复读取旧状态。
   */
  const selectConversation = async (
    conversationId: string,
    sourceConversations?: ConversationItem[],
    latestAssistantMcpCalls?: McpCallItem[],
    syncUrl = true,
    preferPreviousAssistantContent = false,
    snapshotContext?: {
      runtimeTarget: 'cloud' | 'local';
      workspacePath: string | null;
    },
  ) => {
    const token = currentToken();
    if (!token) {
      return;
    }
    setActiveConversationId(conversationId);
    if (syncUrl) {
      // 仅用户显式切换会话时更新 URL，初始化恢复阶段由独立分支控制，避免误覆盖初始参数。
      writeConversationIdToUrl(conversationId);
    }
    const seenRuntimeTarget = snapshotContext?.runtimeTarget ?? activeRuntimeTarget;
    const seenWorkspacePath = snapshotContext?.workspacePath ?? workspacePath ?? null;
    const seenSnapshot = snapshotContext
      ? readWorkspaceSnapshot(buildWorkspacePartitionKey(seenRuntimeTarget, seenWorkspacePath))
      : null;
    const conversationListForSeenState = sourceConversations ?? conversations;
    let conversationListForPersist = conversationListForSeenState;
    let seenTaskFinishedAtByConversationIdForPersist: Record<string, string> | null = null;
    const selectedConversationForSeenState = conversationListForSeenState.find(
      (item) => item.id === conversationId,
    ) ?? seenSnapshot?.conversations.find((item) => item.id === conversationId);
    const shouldMarkBackendTaskCompletionRead =
      Boolean(selectedConversationForSeenState?.lastTaskFinishedAt) &&
      selectedConversationForSeenState?.taskCompletionRead === false;
    if (shouldMarkBackendTaskCompletionRead) {
      await ChatApi.markTaskCompletionRead(token, conversationId);
    }
    if (selectedConversationForSeenState?.lastTaskFinishedAt) {
      if (selectedConversationForSeenState.taskCompletionRead == null) {
        markConversationTaskCompletionSeen(
          seenRuntimeTarget,
          seenWorkspacePath,
          conversationId,
          selectedConversationForSeenState.lastTaskFinishedAt,
        );
      }
      const clearCompletionReminder = (items: ConversationItem[]) =>
        items.map((item) =>
          item.id === conversationId
            ? {
                ...item,
                taskCompletionRead:
                  item.taskCompletionRead == null ? item.taskCompletionRead : true,
                hasUnreadTaskCompletion: false,
              }
            : item,
        );
      const targetConversationsForSeenState = conversationListForSeenState.some(
        (item) => item.id === conversationId,
      )
        ? conversationListForSeenState
        : (seenSnapshot?.conversations ?? conversationListForSeenState);
      const nextConversations = clearCompletionReminder(targetConversationsForSeenState);
      conversationListForPersist = nextConversations;
      if (selectedConversationForSeenState.taskCompletionRead == null) {
        seenTaskFinishedAtByConversationIdForPersist = {
          ...(readWorkspaceSnapshot(
            buildWorkspacePartitionKey(seenRuntimeTarget, seenWorkspacePath),
          ).seenTaskFinishedAtByConversationId ?? {}),
          [conversationId]: selectedConversationForSeenState.lastTaskFinishedAt,
        };
      } else {
        seenTaskFinishedAtByConversationIdForPersist = readWorkspaceSnapshot(
          buildWorkspacePartitionKey(seenRuntimeTarget, seenWorkspacePath),
        ).seenTaskFinishedAtByConversationId ?? {};
      }
      setConversations(nextConversations);
      upsertWorkspaceSnapshot(seenRuntimeTarget, seenWorkspacePath, {
        conversations: nextConversations,
        seenTaskFinishedAtByConversationId: seenTaskFinishedAtByConversationIdForPersist,
      });
      refreshWorkspaceGroups('all');
    }
    // 关键约束：刷新恢复时优先渲染消息主区，避免右栏慢接口阻塞首屏可读内容。
    setExecutionSteps([]);
    setReferences([]);
    setArtifacts([]);
    setCurrentExperts([]);
    setCurrentSkills([]);
    setCurrentMcps([]);
    const nextMessagesPromise = ChatApi.listMessages(token, conversationId);
    const nextStepsPromise = ChatApi.listSteps(token, conversationId);
    const nextReferencesPromise = ChatApi.listReferences(token, conversationId);
    const nextArtifactsPromise = ChatApi.listArtifacts(token, conversationId);
    const nextCurrentExpertsPromise = ChatApi.listCurrentExperts(token, conversationId);
    const nextCurrentSkillsPromise = ChatApi.listCurrentSkills(token, conversationId);
    const nextCurrentMcpsPromise = ChatApi.listCurrentMcps(token, conversationId);

    const nextMessages = await nextMessagesPromise;
    const shouldPreservePreviousAssistantContent =
      preferPreviousAssistantContent ||
      isActiveAssistantReplayContext(
        messagesRef.current,
        conversationId,
        streamStateRef.current,
      );
    const nextReplayMessages = patchLatestAssistantReplayPanels(nextMessages, {
      latestAssistantMcpCalls,
      previousMessages: messagesRef.current,
      targetConversationId: conversationId,
      executionSteps: [],
      references: [],
      preferPreviousContent: shouldPreservePreviousAssistantContent,
    });
    setMessages(nextReplayMessages);

    const [
      nextSteps,
      nextReferences,
      nextArtifacts,
      nextCurrentExperts,
      nextCurrentSkills,
      nextCurrentMcps,
    ] = await Promise.all([
      nextStepsPromise,
      nextReferencesPromise,
      nextArtifactsPromise,
      nextCurrentExpertsPromise,
      nextCurrentSkillsPromise,
      nextCurrentMcpsPromise,
    ]);
    const nextReplayMessagesWithPanels = patchLatestAssistantReplayPanels(nextMessages, {
      latestAssistantMcpCalls,
      previousMessages: nextReplayMessages,
      targetConversationId: conversationId,
      executionSteps: nextSteps,
      references: nextReferences,
      preferPreviousContent: shouldPreservePreviousAssistantContent,
    });
    setMessages(nextReplayMessagesWithPanels);
    setExecutionSteps(nextSteps);
    setReferences(nextReferences);
    setArtifacts(nextArtifacts);
    setCurrentExperts(nextCurrentExperts);
    setCurrentSkills(nextCurrentSkills);
    setCurrentMcps(nextCurrentMcps);
    const conversationList = conversationListForPersist;
    const selectedConversation = conversationList.find((item) => item.id === conversationId);
    if (selectedConversation?.lastRunId && nextReplayMessagesWithPanels.length > 0) {
      streamStateRef.current = {
        conversationId,
        activeMessageId:
          nextReplayMessagesWithPanels[nextReplayMessagesWithPanels.length - 1].id,
      };
    }
    if (snapshotContext) {
      saveConversationRecordToWorkspace(
        snapshotContext.runtimeTarget,
        snapshotContext.workspacePath,
        conversationId,
        conversationList,
        {
          messages: nextReplayMessagesWithPanels,
          executionSteps: nextSteps,
          references: nextReferences,
          artifacts: nextArtifacts,
          currentExperts: nextCurrentExperts,
          currentSkills: nextCurrentSkills,
          currentMcps: nextCurrentMcps,
        },
      );
      const latestSnapshotAfterRecordSave = readWorkspaceSnapshot(
        buildWorkspacePartitionKey(snapshotContext.runtimeTarget, snapshotContext.workspacePath),
      );
      upsertWorkspaceSnapshot(snapshotContext.runtimeTarget, snapshotContext.workspacePath, {
        conversations: conversationList,
        activeConversationId: conversationId,
        seenTaskFinishedAtByConversationId:
          seenTaskFinishedAtByConversationIdForPersist ??
          latestSnapshotAfterRecordSave.seenTaskFinishedAtByConversationId ??
          {},
      });
      refreshWorkspaceGroups('all');
      return;
    }
    persistConversationState(conversationId, conversationList, {
      messages: nextReplayMessagesWithPanels,
      executionSteps: nextSteps,
      references: nextReferences,
      artifacts: nextArtifacts,
      currentExperts: nextCurrentExperts,
      currentSkills: nextCurrentSkills,
      currentMcps: nextCurrentMcps,
    });
  };

  /**
   * 提交聊天请求并在本地模拟最小流式状态，随后从后端回放最新数据。
   */
  const submitMessage = async () => {
    const question = inputValue.trim();
    if (!question) {
      return;
    }
    const token = currentToken();
    // 关键约束：当页面内存态仍显示已登录但本地 token 已丢失时，不能静默吞掉发送动作。
    // 这里统一给出“登录失效”提示并触发未授权回调，确保用户能看到明确反馈并回到登录流程。
    if (!token) {
      setStreamError(UserErrorMessages.AUTH_SESSION_EXPIRED);
      onUnauthorizedRef.current?.();
      return;
    }
    setStreamError('');
    // 关键约束：本地模式下只要存在 workspaceId 或已绑定目录任一条件，就允许发送，避免目录已绑定但 ID 延迟回写时误拦截。
    const hasLocalWorkspaceContext =
      (workspaceId != null && workspaceId.trim().length > 0) ||
      (workspacePath != null && workspacePath.trim().length > 0);
    if (activeRuntimeTarget === 'local' && !hasLocalWorkspaceContext) {
      setStreamError('请选择本地工作空间后再发送消息');
      return;
    }
    const submittedInputValue = inputValue;
    const submittedAttachments = pendingAttachments;
    // 交互约束：点击发送后立即清空输入与待发送附件，避免用户误判请求未触发。
    setInputValue('');
    setPendingAttachments([]);
    let uploadedAttachments: ChatAttachmentItem[] = [];
    try {
      uploadedAttachments = await uploadPendingAttachments(token, activeConversationId, submittedAttachments);
    } catch (error) {
      // 上传失败时恢复发送前输入与附件，允许用户修正后重试。
      setInputValue(submittedInputValue);
      restorePendingAttachmentsAfterUploadFailed(submittedAttachments);
      setStreamError(error instanceof Error ? error.message : UserErrorMessages.CHAT_ATTACHMENT_UPLOAD_FAILED);
      return;
    }
    const attachmentIds = uploadedAttachments.map((attachment) => attachment.id);
    setIsStreaming(true);
    const streamSessionId = createStreamSessionId();
    activeStreamSessionIdRef.current = streamSessionId;
    abortControllerRef.current?.abort();
    const streamAbortController = new AbortController();
    abortControllerRef.current = streamAbortController;

    const optimisticConversationId =
      activeConversationId ??
      (activeRuntimeTarget === 'local' ? createLocalConversationId() : 'pending-conversation');
    const optimisticMessageId = `optimistic-user-${Date.now()}`;
    const optimisticAssistantId = `optimistic-assistant-${Date.now()}`;
    streamMcpCallsRef.current[optimisticAssistantId] = [];
    streamStateRef.current = {
      conversationId: optimisticConversationId,
      activeMessageId: optimisticAssistantId,
    };

  const nextMessages: ChatMessageItem[] = [
      ...messages,
      {
        id: optimisticMessageId,
        conversationId: optimisticConversationId,
        role: 'USER',
        content: question,
        attachments: uploadedAttachments,
        status: 'COMPLETED',
      },
      {
        id: optimisticAssistantId,
        conversationId: optimisticConversationId,
        role: 'ASSISTANT',
        content: '',
        processCards: [],
        timelineItems: [],
        status: 'streaming',
      },
    ];
    setMessages(nextMessages);

    persistConversationState(optimisticConversationId, conversations, {
      messages: nextMessages,
      executionSteps,
      references,
      artifacts,
      currentExperts,
      currentSkills,
      currentMcps,
    });

    try {
      const response = await fetch(
        buildStreamRequestUrl(
          question,
          activeConversationId,
          activeRuntimeTarget === 'local' ? null : workspaceId,
          deepThinkingEnabled,
          mcpConnected,
          selectedMcpCodes,
          selectedSkillCodes,
          selectedExpertCode,
          workspacePath,
          attachmentIds,
          activeRuntimeTarget,
        ),
        {
          headers: {
            satoken: token,
          },
          signal: streamAbortController.signal,
        },
      );
      await ChatApi.assertStreamAuthorized(response);
      // 附件上传成功并已发出流请求后即可释放预览 URL，避免长期占用浏览器内存。
      submittedAttachments.forEach((item) => URL.revokeObjectURL(item.previewUrl));
      await consumeSseStream(response, optimisticAssistantId, streamSessionId);
      if (!isActiveStreamSession(streamSessionId)) {
        return;
      }
      if (activeRuntimeTarget === 'local') {
        persistLocalStreamResult(
          optimisticConversationId,
          optimisticAssistantId,
          streamMcpCallsRef.current[optimisticAssistantId],
        );
        refreshWorkspaceGroups('all');
        return;
      }
      const nextConversations = await loadConversations(token);
      const nextConversationId = streamStateRef.current?.conversationId ?? activeConversationId;
      if (nextConversationId) {
        await selectConversation(
          nextConversationId,
          nextConversations,
          streamMcpCallsRef.current[optimisticAssistantId],
          true,
          true,
        );
      }
      refreshWorkspaceGroups('all');
    } catch (error) {
      if (!isActiveStreamSession(streamSessionId)) {
        return;
      }
      if (error instanceof DOMException && error.name === 'AbortError') {
        setMessages((previousMessages) =>
          previousMessages.map((message) =>
            message.id === optimisticAssistantId
              ? {
                  ...message,
                  status: 'cancelled',
                  searchProgress: message.searchProgress
                    ? {
                        ...message.searchProgress,
                        status: 'cancelled',
                      }
                    : undefined,
                }
              : message,
          ),
        );
        setStreamError('已停止当前生成');
      } else if (error instanceof ChatApi.UnauthorizedError) {
        onUnauthorizedRef.current?.();
      } else {
        setStreamError(error instanceof Error ? error.message : UserErrorMessages.CHAT_REQUEST_FAILED);
      }
    } finally {
      delete streamMcpCallsRef.current[optimisticAssistantId];
      if (abortControllerRef.current === streamAbortController) {
        abortControllerRef.current = null;
      }
      if (isActiveStreamSession(streamSessionId)) {
        activeStreamSessionIdRef.current = null;
        setIsStreaming(false);
      }
    }
  };

  /**
   * 对当前会话发起取消请求。
   */
  const cancelCurrentStream = async () => {
    const runningConversationId = streamStateRef.current?.conversationId ?? activeConversationId;
    if (!isStreaming && !abortControllerRef.current) {
      return;
    }
    activeStreamSessionIdRef.current = null;
    const activeMessageId = streamStateRef.current?.activeMessageId;
    if (activeMessageId) {
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === activeMessageId
            ? {
                ...message,
                status: 'cancelled',
              }
            : message,
        ),
      );
    }
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    setIsStreaming(false);
    setStreamError('已停止当前生成');
    hideStreamQueueState();
    const token = currentToken();
    if (
      !token ||
      !runningConversationId ||
      runningConversationId === 'pending-conversation'
    ) {
      return;
    }
    setIsCancelling(true);
    try {
      await ChatApi.cancelConversation(token, runningConversationId);
    } finally {
      setIsCancelling(false);
    }
  };

  /**
   * 本地模式流结束后直接把当前内存中的消息与过程面板固化到本地快照。
   * 业务约束：本地对话不回查云端 conversations/messages 接口，避免创建或依赖云端历史记录。
   * @param optimisticConversationId 发送前生成的本地临时会话标识。
   * @param optimisticAssistantId 当前助手消息标识。
   * @param latestAssistantMcpCalls 流式阶段累计的工具调用。
   */
  const persistLocalStreamResult = (
    optimisticConversationId: string,
    optimisticAssistantId: string,
    latestAssistantMcpCalls?: McpCallItem[],
  ) => {
    const finalConversationId =
      streamStateRef.current?.conversationId ?? activeConversationIdRef.current ?? optimisticConversationId;
    const conversationTitle = resolveLocalConversationTitle(messagesRef.current, finalConversationId);
    const nextMessages = messagesRef.current.map((message) => ({
      ...message,
      conversationId:
        message.conversationId === optimisticConversationId ? finalConversationId : message.conversationId,
      mcpCalls:
        message.id === optimisticAssistantId && latestAssistantMcpCalls != null
          ? mergeMcpCallsById(message.mcpCalls ?? [], latestAssistantMcpCalls)
          : message.mcpCalls,
    }));
    setActiveConversationId(finalConversationId);
    writeConversationIdToUrl(finalConversationId);
    setMessages(nextMessages);
    const nextConversation: ConversationItem = {
      id: finalConversationId,
      title: conversationTitle,
      status: 'ACTIVE',
      workspaceType: 'LOCAL',
    };
    const nextConversations = upsertConversationToTop(
      conversations.filter((conversation) => conversation.id !== optimisticConversationId),
      nextConversation,
    );
    setConversations(nextConversations);
    persistConversationState(finalConversationId, nextConversations, {
      messages: nextMessages,
      executionSteps,
      references,
      artifacts,
      currentExperts,
      currentSkills,
      currentMcps,
    });
  };

  /**
   * 解析本地会话标题，优先使用首条用户问题，避免为了标题再请求后端会话详情。
   * @param sourceMessages 当前消息列表。
   * @param conversationId 会话标识。
   * @returns 会话标题。
   */
  const resolveLocalConversationTitle = (
    sourceMessages: ChatMessageItem[],
    conversationId: string,
  ) => {
    const firstUserMessage = sourceMessages.find(
      (message) => message.conversationId === conversationId && message.role === 'USER',
    );
    const fallbackUserMessage = sourceMessages.find((message) => message.role === 'USER');
    const title = (firstUserMessage?.content ?? fallbackUserMessage?.content ?? '本地对话').trim();
    return title.length > 40 ? title.slice(0, 40) : title;
  };

  /**
   * 切换到“新建对话”空态，并确保下一次发送不再复用旧会话标识。
   */
  const startNewConversation = async (
    createContext?: WorkspaceConversationCreateContext,
  ) => {
    const token = currentToken();
    const runningConversationId = streamStateRef.current?.conversationId ?? activeConversationId;

    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    if (isStreaming && token && runningConversationId) {
      try {
        await ChatApi.cancelConversation(token, runningConversationId);
      } catch {
        // 步骤：新建流程以清空本地上下文为主，取消失败不应阻断用户回到首页空态。
      }
    }

    abortControllerRef.current = null;
    activeStreamSessionIdRef.current = null;
    streamStateRef.current = null;
    setIsStreaming(false);
    setIsCancelling(false);
    hideStreamQueueState();
    setStreamError('');
    setInputValue('');

    /**
     * 侧栏从指定分组触发“新建”时，先切换到目标环境/工作空间，再重置会话空态。
     */
    if (createContext) {
      const targetWorkspacePath =
        createContext.runtimeTarget === 'local' ? createContext.workspacePath ?? null : null;
      const targetPartitionKey = buildWorkspacePartitionKey(
        createContext.runtimeTarget,
        targetWorkspacePath,
      );
      const isSamePartition =
        activeRuntimeTarget === createContext.runtimeTarget &&
        activeWorkspacePartitionKey === targetPartitionKey;
      if (!isSamePartition) {
        await switchWorkspacePartition(
          createContext.runtimeTarget,
          targetWorkspacePath,
          false,
          false,
        );
      }
    }

    clearConversationPlayback();
  };

  /**
   * 重命名指定会话并刷新左侧列表。
   * @param conversationId 会话标识。
   * @param title 新标题。
   */
  const renameConversation = async (conversationId: string, title: string) => {
    const token = currentToken();
    if (!token || !title.trim()) {
      return;
    }
    await ChatApi.renameConversation(token, conversationId, title.trim());
    const nextConversations = await loadConversations(token);
    if (activeConversationId === conversationId) {
      const renamedConversation = nextConversations.find((item) => item.id === conversationId);
      if (renamedConversation && messages.length) {
        setMessages((previousMessages) =>
          previousMessages.map((message, index) =>
            index === previousMessages.length - 1 ? { ...message } : message,
          ),
        );
      }
    }
    refreshWorkspaceGroups('all');
  };

  /**
   * 删除指定会话，并在必要时将主区回退到首页空态。
   * @param conversationId 会话标识。
   */
  const deleteConversation = async (conversationId: string) => {
    const token = currentToken();
    if (!token) {
      return;
    }
    await ChatApi.deleteConversation(token, conversationId);
    const nextConversations = await loadConversations(token);
    if (activeConversationId === conversationId) {
      if (nextConversations[0]?.id) {
        await selectConversation(nextConversations[0].id, nextConversations);
      } else {
        clearConversationPlayback();
      }
    }
    refreshWorkspaceGroups('all');
  };

  /**
   * 删除会话中的指定消息，并同步裁剪本地回放，避免刷新前仍看到已删除内容。
   * @param conversationId 会话标识。
   * @param messageIds 待删除消息标识。
   */
  const deleteConversationMessages = async (conversationId: string, messageIds: string[]) => {
    const token = currentToken();
    const normalizedMessageIds = normalizePersistedMessageIds(messageIds);
    if (!token || normalizedMessageIds.length === 0) {
      return;
    }
    await ChatApi.deleteConversationMessages(token, conversationId, normalizedMessageIds);
    const deletedMessageIdSet = new Set(normalizedMessageIds);
    const nextMessages = messagesRef.current.filter((message) => !deletedMessageIdSet.has(message.id));
    setMessages(nextMessages);
    persistConversationState(conversationId, conversations, {
      messages: nextMessages,
      executionSteps,
      references,
      artifacts,
      currentExperts,
      currentSkills,
      currentMcps,
    });
    refreshWorkspaceGroups('all');
  };

  /**
   * 从某条用户消息重新发送：旧问题及其后续回答先软删除，再把编辑后的问题作为新的上下文继续生成。
   * @param messageId 被编辑的用户消息标识。
   * @param content 编辑后的消息内容。
   */
  const resendUserMessage = async (messageId: string, content: string) => {
    const normalizedContent = content.trim();
    if (!normalizedContent) {
      return;
    }
    const token = currentToken();
    if (!token) {
      setStreamError(UserErrorMessages.AUTH_SESSION_EXPIRED);
      onUnauthorizedRef.current?.();
      return;
    }
    const currentMessages = messagesRef.current;
    const targetMessageIndex = currentMessages.findIndex(
      (message) => message.id === messageId && message.role === 'USER',
    );
    if (targetMessageIndex < 0) {
      return;
    }
    const targetMessage = currentMessages[targetMessageIndex];
    const targetConversationId = targetMessage.conversationId || activeConversationIdRef.current;
    if (!targetConversationId || targetConversationId === 'pending-conversation') {
      return;
    }
    const replacedMessageIds = normalizePersistedMessageIds(
      currentMessages.slice(targetMessageIndex).map((message) => message.id),
    );
    if (replacedMessageIds.length > 0) {
      await ChatApi.deleteConversationMessages(token, targetConversationId, replacedMessageIds);
    }

    abortControllerRef.current?.abort();
    const streamSessionId = createStreamSessionId();
    activeStreamSessionIdRef.current = streamSessionId;
    const streamAbortController = new AbortController();
    abortControllerRef.current = streamAbortController;

    const optimisticUserId = `optimistic-edit-user-${Date.now()}`;
    const optimisticAssistantId = `optimistic-edit-assistant-${Date.now()}`;
    streamMcpCallsRef.current[optimisticAssistantId] = [];
    streamStateRef.current = {
      conversationId: targetConversationId,
      activeMessageId: optimisticAssistantId,
    };

    const reusedAttachmentIds = normalizePersistedMessageIds(
      (targetMessage.attachments ?? []).map((attachment) => attachment.id),
    );
    const nextMessages: ChatMessageItem[] = [
      ...currentMessages.slice(0, targetMessageIndex),
      {
        ...targetMessage,
        id: optimisticUserId,
        conversationId: targetConversationId,
        content: normalizedContent,
        status: 'COMPLETED',
      },
      {
        id: optimisticAssistantId,
        conversationId: targetConversationId,
        role: 'ASSISTANT',
        content: '',
        processCards: [],
        timelineItems: [],
        status: 'streaming',
      },
    ];
    setMessages(nextMessages);
    persistConversationState(targetConversationId, conversations, {
      messages: nextMessages,
      executionSteps,
      references,
      artifacts,
      currentExperts,
      currentSkills,
      currentMcps,
    });
    setIsStreaming(true);
    setStreamError('');
    hideStreamQueueState();

    try {
      const response = await fetch(
        buildStreamRequestUrl(
          normalizedContent,
          targetConversationId,
          workspaceId,
          deepThinkingEnabled,
          mcpConnected,
          selectedMcpCodes,
          targetMessage.skillCodes ?? selectedSkillCodes,
          selectedExpertCode,
          workspacePath,
          reusedAttachmentIds,
        ),
        {
          headers: {
            satoken: token,
          },
          signal: streamAbortController.signal,
        },
      );
      await ChatApi.assertStreamAuthorized(response);
      await consumeSseStream(response, optimisticAssistantId, streamSessionId);
      if (!isActiveStreamSession(streamSessionId)) {
        return;
      }
      const nextConversations = await loadConversations(token);
      await selectConversation(
        targetConversationId,
        nextConversations,
        streamMcpCallsRef.current[optimisticAssistantId],
        true,
        true,
      );
      refreshWorkspaceGroups('all');
    } catch (error) {
      if (!isActiveStreamSession(streamSessionId)) {
        return;
      }
      if (error instanceof DOMException && error.name === 'AbortError') {
        setStreamError('已停止当前生成');
      } else if (error instanceof ChatApi.UnauthorizedError) {
        onUnauthorizedRef.current?.();
      } else {
        const message = error instanceof Error ? error.message : UserErrorMessages.CHAT_REQUEST_FAILED;
        setStreamError(message);
        setMessages((previousMessages) =>
          previousMessages.map((item) =>
            item.id === optimisticAssistantId
              ? {
                  ...item,
                  status: 'error',
                  errorMessage: message,
                }
              : item,
          ),
        );
      }
    } finally {
      delete streamMcpCallsRef.current[optimisticAssistantId];
      if (abortControllerRef.current === streamAbortController) {
        abortControllerRef.current = null;
      }
      if (isActiveStreamSession(streamSessionId)) {
        activeStreamSessionIdRef.current = null;
        setIsStreaming(false);
      }
    }
  };

  /**
   * 在指定分组上下文内切换会话置顶状态，并把结果写回本地快照。
   * @param conversationId 会话标识。
   * @param actionContext 会话所属分组上下文。
   * @returns 切换后是否处于置顶状态。
   */
  const toggleConversationPin = async (
    conversationId: string,
    actionContext: ConversationActionContext,
  ) => {
    const snapshot = readWorkspaceSnapshot(actionContext.partitionKey);
    const currentPinnedConversationIds = snapshot.pinnedConversationIds ?? [];
    const isPinned = currentPinnedConversationIds.includes(conversationId);
    const nextPinnedConversationIds = isPinned
      ? currentPinnedConversationIds.filter((item) => item !== conversationId)
      : [conversationId, ...currentPinnedConversationIds.filter((item) => item !== conversationId)];
    const nextConversations = applyPinnedConversationOrder(
      snapshot.conversations,
      sanitizePinnedConversationIds(nextPinnedConversationIds, snapshot.conversations),
    );
    writeWorkspaceSnapshot(actionContext.partitionKey, {
      ...snapshot,
      pinnedConversationIds: sanitizePinnedConversationIds(nextPinnedConversationIds, nextConversations),
      conversations: nextConversations,
    });
    if (actionContext.partitionKey === activeWorkspacePartitionKey) {
      setConversations(nextConversations);
    }
    refreshWorkspaceGroups('all');
    return !isPinned;
  };

  /**
   * 导出单会话到指定格式；若本地快照缺少内容，则先按会话接口补齐回放。
   * @param conversationId 会话标识。
   * @param format 导出格式。
   * @param actionContext 会话所属分组上下文。
   */
  const exportConversation = async (
    conversationId: string,
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => {
    await exportConversations([conversationId], format, actionContext);
  };

  /**
   * 批量导出多个会话。
   * @param conversationIds 会话标识列表。
   * @param format 导出格式。
   * @param actionContext 会话所属分组上下文。
   */
  const exportConversations = async (
    conversationIds: string[],
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => {
    const normalizedConversationIds = Array.from(new Set(conversationIds.filter(Boolean)));
    if (normalizedConversationIds.length === 0) {
      return;
    }
    const exportRecords = [];
    for (const conversationId of normalizedConversationIds) {
      exportRecords.push(await resolveConversationExportRecord(conversationId, actionContext));
    }
    const fileName = buildConversationExportFileName(
      normalizedConversationIds.length === 1
        ? exportRecords[0]?.title || normalizedConversationIds[0]
        : `批量导出-${normalizedConversationIds.length}-会话`,
      format,
    );
    const { content, mimeType } = serializeConversationExport(exportRecords, format);
    downloadConversationExport(fileName, content, mimeType);
  };

  /**
   * 批量删除指定会话，并尽量保持当前分组内的回放与列表状态一致。
   * @param conversationIds 会话标识列表。
   * @param actionContext 会话所属分组上下文。
   */
  const deleteConversations = async (
    conversationIds: string[],
    actionContext: ConversationActionContext,
  ) => {
    const normalizedConversationIds = Array.from(new Set(conversationIds.filter(Boolean)));
    if (normalizedConversationIds.length === 0) {
      return;
    }
    for (const conversationId of normalizedConversationIds) {
      await deleteConversation(conversationId);
    }
    const snapshot = readWorkspaceSnapshot(actionContext.partitionKey);
    const nextPinnedConversationIds = sanitizePinnedConversationIds(
      (snapshot.pinnedConversationIds ?? []).filter(
        (conversationId) => !normalizedConversationIds.includes(conversationId),
      ),
      snapshot.conversations,
    );
    writeWorkspaceSnapshot(actionContext.partitionKey, {
      ...snapshot,
      pinnedConversationIds: nextPinnedConversationIds,
      conversations: applyPinnedConversationOrder(snapshot.conversations, nextPinnedConversationIds),
    });
    refreshWorkspaceGroups('all');
  };

  /**
   * 将后端返回的分享路径规范成当前站点下的绝对地址，避免前端复制到的是相对路径。
   * @param shareUrl 后端返回的分享路径。
   * @param baseUrl 当前站点地址。
   * @returns 可直接复制的绝对 URL。
   */
  function resolveAbsoluteShareUrl(shareUrl: string, baseUrl: string) {
    const normalizedShareUrl = shareUrl.trim();
    if (!normalizedShareUrl) {
      return '';
    }
    try {
      return new URL(normalizedShareUrl, baseUrl).toString();
    } catch {
      return normalizedShareUrl;
    }
  }

  /**
   * 为指定会话生成分享链接；可选消息范围由公开页查询参数负责过滤。
   * @param conversationId 会话标识。
   * @param options 分享范围。
   */
  const shareConversation = async (
    conversationId: string,
    options?: ShareConversationOptions,
  ) => {
    const token = currentToken();
    if (!token) {
      onUnauthorizedRef.current?.();
      return '';
    }
    const shareResult = await ChatApi.shareConversation(token, conversationId, options);
    return resolveAbsoluteShareUrl(shareResult.shareUrl, window.location.origin);
  };

  /**
   * 重新生成指定会话的助手回复；传入消息 ID 时前端先覆盖原槽位再重新拉流。
   * @param conversationId 会话标识。
   * @param options 重新生成定位参数。
   */
  const regenerateConversation = async (
    conversationId: string,
    options?: RegenerateConversationOptions,
  ) => {
    const token = currentToken();
    if (!token) {
      onUnauthorizedRef.current?.();
      return;
    }
    if (options?.assistantMessageId) {
      await regenerateConversationFromMessage(token, conversationId, options.assistantMessageId);
      return;
    }
    await ChatApi.regenerateConversation(token, conversationId);
    const nextConversations = await loadConversations(token);
    if (activeConversationIdRef.current === conversationId) {
      await selectConversation(conversationId, nextConversations, undefined, true, false);
    }
  };

  /**
   * 覆盖式重新生成：删除原助手消息及之后内容，再把新流写入同一位置。
   * @param token 当前登录令牌。
   * @param conversationId 会话标识。
   * @param assistantMessageId 被重新生成的助手消息。
   */
  const regenerateConversationFromMessage = async (
    token: string,
    conversationId: string,
    assistantMessageId: string,
  ) => {
    const currentMessages = messagesRef.current;
    const assistantMessageIndex = currentMessages.findIndex((message) => message.id === assistantMessageId);
    if (assistantMessageIndex < 0) {
      return;
    }
    const previousUserMessage = [...currentMessages.slice(0, assistantMessageIndex)]
      .reverse()
      .find((message) => message.role === 'USER');
    if (!previousUserMessage) {
      return;
    }
    abortControllerRef.current?.abort();
    const streamSessionId = createStreamSessionId();
    activeStreamSessionIdRef.current = streamSessionId;
    const streamAbortController = new AbortController();
    abortControllerRef.current = streamAbortController;
    const optimisticAssistantId = `optimistic-regenerate-assistant-${Date.now()}`;
    streamMcpCallsRef.current[optimisticAssistantId] = [];
    streamStateRef.current = {
      conversationId,
      activeMessageId: optimisticAssistantId,
    };
    const nextMessages: ChatMessageItem[] = [
      ...currentMessages.slice(0, assistantMessageIndex),
      {
        id: optimisticAssistantId,
        conversationId,
        role: 'ASSISTANT',
        content: '',
        processCards: [],
        timelineItems: [],
        status: 'streaming',
      },
    ];
    setMessages(nextMessages);
    persistConversationState(conversationId, conversations, {
      messages: nextMessages,
      executionSteps,
      references,
      artifacts,
      currentExperts,
      currentSkills,
      currentMcps,
    });
    setIsStreaming(true);
    setStreamError('');
    hideStreamQueueState();
    try {
      const response = await fetch(
        buildStreamRequestUrl(
          previousUserMessage.content,
          conversationId,
          workspaceId,
          deepThinkingEnabled,
          mcpConnected,
          selectedMcpCodes,
          selectedSkillCodes,
          selectedExpertCode,
          workspacePath,
          [],
        ),
        {
          headers: {
            satoken: token,
          },
          signal: streamAbortController.signal,
        },
      );
      await ChatApi.assertStreamAuthorized(response);
      await consumeSseStream(response, optimisticAssistantId, streamSessionId);
      if (isActiveStreamSession(streamSessionId)) {
        await loadConversations(token);
      }
      refreshWorkspaceGroups('all');
    } catch (error) {
      if (!isActiveStreamSession(streamSessionId)) {
        return;
      }
      if (error instanceof DOMException && error.name === 'AbortError') {
        setStreamError('已停止当前生成');
      } else if (error instanceof ChatApi.UnauthorizedError) {
        onUnauthorizedRef.current?.();
      } else {
        const message = error instanceof Error ? error.message : UserErrorMessages.CHAT_REQUEST_FAILED;
        setStreamError(message);
        setMessages((previousMessages) =>
          previousMessages.map((item) =>
            item.id === optimisticAssistantId
              ? {
                  ...item,
                  status: 'error',
                  errorMessage: message,
                }
              : item,
          ),
        );
      }
    } finally {
      delete streamMcpCallsRef.current[optimisticAssistantId];
      if (abortControllerRef.current === streamAbortController) {
        abortControllerRef.current = null;
      }
      if (isActiveStreamSession(streamSessionId)) {
        activeStreamSessionIdRef.current = null;
        setIsStreaming(false);
      }
    }
  };

  /**
   * 读取当前登录态 token。
   * @returns token 或 null。
   */
  const currentToken = () => AuthStorage.getSession()?.token ?? null;

  /**
   * 清空前端工作台状态，避免退出登录后仍显示上个用户会话。
   */
  const resetWorkspace = () => {
    abortControllerRef.current?.abort();
    activeStreamSessionIdRef.current = null;
    setConversations([]);
    clearConversationPlayback();
    setSampleQuestions([]);
    setAvailableExperts([]);
    setCurrentExperts([]);
    setSelectedExpertCode(null);
    setAvailableSkills([]);
    setCurrentSkills([]);
    setSelectedSkillCodes([]);
    setAvailableMcps([]);
    setCurrentMcps([]);
    setSelectedMcpCodes([]);
    setMcpConnected(false);
    setIsStreaming(false);
    setIsCancelling(false);
    hideStreamQueueState();
    setStreamError('');
    setInputValue('');
    clearPendingAttachments();
    setRenameDialogState({ isOpen: false, conversationId: null, initialTitle: '', actionContext: undefined });
    setDeleteDialogState({ isOpen: false, conversationId: null, title: '', actionContext: undefined });
    streamStateRef.current = null;
  };

  /**
   * 增量消费后端 SSE，并把关键事件同步到前端三栏状态。
   * @param response fetch 返回的 SSE 响应。
   * @param optimisticAssistantId 当前流式助手消息标识。
   * @param streamSessionId 当前流会话编号。
   */
  const consumeSseStream = async (
    response: Response,
    optimisticAssistantId: string,
    streamSessionId: number,
  ) => {
    const reader = response.body?.getReader();
    if (!reader) {
      return;
    }
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        const { events } = extractSseEvents(`${buffer}\n\n`);
        for (const event of events) {
          applySseEvent(event.event, event.data, optimisticAssistantId, streamSessionId);
        }
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      const { events, remainder } = extractSseEvents(buffer);
      buffer = remainder;
      for (const event of events) {
        applySseEvent(event.event, event.data, optimisticAssistantId, streamSessionId);
      }
    }
  };

  /**
   * 根据事件类型更新会话、消息和右栏状态。
   * @param eventName 事件名。
   * @param payload 事件载荷。
   * @param optimisticAssistantId 当前流式助手消息标识。
   * @param streamSessionId 当前流会话编号。
   */
  const applySseEvent = (
    eventName: string,
    payload: unknown,
    optimisticAssistantId: string,
    streamSessionId: number,
  ) => {
    if (!isActiveStreamSession(streamSessionId)) {
      return;
    }
    if (eventName === 'meta' && isRecord(payload)) {
      const conversationId = String(payload.conversationId ?? '');
      if (conversationId) {
        streamStateRef.current = {
          conversationId,
          activeMessageId: optimisticAssistantId,
        };
        setActiveConversationId(conversationId);
        upsertConversationFromStreamMeta(conversationId);
        // 业务约束：流式过程中一旦后端分配了新会话 ID，需立刻写入 URL 以支持刷新恢复。
        writeConversationIdToUrl(conversationId);
      }
      hideStreamQueueState();
      return;
    }

    if (eventName === 'queued' && isRecord(payload)) {
      const position = Math.max(1, Number(payload.position ?? 1));
      scheduleStreamQueueState(position);
      setStreamError('');
      return;
    }

    if (eventName === 'queue-accepted') {
      hideStreamQueueState();
      return;
    }

    if (eventName === 'reject' && isRecord(payload)) {
      const reason = String(payload.reason ?? '').trim();
      const resolvedMessage = resolveQueueRejectMessage(reason);
      hideStreamQueueState();
      setStreamError(resolvedMessage);
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === optimisticAssistantId
            ? {
                ...message,
                status: 'error',
                errorMessage: resolvedMessage,
                searchProgress: message.searchProgress
                  ? {
                      ...message.searchProgress,
                      status: 'error',
                    }
                  : undefined,
              }
            : message,
        ),
      );
      return;
    }

    if (eventName === 'message' && isRecord(payload) && payload.type === 'response') {
      const delta = String(payload.delta ?? '');
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === optimisticAssistantId
            ? (() => {
                const currentProcessCards = message.processCards ?? [];
                // 正文 token 本身已经是最终回答，过程链路只保留真实分析与工具事件，避免展示无信息量的固定“整理中”步骤。
                const nextProcessCards = finalizeCardsByType(currentProcessCards, ['analysis']);
                return {
                  ...message,
                  content: `${message.content}${delta}`,
                  processCards: nextProcessCards,
                  timelineItems: appendContentToTimeline(
                    syncProcessCardsToTimeline(
                      message.timelineItems,
                      currentProcessCards,
                      nextProcessCards,
                    ),
                    delta,
                  ),
                };
              })()
            : message,
        ),
      );
      return;
    }

    if (eventName === 'thinking' && isRecord(payload) && payload.type === 'thinking') {
      const delta = String(payload.delta ?? '');
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === optimisticAssistantId
            ? (() => {
                const currentProcessCards = message.processCards ?? [];
                const thinkingCardId = resolveThinkingProcessCardId(currentProcessCards);
                const existingThinkingCard = currentProcessCards.find((card) => card.id === thinkingCardId);
                const nextThinkingSummary = `${existingThinkingCard?.summary ?? ''}${delta}`;
                const nextThinkingContent = `${message.thinkingContent ?? ''}${delta}`;
                const nextProcessCards = upsertProcessCard(
                  currentProcessCards,
                  buildAnalysisProcessCard(
                    thinkingCardId,
                    nextThinkingSummary,
                    thinkingCardId === 'analysis-after-tools' ? '分析检索结果' : '分析问题',
                  ),
                );
                return {
                  ...message,
                  thinkingContent: nextThinkingContent,
                  processCards: nextProcessCards,
                  timelineItems: syncProcessCardsToTimeline(
                    message.timelineItems,
                    currentProcessCards,
                    nextProcessCards,
                  ),
                };
              })()
            : message,
        ),
      );
      return;
    }

    if ((eventName === 'mcp-call' || eventName === 'tool-call') && isRecord(payload)) {
      // Local model tools share the MCP process-card path so shell/apply_patch stays in the main message flow.
      const phase = resolveMcpCallPhase(payload.phase);
      const status = resolveMcpCallStatus(phase);
      const callId = normalizeOptionalString(payload.callId);
      const params = normalizeMcpCallParams(payload.params);
      const rawResult = payload.rawResult ?? payload.content;
      const resultMetadata =
        isRecord(payload.resultMetadata)
          ? payload.resultMetadata
          : isRecord(payload.metadata)
            ? payload.metadata
            : undefined;
      const call: McpCallItem = {
        callId,
        toolId: String(payload.toolId ?? ''),
        displayName: String(payload.displayName ?? payload.toolId ?? ''),
        input: String(payload.input ?? ''),
        content: String(payload.content ?? ''),
        metadata: isRecord(payload.metadata) ? payload.metadata : undefined,
        phase,
        status,
        params,
        rawResult,
        resultMetadata,
        reactThought: normalizeOptionalString(payload.reactThought),
        reactAction: normalizeOptionalString(payload.reactAction),
        reactObservation: normalizeOptionalString(payload.reactObservation),
        progressStage: normalizeOptionalString(payload.progressStage),
        progressText: normalizeOptionalString(payload.progressText),
        progressDetail: isRecord(payload.progressDetail) ? payload.progressDetail : undefined,
        startedAt: normalizeOptionalString(payload.startedAt),
        finishedAt: normalizeOptionalString(payload.finishedAt),
        errorMessage: normalizeOptionalString(payload.errorMessage),
      };
      streamMcpCallsRef.current[optimisticAssistantId] = mergeMcpCallsById(
        streamMcpCallsRef.current[optimisticAssistantId] ?? [],
        call,
      );
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === optimisticAssistantId
            ? (() => {
                const currentProcessCards = message.processCards ?? [];
                const nextProcessCards = mergeMcpCallIntoProcessCards(currentProcessCards, call);
                return {
                  ...message,
                  mcpCalls: mergeMcpCallsById(message.mcpCalls ?? [], call),
                  processCards: nextProcessCards,
                  timelineItems: syncProcessCardsToTimeline(
                    message.timelineItems,
                    currentProcessCards,
                    nextProcessCards,
                  ),
                };
              })()
            : message,
        ),
      );
      return;
    }

    if (eventName === 'step' && isRecord(payload)) {
      const stepType = String(payload.stepType ?? '');
      setExecutionSteps((previousSteps) =>
        upsertById(previousSteps, {
          id: String(payload.id ?? ''),
          runId: String(payload.runId ?? ''),
          stepType,
          stepTitle: String(payload.stepTitle ?? ''),
          stepStatus: String(payload.stepStatus ?? ''),
          sequenceNo: Number(payload.sequenceNo ?? 0),
          content: typeof payload.content === 'string' ? payload.content : undefined,
        }),
      );
      if (isSearchStepType(stepType)) {
        // 业务意图：只要进入搜索步骤就立即展示进度面板，哪怕此时还没有返回任何来源条目。
        setMessages((previousMessages) =>
          previousMessages.map((message) =>
            message.id === optimisticAssistantId
              ? (() => {
                  const currentProcessCards = message.processCards ?? [];
                  const nextProcessCards = mergeSearchStepIntoProcessCards(
                    currentProcessCards,
                    {
                      id: String(payload.id ?? 'search-step'),
                      title: String(payload.stepTitle ?? '调用网页搜索'),
                      summary:
                        typeof payload.content === 'string' && payload.content.trim().length > 0
                          ? payload.content
                          : '正在检索实时资料。',
                      status:
                        String(payload.stepStatus ?? '').trim().toUpperCase() === 'COMPLETED'
                          ? 'completed'
                          : 'running',
                    },
                  );
                  return {
                    ...message,
                    searchProgress: {
                      status: 'running',
                      items: message.searchProgress?.items ?? [],
                    },
                    processCards: nextProcessCards,
                    timelineItems: syncProcessCardsToTimeline(
                      message.timelineItems,
                      currentProcessCards,
                      nextProcessCards,
                    ),
                  };
                })()
              : message,
          ),
        );
      }
      return;
    }

    if (eventName === 'reference' && isRecord(payload)) {
      const nextReference = {
        id: String(payload.id ?? ''),
        runId: String(payload.runId ?? ''),
        messageId: payload.messageId == null ? undefined : String(payload.messageId),
        conversationId: String(payload.conversationId ?? ''),
        sourceType: typeof payload.sourceType === 'string' ? payload.sourceType : undefined,
        title: String(payload.title ?? ''),
        url: typeof payload.url === 'string' ? payload.url : undefined,
        siteName: typeof payload.siteName === 'string' ? payload.siteName : undefined,
        snippet: typeof payload.snippet === 'string' ? payload.snippet : undefined,
        rankNo: payload.rankNo == null ? undefined : Number(payload.rankNo),
      };
      setReferences((previousReferences) =>
        upsertById(previousReferences, nextReference),
      );
      // 关键约束：搜索来源在流式阶段逐条到达，需同步追加到助手消息内以驱动“1/2/3...”实时进度反馈。
      setMessages((previousMessages) =>
        previousMessages.map((message) => {
          if (message.id !== optimisticAssistantId) {
            return message;
          }
          const currentProcessCards = message.processCards ?? [];
          const nextProcessCards = mergeReferenceIntoProcessCards(
            currentProcessCards,
            nextReference,
          );
          return {
            ...message,
            searchProgress: {
              status: 'running',
              items: upsertById(message.searchProgress?.items ?? [], {
                id: nextReference.id,
                title: nextReference.title,
                url: nextReference.url,
                siteName: nextReference.siteName,
              }),
            },
            processCards: nextProcessCards,
            timelineItems: syncProcessCardsToTimeline(
              message.timelineItems,
              currentProcessCards,
              nextProcessCards,
            ),
          };
        }),
      );
      return;
    }

    if (eventName === 'artifact' && isRecord(payload)) {
      setArtifacts((previousArtifacts) =>
        upsertById(previousArtifacts, {
          id: String(payload.id ?? ''),
          runId: String(payload.runId ?? ''),
          messageId: payload.messageId == null ? undefined : String(payload.messageId),
          conversationId: String(payload.conversationId ?? ''),
          artifactType: String(payload.artifactType ?? ''),
          name: String(payload.name ?? ''),
          mimeType: typeof payload.mimeType === 'string' ? payload.mimeType : undefined,
          storagePath: String(payload.storagePath ?? ''),
          contentPreview:
            typeof payload.preview === 'string'
              ? payload.preview
              : typeof payload.contentPreview === 'string'
                ? payload.contentPreview
                : undefined,
        }),
      );
      return;
    }

    if (eventName === 'finish' && isRecord(payload)) {
      const finishConversationId = String(payload.conversationId ?? '').trim();
      if (finishConversationId) {
        const finishTitle = typeof payload.title === 'string' ? payload.title : undefined;
        // 关键约束：即使后端未先下发 meta，也要在 finish 阶段收敛到真实会话 ID，
        // 避免后续回放请求继续命中 pending-conversation 导致左侧历史延迟或丢失。
        streamStateRef.current = {
          conversationId: finishConversationId,
          activeMessageId: optimisticAssistantId,
        };
        setActiveConversationId(finishConversationId);
        upsertConversationFromStreamMeta(finishConversationId, finishTitle);
        writeConversationIdToUrl(finishConversationId);
      }
      hideStreamQueueState();
      // 业务约束：finish 事件表示模型输出已完成，需立刻恢复输入区发送态，避免“停止”按钮滞留。
      setIsStreaming(false);
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === optimisticAssistantId
              ? {
                  ...message,
                  content: String(payload.content ?? message.content),
                  status: 'done',
                  searchProgress: message.searchProgress
                  ? {
                      ...message.searchProgress,
                      status: 'completed',
                    }
                  : undefined,
                  processCards: finalizeProcessCards(message.processCards ?? [], 'completed'),
                  timelineItems: finalizeTimelineProcessCards(
                    ensureTimelineContent(
                      message.timelineItems,
                      String(payload.content ?? message.content),
                    ),
                    'completed',
                  ),
              }
            : message,
        ),
      );
      return;
    }

    if (eventName === 'cancel') {
      hideStreamQueueState();
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === optimisticAssistantId
              ? {
                  ...message,
                  status: 'cancelled',
                  searchProgress: message.searchProgress
                  ? {
                      ...message.searchProgress,
                      status: 'cancelled',
                    }
                  : undefined,
                  processCards: finalizeProcessCards(message.processCards ?? [], 'cancelled'),
                  timelineItems: finalizeTimelineProcessCards(
                    message.timelineItems,
                    'cancelled',
                  ),
              }
            : message,
        ),
      );
      return;
    }

    if (eventName === 'error' && isRecord(payload)) {
      hideStreamQueueState();
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === optimisticAssistantId
              ? {
                  ...message,
                  status: 'error',
                  errorMessage: String(payload.message ?? UserErrorMessages.CHAT_REQUEST_FAILED),
                  searchProgress: message.searchProgress
                  ? {
                      ...message.searchProgress,
                      status: 'error',
                    }
                  : undefined,
                  processCards: finalizeProcessCards(message.processCards ?? [], 'error'),
                  timelineItems: finalizeTimelineProcessCards(
                    message.timelineItems,
                    'error',
                  ),
              }
            : message,
        ),
      );
    }
  };

  return {
    runtimeTargets,
    activeRuntimeTarget,
    workspaceGroups,
    activeWorkspacePartitionKey,
    workspacePath,
    workspaceLabel,
    workspaceRuntimeTarget: activeRuntimeTarget,
    conversations,
    activeConversationId,
    messages,
    executionSteps,
    references,
    artifacts,
    sampleQuestions,
    availableExperts,
    selectedExpertCode,
    currentExperts,
    availableSkills,
    currentSkills,
    selectedSkillCodes,
    availableMcps,
    currentMcps,
    selectedMcpCodes,
    mcpConnected,
    isStreaming,
    isCancelling,
    deepThinkingEnabled,
    streamQueueState,
    streamError,
    inputValue,
    pendingAttachments,
    isBootstrapping,
    setInputValue,
    setSelectedExpertCode,
    addPendingAttachments,
    removePendingAttachment,
    clearPendingAttachments,
    setDeepThinkingEnabled,
    setSelectedSkillCodes,
    setSelectedMcpCodes,
    setMcpConnected,
    setActiveRuntimeTarget,
    pickRepositoryDirectory,
    setActiveWorkspacePath,
    submitMessage,
    cancelCurrentStream,
    selectConversation,
    selectConversationInWorkspace,
    startNewConversation,
    renameConversation,
    deleteConversation,
    deleteConversationMessages,
    shareConversation,
    regenerateConversation,
    resendUserMessage,
    toggleConversationPin,
    exportConversation,
    exportConversations,
    deleteConversations,
    renameDialog: {
      ...renameDialogState,
      open: (conversationId: string, initialTitle: string, actionContext?: ConversationActionContext) =>
        setRenameDialogState({ isOpen: true, conversationId, initialTitle, actionContext }),
      close: () =>
        setRenameDialogState({
          isOpen: false,
          conversationId: null,
          initialTitle: '',
          actionContext: undefined,
        }),
    },
    deleteDialog: {
      ...deleteDialogState,
      open: (conversationId: string, title: string, actionContext?: ConversationActionContext) =>
        setDeleteDialogState({ isOpen: true, conversationId, title, actionContext }),
      close: () =>
        setDeleteDialogState({
          isOpen: false,
          conversationId: null,
          title: '',
          actionContext: undefined,
        }),
    },
  } satisfies ChatWorkspaceController;

  /**
   * 为导出链路读取或补齐指定会话的本地回放记录。
   * @param conversationId 会话标识。
   * @param actionContext 会话所属分组上下文。
   * @returns 可导出的结构化记录。
   */
  async function resolveConversationExportRecord(
    conversationId: string,
    actionContext: ConversationActionContext,
  ) {
    const snapshot = readWorkspaceSnapshot(actionContext.partitionKey);
    const existingConversation = snapshot.conversations.find((conversation) => conversation.id === conversationId);
    const token = currentToken();
    if (!token) {
      throw new Error(UserErrorMessages.AUTH_SESSION_EXPIRED);
    }
    const existingRecord = snapshot.conversationRecords?.[conversationId];
    if (
      existingRecord &&
      (
        existingRecord.messages.length > 0 ||
        existingRecord.executionSteps.length > 0 ||
        existingRecord.references.length > 0 ||
        existingRecord.artifacts.length > 0
      )
    ) {
      return {
        id: conversationId,
        title: existingConversation?.title ?? `会话 ${conversationId}`,
        exportedAt: new Date().toISOString(),
        ...existingRecord,
      };
    }

    const [
      messages,
      executionSteps,
      references,
      artifacts,
      currentExperts,
      currentSkills,
      currentMcps,
    ] = await Promise.all([
      ChatApi.listMessages(token, conversationId),
      ChatApi.listSteps(token, conversationId),
      ChatApi.listReferences(token, conversationId),
      ChatApi.listArtifacts(token, conversationId),
      ChatApi.listCurrentExperts(token, conversationId),
      ChatApi.listCurrentSkills(token, conversationId),
      ChatApi.listCurrentMcps(token, conversationId),
    ]);

    saveConversationRecordToWorkspace(
      actionContext.runtimeTarget,
      actionContext.workspacePath,
      conversationId,
      snapshot.conversations,
      {
        messages,
        executionSteps,
        references,
        artifacts,
        currentExperts,
        currentSkills,
        currentMcps,
      },
    );
    return {
      id: conversationId,
      title: existingConversation?.title ?? `会话 ${conversationId}`,
      exportedAt: new Date().toISOString(),
      messages,
      executionSteps,
      references,
      artifacts,
      currentExperts,
      currentSkills,
      currentMcps,
    };
  }

  /**
   * 生成导出文件名，避免会话标题中的非法字符影响浏览器下载。
   * @param title 会话标题。
   * @param format 导出格式。
   * @returns 适用于下载的文件名。
   */
  function buildConversationExportFileName(
    title: string,
    format: ConversationExportFormat,
  ) {
    const normalizedTitle = title.replace(/[\\/:*?"<>|]+/g, '-').trim() || 'conversation';
    const extensionByFormat: Record<ConversationExportFormat, string> = {
      word: 'doc',
      pdf: 'pdf',
      txt: 'txt',
      json: 'json',
      markdown: 'md',
    };
    return `${normalizedTitle}.${extensionByFormat[format]}`;
  }

  /**
   * 根据导出格式生成下载内容；Word/PDF 当前导出可读文本载体，后续可替换为真实二进制生成器。
   * @param exportRecords 会话导出记录。
   * @param format 导出格式。
   * @returns 下载内容与 MIME 类型。
   */
  function serializeConversationExport(
    exportRecords: Array<{
      id: string;
      title: string;
      exportedAt: string;
      messages: ChatMessageItem[];
      executionSteps: ExecutionStepItem[];
      references: ReferenceItem[];
      artifacts: ArtifactItem[];
    }>,
    format: ConversationExportFormat,
  ) {
    if (format === 'json') {
      return {
        content: JSON.stringify(
          {
            exportedAt: new Date().toISOString(),
            count: exportRecords.length,
            conversations: exportRecords,
          },
          null,
          2,
        ),
        mimeType: 'application/json;charset=utf-8',
      };
    }
    if (format === 'txt') {
      return {
        content: serializeConversationExportAsPlainText(exportRecords),
        mimeType: 'text/plain;charset=utf-8',
      };
    }
    if (format === 'word') {
      return {
        content: serializeConversationExportAsHtml(exportRecords),
        mimeType: 'application/msword;charset=utf-8',
      };
    }
    if (format === 'pdf') {
      return {
        content: serializeConversationExportAsPlainText(exportRecords),
        mimeType: 'application/pdf;charset=utf-8',
      };
    }
    return {
      content: serializeConversationExportAsMarkdown(exportRecords),
      mimeType: 'text/markdown;charset=utf-8',
    };
  }

  /**
   * 将导出记录序列化为 Markdown 文本，便于用户直接阅读或转发。
   * @param exportRecords 导出记录列表。
   * @returns Markdown 文本。
   */
  function serializeConversationExportAsMarkdown(
    exportRecords: Array<{
      id: string;
      title: string;
      exportedAt: string;
      messages: ChatMessageItem[];
      executionSteps: ExecutionStepItem[];
      references: ReferenceItem[];
      artifacts: ArtifactItem[];
    }>,
  ) {
    return exportRecords
      .map((record) => {
        const messageLines = record.messages.map((message) => {
          const roleLabel =
            message.role === 'USER'
              ? '用户'
              : message.role === 'ASSISTANT'
                ? '助手'
                : '系统';
          return `### ${roleLabel}\n\n${message.content || ''}`;
        });
        const referenceLines = record.references.length
          ? [
              '## 来源',
              '',
              ...record.references.map((reference) => {
                const title = reference.title || reference.siteName || '未命名来源';
                const url = reference.url ? ` (${reference.url})` : '';
                return `- ${title}${url}`;
              }),
            ]
          : [];
        const artifactLines = record.artifacts.length
          ? [
              '## 产物',
              '',
              ...record.artifacts.map((artifact) => `- ${artifact.name} [${artifact.artifactType}]`),
            ]
          : [];
        return [
          `# ${record.title}`,
          '',
          `- 会话 ID：${record.id}`,
          `- 导出时间：${record.exportedAt}`,
          '',
          '## 消息',
          '',
          ...messageLines,
          '',
          ...referenceLines,
          ...(referenceLines.length ? [''] : []),
          ...artifactLines,
        ].join('\n');
      })
      .join('\n\n---\n\n');
  }

  /**
   * 将导出记录序列化为纯文本，供 TXT 与轻量 PDF 下载复用。
   * @param exportRecords 导出记录列表。
   * @returns 纯文本内容。
   */
  function serializeConversationExportAsPlainText(
    exportRecords: Array<{
      id: string;
      title: string;
      exportedAt: string;
      messages: ChatMessageItem[];
    }>,
  ) {
    return exportRecords
      .map((record) => {
        const messageLines = record.messages.map((message) => {
          const roleLabel =
            message.role === 'USER'
              ? '用户'
              : message.role === 'ASSISTANT'
                ? '助手'
                : '系统';
          return `${roleLabel}：\n${message.content || ''}`;
        });
        return [
          record.title,
          `会话 ID：${record.id}`,
          `导出时间：${record.exportedAt}`,
          '',
          ...messageLines,
        ].join('\n\n');
      })
      .join('\n\n----------------\n\n');
  }

  /**
   * 将导出记录序列化为 Word 可打开的 HTML 文档。
   * @param exportRecords 导出记录列表。
   * @returns HTML 文档字符串。
   */
  function serializeConversationExportAsHtml(
    exportRecords: Array<{
      id: string;
      title: string;
      exportedAt: string;
      messages: ChatMessageItem[];
    }>,
  ) {
    const escapeHtml = (value: string) =>
      value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
    const body = exportRecords
      .map((record) => {
        const messageHtml = record.messages
          .map((message) => {
            const roleLabel =
              message.role === 'USER'
                ? '用户'
                : message.role === 'ASSISTANT'
                  ? '助手'
                  : '系统';
            return `<h3>${roleLabel}</h3><p>${escapeHtml(message.content || '').replace(/\n/g, '<br/>')}</p>`;
          })
          .join('');
        return `<section><h1>${escapeHtml(record.title)}</h1><p>会话 ID：${escapeHtml(record.id)}</p><p>导出时间：${escapeHtml(record.exportedAt)}</p>${messageHtml}</section>`;
      })
      .join('<hr/>');
    return `<!doctype html><html><head><meta charset="utf-8"><title>CodingX Conversation Export</title></head><body>${body}</body></html>`;
  }

  /**
   * 触发浏览器下载导出文件。
   * @param fileName 文件名。
   * @param content 文件内容。
   * @param mimeType 文件 MIME 类型。
   */
  function downloadConversationExport(fileName: string, content: string, mimeType: string) {
    const blob = new Blob([content], { type: mimeType });
    const objectUrl = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = fileName;
    anchor.click();
    window.setTimeout(() => {
      window.URL.revokeObjectURL(objectUrl);
    }, 0);
  }

  /**
   * 只刷新真实会话列表，避免在发送后错误回跳到旧会话。
   * @param token 当前登录令牌。
   * @returns 最新会话列表。
   */
  async function loadConversations(token: string, effectiveWorkspaceId: string | null = workspaceId) {
    const shouldKeepLandingState =
      activeConversationId == null && messages.length === 0 && !readConversationIdFromUrl();
    const fallbackWorkspacePath = workspacePath ?? null;
    const currentSnapshot = readWorkspaceSnapshot(
      buildWorkspacePartitionKey(activeRuntimeTarget, fallbackWorkspacePath),
    );
    const persistedActiveConversationId = currentSnapshot.activeConversationId ?? null;
    const effectiveActiveConversationId = activeConversationId ?? persistedActiveConversationId;
    if (activeRuntimeTarget === 'local') {
      const snapshotConversations = applyTaskCompletionReminders(
        currentSnapshot.conversations,
        currentSnapshot.seenTaskFinishedAtByConversationId ?? {},
        effectiveActiveConversationId,
      );
      const nextActiveConversationId =
        activeConversationId ??
        persistedActiveConversationId ??
        (shouldKeepLandingState ? null : (snapshotConversations[0]?.id ?? null));
      setConversations(snapshotConversations);
      upsertWorkspaceSnapshot('local', fallbackWorkspacePath, {
        conversations: snapshotConversations,
        activeConversationId: nextActiveConversationId,
        workspaceLabel: fallbackWorkspacePath
          ? getWorkspaceLabel(fallbackWorkspacePath)
          : getDefaultWorkspaceLabel('local'),
        conversationRecords: currentSnapshot.conversationRecords,
        seenTaskFinishedAtByConversationId: currentSnapshot.seenTaskFinishedAtByConversationId ?? {},
      });
      refreshWorkspaceGroups('all');
      return snapshotConversations;
    }
    const remoteConversations = await ChatApi.listConversations(token, effectiveWorkspaceId);
    // 关键约束：流式生成期间会话列表可能返回慢数据或空数据，不能把 meta 已写入的当前会话从侧栏快照中抹掉。
    const protectedRemoteConversations = hasActiveStreamPlayback()
      ? mergeConversationListById(remoteConversations, currentSnapshot.conversations)
      : remoteConversations;
    const seenTaskFinishedAtByConversationId = markActiveTaskCompletionSeen(
      protectedRemoteConversations,
      currentSnapshot.seenTaskFinishedAtByConversationId ?? {},
      effectiveActiveConversationId,
    );
    if (activeRuntimeTarget === 'cloud') {
      const visibleConversations = applyTaskCompletionReminders(
        filterWorkspaceConversationsByRuntimeTarget(
          protectedRemoteConversations,
          activeRuntimeTarget,
        ),
        seenTaskFinishedAtByConversationId,
        effectiveActiveConversationId,
      );
      const visibleConversationIds = new Set(visibleConversations.map((conversation) => conversation.id));
      const visibleConversationRecords = Object.fromEntries(
        Object.entries(currentSnapshot.conversationRecords ?? {}).filter(([conversationId]) =>
          visibleConversationIds.has(conversationId),
        ),
      );
      const nextActiveConversationId =
        activeConversationId != null && visibleConversationIds.has(activeConversationId)
          ? activeConversationId
          : persistedActiveConversationId != null && visibleConversationIds.has(persistedActiveConversationId)
            ? persistedActiveConversationId
            : shouldKeepLandingState
              ? null
              : (visibleConversations[0]?.id ?? null);
      setConversations(visibleConversations);
      upsertWorkspaceSnapshot(activeRuntimeTarget, fallbackWorkspacePath, {
        conversations: visibleConversations,
        activeConversationId: nextActiveConversationId,
        workspaceLabel: fallbackWorkspacePath
          ? getWorkspaceLabel(fallbackWorkspacePath)
          : getDefaultWorkspaceLabel(activeRuntimeTarget),
        conversationRecords: visibleConversationRecords,
        seenTaskFinishedAtByConversationId,
      });
      refreshWorkspaceGroups('all');
      return visibleConversations;
    }
    const nextWorkspaceConversations = applyTaskCompletionReminders(
      resolveWorkspaceConversations(
        activeRuntimeTarget,
        fallbackWorkspacePath,
        effectiveWorkspaceId,
        protectedRemoteConversations,
      ),
      seenTaskFinishedAtByConversationId,
      effectiveActiveConversationId,
    );
    markWorkspaceConversationOwnership(
      activeRuntimeTarget,
      fallbackWorkspacePath,
      nextWorkspaceConversations.map((conversation) => conversation.id),
    );
    const nextHistoryConversations = filterUnassignedConversations(protectedRemoteConversations);
    const localDefaultSnapshot = readWorkspaceSnapshot(buildWorkspacePartitionKey('local', null));
    const localDefaultPartitionKey = buildWorkspacePartitionKey('local', null);
    const isLocalDefaultActivePartition =
      buildWorkspacePartitionKey(activeRuntimeTarget, fallbackWorkspacePath) === localDefaultPartitionKey;
    const localDefaultSeenTaskFinishedAtByConversationId = isLocalDefaultActivePartition
      ? seenTaskFinishedAtByConversationId
      : (localDefaultSnapshot.seenTaskFinishedAtByConversationId ?? {});
    const mergedLocalDefaultConversations = applyTaskCompletionReminders(
      mergeConversationListById(
        localDefaultSnapshot.conversations,
        nextHistoryConversations,
        { mergeExisting: true },
      ),
      localDefaultSeenTaskFinishedAtByConversationId,
      isLocalDefaultActivePartition ? effectiveActiveConversationId : null,
    );
    setConversations(nextWorkspaceConversations);
    upsertWorkspaceSnapshot(activeRuntimeTarget, fallbackWorkspacePath, {
      conversations: nextWorkspaceConversations,
      activeConversationId:
        activeConversationId ??
        persistedActiveConversationId ??
        (shouldKeepLandingState ? null : (nextWorkspaceConversations[0]?.id ?? null)),
      workspaceLabel: fallbackWorkspacePath
        ? getWorkspaceLabel(fallbackWorkspacePath)
        : getDefaultWorkspaceLabel(activeRuntimeTarget),
      seenTaskFinishedAtByConversationId,
    });
    upsertWorkspaceSnapshot('local', null, {
      conversations: mergedLocalDefaultConversations,
      activeConversationId: localDefaultSnapshot.activeConversationId ?? null,
      workspaceLabel: getDefaultWorkspaceLabel('local'),
      conversationRecords: localDefaultSnapshot.conversationRecords,
      seenTaskFinishedAtByConversationId: localDefaultSeenTaskFinishedAtByConversationId,
    });
    if (
      activeConversationId &&
      !nextWorkspaceConversations.some((conversation) => conversation.id === activeConversationId)
    ) {
      const nextConversationId = nextWorkspaceConversations[0]?.id ?? null;
      setActiveConversationId(nextConversationId);
      writeConversationIdToUrl(nextConversationId);
    }
    refreshWorkspaceGroups('all');
    return nextWorkspaceConversations;
  }

  /**
   * 清空当前主区与右栏回放状态，但保留左侧真实会话历史。
   */
  function clearConversationPlayback(keepUrl = false) {
    setActiveConversationId(null);
    if (!keepUrl) {
      writeConversationIdToUrl(null);
    }
    setMessages([]);
    setExecutionSteps([]);
    setReferences([]);
    setArtifacts([]);
    setCurrentExperts([]);
    setCurrentSkills([]);
    setCurrentMcps([]);
  }

  /**
   * 将当前会话回放保存到指定工作空间分区。
   * @param conversationId 会话标识。
   * @param conversationList 会话列表。
   * @param record 快照内容。
   */
  function persistConversationState(
    conversationId: string | null,
    conversationList: ConversationItem[],
      record: {
        messages: ChatMessageItem[];
        executionSteps: ExecutionStepItem[];
        references: ReferenceItem[];
        artifacts: ArtifactItem[];
        currentExperts: CurrentExpertItem[];
        currentSkills: CurrentSkillItem[];
        currentMcps: CurrentMcpItem[];
      },
  ) {
    if (!activeWorkspacePartitionKey) {
      return;
    }
    saveConversationRecordToWorkspace(
      activeRuntimeTarget,
      workspacePath,
      conversationId,
      conversationList,
      record,
    );
    // 历史分组独立维护：会话保存后仅刷新当前工作空间快照，不回写到历史分组。
    upsertWorkspaceSnapshot(activeRuntimeTarget, workspacePath, {
      conversations: conversationList,
      activeConversationId: conversationId,
    });
    refreshWorkspaceGroups('all');
  }

  /**
   * 从快照恢复指定会话。
   * @param partitionKey 分区键。
   * @param conversationId 会话标识。
   */
  async function restoreWorkspaceSnapshot(partitionKey: string, conversationId: string) {
    const snapshot = readWorkspaceSnapshot(partitionKey);
    setConversations(snapshot.conversations);
    refreshWorkspaceGroups('all');
    // 刷新恢复链路保持 URL 与当前会话一致，确保分享链接和硬刷新都能恢复到同一会话。
    writeConversationIdToUrl(conversationId);
    const record = snapshot.conversationRecords[conversationId];
    if (!record) {
      if (snapshot.runtimeTarget === 'local') {
        // 本地分区禁止回退到云端回放接口；缺失记录时只恢复空本地会话壳。
        setActiveConversationId(conversationId);
        setMessages([]);
        setExecutionSteps([]);
        setReferences([]);
        setArtifacts([]);
        setCurrentExperts([]);
        setCurrentSkills([]);
        setCurrentMcps([]);
        return;
      }
      await selectConversation(conversationId, snapshot.conversations, undefined, false);
      return;
    }
    const replayMessages = patchLatestAssistantReplayPanels(record.messages, {
      previousMessages: messagesRef.current,
      executionSteps: record.executionSteps,
      references: record.references,
    });
    setActiveConversationId(conversationId);
    setMessages(replayMessages);
    setExecutionSteps(record.executionSteps);
    setReferences(record.references);
    setArtifacts(record.artifacts);
    setCurrentExperts(record.currentExperts ?? []);
    setCurrentSkills(record.currentSkills);
    setCurrentMcps(record.currentMcps);
    persistConversationState(conversationId, snapshot.conversations, {
      messages: replayMessages,
      executionSteps: record.executionSteps,
      references: record.references,
      artifacts: record.artifacts,
      currentExperts: record.currentExperts ?? [],
      currentSkills: record.currentSkills,
      currentMcps: record.currentMcps,
    });
  }

  /**
   * 解析地址栏中的会话参数，用于刷新后按链接恢复目标会话。
   * @returns 会话标识，不存在时返回 null。
   */
  function readConversationIdFromUrl() {
    const searchParams = new URLSearchParams(window.location.search);
    const rawConversationId = searchParams.get(CONVERSATION_ID_QUERY_KEY);
    if (!rawConversationId) {
      return null;
    }
    const normalizedConversationId = rawConversationId.trim();
    return normalizedConversationId.length > 0 ? normalizedConversationId : null;
  }

  /**
   * 将当前会话写回地址栏，供刷新恢复与会话链接分享复用。
   * @param conversationId 会话标识；为空时移除参数。
   */
  function writeConversationIdToUrl(conversationId: string | null) {
    const nextUrl = new URL(window.location.href);
    const normalizedConversationId = conversationId == null ? '' : String(conversationId);
    if (normalizedConversationId.trim().length > 0) {
      nextUrl.searchParams.set(CONVERSATION_ID_QUERY_KEY, normalizedConversationId);
    } else {
      nextUrl.searchParams.delete(CONVERSATION_ID_QUERY_KEY);
    }
    window.history.replaceState(window.history.state, '', nextUrl.toString());
  }

  /**
   * 计算初始化阶段应优先恢复的会话，仅在 URL 显式指定时恢复对应会话。
   * @param nextConversations 最新会话列表。
   * @returns 会话标识或 null。
   */
  function resolvePreferredConversationId(nextConversations: ConversationItem[]) {
    if (!hasHydratedInitialConversationRef.current && activeWorkspacePartitionKey) {
      const snapshot = readWorkspaceSnapshot(activeWorkspacePartitionKey);
      const snapshotConversationId = snapshot.activeConversationId;
      const matchedByConversationList = nextConversations.some(
        (conversation) => conversation.id === snapshotConversationId,
      );
      const matchedBySnapshotRecord = hasPersistedConversationRecord(snapshotConversationId);
      if (snapshotConversationId && (matchedByConversationList || matchedBySnapshotRecord)) {
        return snapshotConversationId;
      }
    }
    if (!hasHydratedInitialConversationRef.current) {
      const initialConversationId = initialUrlConversationIdRef.current;
      const matchedByConversationList = nextConversations.some(
        (conversation) => conversation.id === initialConversationId,
      );
      const matchedBySnapshotRecord = hasPersistedConversationRecord(initialConversationId);
      if (initialConversationId && (matchedByConversationList || matchedBySnapshotRecord)) {
        return initialConversationId;
      }
    }
    return null;
  }

  /**
   * 在远端会话列表返回前，尝试基于本地快照预恢复会话，减少刷新期间首页闪烁。
   * @returns 快照中可恢复的会话标识。
   */
  function resolvePreferredConversationIdFromSnapshot() {
    if (hasHydratedInitialConversationRef.current || !activeWorkspacePartitionKey) {
      return null;
    }
    const snapshot = readWorkspaceSnapshot(activeWorkspacePartitionKey);
    const initialConversationId = initialUrlConversationIdRef.current;
    if (!initialConversationId) {
      return null;
    }
    return snapshot.conversationRecords[initialConversationId] != null ||
      snapshot.conversations.some((conversation) => conversation.id === initialConversationId)
      ? initialConversationId
      : null;
  }

  /**
   * 判断当前分区是否存在可回放的会话记录，避免初始化阶段误触发不必要的接口回放。
   * @param conversationId 会话标识。
   * @returns 是否存在对应快照记录。
   */
  function hasPersistedConversationRecord(conversationId: string | null) {
    if (!conversationId || !activeWorkspacePartitionKey) {
      return false;
    }
    const snapshot = readWorkspaceSnapshot(activeWorkspacePartitionKey);
    const record = snapshot.conversationRecords[conversationId];
    if (!record) {
      return false;
    }
    if (readWorkspaceSnapshot(activeWorkspacePartitionKey).runtimeTarget === 'local') {
      return true;
    }
    return (
      record.messages.length > 0 ||
      record.executionSteps.length > 0 ||
      record.references.length > 0 ||
      record.artifacts.length > 0 ||
      (record.currentExperts?.length ?? 0) > 0 ||
      (record.currentSkills?.length ?? 0) > 0 ||
      (record.currentMcps?.length ?? 0) > 0
    );
  }

  /**
   * 判断当前是否仍有流式会话在占用主消息区，供过期恢复任务避让实时生成状态。
   * @returns 是否存在活跃流式会话。
   */
  function hasActiveStreamPlayback() {
    return activeStreamSessionIdRef.current != null || abortControllerRef.current != null;
  }

  /**
   * 根据已归属记录推导当前工作空间会话列表，避免把其他工作空间会话写入当前分区。
   * @param runtimeTarget 当前运行环境。
   * @param currentWorkspacePath 当前工作空间路径。
   * @param remoteConversations 后端返回会话列表。
   * @returns 当前工作空间应展示的会话列表。
   */
  function resolveWorkspaceConversations(
    runtimeTarget: 'cloud' | 'local',
    currentWorkspacePath: string | null,
    currentWorkspaceId: string | null,
    remoteConversations: ConversationItem[],
  ) {
    const normalizedWorkspaceId = normalizeWorkspaceId(currentWorkspaceId);
    if (runtimeTarget === 'local' && normalizedWorkspaceId) {
      const hasWorkspaceIdData = remoteConversations.some(
        (conversation) => normalizeWorkspaceId(conversation.workspaceId) != null,
      );
      if (!hasWorkspaceIdData) {
        // 当后端响应未回填 workspaceId 字段时，兜底信任“按 workspaceId 查询”返回结果，避免列表被误过滤为空。
        return remoteConversations;
      }
      return remoteConversations.filter(
        (conversation) =>
          normalizeWorkspaceId(conversation.workspaceId) === normalizedWorkspaceId,
      );
    }
    const currentPartitionKey = buildWorkspacePartitionKey(runtimeTarget, currentWorkspacePath);
    const snapshot = readWorkspaceSnapshot(currentPartitionKey);
    const hasAnyOwnedRecords = Object.values(snapshot.conversationRecords ?? {}).some(
      (record) => record.owned === true,
    );
    if (!hasAnyOwnedRecords) {
      // 首次进入尚未建立归属映射时，默认采用后端返回列表，避免工作空间已有会话却被过滤为空。
      return remoteConversations;
    }
    return remoteConversations.filter(
      (conversation) => findWorkspacePartitionByConversationId(conversation.id) === currentPartitionKey,
    );
  }

  /**
   * 将用户选择或粘贴的文件加入待发送附件队列。
   * @param files 需要加入的文件集合。
   */
  async function addPendingAttachments(files: File[]) {
    if (files.length === 0) {
      return;
    }
    const normalizedFiles = files.filter((file) => file.size > 0).slice(0, 9);
    const nextItems = normalizedFiles.map((file, index) => ({
      clientId: `pending-${Date.now()}-${index}-${Math.random().toString(16).slice(2, 10)}`,
      file,
      previewUrl: URL.createObjectURL(file),
      uploadStatus: 'pending' as const,
    }));
    setPendingAttachments((previous) => {
      const merged = [...previous, ...nextItems];
      if (merged.length <= 9) {
        return merged;
      }
      const overflowItems = merged.slice(9);
      overflowItems.forEach((item) => URL.revokeObjectURL(item.previewUrl));
      return merged.slice(0, 9);
    });
  }

  /**
   * 删除待发送附件。
   * @param clientId 前端临时附件主键。
   */
  function removePendingAttachment(clientId: string) {
    setPendingAttachments((previous) => {
      const target = previous.find((item) => item.clientId === clientId);
      if (target) {
        URL.revokeObjectURL(target.previewUrl);
      }
      return previous.filter((item) => item.clientId !== clientId);
    });
  }

  /**
   * 清空待发送附件并释放预览 URL，避免内存泄漏。
   */
  function clearPendingAttachments() {
    setPendingAttachments((previous) => {
      previous.forEach((item) => URL.revokeObjectURL(item.previewUrl));
      return [];
    });
  }

  /**
   * 将待发送附件上传到后端，返回可透传给消息发送链路的附件列表。
   * @param token 当前登录令牌。
   * @param conversationId 当前会话标识。
   * @returns 上传后的附件信息。
   */
async function uploadPendingAttachments(
    token: string,
    conversationId: string | null,
    sourcePendingAttachments: PendingAttachmentItem[] = pendingAttachments,
  ): Promise<ChatAttachmentItem[]> {
    const pendingItems = sourcePendingAttachments.filter(
      (item) => item.uploadStatus === 'pending' || item.uploadStatus === 'failed',
    );
    if (pendingItems.length === 0) {
      return sourcePendingAttachments
        .map((item) => item.attachment)
        .filter((attachment): attachment is ChatAttachmentItem => attachment != null);
    }
    const uploadedItems: ChatAttachmentItem[] = [];
    for (const pendingItem of pendingItems) {
      setPendingAttachments((previous) =>
        previous.map((item) =>
          item.clientId === pendingItem.clientId
            ? {
                ...item,
                uploadStatus: 'uploading',
                uploadError: undefined,
              }
            : item,
        ),
      );
      try {
        const uploadedAttachment = await ChatApi.uploadAttachment(
          token,
          pendingItem.file,
          conversationId,
        );
        uploadedItems.push(uploadedAttachment);
        setPendingAttachments((previous) =>
          previous.map((item) =>
            item.clientId === pendingItem.clientId
              ? {
                  ...item,
                  uploadStatus: 'uploaded',
                  uploadError: undefined,
                  attachment: uploadedAttachment,
                }
              : item,
          ),
        );
      } catch (error) {
        setPendingAttachments((previous) =>
          previous.map((item) =>
            item.clientId === pendingItem.clientId
              ? {
                  ...item,
                  uploadStatus: 'failed',
                  uploadError: error instanceof Error ? error.message : UserErrorMessages.CHAT_ATTACHMENT_UPLOAD_FAILED,
                }
              : item,
          ),
        );
        throw error;
      }
    }
    const stableUploaded = sourcePendingAttachments
      .map((item) => item.attachment)
      .filter((attachment): attachment is ChatAttachmentItem => attachment != null);
    return [...stableUploaded, ...uploadedItems];
  }

  /**
   * 上传失败后恢复发送前附件列表，确保用户可见失败条目并支持直接重试。
   * @param previousPendingAttachments 发送前附件快照。
   */
  function restorePendingAttachmentsAfterUploadFailed(previousPendingAttachments: PendingAttachmentItem[]) {
    setPendingAttachments((currentAttachments) => {
      const failedAttachmentMap = new Map(
        currentAttachments
          .filter((item) => item.uploadStatus === 'failed')
          .map((item) => [item.clientId, item]),
      );
      return previousPendingAttachments.map((item) => {
        const failedItem = failedAttachmentMap.get(item.clientId);
        if (!failedItem) {
          // 业务意图：未失败附件回到待发送态，用户可在修复失败项后整体重发。
          return item;
        }
        return {
          ...item,
          uploadStatus: 'failed',
          uploadError: failedItem.uploadError,
          attachment: failedItem.attachment ?? item.attachment,
        };
      });
    });
  }
}

/**
 * 统一归一化 workspaceId，避免空串和空白值误判为有效空间标识。
 * @param workspaceId 原始 workspaceId。
 * @returns 归一化后的标识，缺失时返回 null。
 */
function normalizeWorkspaceId(workspaceId: string | null | undefined) {
  const normalizedWorkspaceId = workspaceId == null ? '' : String(workspaceId).trim();
  return normalizedWorkspaceId.length > 0 ? normalizedWorkspaceId : null;
}

/**
 * 只保留已落库的数字主键，避免乐观消息 ID 进入后端 Long 参数导致反序列化失败。
 * @param messageIds 前端消息或附件标识列表。
 * @returns 去重后的数字标识字符串列表。
 */
function normalizePersistedMessageIds(messageIds: Array<string | null | undefined>) {
  const normalizedIds: string[] = [];
  messageIds.forEach((messageId) => {
    const normalizedMessageId = String(messageId ?? '').trim();
    if (!/^\d+$/.test(normalizedMessageId) || normalizedIds.includes(normalizedMessageId)) {
      return;
    }
    normalizedIds.push(normalizedMessageId);
  });
  return normalizedIds;
}

/**
 * 统一拼装聊天流请求地址，保证新建态不会误带旧会话标识。
 * @param question 用户输入问题。
 * @param conversationId 当前选中的会话标识。
 * @returns 可直接用于 fetch 的 SSE 地址。
 */
export function buildStreamRequestUrl(
  question: string,
  conversationId: string | null,
  workspaceId: string | null,
  deepThinkingEnabled: boolean,
  mcpConnected: boolean,
  selectedMcpCodes: string[],
  selectedSkillCodes: string[],
  repositoryPathOrSelectedExpertCode?: string | null,
  attachmentIdsOrRepositoryPath?: string[] | string | null,
  maybeAttachmentIds?: string[],
  maybeSelectedExpertCodeOrRuntimeTarget?: string | null,
  maybeRuntimeTarget?: 'cloud' | 'local',
) {
  const skillMessageParseResult = parseSkillMessage(question);
  const searchParams = new URLSearchParams({
    question: skillMessageParseResult.question,
  });
  let repositoryPath: string | null | undefined;
  let attachmentIds: string[] | undefined;
  let selectedExpertCode: string | null | undefined;
  let runtimeTarget: 'cloud' | 'local' | undefined;
  if (Array.isArray(attachmentIdsOrRepositoryPath)) {
    repositoryPath = repositoryPathOrSelectedExpertCode;
    attachmentIds = attachmentIdsOrRepositoryPath;
    if (maybeSelectedExpertCodeOrRuntimeTarget === 'cloud' || maybeSelectedExpertCodeOrRuntimeTarget === 'local') {
      runtimeTarget = maybeSelectedExpertCodeOrRuntimeTarget;
    } else {
      selectedExpertCode = maybeSelectedExpertCodeOrRuntimeTarget;
      runtimeTarget = maybeRuntimeTarget;
    }
  } else {
    repositoryPath = attachmentIdsOrRepositoryPath;
    attachmentIds = maybeAttachmentIds;
    selectedExpertCode = repositoryPathOrSelectedExpertCode;
    runtimeTarget =
      maybeSelectedExpertCodeOrRuntimeTarget === 'cloud' || maybeSelectedExpertCodeOrRuntimeTarget === 'local'
        ? maybeSelectedExpertCodeOrRuntimeTarget
        : maybeRuntimeTarget;
  }
  const normalizedConversationId = conversationId == null ? '' : String(conversationId).trim();
  if (
    normalizedConversationId.length > 0 &&
    // 本地旧快照可能含 local-* 客户端 ID，后端 stream 参数是 Long，只能透传数值 ID。
    (runtimeTarget !== 'local' || /^\d+$/.test(normalizedConversationId))
  ) {
    searchParams.set('conversationId', normalizedConversationId);
  }
  if (runtimeTarget === 'local') {
    searchParams.set('runtimeTarget', 'local');
  }
  if (runtimeTarget !== 'local' && workspaceId != null && workspaceId.trim().length > 0) {
    searchParams.set('workspaceId', workspaceId);
  }
  if (deepThinkingEnabled) {
    searchParams.set('deepThinking', 'true');
  }
  if (mcpConnected && selectedMcpCodes.length > 0) {
    searchParams.set('mcpCodes', selectedMcpCodes.join(','));
  }
  if (selectedSkillCodes.length > 0) {
    searchParams.set('skillCodes', selectedSkillCodes.join(','));
  }
  if (selectedExpertCode && selectedExpertCode.trim().length > 0) {
    searchParams.set('expertCode', selectedExpertCode);
  }
  if (repositoryPath && repositoryPath.trim().length > 0) {
    searchParams.set('repositoryPath', repositoryPath);
  }
  if (attachmentIds != null && attachmentIds.length > 0) {
    searchParams.set('attachmentIds', attachmentIds.join(','));
  }
  if (skillMessageParseResult.structuredMessages.length > 0) {
    searchParams.set('messages', JSON.stringify(skillMessageParseResult.structuredMessages));
  }
  return `/api/chat/stream?${searchParams.toString()}`;
}

/**
 * 解析技能命令输入，提取连续前缀 @skill 并生成结构化消息，返回去除命令后的纯文本问题。
 * @param rawQuestion 原始输入。
 * @returns 解析结果。
 */
function parseSkillMessage(rawQuestion: string): {
  question: string;
  structuredMessages: Array<Record<string, unknown>>;
} {
  let remainingQuestion = rawQuestion.trim();
  const parsedSkillCodes: string[] = [];
  while (true) {
    const skillMatch = remainingQuestion.match(/^@([a-zA-Z0-9_-]+)\s*(.*)$/);
    if (!skillMatch) {
      break;
    }
    const skillCode = skillMatch[1];
    parsedSkillCodes.push(skillCode);
    remainingQuestion = (skillMatch[2] ?? '').trim();
  }
  if (parsedSkillCodes.length === 0) {
    return { question: remainingQuestion, structuredMessages: [] };
  }
  const normalizedSkillCodes = Array.from(new Set(parsedSkillCodes));
  const structuredMessages: Array<Record<string, unknown>> = normalizedSkillCodes.map((skillCode) => ({
    type: 'slash_command',
    data: {
      id: `^/${skillCode}/SKILL.md`,
      command: skillCode,
      command_type: 'skill',
      parameters: {
        argCount: 0,
        hasArgumentsVar: false,
        parameterValues: {},
      },
    },
  }));
  structuredMessages.push({
    type: 'text',
    data: {
      content: remainingQuestion,
    },
  });
  return {
    question: remainingQuestion,
    structuredMessages,
  };
}

/**
 * 判断未知载荷是否可按对象读取。
 * @param value 未知值。
 * @returns 是否为对象记录。
 */
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/**
 * 将后端 reject reason 映射为可直接展示给用户的中文提示。
 * @param reason 后端拒绝原因编码。
 * @returns 前端提示文案。
 */
function resolveQueueRejectMessage(reason: string): string {
  if (reason.toLowerCase() === 'busy') {
    return UserErrorMessages.CHAT_QUEUE_BUSY;
  }
  if (!reason) {
    return UserErrorMessages.CHAT_QUEUE_UNAVAILABLE;
  }
  return reason;
}

/**
 * 按主键更新或插入列表项，保证流式事件与回放刷新语义一致。
 * @param items 旧列表。
 * @param nextItem 新项目。
 * @returns 合并后的列表。
 */
function upsertById<T extends { id: string }>(items: T[], nextItem: T): T[] {
  const existingIndex = items.findIndex((item) => item.id === nextItem.id);
  if (existingIndex === -1) {
    return [...items, nextItem];
  }
  return items.map((item) => (item.id === nextItem.id ? nextItem : item));
}

/**
 * 归一化 MCP 调用阶段，未知值按 complete 兜底，保证旧事件可展示。
 * @param phaseValue 原始阶段字段。
 * @returns 标准化阶段。
 */
function resolveMcpCallPhase(phaseValue: unknown): 'start' | 'progress' | 'complete' | 'error' {
  const normalizedPhaseValue = typeof phaseValue === 'string' ? phaseValue.trim().toLowerCase() : '';
  if (normalizedPhaseValue === 'start') {
    return 'start';
  }
  if (normalizedPhaseValue === 'progress') {
    return 'progress';
  }
  if (normalizedPhaseValue === 'error') {
    return 'error';
  }
  return 'complete';
}

/**
 * 将 MCP 调用阶段映射为面板状态，供前端直接展示调用进度。
 * @param phase 标准化阶段。
 * @returns 调用状态。
 */
function resolveMcpCallStatus(
  phase: 'start' | 'progress' | 'complete' | 'error',
): 'running' | 'completed' | 'error' {
  if (phase === 'start' || phase === 'progress') {
    return 'running';
  }
  if (phase === 'error') {
    return 'error';
  }
  return 'completed';
}

/**
 * 归一化可选字符串，空值统一返回 undefined，避免脏字段污染面板展示。
 * @param value 原始值。
 * @returns 归一化字符串。
 */
function normalizeOptionalString(value: unknown): string | undefined {
  if (typeof value !== 'string') {
    return undefined;
  }
  const normalizedValue = value.trim();
  return normalizedValue.length > 0 ? normalizedValue : undefined;
}

/**
 * 解析 MCP 参数字段，仅保留对象或非空字符串，保障面板可读性。
 * @param paramsValue 原始参数字段。
 * @returns 归一化参数。
 */
function normalizeMcpCallParams(
  paramsValue: unknown,
): Record<string, unknown> | string | undefined {
  if (isRecord(paramsValue)) {
    return paramsValue;
  }
  if (typeof paramsValue === 'string') {
    const normalizedValue = paramsValue.trim();
    return normalizedValue.length > 0 ? normalizedValue : undefined;
  }
  return undefined;
}

/**
 * 按 callId 合并 MCP 调用记录，保证 start/complete 事件更新同一条卡片。
 * @param calls 当前调用列表。
 * @param nextCall 待写入调用记录。
 * @returns 合并后的调用列表。
 */
function mergeMcpCallsById(calls: McpCallItem[], nextCall: McpCallItem): McpCallItem[] {
  if (!nextCall.callId) {
    return [...calls, nextCall];
  }
  const existingIndex = calls.findIndex((call) => call.callId === nextCall.callId);
  if (existingIndex < 0) {
    return [...calls, nextCall];
  }
  return calls.map((call, index) =>
    index === existingIndex
      ? {
          ...call,
          ...nextCall,
        }
      : call,
  );
}

/**
 * 构造分析阶段卡片；真实 thinking 到达后必须完整展示，避免中途省略导致用户无法核对过程。
 * @param id 卡片标识。
 * @param thinkingContent 可选思考内容。
 * @param title 过程标题。
 * @returns 分析卡片。
 */
function buildAnalysisProcessCard(id: string, thinkingContent?: string, title = '分析问题'): ProcessCardItem {
  return {
    id,
    type: 'analysis',
    title,
    summary: formatThinkingProcessContent(thinkingContent ?? ''),
    status: 'running',
  };
}

/**
 * 根据搜索步骤创建 ReAct 工具动作卡片；搜索 query 作为参数摘要展示。
 * @param options 搜索步骤信息。
 * @returns 搜索行动卡片。
 */
function buildSearchToolCallCard(options: {
  id: string;
  title: string;
  summary: string;
  status?: ProcessCardItem['status'];
}): ProcessCardItem {
  const queryText = resolveSearchQueryText(options);
  return {
    id: `process-${options.id}`,
    type: 'tool_call',
    title: '行动',
    summary: queryText ? `调用网页搜索：${queryText}` : '调用网页搜索',
    status: options.status ?? 'running',
    toolId: 'search',
    displayName: '网页搜索',
    presentation: 'react',
    details: queryText
      ? [
          {
            label: '参数',
            content: queryText,
          },
        ]
      : undefined,
  };
}

/**
 * 归一化搜索步骤中的用户可读查询文本。
 * @param options 搜索步骤信息。
 * @returns 可展示搜索词。
 */
function resolveSearchQueryText(options: {
  title: string;
  summary: string;
}): string {
  const stepTitle = options.title.trim();
  const queryText = options.summary.trim();
  return queryText || (stepTitle && stepTitle !== '搜索资料' ? stepTitle : '');
}

/**
 * 将搜索步骤合并为工具动作；普通回答和真实 thinking 只来自 SSE 正文/thinking 事件，前端不伪造思考内容。
 * @param cards 当前卡片列表。
 * @param options 搜索步骤信息。
 * @returns 合并后的卡片列表。
 */
function mergeSearchStepIntoProcessCards(
  cards: ProcessCardItem[],
  options: {
    id: string;
    title: string;
    summary: string;
    status?: ProcessCardItem['status'];
  },
): ProcessCardItem[] {
  const nextCards = finalizeCardsByType(cards, ['analysis']);
  return upsertProcessCard(nextCards, buildSearchToolCallCard(options));
}

/**
 * 将单条来源条目合并进统一过程卡片，来源到达后自动补齐结果卡片与整理卡片。
 * @param cards 当前卡片列表。
 * @param reference 新来源。
 * @returns 合并后的卡片列表。
 */
function mergeReferenceIntoProcessCards(
  cards: ProcessCardItem[],
  reference: ReferenceItem,
): ProcessCardItem[] {
  const sourceLabel = resolveSearchSourceLabel(reference);
  const nextCards = hydratePendingSearchToolCallCard(
    finalizeCardsByType(cards, ['analysis', 'tool_call']),
    reference,
  );
  const nextResultDetails = [
    {
      label: '结果',
      content: [reference.title, reference.siteName, reference.url].filter(Boolean).join('\n'),
    },
  ];
  return upsertProcessCard(
    nextCards,
    {
      id: `search-result-${reference.id}`,
      type: 'tool_result',
      title: '观察',
      summary: buildSearchObservationSummary(reference, sourceLabel),
      status: 'completed',
      toolId: 'search',
      displayName: '网页搜索',
      presentation: 'react',
      details: nextResultDetails,
    },
  );
}

/**
 * 搜索 step 先于来源到达时可能只有兜底文案；来源返回后只补强工具动作摘要，不伪造思考节点。
 * @param cards 当前卡片列表。
 * @param reference 新来源。
 * @returns 补强后的过程卡片。
 */
function hydratePendingSearchToolCallCard(
  cards: ProcessCardItem[],
  reference: ReferenceItem,
): ProcessCardItem[] {
  const queryText = resolveReferenceQueryText(reference);
  if (!queryText) {
    return cards;
  }
  const pendingToolIndex = cards.findIndex(
    (card) =>
      card.toolId === 'search' &&
      card.type === 'tool_call' &&
      card.presentation === 'react' &&
      card.summary === '调用网页搜索：正在检索实时资料。',
  );
  if (pendingToolIndex < 0) {
    return cards;
  }
  return cards.map((card, index) => {
    if (index === pendingToolIndex) {
      return {
        ...card,
        summary: `调用网页搜索：${queryText}`,
        details: [
          {
            label: '参数',
            content: queryText,
          },
        ],
      };
    }
    return card;
  });
}

/**
 * 从来源事件中提取可读查询兜底；优先标题，其次摘要或 URL。
 * @param reference 搜索来源。
 * @returns 可展示查询文本。
 */
function resolveReferenceQueryText(reference: ReferenceItem): string {
  return (reference.title || reference.snippet || reference.url || '').trim();
}

/**
 * 将搜索来源转换为 ReAct 工具结果摘要，只保留站点与标题，避免过程行重复展示“网页搜索返回”前缀。
 * @param reference 搜索来源。
 * @param sourceLabel 来源站点标签。
 * @returns 观察摘要。
 */
function buildSearchObservationSummary(reference: ReferenceItem, sourceLabel: string): string {
  const resultText = reference.title || reference.snippet || reference.url || '已获取检索来源，正在比对可用结论。';
  return sourceLabel ? `${sourceLabel}：${resultText}` : resultText;
}

/**
 * 提取搜索来源标签，优先展示站点名，缺失时用完整域名兜底，形成简洁来源过程行。
 * @param source 搜索来源。
 * @returns 来源标签。
 */
function resolveSearchSourceLabel(source: { siteName?: string; url?: string }) {
  const siteName = source.siteName?.trim();
  if (siteName) {
    return siteName;
  }
  const url = source.url?.trim();
  if (!url) {
    return '';
  }
  try {
    return new URL(url).hostname;
  } catch {
    return url.replace(/^https?:\/\//, '').split('/')[0] ?? '';
  }
}

/**
 * 从搜索进度来源构造工具结果过程行，保证历史回放与实时流展示都按来源逐条铺开。
 * @param item 搜索来源条目。
 * @param status 搜索过程状态。
 * @returns 搜索来源过程卡片。
 */
function buildSearchResultProcessCardFromProgressItem(
  item: MessageSearchProgressItem,
  status: MessageSearchProgress['status'],
): ProcessCardItem {
  const sourceLabel = resolveSearchSourceLabel(item);
  return {
    id: `replay-search-result-${item.id}`,
    type: 'tool_result',
    title: '观察',
    summary: buildSearchObservationSummary(
      {
        id: item.id,
        runId: '',
        conversationId: '',
        title: item.title,
        url: item.url,
        siteName: item.siteName,
      },
      sourceLabel,
    ),
    status: status === 'error' ? 'error' : 'completed',
    toolId: 'search',
    displayName: '网页搜索',
    presentation: 'react',
    details: [
      {
        label: '结果',
        content: [item.title, item.siteName, item.url].filter(Boolean).join('\n'),
      },
    ],
  };
}

/**
 * 将 MCP 事件映射为统一过程卡片。
 * @param cards 当前卡片列表。
 * @param call MCP 调用记录。
 * @returns 合并后的卡片列表。
 */
function mergeMcpCallIntoProcessCards(
  cards: ProcessCardItem[],
  call: McpCallItem,
): ProcessCardItem[] {
  let nextCards = finalizeCardsByType(cards, ['analysis']);
  const isReactToolProcess = Boolean(call.reactAction || call.reactObservation);
  if (call.phase === 'start' || call.phase === 'progress') {
    const existingCard = nextCards.find((card) => card.id === `tool-call-${call.callId ?? call.toolId}`);
    const nextDetails = call.params
      ? [
          {
            label: '参数',
            content: formatProcessCardDetail(call.params),
          },
        ]
      : existingCard?.details;
    if (isReactToolProcess) {
      // 工具事件里的 reactThought 可能是后端兜底模板，不代表模型真实正文；只展示动作和观察。
      nextCards = upsertProcessCard(nextCards, {
        id: `tool-call-${call.callId ?? call.toolId}`,
        type: 'tool_call',
        title: '行动',
        summary: call.reactAction ?? `调用 ${call.displayName || call.toolId || '工具'}`,
        status: call.status === 'error' ? 'error' : 'running',
        toolId: call.toolId,
        displayName: call.displayName,
        presentation: 'react',
        details: nextDetails,
      });
      return nextCards;
    }
    nextCards = upsertProcessCard(nextCards, {
      id: `tool-call-${call.callId ?? call.toolId}`,
      type: 'tool_call',
      title: `调用${call.displayName || call.toolId || '工具'}`,
      summary:
        call.progressText ??
        `正在调用${call.displayName || call.toolId || '工具'}。`,
      status: call.status === 'error' ? 'error' : 'running',
      toolId: call.toolId,
      displayName: call.displayName,
      details: nextDetails,
    });
    return nextCards;
  }
  if (call.phase === 'complete' || call.phase === 'error') {
    nextCards = finalizeCardsByType(nextCards, ['tool_call']);
    if (isReactToolProcess) {
      nextCards = upsertProcessCard(nextCards, {
        id: `tool-result-${call.callId ?? call.toolId}`,
        type: 'tool_result',
        title: '观察',
        summary:
          call.reactObservation ??
          (call.phase === 'error'
            ? call.errorMessage || `工具 ${call.displayName || call.toolId || ''} 执行异常。`
            : resolveToolResultSummary(call)),
        status: call.phase === 'error' ? 'error' : 'completed',
        toolId: call.toolId,
        displayName: call.displayName,
        presentation: 'react',
        details: [
          ...(call.params
            ? [
                {
                  label: '参数',
                  content: formatProcessCardDetail(call.params),
                },
              ]
            : []),
          ...(call.rawResult != null || call.errorMessage
            ? [
                {
                  label: call.phase === 'error' ? '异常' : '结果',
                  content: formatProcessCardDetail(call.rawResult ?? call.errorMessage),
                },
              ]
            : []),
        ],
      });
      return nextCards;
    }
    nextCards = upsertProcessCard(nextCards, {
      id: `tool-result-${call.callId ?? call.toolId}`,
      type: 'tool_result',
      title: '已获取结果',
      summary: resolveToolResultSummary(call),
      status: 'completed',
      toolId: call.toolId,
      displayName: call.displayName,
      details: [
        ...(call.params
          ? [
              {
                label: '参数',
                content: formatProcessCardDetail(call.params),
              },
            ]
          : []),
        ...(call.rawResult != null
          ? [
              {
                label: '结果',
                content: formatProcessCardDetail(call.rawResult),
              },
            ]
          : []),
      ],
    });
  }
  return nextCards;
}

/**
 * 统一上收过程卡片终态。
 * @param cards 当前卡片列表。
 * @param status 目标状态。
 * @returns 收口后的卡片列表。
 */
function finalizeProcessCards(
  cards: ProcessCardItem[],
  status: 'completed' | 'cancelled' | 'error',
): ProcessCardItem[] {
  return cards.map((card) =>
    card.status === 'running'
      ? {
          ...card,
          status,
        }
      : card,
  );
}

/**
 * 仅将指定类型中仍处于 running 的卡片转为 completed。
 * @param cards 卡片列表。
 * @param types 目标类型列表。
 * @returns 更新后的卡片列表。
 */
function finalizeCardsByType(cards: ProcessCardItem[], types: ProcessCardItem['type'][]): ProcessCardItem[] {
  return cards.map((card) =>
    types.includes(card.type) && card.status === 'running'
      ? {
          ...card,
          status: 'completed',
        }
      : card,
  );
}

/**
 * 将正文 delta 追加到消息时间线；若上一段是过程节点则开启新的正文片段。
 * @param items 当前时间线。
 * @param delta 新到达正文。
 * @returns 更新后的时间线。
 */
function appendContentToTimeline(
  items: MessageTimelineItem[] | undefined,
  delta: string,
): MessageTimelineItem[] | undefined {
  if (!delta) {
    return items;
  }
  const nextItems = [...(items ?? [])];
  const lastItem = nextItems[nextItems.length - 1];
  if (lastItem?.type === 'content') {
    nextItems[nextItems.length - 1] = {
      ...lastItem,
      content: `${lastItem.content}${delta}`,
    };
    return nextItems;
  }
  nextItems.push({
    id: `content-${nextItems.length + 1}`,
    type: 'content',
    content: delta,
  });
  return nextItems;
}

/**
 * 把最新过程卡片同步进消息时间线；已有过程只更新内容，不改变原始到达顺序。
 * @param items 当前时间线。
 * @param previousCards 合并前过程卡片。
 * @param nextCards 合并后过程卡片。
 * @returns 更新后的时间线。
 */
function syncProcessCardsToTimeline(
  items: MessageTimelineItem[] | undefined,
  previousCards: ProcessCardItem[] | undefined,
  nextCards: ProcessCardItem[] | undefined,
): MessageTimelineItem[] | undefined {
  if (!nextCards || nextCards.length === 0) {
    return items;
  }
  const previousCardIds = new Set((previousCards ?? []).map((card) => card.id));
  const nextItems = [...(items ?? [])];
  for (const card of nextCards) {
    const existingTimelineIndex = nextItems.findIndex(
      (item) => item.type === 'process' && item.card.id === card.id,
    );
    if (existingTimelineIndex >= 0) {
      const existingItem = nextItems[existingTimelineIndex];
      if (existingItem.type === 'process') {
        nextItems[existingTimelineIndex] = {
          ...existingItem,
          card,
        };
      }
      continue;
    }
    if (!previousCardIds.has(card.id)) {
      nextItems.push({
        id: `process-${card.id}`,
        type: 'process',
        card,
      });
    }
  }
  return nextItems;
}

/**
 * 终止、取消或异常时同步时间线中的过程状态，避免时间线与旧 processCards 字段显示不一致。
 * @param items 当前时间线。
 * @param status 目标过程状态。
 * @returns 更新后的时间线。
 */
function finalizeTimelineProcessCards(
  items: MessageTimelineItem[] | undefined,
  status: ProcessCardItem['status'],
): MessageTimelineItem[] | undefined {
  if (!items || items.length === 0) {
    return items;
  }
  return items.map((item) =>
    item.type === 'process'
      ? {
          ...item,
          card: {
            ...item.card,
            status: item.card.status === 'running' ? status : item.card.status,
          },
        }
      : item,
  );
}

/**
 * finish 只补齐流式正文缺失的尾段，避免把已穿插的正文和过程重新洗牌。
 * @param items 当前时间线。
 * @param content 最终正文。
 * @returns 更新后的时间线。
 */
function ensureTimelineContent(
  items: MessageTimelineItem[] | undefined,
  content: string,
): MessageTimelineItem[] | undefined {
  if (!content) {
    return items;
  }
  const timelineContent = (items ?? [])
    .filter((item): item is Extract<MessageTimelineItem, { type: 'content' }> => item.type === 'content')
    .map((item) => item.content)
    .join('');
  if (!timelineContent) {
    return appendContentToTimeline(items, content);
  }
  if (content.length <= timelineContent.length || !content.startsWith(timelineContent)) {
    return items;
  }
  return appendContentToTimeline(items, content.slice(timelineContent.length));
}

/**
 * 按卡片主键更新或插入过程卡片。
 * @param cards 当前卡片列表。
 * @param nextCard 待合并卡片。
 * @returns 合并后的卡片列表。
 */
function upsertProcessCard(cards: ProcessCardItem[], nextCard: ProcessCardItem): ProcessCardItem[] {
  const existingIndex = cards.findIndex((card) => card.id === nextCard.id);
  if (existingIndex < 0) {
    return [...cards, nextCard];
  }
  return cards.map((card, index) =>
    index === existingIndex
      ? {
          ...card,
          ...nextCard,
        }
      : card,
  );
}

/**
 * 合并同类详情，避免多条搜索来源到达时后到结果覆盖先到结果。
 * @param currentDetails 当前详情。
 * @param nextDetails 新详情。
 * @returns 合并后的详情。
 */
function mergeProcessCardDetails(
  currentDetails: NonNullable<ProcessCardItem['details']>,
  nextDetails: NonNullable<ProcessCardItem['details']>,
): NonNullable<ProcessCardItem['details']> {
  const mergedDetails = [...currentDetails];
  for (const nextDetail of nextDetails) {
    const existingIndex = mergedDetails.findIndex((detail) => detail.label === nextDetail.label);
    if (existingIndex < 0) {
      mergedDetails.push(nextDetail);
      continue;
    }
    const existingDetail = mergedDetails[existingIndex];
    const nextContent = nextDetail.content.trim();
    if (!nextContent || existingDetail.content.includes(nextContent)) {
      continue;
    }
    mergedDetails[existingIndex] = {
      ...existingDetail,
      content: [existingDetail.content, nextContent].filter(Boolean).join('\n\n'),
    };
  }
  return mergedDetails;
}

/**
 * 从工具结果中抽取更有信息量的摘要，避免显示空泛固定文案。
 * @param call MCP 调用记录。
 * @returns 用户可读结果摘要。
 */
function resolveToolResultSummary(call: McpCallItem): string {
  const rawResultText = formatProcessCardDetail(call.rawResult ?? call.content).replace(/\s+/g, ' ').trim();
  if (rawResultText) {
    return rawResultText.length > 80 ? `${rawResultText.slice(0, 80)}...` : rawResultText;
  }
  return `已获取${call.displayName || call.toolId || '工具'}结果。`;
}

/**
 * 根据已经发生的过程判断新 thinking 应追加到哪个分析段，确保工具结果后的分析显示在工具链路下面。
 * @param cards 当前过程卡片。
 * @returns thinking 卡片标识。
 */
function resolveThinkingProcessCardId(cards: ProcessCardItem[]): string {
  return cards.some((card) => card.type === 'tool_call' || card.type === 'tool_result')
    ? 'analysis-after-tools'
    : 'analysis-thinking';
}

/**
 * 格式化真实 thinking 内容：只清理首尾空白，不截断、不压缩换行，保证主消息区能完整回放。
 * @param content thinking 内容。
 * @returns 可展示的完整过程文本。
 */
function formatThinkingProcessContent(content: string): string {
  return content.trim();
}

/**
 * 统一格式化过程卡片细节，确保对象与数组可稳定展示。
 * @param value 细节值。
 * @returns 文本化内容。
 */
function formatProcessCardDetail(value: unknown): string {
  if (typeof value === 'string') {
    return value;
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

/**
 * 判断执行步骤是否属于联网搜索阶段，兼容大小写和未来扩展命名。
 * @param stepType 后端返回的步骤类型。
 * @returns 是否应触发搜索进度面板。
 */
function isSearchStepType(stepType: string): boolean {
  return stepType.trim().toLowerCase().includes('search');
}

/**
 * 将流式阶段采集到的 MCP 调用回填到末条助手消息，避免刷新历史回放时面板闪现后消失。
 * @param messages 接口返回的历史消息。
 * @param latestAssistantMcpCalls 流式阶段采集的 MCP 调用列表。
 * @returns 回填后的消息列表。
 */
function patchLatestAssistantMcpCalls(
  messages: ChatMessageItem[],
  latestAssistantMcpCalls?: McpCallItem[],
): ChatMessageItem[] {
  if (!latestAssistantMcpCalls || latestAssistantMcpCalls.length === 0) {
    return messages;
  }
  const latestAssistantIndex = [...messages]
    .map((message, index) => ({ message, index }))
    .reverse()
    .find((item) => item.message.role === 'ASSISTANT')?.index;
  if (latestAssistantIndex == null) {
    return messages;
  }
  const latestAssistantMessage = messages[latestAssistantIndex];
  if (latestAssistantMessage.mcpCalls && latestAssistantMessage.mcpCalls.length > 0) {
    return messages;
  }
  return messages.map((message, index) =>
    index === latestAssistantIndex
      ? {
          ...message,
          mcpCalls: latestAssistantMcpCalls,
        }
      : message,
  );
}

/**
 * 仅保留与“联网搜索”语义相关的来源条目，避免普通来源误触发搜索进度面板。
 * @param references 来源回放列表。
 * @returns 搜索进度条目列表。
 */
function buildSearchProgressItemsFromReferences(
  references: ReferenceItem[],
): MessageSearchProgressItem[] {
  if (references.length === 0) {
    return [];
  }
  return references
    .filter((reference) => {
      const normalizedSourceType = String(reference.sourceType ?? '').trim().toLowerCase();
      // 关键约束：后端联网搜索来源当前使用 sourceType=web，回放时需视为搜索来源，否则刷新后会丢失进度条目。
      return (
        normalizedSourceType.length === 0 ||
        normalizedSourceType.includes('search') ||
        normalizedSourceType === 'web'
      );
    })
    .map((reference) => ({
      id: reference.id,
      title: reference.title,
      url: reference.url,
      siteName: reference.siteName,
    }));
}

/**
 * 基于步骤与来源回放兜底重建搜索进度，保证刷新或历史回放后消息内面板稳定存在。
 * @param executionSteps 步骤回放列表。
 * @param references 来源回放列表。
 * @returns 搜索进度，若无搜索语义返回 undefined。
 */
function deriveSearchProgressFromReplay(
  executionSteps: ExecutionStepItem[],
  references: ReferenceItem[],
): MessageSearchProgress | undefined {
  const hasSearchStep = executionSteps.some((step) => isSearchStepType(step.stepType));
  const searchItems = buildSearchProgressItemsFromReferences(references);
  if (!hasSearchStep && searchItems.length === 0) {
    return undefined;
  }
  const hasRunningSearchStep = executionSteps.some((step) =>
    isSearchStepType(step.stepType) && String(step.stepStatus ?? '').trim().toUpperCase() !== 'COMPLETED',
  );
  return {
    status: hasRunningSearchStep ? 'running' : 'completed',
    items: searchItems,
  };
}

/**
 * 判断执行步骤是否属于 MCP 工具执行阶段，兼容历史数据中的标题兜底命名。
 * @param step 执行步骤。
 * @returns 是否为 MCP 工具步骤。
 */
function isMcpStep(step: ExecutionStepItem): boolean {
  const normalizedStepType = String(step.stepType ?? '').trim().toLowerCase();
  const normalizedStepTitle = String(step.stepTitle ?? '').trim().toLowerCase();
  return normalizedStepType.includes('mcp') || normalizedStepTitle.includes('mcp');
}

/**
 * 从步骤回放兜底生成 MCP 调用面板数据，保障历史会话在缺失消息级字段时仍可展示。
 * @param executionSteps 执行步骤列表。
 * @returns 生成的 MCP 调用列表。
 */
function deriveMcpCallsFromSteps(executionSteps: ExecutionStepItem[]): McpCallItem[] {
  return executionSteps
    .filter((step) => isMcpStep(step))
    .map((step) => {
      const normalizedStepStatus = String(step.stepStatus ?? '').trim().toUpperCase();
      const stepContent = step.content ?? '';
      return {
        callId: `replay-step-${step.id}`,
        toolId: String(step.stepType ?? 'mcp_tool'),
        displayName: step.stepTitle || 'MCP 调用',
        input: '',
        content: stepContent,
        rawResult: stepContent || undefined,
        phase: normalizedStepStatus === 'COMPLETED' ? 'complete' : 'start',
        status: normalizedStepStatus === 'COMPLETED' ? 'completed' : 'running',
      } satisfies McpCallItem;
    });
}

/**
 * 从当前消息列表中读取末条助手消息的面板字段，用于会话回放时兜底补齐。
 * @param messages 消息列表。
 * @returns 面板字段快照。
 */
function readLatestAssistantPanelState(messages: ChatMessageItem[]): {
  id?: string;
  content?: string;
  conversationId?: string;
  mcpCalls?: McpCallItem[];
  processCards?: ProcessCardItem[];
  searchProgress?: MessageSearchProgress;
  timelineItems?: MessageTimelineItem[];
} {
  const latestAssistantMessage = [...messages]
    .reverse()
    .find((message) => message.role === 'ASSISTANT');
  if (!latestAssistantMessage) {
    return {};
  }
  return {
    id: latestAssistantMessage.id,
    content: latestAssistantMessage.content,
    conversationId: latestAssistantMessage.conversationId,
    mcpCalls: latestAssistantMessage.mcpCalls,
    processCards: latestAssistantMessage.processCards,
    searchProgress: latestAssistantMessage.searchProgress,
    timelineItems: latestAssistantMessage.timelineItems,
  };
}

/**
 * 在会话重载时回填末条助手消息的 MCP/搜索面板字段，避免流式字段被接口空值覆盖。
 * @param messages 回放消息。
 * @param options 回填上下文。
 * @returns 合并后的消息列表。
 */
function patchLatestAssistantReplayPanels(
  messages: ChatMessageItem[],
  options: {
    latestAssistantMcpCalls?: McpCallItem[];
    previousMessages?: ChatMessageItem[];
    targetConversationId?: string;
    executionSteps: ExecutionStepItem[];
    references: ReferenceItem[];
    preferPreviousContent?: boolean;
  },
): ChatMessageItem[] {
  const latestAssistantMcpCalls =
    options.latestAssistantMcpCalls && options.latestAssistantMcpCalls.length > 0
      ? options.latestAssistantMcpCalls
      : undefined;
  const replayMessages = patchLatestAssistantMcpCalls(messages, latestAssistantMcpCalls);
  const latestAssistantIndex = [...replayMessages]
    .map((message, index) => ({ message, index }))
    .reverse()
    .find((item) => item.message.role === 'ASSISTANT')?.index;
  if (latestAssistantIndex == null) {
    return replayMessages;
  }
  const latestAssistantMessage = replayMessages[latestAssistantIndex];
  const previousPanelState = readLatestAssistantPanelState(options.previousMessages ?? []);
  const derivedMcpCalls = deriveMcpCallsFromSteps(options.executionSteps);
  const previousReplayContent = normalizeReplayContent(previousPanelState.content);
  const nextReplayContent = normalizeReplayContent(latestAssistantMessage.content);
  const targetConversationId = normalizeReplayConversationId(options.targetConversationId);
  const latestMessageConversationId = normalizeReplayConversationId(
    latestAssistantMessage.conversationId,
  );
  const isTargetConversationReplay =
    targetConversationId.length > 0 && latestMessageConversationId === targetConversationId;
  const shouldPreserveReplayContent =
    // 业务边界：只有 selectConversation 已确认这是当前流式助手的目标会话回放时，
    // 才允许空/短历史正文让位给本地正文；普通历史切换继续以接口返回为准。
    options.preferPreviousContent === true &&
    isTargetConversationReplay &&
    previousReplayContent.length > 0 &&
    previousReplayContent.length > nextReplayContent.length;
  const nextContent = shouldPreserveReplayContent
    ? previousPanelState.content
    : latestAssistantMessage.content;
  const nextMcpCalls =
    latestAssistantMessage.mcpCalls && latestAssistantMessage.mcpCalls.length > 0
      ? latestAssistantMessage.mcpCalls
      : previousPanelState.mcpCalls && previousPanelState.mcpCalls.length > 0
        ? previousPanelState.mcpCalls
        : derivedMcpCalls.length > 0
          ? derivedMcpCalls
          : undefined;
  const replaySearchProgress = deriveSearchProgressFromReplay(options.executionSteps, options.references);
  const nextSearchProgress =
    latestAssistantMessage.searchProgress ??
    previousPanelState.searchProgress ??
    replaySearchProgress;
  const shouldPreservePreviousPanels =
    shouldPreserveReplayContent &&
    previousPanelState.processCards != null &&
    previousPanelState.processCards.length > 0;
  const derivedProcessCards = deriveProcessCardsFromReplay({
    message: latestAssistantMessage,
    executionSteps: options.executionSteps,
    references: options.references,
    mcpCalls: nextMcpCalls ?? [],
    searchProgress: nextSearchProgress,
  });
  const nextProcessCards = shouldPreservePreviousPanels
    // 活跃流收敛到目标会话时，本地过程卡和 timeline 的 ID 是同一套来源；
    // 继续用回放兜底卡会追加 replay-* 节点，导致正文虽保留但过程链路被压扁。
    ? previousPanelState.processCards
    : pickMostInformativeProcessCards([
        latestAssistantMessage.processCards,
        previousPanelState.processCards,
        derivedProcessCards,
      ]);
  const nextTimelineItems = mergeReplayTimelineItems(
    latestAssistantMessage.timelineItems,
    previousPanelState.timelineItems,
    nextProcessCards,
    shouldPreserveReplayContent,
  );
  const shouldPatch =
    (nextMcpCalls && nextMcpCalls.length > 0) !=
      ((latestAssistantMessage.mcpCalls?.length ?? 0) > 0) ||
    nextSearchProgress !== latestAssistantMessage.searchProgress ||
    nextProcessCards !== latestAssistantMessage.processCards ||
    nextTimelineItems !== latestAssistantMessage.timelineItems ||
    normalizeReplayContent(nextContent) !== nextReplayContent;
  if (!shouldPatch) {
    return replayMessages;
  }
  return replayMessages.map((message, index) =>
    index === latestAssistantIndex
      ? {
          ...message,
          content: nextContent,
          mcpCalls: nextMcpCalls,
          processCards: nextProcessCards,
          searchProgress: nextSearchProgress,
          timelineItems: nextTimelineItems,
        }
      : message,
  );
}

/**
 * 判断本地末条助手消息是否属于正在收敛的活跃流式会话。
 * 业务边界：新建云端会话收到真实 ID 后，乐观消息的 conversationId 可能仍是 pending，
 * 因此必须用目标会话 ID 与活跃流 messageId 双重确认，避免普通历史浏览误保留本地旧正文。
 */
function isActiveAssistantReplayContext(
  messages: ChatMessageItem[],
  targetConversationId: string,
  activeStreamState: ActiveStreamState | null,
) {
  const normalizedTargetConversationId = normalizeReplayConversationId(targetConversationId);
  if (
    normalizedTargetConversationId.length === 0 ||
    normalizeReplayConversationId(activeStreamState?.conversationId) !== normalizedTargetConversationId ||
    !activeStreamState?.activeMessageId
  ) {
    return false;
  }
  const previousPanelState = readLatestAssistantPanelState(messages);
  return previousPanelState.id === activeStreamState.activeMessageId;
}

/**
 * 回放同一会话时优先保留本地已形成的混合时间线，再把历史接口补充的过程卡同步进去。
 * @param replayTimeline 历史消息自带的时间线。
 * @param previousTimeline 当前本地消息时间线。
 * @param processCards 合并后的过程卡片。
 * @param preservePreviousTimeline 是否应保护本地流式时间线。
 * @returns 合并后的时间线。
 */
function mergeReplayTimelineItems(
  replayTimeline: MessageTimelineItem[] | undefined,
  previousTimeline: MessageTimelineItem[] | undefined,
  processCards: ProcessCardItem[] | undefined,
  preservePreviousTimeline: boolean,
): MessageTimelineItem[] | undefined {
  if (!preservePreviousTimeline || !previousTimeline || previousTimeline.length === 0) {
    return replayTimeline;
  }
  return syncProcessCardsToTimeline(
    previousTimeline,
    previousTimeline
      .filter((item): item is Extract<MessageTimelineItem, { type: 'process' }> => item.type === 'process')
      .map((item) => item.card),
    processCards,
  );
}

/**
 * 回放阶段从多种候选过程链路中选择信息量最高的一条，避免完整流式过程被接口兜底短文案覆盖。
 * @param candidates 候选过程列表，顺序代表来源优先级。
 * @returns 最完整的过程列表。
 */
function pickMostInformativeProcessCards(
  candidates: Array<ProcessCardItem[] | undefined>,
): ProcessCardItem[] | undefined {
  const availableCandidates = candidates.filter(
    (candidate): candidate is ProcessCardItem[] => Array.isArray(candidate) && candidate.length > 0,
  );
  if (availableCandidates.length === 0) {
    return undefined;
  }
  return availableCandidates.reduce((bestCandidate, candidate) => {
    const bestScore = scoreProcessCards(bestCandidate);
    const candidateScore = scoreProcessCards(candidate);
    return candidateScore > bestScore ? candidate : bestCandidate;
  });
}

/**
 * 过程链路评分：真实工具明细、结果细节、较长摘要都比兜底恢复文案更有价值。
 * @param cards 过程卡片列表。
 * @returns 信息量评分。
 */
function scoreProcessCards(cards: ProcessCardItem[]): number {
  return cards.reduce((score, card) => {
    const detailScore = (card.details ?? []).reduce(
      (detailTotal, detail) => detailTotal + Math.min(detail.content.length, 500) / 10,
      0,
    );
    const summaryScore = Math.min(card.summary.length, 220) / 20;
    const restorePenalty = card.summary.includes('已恢复历史') || card.summary.includes('历史上下文')
      ? 25
      : 0;
    const typeScore = card.type === 'tool_call' || card.type === 'tool_result' ? 18 : 8;
    return score + typeScore + detailScore + summaryScore - restorePenalty;
  }, 0);
}

/**
 * 归一化消息正文，便于比较“哪一份回放更完整”。
 * @param content 消息正文。
 * @returns 去除首尾空白后的正文。
 */
function normalizeReplayContent(content?: string) {
  return (content ?? '').trim();
}

function normalizeReplayConversationId(conversationId?: string | null) {
  return String(conversationId ?? '').trim();
}

/**
 * 清理旧版前端/后端模板生成的工具前“思考”文案；只保留真实正文、工具调用和工具结果。
 * @param messages 待展示消息列表。
 * @returns 已过滤伪造过程文案的消息列表。
 */
function sanitizeMessagesForProcessDisplay(messages: ChatMessageItem[]): ChatMessageItem[] {
  return messages.map((message) => {
    const nextProcessCards = sanitizeProcessCardsForDisplay(message.processCards);
    const nextTimelineItems = sanitizeTimelineItemsForDisplay(message.timelineItems);
    if (nextProcessCards === message.processCards && nextTimelineItems === message.timelineItems) {
      return message;
    }
    return {
      ...message,
      processCards: nextProcessCards,
      timelineItems: nextTimelineItems,
    };
  });
}

/**
 * 过滤过程卡片中的模板化工具前说明。
 * @param cards 原始过程卡片。
 * @returns 过滤后的过程卡片。
 */
function sanitizeProcessCardsForDisplay(
  cards: ProcessCardItem[] | undefined,
): ProcessCardItem[] | undefined {
  if (!cards) {
    return cards;
  }
  const nextCards = cards.filter((card) => !isSyntheticToolThoughtCard(card));
  return nextCards.length === cards.length ? cards : nextCards;
}

/**
 * 同步过滤消息时间线里的模板化工具前说明，避免刷新历史时再次显示假过程。
 * @param items 原始时间线。
 * @returns 过滤后的时间线。
 */
function sanitizeTimelineItemsForDisplay(
  items: MessageTimelineItem[] | undefined,
): MessageTimelineItem[] | undefined {
  if (!items) {
    return items;
  }
  const nextItems = items.filter(
    (item) => item.type !== 'process' || !isSyntheticToolThoughtCard(item.card),
  );
  return nextItems.length === items.length ? items : nextItems;
}

/**
 * 判断过程卡是否为模板化工具前说明，而非模型真实输出。
 * @param card 过程卡片。
 * @returns 是否应隐藏。
 */
function isSyntheticToolThoughtCard(card: ProcessCardItem): boolean {
  if (card.type !== 'analysis' || card.presentation !== 'react') {
    return false;
  }
  const summary = card.summary.trim();
  return (
    summary.startsWith('需要调用') ||
    summary.startsWith('需要通过网页搜索确认资料') ||
    summary === '需要通过网页搜索获取实时资料。'
  );
}

/**
 * 根据历史消息面板字段兜底派生主消息区过程链路，避免刷新后过程信息完全消失。
 * @param options 回放上下文。
 * @returns 最小可用过程卡片列表。
 */
function deriveProcessCardsFromReplay(options: {
  message: ChatMessageItem;
  executionSteps: ExecutionStepItem[];
  references: ReferenceItem[];
  mcpCalls: McpCallItem[];
  searchProgress?: MessageSearchProgress;
}): ProcessCardItem[] | undefined {
  const processCards: ProcessCardItem[] = [];
  if (options.message.role !== 'ASSISTANT') {
    return undefined;
  }
  const searchSteps = options.executionSteps.filter((step) => isSearchStepType(step.stepType));
  const replayAnalysisSummary = resolveReplayAnalysisSummary(options);
  if (replayAnalysisSummary) {
    processCards.push({
      id: 'replay-analysis',
      type: 'analysis',
      title: '分析问题',
      summary: replayAnalysisSummary,
      status: 'completed',
    });
  }
  for (const call of options.mcpCalls) {
    processCards.push({
      id: `replay-tool-call-${call.callId ?? call.toolId}`,
      type: 'tool_call',
      title: `调用${call.displayName || call.toolId || '工具'}`,
      summary: call.progressText ?? call.content ?? `调用${call.displayName || call.toolId || '工具'}。`,
      status: call.status === 'error' ? 'error' : 'completed',
      toolId: call.toolId,
      displayName: call.displayName,
      details: call.params
        ? [
            {
              label: '参数',
              content: formatProcessCardDetail(call.params),
            },
          ]
        : undefined,
    });
    if (call.rawResult != null || call.content) {
      processCards.push({
        id: `replay-tool-result-${call.callId ?? call.toolId}`,
        type: 'tool_result',
        title: '已获取结果',
        summary: resolveToolResultSummary(call),
        status: call.status === 'error' ? 'error' : 'completed',
        toolId: call.toolId,
        displayName: call.displayName,
        details: [
          {
            label: '结果',
            content: formatProcessCardDetail(call.rawResult ?? call.content),
          },
        ],
      });
    }
  }
  if (!processCards.some((card) => card.toolId === 'search')) {
    for (const step of searchSteps) {
      processCards.push(
        buildSearchToolCallCard({
          id: `replay-${step.id}`,
          title: step.stepTitle || '搜索资料',
          summary: step.content ?? '',
          status:
            String(step.stepStatus ?? '').trim().toUpperCase() === 'COMPLETED'
              ? 'completed'
              : 'running',
        }),
      );
    }
  }
  if (
    (options.searchProgress?.items.length ?? 0) > 0 &&
    !processCards.some((card) => card.type === 'tool_result' && card.toolId === 'search')
  ) {
    processCards.push(
      ...(options.searchProgress?.items ?? []).map((item) =>
        buildSearchResultProcessCardFromProgressItem(
          item,
          options.searchProgress?.status ?? 'completed',
        ),
      ),
    );
  }
  return processCards.length > 0 ? processCards : undefined;
}

/**
 * 从历史消息或非工具步骤中提取真实分析摘要；没有真实来源时不伪造过程文案。
 * @param options 回放上下文。
 * @returns 分析摘要；没有可用真实分析时返回 undefined。
 */
function resolveReplayAnalysisSummary(options: {
  message: ChatMessageItem;
  executionSteps: ExecutionStepItem[];
  references: ReferenceItem[];
  mcpCalls: McpCallItem[];
  searchProgress?: MessageSearchProgress;
}): string | undefined {
  const thinkingContent = options.message.thinkingContent?.trim();
  if (thinkingContent) {
    return thinkingContent;
  }
  const firstMeaningfulStep = options.executionSteps.find(
    (step) =>
      !isSearchStepType(step.stepType) &&
      !isMcpStep(step) &&
      String(step.content ?? step.stepTitle ?? '').trim().length > 0,
  );
  if (firstMeaningfulStep?.content) {
    return firstMeaningfulStep.content;
  }
  if (firstMeaningfulStep?.stepTitle) {
    return firstMeaningfulStep.stepTitle;
  }
  return undefined;
}
