import { useEffect, useRef, useState } from 'react';
import { AuthStorage } from '../../utils/authStorage';
import { ChatApi } from './chatApi';
import { extractSseEvents } from './sse';
import {
  ActiveStreamState,
  ArtifactItem,
  ChatMessageItem,
  ChatWorkspaceController,
  ConversationItem,
  ExecutionStepItem,
  McpCallItem,
  McpItem,
  ReferenceItem,
  SampleQuestionItem,
} from './types';

/**
 * 聚合聊天页三栏所需的真实状态、接口请求与 SSE 流式控制。
 */
export function useChatWorkspace(isAuthenticated: boolean) {
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [executionSteps, setExecutionSteps] = useState<ExecutionStepItem[]>([]);
  const [references, setReferences] = useState<ReferenceItem[]>([]);
  const [artifacts, setArtifacts] = useState<ArtifactItem[]>([]);
  const [sampleQuestions, setSampleQuestions] = useState<SampleQuestionItem[]>([]);
  const [availableMcps, setAvailableMcps] = useState<McpItem[]>([]);
  const [selectedMcpCodes, setSelectedMcpCodesState] = useState<string[]>([]);
  const [mcpConnected, setMcpConnected] = useState(true);
  const [isStreaming, setIsStreaming] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [deepThinkingEnabled, setDeepThinkingEnabled] = useState(false);
  const [streamError, setStreamError] = useState('');
  const [inputValue, setInputValue] = useState('');
  const [isBootstrapping, setIsBootstrapping] = useState(false);
  const [renameDialogState, setRenameDialogState] = useState<{ isOpen: boolean; conversationId: string | null; initialTitle: string }>({
    isOpen: false,
    conversationId: null,
    initialTitle: '',
  });
  const [deleteDialogState, setDeleteDialogState] = useState<{ isOpen: boolean; conversationId: string | null; title: string }>({
    isOpen: false,
    conversationId: null,
    title: '',
  });
  const streamStateRef = useRef<ActiveStreamState | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  /**
   * 统一更新技能选择列表，支持直接赋值与函数式更新。
   * @param nextValue 目标值或计算函数。
   */
  const setSelectedMcpCodes = (nextValue: string[] | ((previous: string[]) => string[])) => {
    if (typeof nextValue === 'function') {
      setSelectedMcpCodesState((previous) => nextValue(previous));
      return;
    }
    setSelectedMcpCodesState(nextValue);
  };

  useEffect(() => {
    if (!isAuthenticated) {
      resetWorkspace();
      return;
    }
    void bootstrapWorkspace();
  }, [isAuthenticated]);

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
      const [nextConversations, nextSampleQuestions, nextMcps] = await Promise.all([
        loadConversations(token),
        ChatApi.listSampleQuestions(token),
        ChatApi.listMcps(token),
      ]);
      setSampleQuestions(nextSampleQuestions);
      setAvailableMcps(nextMcps);
      setSelectedMcpCodes(nextMcps.map((item) => item.mcpCode));
      setMcpConnected(nextMcps.length > 0);
      if (nextConversations[0]?.id) {
        await selectConversation(nextConversations[0].id, nextConversations);
      } else {
        clearConversationPlayback();
      }
    } finally {
      setIsBootstrapping(false);
    }
  };

  /**
   * 切换到指定会话并回放消息与右栏数据。
   * @param conversationId 会话标识。
   * @param sourceConversations 可选的最新会话列表，避免重复读取旧状态。
   */
  const selectConversation = async (conversationId: string, sourceConversations?: ConversationItem[]) => {
    const token = currentToken();
    if (!token) {
      return;
    }
    setActiveConversationId(conversationId);
    const [nextMessages, nextSteps, nextReferences, nextArtifacts] = await Promise.all([
      ChatApi.listMessages(token, conversationId),
      ChatApi.listSteps(token, conversationId),
      ChatApi.listReferences(token, conversationId),
      ChatApi.listArtifacts(token, conversationId),
    ]);
    setMessages(nextMessages);
    setExecutionSteps(nextSteps);
    setReferences(nextReferences);
    setArtifacts(nextArtifacts);
    const conversationList = sourceConversations ?? conversations;
    const selectedConversation = conversationList.find((item) => item.id === conversationId);
    if (selectedConversation?.lastRunId && nextMessages.length > 0) {
      streamStateRef.current = {
        conversationId,
        activeMessageId: nextMessages[nextMessages.length - 1].id,
      };
    }
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
    setIsStreaming(true);
    abortControllerRef.current?.abort();
    abortControllerRef.current = new AbortController();

    const optimisticConversationId = activeConversationId ?? 'pending-conversation';
    const optimisticMessageId = `optimistic-user-${Date.now()}`;
    const optimisticAssistantId = `optimistic-assistant-${Date.now()}`;
    streamStateRef.current = {
      conversationId: optimisticConversationId,
      activeMessageId: optimisticAssistantId,
    };

    setMessages((previousMessages) => [
      ...previousMessages,
      {
        id: optimisticMessageId,
        conversationId: optimisticConversationId,
        role: 'USER',
        content: question,
        status: 'COMPLETED',
      },
      {
        id: optimisticAssistantId,
        conversationId: optimisticConversationId,
        role: 'ASSISTANT',
        content: '',
        status: 'streaming',
      },
    ]);

    try {
      const response = await fetch(
        buildStreamRequestUrl(question, activeConversationId, deepThinkingEnabled, mcpConnected, selectedMcpCodes),
        {
        headers: {
          satoken: token,
        },
        signal: abortControllerRef.current.signal,
      });
      if (!response.ok) {
        throw new Error(await response.text() || '聊天请求失败');
      }
      setInputValue('');
      await consumeSseStream(response, optimisticAssistantId);
      const nextConversations = await loadConversations(token);
      const nextConversationId = streamStateRef.current?.conversationId ?? activeConversationId;
      if (nextConversationId) {
        await selectConversation(nextConversationId, nextConversations);
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        setStreamError('已停止当前生成');
      } else {
        setStreamError(error instanceof Error ? error.message : '聊天请求失败');
      }
    } finally {
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
        setMessages((previousMessages) => previousMessages.map((message, index) =>
          index === previousMessages.length - 1 ? { ...message } : message,
        ));
      }
    }
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
    setAvailableMcps([]);
    setSelectedMcpCodes([]);
    setMcpConnected(false);
    setIsStreaming(false);
    setIsCancelling(false);
    setStreamError('');
    setInputValue('');
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
      setExecutionSteps((previousSteps) => upsertById(previousSteps, {
        id: String(payload.id ?? ''),
        runId: String(payload.runId ?? ''),
        stepType: String(payload.stepType ?? ''),
        stepTitle: String(payload.stepTitle ?? ''),
        stepStatus: String(payload.stepStatus ?? ''),
        sequenceNo: Number(payload.sequenceNo ?? 0),
        content: typeof payload.content === 'string' ? payload.content : undefined,
      }));
      return;
    }

    if (eventName === 'reference' && isRecord(payload)) {
      setReferences((previousReferences) => upsertById(previousReferences, {
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
      }));
      return;
    }

    if (eventName === 'artifact' && isRecord(payload)) {
      setArtifacts((previousArtifacts) => upsertById(previousArtifacts, {
        id: String(payload.id ?? ''),
        runId: String(payload.runId ?? ''),
        messageId: payload.messageId == null ? undefined : String(payload.messageId),
        conversationId: String(payload.conversationId ?? ''),
        artifactType: String(payload.artifactType ?? ''),
        name: String(payload.name ?? ''),
        mimeType: typeof payload.mimeType === 'string' ? payload.mimeType : undefined,
        storagePath: String(payload.storagePath ?? ''),
        contentPreview: typeof payload.preview === 'string'
          ? payload.preview
          : typeof payload.contentPreview === 'string'
            ? payload.contentPreview
            : undefined,
      }));
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
    conversations,
    activeConversationId,
    messages,
    executionSteps,
    references,
    artifacts,
    sampleQuestions,
    availableMcps,
    selectedMcpCodes,
    mcpConnected,
    isStreaming,
    isCancelling,
    deepThinkingEnabled,
    streamError,
    inputValue,
    isBootstrapping,
    setInputValue,
    setDeepThinkingEnabled,
    setSelectedMcpCodes,
    setMcpConnected,
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
    const nextConversations = await ChatApi.listConversations(token);
    setConversations(nextConversations);
    return nextConversations;
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
  }
}

/**
 * 统一拼装聊天流请求地址，保证新建态不会误带旧会话标识。
 * @param question 用户输入问题。
 * @param conversationId 当前选中的会话标识。
 * @returns 可直接用于 fetch 的 SSE 地址。
 */
function buildStreamRequestUrl(
  question: string,
  conversationId: string | null,
  deepThinkingEnabled: boolean,
  mcpConnected: boolean,
  selectedMcpCodes: string[],
) {
  const searchParams = new URLSearchParams({
    question,
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
  return `/api/chat/stream?${searchParams.toString()}`;
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
