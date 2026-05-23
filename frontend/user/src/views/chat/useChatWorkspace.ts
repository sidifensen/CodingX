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
  ConversationItem,
  ExecutionStepItem,
  McpCallItem,
  McpItem,
  MessageSearchProgress,
  MessageSearchProgressItem,
  PendingAttachmentItem,
  ProcessCardItem,
  ReferenceItem,
  SampleQuestionItem,
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
  markWorkspaceConversationOwnership,
  readWorkspaceSnapshot,
  saveConversationRecordToWorkspace,
  upsertWorkspaceSnapshot,
} from './localConversationStorage';

const DEFAULT_CLOUD_WORKSPACE_LABEL = '历史记录';
const DEFAULT_LOCAL_WORKSPACE_LABEL = '历史记录';
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
 * 按会话 ID 去重合并列表，保留已有顺序并将新增项追加到末尾。
 * @param primary 主列表。
 * @param secondary 待合并列表。
 * @returns 合并后的会话列表。
 */
function mergeConversationListById(primary: ConversationItem[], secondary: ConversationItem[]) {
  const mergedConversations = [...primary];
  const existingConversationIds = new Set(primary.map((conversation) => conversation.id));
  for (const conversation of secondary) {
    if (existingConversationIds.has(conversation.id)) {
      continue;
    }
    existingConversationIds.add(conversation.id);
    mergedConversations.push(conversation);
  }
  return mergedConversations;
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
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
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
  }>({
    isOpen: false,
    conversationId: null,
    initialTitle: '',
  });
  const [deleteDialogState, setDeleteDialogState] = useState<{
    isOpen: boolean;
    conversationId: string | null;
    title: string;
  }>({
    isOpen: false,
    conversationId: null,
    title: '',
  });
  const streamStateRef = useRef<ActiveStreamState | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const streamMcpCallsRef = useRef<Record<string, McpCallItem[]>>({});
  const streamSessionSeedRef = useRef(0);
  const activeStreamSessionIdRef = useRef<number | null>(null);
  const streamQueueTimerRef = useRef<number | null>(null);
  const skipNextRuntimeSyncRef = useRef(false);

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
          };
    const nextConversations = upsertConversationToTop(conversations, nextConversation);
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
    // 业务约束：云端环境不绑定本地目录，避免与本地工作空间混淆。
    const nextWorkspacePath =
      activeRuntimeTarget === 'local' ? hostBoundRepositoryPath : null;
    const nextWorkspaceId =
      activeRuntimeTarget === 'local' ? hostWorkspaceId : null;
    const nextPartitionKey = buildWorkspacePartitionKey(activeRuntimeTarget, nextWorkspacePath);
    const isSamePartition = nextPartitionKey === activeWorkspacePartitionKey;
    const nextWorkspaceLabel = nextWorkspacePath
      ? getWorkspaceLabel(nextWorkspacePath)
      : getDefaultWorkspaceLabel(activeRuntimeTarget);
    const nextSnapshot = readWorkspaceSnapshot(nextPartitionKey);
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
      const preferredConversationId = didHydrateConversationFromSnapshot
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
      } else if (!shouldKeepLandingState && !didHydrateConversationFromSnapshot) {
        // 业务意图：首次进入且无显式会话上下文时停留首页，不自动跳转到最新会话。
        clearConversationPlayback(true);
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
    if (selectionContext.groupType === 'history' || isWorkspaceHistoryPartitionKey(selectionContext.partitionKey)) {
      await selectConversation(conversationId, conversations);
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
    const latestSnapshot = readWorkspaceSnapshot(selectionContext.partitionKey);
    await selectConversation(conversationId, latestSnapshot.conversations);
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
    const nextReplayMessages = patchLatestAssistantReplayPanels(nextMessages, {
      latestAssistantMcpCalls,
      previousMessages: messages,
      executionSteps: [],
      references: [],
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
      executionSteps: nextSteps,
      references: nextReferences,
    });
    setMessages(nextReplayMessagesWithPanels);
    setExecutionSteps(nextSteps);
    setReferences(nextReferences);
    setArtifacts(nextArtifacts);
    setCurrentExperts(nextCurrentExperts);
    setCurrentSkills(nextCurrentSkills);
    setCurrentMcps(nextCurrentMcps);
    const conversationList = sourceConversations ?? conversations;
    const selectedConversation = conversationList.find((item) => item.id === conversationId);
    if (selectedConversation?.lastRunId && nextReplayMessagesWithPanels.length > 0) {
      streamStateRef.current = {
        conversationId,
        activeMessageId:
          nextReplayMessagesWithPanels[nextReplayMessagesWithPanels.length - 1].id,
      };
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

    const optimisticConversationId = activeConversationId ?? 'pending-conversation';
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
        processCards: [buildAnalysisProcessCard('analysis-initial')],
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
          workspaceId,
          deepThinkingEnabled,
          mcpConnected,
          selectedMcpCodes,
          selectedSkillCodes,
          selectedExpertCode,
          workspacePath,
          attachmentIds,
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
      const nextConversations = await loadConversations(token);
      const nextConversationId = streamStateRef.current?.conversationId ?? activeConversationId;
      if (nextConversationId) {
        await selectConversation(
          nextConversationId,
          nextConversations,
          streamMcpCallsRef.current[optimisticAssistantId],
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
    setRenameDialogState({ isOpen: false, conversationId: null, initialTitle: '' });
    setDeleteDialogState({ isOpen: false, conversationId: null, title: '' });
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
            ? {
                ...message,
                content: `${message.content}${delta}`,
                processCards: upsertProcessCard(
                  finalizeCardsByType(message.processCards ?? [], ['analysis']),
                  {
                    id: 'synthesis-direct',
                    type: 'synthesis',
                    title: '整理结论',
                    summary: '正在整理最终回答。',
                    status: 'running',
                  },
                ),
              }
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
            ? {
                ...message,
                thinkingContent: `${message.thinkingContent ?? ''}${delta}`,
                processCards: upsertProcessCard(
                  message.processCards ?? [],
                  buildAnalysisProcessCard('analysis-thinking', delta),
                ),
              }
            : message,
        ),
      );
      return;
    }

    if (eventName === 'mcp-call' && isRecord(payload)) {
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
            ? {
                ...message,
                mcpCalls: mergeMcpCallsById(message.mcpCalls ?? [], call),
                processCards: mergeMcpCallIntoProcessCards(message.processCards ?? [], call),
              }
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
              ? {
                  ...message,
                  searchProgress: {
                    status: 'running',
                    items: message.searchProgress?.items ?? [],
                  },
                  processCards: upsertProcessCard(
                    message.processCards ?? [],
                    buildSearchToolCallCard({
                      id: String(payload.id ?? 'search-step'),
                      title: String(payload.stepTitle ?? '调用网页搜索'),
                      summary:
                        typeof payload.content === 'string' && payload.content.trim().length > 0
                          ? payload.content
                          : '正在检索实时资料。',
                    }),
                  ),
                }
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
            processCards: mergeReferenceIntoProcessCards(
              message.processCards ?? [],
              nextReference,
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
    renameDialog: {
      ...renameDialogState,
      open: (conversationId: string, initialTitle: string) =>
        setRenameDialogState({ isOpen: true, conversationId, initialTitle }),
      close: () => setRenameDialogState({ isOpen: false, conversationId: null, initialTitle: '' }),
    },
    deleteDialog: {
      ...deleteDialogState,
      open: (conversationId: string, title: string) =>
        setDeleteDialogState({ isOpen: true, conversationId, title }),
      close: () => setDeleteDialogState({ isOpen: false, conversationId: null, title: '' }),
    },
  } satisfies ChatWorkspaceController;

  /**
   * 只刷新真实会话列表，避免在发送后错误回跳到旧会话。
   * @param token 当前登录令牌。
   * @returns 最新会话列表。
   */
  async function loadConversations(token: string, effectiveWorkspaceId: string | null = workspaceId) {
    const remoteConversations = await ChatApi.listConversations(token, effectiveWorkspaceId);
    const shouldKeepLandingState =
      activeConversationId == null && messages.length === 0 && !readConversationIdFromUrl();
    const fallbackWorkspacePath = workspacePath ?? null;
    const currentSnapshot = readWorkspaceSnapshot(
      buildWorkspacePartitionKey(activeRuntimeTarget, fallbackWorkspacePath),
    );
    const persistedActiveConversationId = currentSnapshot.activeConversationId ?? null;
    if (activeRuntimeTarget === 'cloud') {
      setConversations(remoteConversations);
      upsertWorkspaceSnapshot(activeRuntimeTarget, fallbackWorkspacePath, {
        conversations: remoteConversations,
        activeConversationId:
          activeConversationId ??
          persistedActiveConversationId ??
          (shouldKeepLandingState ? null : (remoteConversations[0]?.id ?? null)),
        workspaceLabel: fallbackWorkspacePath
          ? getWorkspaceLabel(fallbackWorkspacePath)
          : getDefaultWorkspaceLabel(activeRuntimeTarget),
      });
      refreshWorkspaceGroups('all');
      return remoteConversations;
    }
    const nextWorkspaceConversations = resolveWorkspaceConversations(
      activeRuntimeTarget,
      fallbackWorkspacePath,
      effectiveWorkspaceId,
      remoteConversations,
    );
    markWorkspaceConversationOwnership(
      activeRuntimeTarget,
      fallbackWorkspacePath,
      nextWorkspaceConversations.map((conversation) => conversation.id),
    );
    const nextHistoryConversations = filterUnassignedConversations(remoteConversations);
    const localDefaultSnapshot = readWorkspaceSnapshot(buildWorkspacePartitionKey('local', null));
    const mergedLocalDefaultConversations = mergeConversationListById(
      localDefaultSnapshot.conversations,
      nextHistoryConversations,
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
    });
    upsertWorkspaceSnapshot('local', null, {
      conversations: mergedLocalDefaultConversations,
      activeConversationId: localDefaultSnapshot.activeConversationId ?? null,
      workspaceLabel: getDefaultWorkspaceLabel('local'),
      conversationRecords: localDefaultSnapshot.conversationRecords,
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
      await selectConversation(conversationId, snapshot.conversations, undefined, false);
      return;
    }
    const replayMessages = patchLatestAssistantReplayPanels(record.messages, {
      previousMessages: messages,
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
  maybeSelectedExpertCode?: string | null,
) {
  const skillMessageParseResult = parseSkillMessage(question);
  const searchParams = new URLSearchParams({
    question: skillMessageParseResult.question,
  });
  let repositoryPath: string | null | undefined;
  let attachmentIds: string[] | undefined;
  let selectedExpertCode: string | null | undefined;
  if (Array.isArray(attachmentIdsOrRepositoryPath)) {
    repositoryPath = repositoryPathOrSelectedExpertCode;
    attachmentIds = attachmentIdsOrRepositoryPath;
    selectedExpertCode = maybeSelectedExpertCode;
  } else {
    repositoryPath = attachmentIdsOrRepositoryPath;
    attachmentIds = maybeAttachmentIds;
    selectedExpertCode = repositoryPathOrSelectedExpertCode;
  }
  if (conversationId != null) {
    searchParams.set('conversationId', String(conversationId));
  }
  if (workspaceId != null && workspaceId.trim().length > 0) {
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
 * 构造默认的分析阶段卡片；若传入 thinking 增量则使用可读摘要覆盖默认文案。
 * @param id 卡片标识。
 * @param thinkingDelta 可选思考增量。
 * @returns 分析卡片。
 */
function buildAnalysisProcessCard(id: string, thinkingDelta?: string): ProcessCardItem {
  return {
    id,
    type: 'analysis',
    title: '分析问题',
    summary:
      thinkingDelta && thinkingDelta.trim().length > 0
        ? summarizeThinkingDelta(thinkingDelta)
        : '正在判断问题类型，并准备选择合适的工具或回答路径。',
    status: 'running',
  };
}

/**
 * 根据搜索步骤创建统一的工具调用卡片。
 * @param options 搜索步骤信息。
 * @returns 搜索工具卡片。
 */
function buildSearchToolCallCard(options: {
  id: string;
  title: string;
  summary: string;
}): ProcessCardItem {
  return {
    id: `process-${options.id}`,
    type: 'tool_call',
    title: options.title || '调用网页搜索',
    summary: options.summary || '正在检索实时资料。',
    status: 'running',
    toolId: 'search',
    displayName: '网页搜索',
  };
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
  const nextCards = upsertProcessCard(
    finalizeCardsByType(cards, ['analysis', 'tool_call']),
    {
      id: 'search-result',
      type: 'tool_result',
      title: '已获取结果',
      summary: '已获取检索来源，正在比对可用结论。',
      status: 'completed',
      toolId: 'search',
      displayName: '网页搜索',
      details: [
        {
          label: '结果',
          content: [reference.title, reference.siteName, reference.url].filter(Boolean).join('\n'),
        },
      ],
    },
  );
  return upsertProcessCard(nextCards, {
    id: 'search-synthesis',
    type: 'synthesis',
    title: '整理结论',
    summary: '正在根据检索结果整理最终回答。',
    status: 'running',
  });
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
  if (call.phase === 'start' || call.phase === 'progress') {
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
      details: call.params
        ? [
            {
              label: '参数',
              content: formatProcessCardDetail(call.params),
            },
          ]
        : undefined,
    });
    return nextCards;
  }
  if (call.phase === 'complete') {
    nextCards = finalizeCardsByType(nextCards, ['tool_call']);
    nextCards = upsertProcessCard(nextCards, {
      id: `tool-result-${call.callId ?? call.toolId}`,
      type: 'tool_result',
      title: '已获取结果',
      summary: `已获取${call.displayName || call.toolId || '工具'}结果。`,
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
    nextCards = upsertProcessCard(nextCards, {
      id: `tool-synthesis-${call.callId ?? call.toolId}`,
      type: 'synthesis',
      title: '整理结论',
      summary: '正在根据工具结果整理最终回答。',
      status: 'running',
      toolId: call.toolId,
      displayName: call.displayName,
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
 * 对思考增量做最小可读摘要，避免直接向用户暴露原始思维流。
 * @param delta thinking 增量。
 * @returns 用户可读摘要。
 */
function summarizeThinkingDelta(delta: string): string {
  const normalized = delta.replace(/\s+/g, ' ').trim();
  if (normalized.length <= 48) {
    return normalized;
  }
  return `${normalized.slice(0, 48)}...`;
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
  mcpCalls?: McpCallItem[];
  processCards?: ProcessCardItem[];
  searchProgress?: MessageSearchProgress;
} {
  const latestAssistantMessage = [...messages]
    .reverse()
    .find((message) => message.role === 'ASSISTANT');
  if (!latestAssistantMessage) {
    return {};
  }
  return {
    mcpCalls: latestAssistantMessage.mcpCalls,
    processCards: latestAssistantMessage.processCards,
    searchProgress: latestAssistantMessage.searchProgress,
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
    executionSteps: ExecutionStepItem[];
    references: ReferenceItem[];
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
  const nextProcessCards =
    latestAssistantMessage.processCards && latestAssistantMessage.processCards.length > 0
      ? latestAssistantMessage.processCards
      : previousPanelState.processCards && previousPanelState.processCards.length > 0
        ? previousPanelState.processCards
        : deriveProcessCardsFromReplay({
            message: latestAssistantMessage,
            executionSteps: options.executionSteps,
            references: options.references,
            mcpCalls: nextMcpCalls ?? [],
            searchProgress: nextSearchProgress,
          });
  const shouldPatch =
    (nextMcpCalls && nextMcpCalls.length > 0) !=
      ((latestAssistantMessage.mcpCalls?.length ?? 0) > 0) ||
    nextSearchProgress !== latestAssistantMessage.searchProgress ||
    (nextProcessCards && nextProcessCards.length > 0) !=
      ((latestAssistantMessage.processCards?.length ?? 0) > 0);
  if (!shouldPatch) {
    return replayMessages;
  }
  return replayMessages.map((message, index) =>
    index === latestAssistantIndex
      ? {
          ...message,
          mcpCalls: nextMcpCalls,
          processCards: nextProcessCards,
          searchProgress: nextSearchProgress,
        }
      : message,
  );
}

/**
 * 根据历史消息面板字段兜底派生主消息区过程时间线，避免刷新后过程信息完全消失。
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
  processCards.push({
    id: 'replay-analysis',
    type: 'analysis',
    title: '分析问题',
    summary: '已根据历史上下文恢复本次回答的处理过程。',
    status: 'completed',
  });
  for (const call of options.mcpCalls) {
    processCards.push({
      id: `replay-tool-call-${call.callId ?? call.toolId}`,
      type: 'tool_call',
      title: `调用${call.displayName || call.toolId || '工具'}`,
      summary: call.progressText ?? `已调用${call.displayName || call.toolId || '工具'}。`,
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
        summary: `已恢复${call.displayName || call.toolId || '工具'}结果。`,
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
  if ((options.searchProgress?.items.length ?? 0) > 0 && !processCards.some((card) => card.toolId === 'search')) {
    processCards.push({
      id: 'replay-search-call',
      type: 'tool_call',
      title: '调用网页搜索',
      summary: '已恢复历史检索过程。',
      status: 'completed',
      toolId: 'search',
      displayName: '网页搜索',
    });
    processCards.push({
      id: 'replay-search-result',
      type: 'tool_result',
      title: '已获取结果',
      summary: `已恢复 ${options.searchProgress?.items.length ?? 0} 条搜索来源。`,
      status: options.searchProgress?.status === 'error' ? 'error' : 'completed',
      toolId: 'search',
      displayName: '网页搜索',
      details: [
        {
          label: '结果',
          content: (options.searchProgress?.items ?? [])
            .map((item) => [item.title, item.siteName, item.url].filter(Boolean).join(' | '))
            .join('\n'),
        },
      ],
    });
  }
  processCards.push({
    id: 'replay-synthesis',
    type: 'synthesis',
    title: '整理结论',
    summary: '已恢复历史回答的整理阶段。',
    status:
      options.message.status === 'FAILED' || options.message.status === 'error'
        ? 'error'
        : options.message.status === 'CANCELLED' || options.message.status === 'cancelled'
          ? 'cancelled'
          : 'completed',
  });
  return processCards.length > 0 ? processCards : undefined;
}
