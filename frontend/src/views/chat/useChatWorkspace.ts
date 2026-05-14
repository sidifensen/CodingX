import { useEffect, useRef, useState } from 'react';
import { AuthStorage } from '../../utils/authStorage';
import { ChatApi } from './chatApi';
import { extractSseEvents } from './sse';
import {
  ActiveStreamState,
  ArtifactItem,
  ChatMessageItem,
  ConversationItem,
  ExecutionStepItem,
  ReferenceItem,
} from './types';

/**
 * 聚合聊天页三栏所需的真实状态、接口请求与 SSE 流式控制。
 */
export function useChatWorkspace(isAuthenticated: boolean) {
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [executionSteps, setExecutionSteps] = useState<ExecutionStepItem[]>([]);
  const [references, setReferences] = useState<ReferenceItem[]>([]);
  const [artifacts, setArtifacts] = useState<ArtifactItem[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [streamError, setStreamError] = useState('');
  const [inputValue, setInputValue] = useState('');
  const [isBootstrapping, setIsBootstrapping] = useState(false);
  const streamStateRef = useRef<ActiveStreamState | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

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
      const nextConversations = await ChatApi.listConversations(token);
      setConversations(nextConversations);
      if (nextConversations[0]?.id) {
        await selectConversation(nextConversations[0].id, nextConversations);
      } else {
        setActiveConversationId(null);
        setMessages([]);
        setExecutionSteps([]);
        setReferences([]);
        setArtifacts([]);
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
  const selectConversation = async (conversationId: number, sourceConversations?: ConversationItem[]) => {
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

    const optimisticConversationId = activeConversationId ?? -1;
    const optimisticMessageId = Date.now();
    const optimisticAssistantId = optimisticMessageId + 1;
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
      const response = await fetch(`/api/chat/stream?conversationId=${encodeURIComponent(activeConversationId ?? 2001)}&question=${encodeURIComponent(question)}`, {
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
      await bootstrapWorkspace();
      const nextConversationId = streamStateRef.current?.conversationId ?? activeConversationId;
      if (nextConversationId) {
        await selectConversation(nextConversationId);
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
   * 提交当前最后一条助手消息的点赞。
   */
  const submitPositiveFeedback = async () => {
    const token = currentToken();
    const latestAssistantMessage = [...messages].reverse().find((message) => message.role === 'ASSISTANT');
    if (!token || !activeConversationId || !latestAssistantMessage) {
      return;
    }
    await ChatApi.submitFeedback(token, latestAssistantMessage.id, {
      conversationId: activeConversationId,
      vote: 1,
      reason: 'helpful',
      comment: 'frontend feedback',
    });
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
    setActiveConversationId(null);
    setMessages([]);
    setExecutionSteps([]);
    setReferences([]);
    setArtifacts([]);
    setIsStreaming(false);
    setIsCancelling(false);
    setStreamError('');
    setInputValue('');
    streamStateRef.current = null;
  };

  /**
   * 增量消费后端 SSE，并把关键事件同步到前端三栏状态。
   * @param response fetch 返回的 SSE 响应。
   * @param optimisticAssistantId 当前流式助手消息标识。
   */
  const consumeSseStream = async (response: Response, optimisticAssistantId: number) => {
    const reader = response.body?.getReader();
    if (!reader) {
      return;
    }
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) {
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
  const applySseEvent = (eventName: string, payload: unknown, optimisticAssistantId: number) => {
    if (eventName === 'meta' && isRecord(payload)) {
      const conversationId = Number(payload.conversationId);
      if (!Number.isNaN(conversationId)) {
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

    if (eventName === 'step' && isRecord(payload)) {
      setExecutionSteps((previousSteps) => upsertById(previousSteps, {
        id: Number(payload.id),
        runId: Number(payload.runId),
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
        id: Number(payload.id),
        runId: Number(payload.runId),
        messageId: payload.messageId == null ? undefined : Number(payload.messageId),
        conversationId: Number(payload.conversationId),
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
        id: Number(payload.id),
        runId: Number(payload.runId),
        messageId: payload.messageId == null ? undefined : Number(payload.messageId),
        conversationId: Number(payload.conversationId),
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
    isStreaming,
    isCancelling,
    streamError,
    inputValue,
    isBootstrapping,
    setInputValue,
    submitMessage,
    cancelCurrentStream,
    selectConversation,
    submitPositiveFeedback,
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
function upsertById<T extends { id: number }>(items: T[], nextItem: T): T[] {
  const existingIndex = items.findIndex((item) => item.id === nextItem.id);
  if (existingIndex === -1) {
    return [...items, nextItem];
  }
  return items.map((item) => (item.id === nextItem.id ? nextItem : item));
}
