/**
 * 统一定义聊天工作区前端状态模型，保证三栏面板和数据请求共享同一份类型约束。
 */
export interface ConversationItem {
  id: number;
  title: string;
  status: string;
  lastMessageAt?: string;
  lastRunId?: number;
}

/**
 * 描述聊天消息回放与流式拼接需要的字段。
 */
export interface ChatMessageItem {
  id: number;
  conversationId: number;
  runId?: number;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  thinkingContent?: string;
  thinkingDuration?: number;
  status: string;
  provider?: string;
  model?: string;
  errorMessage?: string;
  createdAt?: string;
}

/**
 * 描述右栏执行步骤项目。
 */
export interface ExecutionStepItem {
  id: number;
  runId: number;
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
  id: number;
  runId: number;
  messageId?: number;
  conversationId: number;
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
  id: number;
  runId: number;
  messageId?: number;
  conversationId: number;
  artifactType: string;
  name: string;
  mimeType?: string;
  storagePath: string;
  contentPreview?: string;
}

/**
 * 记录当前 SSE 会话的流式上下文。
 */
export interface ActiveStreamState {
  conversationId: number;
  activeMessageId: number;
}

/**
 * 统一描述后端 `meta` 事件目前会返回的会话上下文。
 */
export interface StreamMetaPayload {
  conversationId: number;
  deepThinking?: boolean;
  taskId?: number;
  traceId?: string;
}

/**
 * 统一描述聊天工作区对页面和侧边栏暴露的状态与动作。
 */
export interface ChatWorkspaceController {
  conversations: ConversationItem[];
  activeConversationId: number | null;
  messages: ChatMessageItem[];
  executionSteps: ExecutionStepItem[];
  references: ReferenceItem[];
  artifacts: ArtifactItem[];
  isStreaming: boolean;
  isCancelling: boolean;
  streamError: string;
  inputValue: string;
  isBootstrapping: boolean;
  setInputValue: (value: string) => void;
  submitMessage: () => Promise<void>;
  cancelCurrentStream: () => Promise<void>;
  selectConversation: (conversationId: number, sourceConversations?: ConversationItem[]) => Promise<void>;
  submitPositiveFeedback: () => Promise<void>;
  startNewConversation: () => Promise<void>;
}
