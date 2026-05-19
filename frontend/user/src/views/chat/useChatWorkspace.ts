import { useEffect, useMemo, useRef, useState } from 'react';
import { AuthStorage } from '../../utils/authStorage';
import { ChatApi } from './chatApi';
import { extractSseEvents } from './sse';
import {
  ActiveStreamState,
  ArtifactItem,
  ChatAttachmentItem,
  ChatSkillItem,
  CurrentMcpItem,
  CurrentSkillItem,
  ChatMessageItem,
  ChatWorkspaceController,
  ConversationItem,
  ExecutionStepItem,
  McpCallItem,
  McpItem,
  PendingAttachmentItem,
  ReferenceItem,
  SampleQuestionItem,
  UseChatWorkspaceOptions,
  WorkspaceConversationGroup,
} from './types';
import {
  buildWorkspaceHistoryPartitionKey,
  buildWorkspacePartitionKey,
  filterUnassignedConversations,
  getWorkspaceLabel,
  findWorkspacePartitionByConversationId,
  listWorkspaceGroups,
  markWorkspaceConversationOwnership,
  readWorkspaceSnapshot,
  saveConversationRecordToWorkspace,
  upsertWorkspaceHistorySnapshot,
  upsertWorkspaceSnapshot,
} from './localConversationStorage';

const DEFAULT_CLOUD_WORKSPACE_LABEL = '云端工作空间';
const DEFAULT_LOCAL_WORKSPACE_LABEL = '本地工作空间';

/**
 * 根据运行环境返回默认工作空间标题，避免本地/云端标签混淆。
 * @param runtimeTarget 运行环境。
 * @returns 默认工作空间标题。
 */
function getDefaultWorkspaceLabel(runtimeTarget: 'cloud' | 'local') {
  return runtimeTarget === 'local' ? DEFAULT_LOCAL_WORKSPACE_LABEL : DEFAULT_CLOUD_WORKSPACE_LABEL;
}

/**
 * 聚合聊天页三栏所需的真实状态、接口请求与 SSE 流式控制。
 */
export function useChatWorkspace(
  isAuthenticated: boolean,
  options?: UseChatWorkspaceOptions,
) {
  const onUnauthorizedRef = useRef(options?.onUnauthorized);
  onUnauthorizedRef.current = options?.onUnauthorized;
  const hostContext = options?.hostContext ?? null;
  const bindWorkspacePath =
    options?.bindWorkspacePath ??
    (async () => {
      return;
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
    [hostContext?.executionTargets],
  );
  const [activeRuntimeTarget, setActiveRuntimeTargetState] = useState<'cloud' | 'local'>(
    runtimeTargets[0] ?? 'cloud',
  );
  const [workspaceGroups, setWorkspaceGroups] = useState<WorkspaceConversationGroup[]>([]);
  const [activeWorkspacePartitionKey, setActiveWorkspacePartitionKey] = useState<string | null>(
    null,
  );
  const [workspacePath, setWorkspacePath] = useState<string | null>(null);
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

  /**
   * 统一更新 MCP 选择列表，支持直接赋值与函数式更新。
   * @param nextValue 目标值或计算函数。
   */
  const setSelectedMcpCodes = (nextValue: string[] | ((previous: string[]) => string[])) => {
    if (typeof nextValue === 'function') {
      setSelectedMcpCodesState((previous) => nextValue(previous));
      return;
    }
    setSelectedMcpCodesState(nextValue);
  };

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
   * 当前工作空间分区刷新后，重新读取对应快照，保证切换目录时左侧与主区同步。
   */
  useEffect(() => {
    if (!runtimeTargets.length) {
      return;
    }
    if (!runtimeTargets.includes(activeRuntimeTarget)) {
      setActiveRuntimeTargetState(runtimeTargets[0] ?? 'cloud');
      return;
    }
    // 业务约束：云端环境不绑定本地目录，避免与本地工作空间混淆。
    const nextWorkspacePath =
      activeRuntimeTarget === 'local' ? hostContext?.localResource?.boundRepositoryPath ?? null : null;
    const nextPartitionKey = buildWorkspacePartitionKey(activeRuntimeTarget, nextWorkspacePath);
    const nextSnapshot = readWorkspaceSnapshot(nextPartitionKey);
    setWorkspacePath(nextWorkspacePath);
    setWorkspaceLabel(
      nextWorkspacePath
        ? getWorkspaceLabel(nextWorkspacePath)
        : getDefaultWorkspaceLabel(activeRuntimeTarget),
    );
    setActiveWorkspacePartitionKey(nextPartitionKey);
    setConversations(nextSnapshot.conversations);
    setActiveConversationId(nextSnapshot.activeConversationId);
    setWorkspaceGroups(listWorkspaceGroups(activeRuntimeTarget));
    if (nextSnapshot.activeConversationId && nextSnapshot.conversations.length > 0) {
      void restoreWorkspaceSnapshot(nextPartitionKey, nextSnapshot.activeConversationId);
    } else {
      clearConversationPlayback();
    }
  }, [hostContext, runtimeTargets, activeRuntimeTarget]);

  useEffect(() => {
    setWorkspaceGroups(listWorkspaceGroups(activeRuntimeTarget));
  }, [activeRuntimeTarget]);

  useEffect(() => {
    if (!isAuthenticated) {
      resetWorkspace();
      return;
    }
    if (!activeWorkspacePartitionKey) {
      return;
    }
    void bootstrapWorkspace();
  }, [isAuthenticated, activeWorkspacePartitionKey]);

  /**
   * 加载初始会话列表并默认选中最近会话。
   */
  const bootstrapWorkspace = async () => {
    const token = currentToken();
    if (!token) {
      return;
    }
    setIsBootstrapping(true);
    try {
      const [nextConversations, nextSampleQuestions, nextSkills, nextMcps] = await Promise.all([
        loadConversations(token),
        ChatApi.listSampleQuestions(token),
        ChatApi.listSkills(token),
        ChatApi.listMcps(token),
      ]);
      setSampleQuestions(nextSampleQuestions);
      setAvailableSkills(nextSkills);
      setSelectedSkillCodes([]);
      setAvailableMcps(nextMcps);
      setSelectedMcpCodes(nextMcps.map((item) => item.mcpCode));
      setMcpConnected(nextMcps.length > 0);
      setWorkspaceGroups(listWorkspaceGroups(activeRuntimeTarget));
      if (activeConversationId) {
        await selectConversation(activeConversationId, nextConversations);
      } else if (nextConversations[0]?.id) {
        await selectConversation(nextConversations[0].id, nextConversations);
      } else {
        clearConversationPlayback();
      }
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
    streamStateRef.current = null;
    setIsStreaming(false);
    setIsCancelling(false);
    setStreamError('');
    setInputValue('');
    clearPendingAttachments();
    if (runtimeTarget === 'cloud') {
      await switchWorkspacePartition('cloud', null, false);
      return;
    }
    const fallbackWorkspacePath = hostContext?.localResource?.boundRepositoryPath ?? workspacePath ?? null;
    await switchWorkspacePartition('local', fallbackWorkspacePath, false);
  };

  /**
   * 按指定运行环境切换工作空间分区，可被环境切换与文件夹选择复用。
   * @param runtimeTarget 目标运行环境。
   * @param nextWorkspacePath 目标工作空间路径。
   * @param shouldBindDesktopPath 是否写回桌面宿主绑定目录。
   */
  const switchWorkspacePartition = async (
    runtimeTarget: 'cloud' | 'local',
    nextWorkspacePath: string | null,
    shouldBindDesktopPath: boolean,
  ) => {
    const normalizedWorkspacePath = nextWorkspacePath ? nextWorkspacePath.trim() : null;
    if (
      shouldBindDesktopPath &&
      normalizedWorkspacePath &&
      hostContext?.hostType === 'desktop'
    ) {
      await bindWorkspacePath(normalizedWorkspacePath);
    }
    const nextPartitionKey = buildWorkspacePartitionKey(runtimeTarget, normalizedWorkspacePath);
    const nextSnapshot = readWorkspaceSnapshot(nextPartitionKey);
    setActiveRuntimeTargetState(runtimeTarget);
    setWorkspacePath(normalizedWorkspacePath);
    setWorkspaceLabel(
      normalizedWorkspacePath
        ? getWorkspaceLabel(normalizedWorkspacePath)
        : getDefaultWorkspaceLabel(runtimeTarget),
    );
    setActiveWorkspacePartitionKey(nextPartitionKey);
    setConversations(nextSnapshot.conversations);
    clearConversationPlayback();
    upsertWorkspaceSnapshot(runtimeTarget, normalizedWorkspacePath, {
      conversations: nextSnapshot.conversations,
      activeConversationId: null,
      workspaceLabel: normalizedWorkspacePath
        ? getWorkspaceLabel(normalizedWorkspacePath)
        : getDefaultWorkspaceLabel(runtimeTarget),
    });
    setWorkspaceGroups(listWorkspaceGroups(runtimeTarget));
    await startNewConversation();
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
    await switchWorkspacePartition('local', nextWorkspacePath, true);
  };

  /**
   * 打开文件夹选择器并将结果切换为当前工作空间。
   */
  const pickRepositoryDirectory = async () => {
    const selectedPath = await openRepositoryPicker();
    if (!selectedPath) {
      return;
    }
    await switchWorkspacePartition('local', selectedPath, false);
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
  ) => {
    const token = currentToken();
    if (!token) {
      return;
    }
    setActiveConversationId(conversationId);
    const [
      nextMessages,
      nextSteps,
      nextReferences,
      nextArtifacts,
      nextCurrentSkills,
      nextCurrentMcps,
    ] = await Promise.all([
      ChatApi.listMessages(token, conversationId),
      ChatApi.listSteps(token, conversationId),
      ChatApi.listReferences(token, conversationId),
      ChatApi.listArtifacts(token, conversationId),
      ChatApi.listCurrentSkills(token, conversationId),
      ChatApi.listCurrentMcps(token, conversationId),
    ]);
    setMessages(patchLatestAssistantMcpCalls(nextMessages, latestAssistantMcpCalls));
    setExecutionSteps(nextSteps);
    setReferences(nextReferences);
    setArtifacts(nextArtifacts);
    setCurrentSkills(nextCurrentSkills);
    setCurrentMcps(nextCurrentMcps);
    const conversationList = sourceConversations ?? conversations;
    const selectedConversation = conversationList.find((item) => item.id === conversationId);
    if (selectedConversation?.lastRunId && nextMessages.length > 0) {
      streamStateRef.current = {
        conversationId,
        activeMessageId: nextMessages[nextMessages.length - 1].id,
      };
    }
    persistConversationState(conversationId, conversationList, {
      messages: nextMessages,
      executionSteps: nextSteps,
      references: nextReferences,
      artifacts: nextArtifacts,
      currentSkills: nextCurrentSkills,
      currentMcps: nextCurrentMcps,
    });
  };

  /**
   * 提交聊天请求并在本地模拟最小流式状态，随后从后端回放最新数据。
   */
  const submitMessage = async () => {
    const token = currentToken();
    const question = inputValue.trim();
    if (!token || !question) {
      return;
    }
    setStreamError('');
    let uploadedAttachments: ChatAttachmentItem[] = [];
    try {
      uploadedAttachments = await uploadPendingAttachments(token, activeConversationId);
    } catch (error) {
      setStreamError(error instanceof Error ? error.message : '附件上传失败');
      return;
    }
    const attachmentIds = uploadedAttachments.map((attachment) => attachment.id);
    setIsStreaming(true);
    abortControllerRef.current?.abort();
    abortControllerRef.current = new AbortController();

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
        status: 'streaming',
      },
    ];
    setMessages(nextMessages);

    persistConversationState(optimisticConversationId, conversations, {
      messages: nextMessages,
      executionSteps,
      references,
      artifacts,
      currentSkills,
      currentMcps,
    });

    try {
      const response = await fetch(
        buildStreamRequestUrl(
          question,
          activeConversationId,
          deepThinkingEnabled,
          mcpConnected,
          selectedMcpCodes,
          selectedSkillCodes,
          workspacePath,
          attachmentIds,
        ),
        {
          headers: {
            satoken: token,
          },
          signal: abortControllerRef.current.signal,
        },
      );
      await ChatApi.assertStreamAuthorized(response);
      setInputValue('');
      clearPendingAttachments();
      await consumeSseStream(response, optimisticAssistantId);
      const nextConversations = await loadConversations(token);
      const nextConversationId = streamStateRef.current?.conversationId ?? activeConversationId;
      if (nextConversationId) {
        await selectConversation(
          nextConversationId,
          nextConversations,
          streamMcpCallsRef.current[optimisticAssistantId],
        );
      }
      setWorkspaceGroups(listWorkspaceGroups(activeRuntimeTarget));
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        setStreamError('已停止当前生成');
      } else if (error instanceof ChatApi.UnauthorizedError) {
        onUnauthorizedRef.current?.();
      } else {
        setStreamError(error instanceof Error ? error.message : '聊天请求失败');
      }
    } finally {
      delete streamMcpCallsRef.current[optimisticAssistantId];
      setIsStreaming(false);
      abortControllerRef.current = null;
    }
  };

  /**
   * 对当前会话发起取消请求。
   */
  const cancelCurrentStream = async () => {
    const token = currentToken();
    if (!token || !activeConversationId) {
      return;
    }
    setIsCancelling(true);
    try {
      abortControllerRef.current?.abort();
      await ChatApi.cancelConversation(token, activeConversationId);
      setIsStreaming(false);
      await selectConversation(activeConversationId);
    } finally {
      setIsCancelling(false);
    }
  };

  /**
   * 切换到“新建对话”空态，并确保下一次发送不再复用旧会话标识。
   */
  const startNewConversation = async () => {
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
    streamStateRef.current = null;
    setIsStreaming(false);
    setIsCancelling(false);
    setStreamError('');
    setInputValue('');
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
    setWorkspaceGroups(listWorkspaceGroups(activeRuntimeTarget));
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
    setWorkspaceGroups(listWorkspaceGroups(activeRuntimeTarget));
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
    setConversations([]);
    clearConversationPlayback();
    setSampleQuestions([]);
    setAvailableSkills([]);
    setCurrentSkills([]);
    setSelectedSkillCodes([]);
    setAvailableMcps([]);
    setCurrentMcps([]);
    setSelectedMcpCodes([]);
    setMcpConnected(false);
    setIsStreaming(false);
    setIsCancelling(false);
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
   */
  const consumeSseStream = async (response: Response, optimisticAssistantId: string) => {
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
          applySseEvent(event.event, event.data, optimisticAssistantId);
        }
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      const { events, remainder } = extractSseEvents(buffer);
      buffer = remainder;
      for (const event of events) {
        applySseEvent(event.event, event.data, optimisticAssistantId);
      }
    }
  };

  /**
   * 根据事件类型更新会话、消息和右栏状态。
   * @param eventName 事件名。
   * @param payload 事件载荷。
   * @param optimisticAssistantId 当前流式助手消息标识。
   */
  const applySseEvent = (eventName: string, payload: unknown, optimisticAssistantId: string) => {
    if (eventName === 'meta' && isRecord(payload)) {
      const conversationId = String(payload.conversationId ?? '');
      if (conversationId) {
        streamStateRef.current = {
          conversationId,
          activeMessageId: optimisticAssistantId,
        };
        setActiveConversationId(conversationId);
      }
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
              }
            : message,
        ),
      );
      return;
    }

    if (eventName === 'mcp-call' && isRecord(payload)) {
      const call: McpCallItem = {
        toolId: String(payload.toolId ?? ''),
        displayName: String(payload.displayName ?? payload.toolId ?? ''),
        input: String(payload.input ?? ''),
        content: String(payload.content ?? ''),
        metadata: isRecord(payload.metadata) ? payload.metadata : undefined,
      };
      streamMcpCallsRef.current[optimisticAssistantId] = [
        ...(streamMcpCallsRef.current[optimisticAssistantId] ?? []),
        call,
      ];
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === optimisticAssistantId
            ? {
                ...message,
                mcpCalls: [...(message.mcpCalls ?? []), call],
              }
            : message,
        ),
      );
      return;
    }

    if (eventName === 'step' && isRecord(payload)) {
      setExecutionSteps((previousSteps) =>
        upsertById(previousSteps, {
          id: String(payload.id ?? ''),
          runId: String(payload.runId ?? ''),
          stepType: String(payload.stepType ?? ''),
          stepTitle: String(payload.stepTitle ?? ''),
          stepStatus: String(payload.stepStatus ?? ''),
          sequenceNo: Number(payload.sequenceNo ?? 0),
          content: typeof payload.content === 'string' ? payload.content : undefined,
        }),
      );
      return;
    }

    if (eventName === 'reference' && isRecord(payload)) {
      setReferences((previousReferences) =>
        upsertById(previousReferences, {
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
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === optimisticAssistantId
            ? {
                ...message,
                content: String(payload.content ?? message.content),
                status: 'done',
              }
            : message,
        ),
      );
      return;
    }

    if (eventName === 'cancel') {
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === optimisticAssistantId
            ? {
                ...message,
                status: 'cancelled',
              }
            : message,
        ),
      );
      return;
    }

    if (eventName === 'error' && isRecord(payload)) {
      setMessages((previousMessages) =>
        previousMessages.map((message) =>
          message.id === optimisticAssistantId
            ? {
                ...message,
                status: 'error',
                errorMessage: String(payload.message ?? '聊天请求失败'),
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
    streamError,
    inputValue,
    pendingAttachments,
    isBootstrapping,
    setInputValue,
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
  async function loadConversations(token: string) {
    const remoteConversations = await ChatApi.listConversations(token);
    const fallbackWorkspacePath = workspacePath ?? null;
    const nextWorkspaceConversations = resolveWorkspaceConversations(
      activeRuntimeTarget,
      fallbackWorkspacePath,
      remoteConversations,
    );
    markWorkspaceConversationOwnership(
      activeRuntimeTarget,
      fallbackWorkspacePath,
      nextWorkspaceConversations.map((conversation) => conversation.id),
    );
    const nextHistoryConversations = filterUnassignedConversations(remoteConversations);
    setConversations(nextWorkspaceConversations);
    upsertWorkspaceSnapshot(activeRuntimeTarget, fallbackWorkspacePath, {
      conversations: nextWorkspaceConversations,
      activeConversationId: activeConversationId ?? nextWorkspaceConversations[0]?.id ?? null,
      workspaceLabel: fallbackWorkspacePath
        ? getWorkspaceLabel(fallbackWorkspacePath)
        : getDefaultWorkspaceLabel(activeRuntimeTarget),
    });
    upsertWorkspaceHistorySnapshot(activeRuntimeTarget, nextHistoryConversations);
    if (
      activeConversationId &&
      !nextWorkspaceConversations.some((conversation) => conversation.id === activeConversationId)
    ) {
      setActiveConversationId(nextWorkspaceConversations[0]?.id ?? null);
    }
    setWorkspaceGroups(listWorkspaceGroups(activeRuntimeTarget));
    return nextWorkspaceConversations;
  }

  /**
   * 清空当前主区与右栏回放状态，但保留左侧真实会话历史。
   */
  function clearConversationPlayback() {
    setActiveConversationId(null);
    setMessages([]);
    setExecutionSteps([]);
    setReferences([]);
    setArtifacts([]);
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
    // 业务约束：会话一旦在当前工作空间产生真实回放，需要从历史分组移除，避免重复展示。
    removeConversationFromHistoryGroup(activeRuntimeTarget, conversationId);
    upsertWorkspaceSnapshot(activeRuntimeTarget, workspacePath, {
      conversations: conversationList,
      activeConversationId: conversationId,
    });
    setWorkspaceGroups(listWorkspaceGroups(activeRuntimeTarget));
  }

  /**
   * 从快照恢复指定会话。
   * @param partitionKey 分区键。
   * @param conversationId 会话标识。
   */
  async function restoreWorkspaceSnapshot(partitionKey: string, conversationId: string) {
    const snapshot = readWorkspaceSnapshot(partitionKey);
    setConversations(snapshot.conversations);
    setWorkspaceGroups(listWorkspaceGroups(activeRuntimeTarget));
    const record = snapshot.conversationRecords[conversationId];
    if (!record) {
      await selectConversation(conversationId, snapshot.conversations);
      return;
    }
    setActiveConversationId(conversationId);
    setMessages(record.messages);
    setExecutionSteps(record.executionSteps);
    setReferences(record.references);
    setArtifacts(record.artifacts);
    setCurrentSkills(record.currentSkills);
    setCurrentMcps(record.currentMcps);
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
    remoteConversations: ConversationItem[],
  ) {
    const currentPartitionKey = buildWorkspacePartitionKey(runtimeTarget, currentWorkspacePath);
    return remoteConversations.filter(
      (conversation) => findWorkspacePartitionByConversationId(conversation.id) === currentPartitionKey,
    );
  }

  /**
   * 将指定会话从历史分组移除，避免会话在“当前工作空间”和“历史会话”重复展示。
   * @param runtimeTarget 运行环境。
   * @param conversationId 会话标识。
   */
  function removeConversationFromHistoryGroup(
    runtimeTarget: 'cloud' | 'local',
    conversationId: string | null,
  ) {
    if (!conversationId) {
      return;
    }
    const historyPartitionKey = buildWorkspaceHistoryPartitionKey(runtimeTarget);
    const historySnapshot = readWorkspaceSnapshot(historyPartitionKey);
    if (!historySnapshot.conversations.some((conversation) => conversation.id === conversationId)) {
      return;
    }
    upsertWorkspaceHistorySnapshot(
      runtimeTarget,
      historySnapshot.conversations.filter((conversation) => conversation.id !== conversationId),
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
  ): Promise<ChatAttachmentItem[]> {
    const pendingItems = pendingAttachments.filter(
      (item) => item.uploadStatus === 'pending' || item.uploadStatus === 'failed',
    );
    if (pendingItems.length === 0) {
      return pendingAttachments
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
                  uploadError: error instanceof Error ? error.message : '附件上传失败',
                }
              : item,
          ),
        );
        throw error;
      }
    }
    const stableUploaded = pendingItems
      .map((item) => item.attachment)
      .filter((attachment): attachment is ChatAttachmentItem => attachment != null);
    return [...stableUploaded, ...uploadedItems];
  }
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
  deepThinkingEnabled: boolean,
  mcpConnected: boolean,
  selectedMcpCodes: string[],
  selectedSkillCodes: string[],
  repositoryPath?: string | null,
  attachmentIds?: string[],
) {
  const skillMessageParseResult = parseSkillMessage(question);
  const searchParams = new URLSearchParams({
    question: skillMessageParseResult.question,
  });
  if (conversationId != null) {
    searchParams.set('conversationId', String(conversationId));
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
 * 解析技能命令输入，生成结构化消息并返回纯文本问题。
 * @param rawQuestion 原始输入。
 * @returns 解析结果。
 */
function parseSkillMessage(rawQuestion: string): {
  question: string;
  structuredMessages: Array<Record<string, unknown>>;
} {
  const question = rawQuestion.trim();
  const skillMatch = question.match(/^@([a-zA-Z0-9_-]+)\s*(.*)$/);
  if (!skillMatch) {
    return { question, structuredMessages: [] };
  }
  const skillCode = skillMatch[1];
  const textContent = (skillMatch[2] ?? '').trim();
  const structuredMessages: Array<Record<string, unknown>> = [
    {
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
    },
    {
      type: 'text',
      data: {
        content: textContent,
      },
    },
  ];
  return {
    question: textContent,
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
