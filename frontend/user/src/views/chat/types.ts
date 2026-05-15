/**
 * 统一定义聊天工作区前端状态模型，保证三栏面板和数据请求共享同一份类型约束。
 */
export interface ConversationItem {
  id: string;
  title: string;
  status: string;
  lastMessageAt?: string;
  lastRunId?: string;
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
 * 统一描述聊天工作区对页面和侧边栏暴露的状态与动作。
 */
export interface ChatWorkspaceController {
  conversations: ConversationItem[];
  activeConversationId: string | null;
  messages: ChatMessageItem[];
  executionSteps: ExecutionStepItem[];
  references: ReferenceItem[];
  artifacts: ArtifactItem[];
  sampleQuestions: SampleQuestionItem[];
  isStreaming: boolean;
  isCancelling: boolean;
  streamError: string;
  inputValue: string;
  isBootstrapping: boolean;
  setInputValue: (value: string) => void;
  submitMessage: () => Promise<void>;
  cancelCurrentStream: () => Promise<void>;
  selectConversation: (conversationId: string, sourceConversations?: ConversationItem[]) => Promise<void>;
  startNewConversation: () => Promise<void>;
  renameConversation: (conversationId: string, title: string) => Promise<void>;
  deleteConversation: (conversationId: string) => Promise<void>;
  renameDialog: {
    conversationId: string | null;
    initialTitle: string;
    isOpen: boolean;
    open: (conversationId: string, initialTitle: string) => void;
    close: () => void;
  };
  deleteDialog: {
    conversationId: string | null;
    title: string;
    isOpen: boolean;
    open: (conversationId: string, title: string) => void;
    close: () => void;
  };
}
