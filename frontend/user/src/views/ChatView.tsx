import React from 'react';
import { motion } from 'motion/react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  ArrowUp,
  ExternalLink,
  ChevronDown,
  CheckCircle2,
  CircleStop,
  Cloud,
  Copy,
  Database,
  File,
  Image as ImageIcon,
  X,
  FileText,
  Globe2,
  Brain,
  Sparkles,
  Paperclip,
  FolderOpen,
  Monitor,
  Search,
  Pencil,
  RotateCcw,
  Share2,
  ThumbsDown,
  ThumbsUp,
  Trash2,
  WandSparkles,
} from 'lucide-react';
import { AuthStorage } from '../utils/authStorage';
import { ChatApi } from './chat/chatApi';
import {
  ChatAttachmentItem,
  ChatWorkspaceController,
  ChatMessageItem,
  MessageSearchProgress,
  MessageSearchProgressItem,
  McpItem,
  PendingAttachmentItem,
  ProcessCardItem,
  SharedConversationPayload,
} from './chat/types';

/**
 * 定义聊天视图的输入属性。
 */
interface ChatViewProps {
  isAuthenticated: boolean;
  isDesktopSidebarCollapsed?: boolean;
  onRequireLogin: () => void;
  workspace: ChatWorkspaceController;
}

/**
 * 渲染接入真实后端数据的聊天三栏工作台。
 */
export default function ChatView({
  isAuthenticated,
  isDesktopSidebarCollapsed = false,
  onRequireLogin,
  workspace,
}: ChatViewProps) {
  // 关键约束：只有用户仍停留在消息尾部附近时才自动跟随，避免流式生成期间抢夺用户手动上滑。
  const AUTO_SCROLL_BOTTOM_THRESHOLD = 48;
  // 步骤：基准底部留白用于兜底，避免在首帧测量前消息末尾与输入区发生重叠。
  const CHAT_INPUT_BASE_SAFE_BOTTOM = 160;
  // 步骤：在输入区高度基础上再补偿额外空隙，确保最后一条消息与操作栏保持可读间距。
  const CHAT_INPUT_EXTRA_SAFE_GAP = 24;
  const {
    runtimeTargets,
    activeRuntimeTarget,
    workspaceGroups,
    activeWorkspacePartitionKey,
    activeConversationId,
    workspaceLabel,
    workspacePath,
    messages,
    executionSteps,
    references,
    artifacts,
    sampleQuestions,
    availableExperts,
    selectedExpertCode,
    currentExperts,
    availableSkills,
    selectedSkillCodes,
    availableMcps,
    selectedMcpCodes,
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
    submitMessage,
    cancelCurrentStream,
    shareConversation,
    deleteConversationMessages,
    regenerateConversation,
    resendUserMessage,
    pickRepositoryDirectory,
    renameDialog,
    deleteDialog,
    renameConversation,
    deleteConversation,
    setActiveWorkspacePath,
  } = workspace;
  const safeAvailableExperts = availableExperts ?? [];
  const safeCurrentExperts = currentExperts ?? [];
  const shareSelectionRounds = React.useMemo(
    () => buildShareSelectionRounds(messages),
    [messages],
  );
  const latestAssistantMessageId = React.useMemo(() => {
    return [...messages].reverse().find((message) => message.role === 'ASSISTANT')?.id ?? null;
  }, [messages]);
  const latestMessageAnchorRef = React.useRef<HTMLDivElement | null>(null);
  const chatScrollRegionRef = React.useRef<HTMLDivElement | null>(null);
  const shouldFollowLatestMessageRef = React.useRef(true);
  const [activeSelectorMode, setActiveSelectorMode] = React.useState<'mcp' | 'skill' | 'expert' | null>(null);
  const [activeRuntimeWorkspaceMenu, setActiveRuntimeWorkspaceMenu] = React.useState<
    'runtime' | 'workspace' | null
  >(null);
  const [skillSearchKeyword, setSkillSearchKeyword] = React.useState('');
  const [expertSearchKeyword, setExpertSearchKeyword] = React.useState('');
  const [skillSelectorSource, setSkillSelectorSource] = React.useState<'button' | 'slash' | null>(
    null,
  );
  const skillSelectorSourceRef = React.useRef<'button' | 'slash' | null>(null);
  const selectorLayerRef = React.useRef<HTMLDivElement | null>(null);
  const runtimeWorkspaceLayerRef = React.useRef<HTMLDivElement | null>(null);
  const chatInputRef = React.useRef<HTMLTextAreaElement | null>(null);
  const inputPreviewRef = React.useRef<HTMLDivElement | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const chatInputDockRef = React.useRef<HTMLDivElement | null>(null);
  const [chatInputSafeBottom, setChatInputSafeBottom] = React.useState(CHAT_INPUT_BASE_SAFE_BOTTOM);
  const [previewAttachment, setPreviewAttachment] = React.useState<{
    src: string;
    alt: string;
  } | null>(null);
  const [shareSelectionState, setShareSelectionState] = React.useState<{
    isActive: boolean;
    selectedAssistantMessageIds: string[];
  }>({ isActive: false, selectedAssistantMessageIds: [] });
  const [deleteSelectionState, setDeleteSelectionState] = React.useState<{
    isActive: boolean;
    selectedAssistantMessageIds: string[];
  }>({ isActive: false, selectedAssistantMessageIds: [] });
  const [editingUserMessage, setEditingUserMessage] = React.useState<{
    messageId: string;
    content: string;
    isSubmitting: boolean;
    errorMessage: string;
  } | null>(null);
  const [pendingShareConversationId, setPendingShareConversationId] = React.useState<string | null>(null);
  const [shareDialogState, setShareDialogState] = React.useState<{
    isOpen: boolean;
    isLoading: boolean;
    url: string;
    previewMessages: ChatMessageItem[];
    errorMessage: string;
  }>({
    isOpen: false,
    isLoading: false,
    url: '',
    previewMessages: [],
    errorMessage: '',
  });

  /**
   * 判断 MCP 是否允许用户在当前会话手动开启。
   * 约束：后端显式禁用/不可用时，前端只允许展示，不允许点击切换。
   * @param mcp MCP 配置项。
   * @returns 是否可切换。
   */
  const isMcpSelectable = React.useCallback((mcp: McpItem) => {
    if (mcp.enabled === 0) {
      return false;
    }
    if (mcp.available === false) {
      return false;
    }
    return true;
  }, []);

  /**
   * 进入分享选择模式；默认选中当前助手消息及其前一条用户消息，贴近千问按轮次分享的交互。
   * @param defaultMessageId 默认触发分享的消息标识。
   */
  const startShareSelection = React.useCallback((defaultMessageId?: string) => {
    const defaultSelection = resolveDefaultShareAssistantMessageIds(
      shareSelectionRounds,
      defaultMessageId,
    );
    if (defaultSelection.length === 0) {
      return;
    }
    setDeleteSelectionState({ isActive: false, selectedAssistantMessageIds: [] });
    setEditingUserMessage(null);
    setShareSelectionState({
      isActive: true,
      selectedAssistantMessageIds: defaultSelection,
    });
  }, [shareSelectionRounds]);

  React.useEffect(() => {
    const handleSidebarShareRequest = (event: Event) => {
      const customEvent = event as CustomEvent<{ conversationId?: string }>;
      const targetConversationId = customEvent.detail?.conversationId ?? activeConversationId;
      if (targetConversationId && targetConversationId !== activeConversationId) {
        setPendingShareConversationId(targetConversationId);
        return;
      }
      startShareSelection();
    };
    window.addEventListener('codingx:start-share-conversation', handleSidebarShareRequest);
    return () => {
      window.removeEventListener('codingx:start-share-conversation', handleSidebarShareRequest);
    };
  }, [activeConversationId, startShareSelection]);

  React.useEffect(() => {
    if (
      !pendingShareConversationId ||
      pendingShareConversationId !== activeConversationId ||
      messages.filter((message) => message.role !== 'SYSTEM').length === 0
    ) {
      return;
    }
    setPendingShareConversationId(null);
    startShareSelection();
  }, [activeConversationId, messages, pendingShareConversationId, startShareSelection]);

  React.useEffect(() => {
    if (!shareSelectionState.isActive) {
      return;
    }
    const validAssistantMessageIdSet = new Set(
      shareSelectionRounds.map((round) => round.assistantMessage.id),
    );
    setShareSelectionState((previousState) => {
      const nextSelectedAssistantMessageIds = previousState.selectedAssistantMessageIds.filter(
        (assistantMessageId) => validAssistantMessageIdSet.has(assistantMessageId),
      );
      if (
        nextSelectedAssistantMessageIds.length === previousState.selectedAssistantMessageIds.length
      ) {
        return previousState;
      }
      return {
        ...previousState,
        selectedAssistantMessageIds: nextSelectedAssistantMessageIds,
      };
    });
  }, [shareSelectionRounds, shareSelectionState.isActive]);

  /**
   * 进入删除选择模式；从用户消息触发时默认选中该问题对应的一轮问答。
   * @param defaultMessageId 触发删除的消息标识。
   */
  const startDeleteSelection = React.useCallback((defaultMessageId?: string) => {
    const defaultSelection = resolveDefaultDeleteAssistantMessageIds(
      shareSelectionRounds,
      defaultMessageId,
    );
    if (defaultSelection.length === 0) {
      return;
    }
    setShareSelectionState({ isActive: false, selectedAssistantMessageIds: [] });
    setEditingUserMessage(null);
    setDeleteSelectionState({
      isActive: true,
      selectedAssistantMessageIds: defaultSelection,
    });
  }, [shareSelectionRounds]);

  React.useEffect(() => {
    if (!deleteSelectionState.isActive) {
      return;
    }
    const validAssistantMessageIdSet = new Set(
      shareSelectionRounds.map((round) => round.assistantMessage.id),
    );
    setDeleteSelectionState((previousState) => {
      const nextSelectedAssistantMessageIds = previousState.selectedAssistantMessageIds.filter(
        (assistantMessageId) => validAssistantMessageIdSet.has(assistantMessageId),
      );
      if (
        nextSelectedAssistantMessageIds.length === previousState.selectedAssistantMessageIds.length
      ) {
        return previousState;
      }
      return {
        ...previousState,
        selectedAssistantMessageIds: nextSelectedAssistantMessageIds,
      };
    });
  }, [deleteSelectionState.isActive, shareSelectionRounds]);

  /**
   * 切换某条助手回复所属轮次是否参与分享；用户问题由轮次自动附带。
   * @param messageId 助手消息标识。
   */
  const toggleShareMessageSelection = React.useCallback((messageId: string) => {
    setShareSelectionState((previousState) => {
      const nextSelectedAssistantMessageIds = previousState.selectedAssistantMessageIds.includes(messageId)
        ? previousState.selectedAssistantMessageIds.filter(
            (selectedMessageId) => selectedMessageId !== messageId,
          )
        : [...previousState.selectedAssistantMessageIds, messageId];
      return {
        ...previousState,
        selectedAssistantMessageIds: nextSelectedAssistantMessageIds,
      };
    });
  }, []);

  /**
   * 切换某条问答轮次是否参与删除，实际删除时会展开成用户问题与 AI 回复两个消息 ID。
   * @param messageId 助手消息标识。
   */
  const toggleDeleteMessageSelection = React.useCallback((messageId: string) => {
    setDeleteSelectionState((previousState) => {
      const nextSelectedAssistantMessageIds = previousState.selectedAssistantMessageIds.includes(messageId)
        ? previousState.selectedAssistantMessageIds.filter(
            (selectedMessageId) => selectedMessageId !== messageId,
          )
        : [...previousState.selectedAssistantMessageIds, messageId];
      return {
        ...previousState,
        selectedAssistantMessageIds: nextSelectedAssistantMessageIds,
      };
    });
  }, []);

  /**
   * 退出分享选择状态并清空选择结果。
   */
  const cancelShareSelection = React.useCallback(() => {
    setShareSelectionState({ isActive: false, selectedAssistantMessageIds: [] });
  }, []);

  /**
   * 退出删除选择状态并清空选择结果。
   */
  const cancelDeleteSelection = React.useCallback(() => {
    setDeleteSelectionState({ isActive: false, selectedAssistantMessageIds: [] });
  }, []);

  /**
   * 根据已选消息生成分享链接，并展示千问风格预览弹窗。
   */
  const confirmShareSelection = async () => {
    const shareableSelectedMessageIds = resolveShareSelectionMessageIds(
      shareSelectionRounds,
      shareSelectionState.selectedAssistantMessageIds,
    );
    if (!activeConversationId || shareableSelectedMessageIds.length === 0) {
      return;
    }
    // 临时乐观消息只有前端占位 ID，提交给后端只会触发 Long 反序列化失败。
    const previewMessages = resolveShareSelectionPreviewMessages(
      shareSelectionRounds,
      shareSelectionState.selectedAssistantMessageIds,
    );
    setShareDialogState({
      isOpen: true,
      isLoading: true,
      url: '',
      previewMessages,
      errorMessage: '',
    });
    try {
      const shareUrl = await shareConversation(activeConversationId, {
        messageIds: shareableSelectedMessageIds,
      });
      setShareDialogState({
        isOpen: true,
        isLoading: false,
        url: shareUrl,
        previewMessages,
        errorMessage: '',
      });
      cancelShareSelection();
    } catch (error) {
      setShareDialogState({
        isOpen: true,
        isLoading: false,
        url: '',
        previewMessages,
        errorMessage: error instanceof Error ? error.message : '分享失败',
      });
    }
  };

  /**
   * 确认删除当前选中的问答轮次，并让工作台同步删除本地回放与后端上下文。
   */
  const confirmDeleteSelection = async () => {
    const deletableSelectedMessageIds = resolveShareSelectionMessageIds(
      shareSelectionRounds,
      deleteSelectionState.selectedAssistantMessageIds,
    );
    if (!activeConversationId || deletableSelectedMessageIds.length === 0) {
      return;
    }
    await deleteConversationMessages(activeConversationId, deletableSelectedMessageIds);
    cancelDeleteSelection();
  };

  /**
   * 过滤技能列表，支持名称与编码模糊检索。
   * @returns 过滤后的技能列表。
   */
  const filteredSkills = React.useMemo(() => {
    const normalizedKeyword = skillSearchKeyword.trim().toLowerCase();
    if (!normalizedKeyword) {
      return availableSkills;
    }
    return availableSkills.filter((skill) => {
      const displayName = (skill.displayName ?? '').toLowerCase();
      const skillCode = (skill.skillCode ?? '').toLowerCase();
      return displayName.includes(normalizedKeyword) || skillCode.includes(normalizedKeyword);
    });
  }, [availableSkills, skillSearchKeyword]);

  /**
   * 过滤专家列表，支持名称与编码模糊检索。
   * @returns 过滤后的专家列表。
   */
  const filteredExperts = React.useMemo(() => {
    const normalizedKeyword = expertSearchKeyword.trim().toLowerCase();
    if (!normalizedKeyword) {
      return safeAvailableExperts;
    }
    return safeAvailableExperts.filter((expert) => {
      const displayName = (expert.displayName ?? '').toLowerCase();
      const expertCode = (expert.expertCode ?? '').toLowerCase();
      const description = (expert.description ?? '').toLowerCase();
      return (
        displayName.includes(normalizedKeyword) ||
        expertCode.includes(normalizedKeyword) ||
        description.includes(normalizedKeyword)
      );
    });
  }, [expertSearchKeyword, safeAvailableExperts]);

  /**
   * 建立可识别技能编码集合，用于从输入文本中反向解析技能选择。
   */
  const availableSkillCodeSet = React.useMemo(
    () => new Set(availableSkills.map((skill) => skill.skillCode)),
    [availableSkills],
  );
  const availableSkillNameMap = React.useMemo(
    () => new Map(availableSkills.map((skill) => [skill.skillCode, skill.displayName])),
    [availableSkills],
  );
  const inputPreviewSegments = React.useMemo(
    () => tokenizeInputForPreview(inputValue, availableSkillNameMap),
    [availableSkillNameMap, inputValue],
  );

  /**
   * 解析输入中的技能文本标记，支持在任意光标位置写入与删除。
   * 标记格式：@skill_code
   * @param rawInput 当前输入文本。
   * @returns 去重后的技能编码列表，顺序按文本中首次出现位置。
   */
  const parseSkillCodesFromInput = React.useCallback(
    (rawInput: string) => {
      const nextSkillCodes: string[] = [];
      const seenSkillCodes = new Set<string>();
      for (const token of extractSkillTokens(rawInput)) {
        const skillCode = token.skillCode;
        if (!availableSkillCodeSet.has(skillCode) || seenSkillCodes.has(skillCode)) {
          continue;
        }
        seenSkillCodes.add(skillCode);
        nextSkillCodes.push(skillCode);
      }
      return nextSkillCodes;
    },
    [availableSkillCodeSet],
  );

  /**
   * 按输入内容同步技能选择，保证“文本即技能来源”，删除文本即可取消技能。
   * @param rawInput 当前输入文本。
   */
  const syncSelectedSkillCodesFromInput = React.useCallback(
    (rawInput: string) => {
      const nextSkillCodes = parseSkillCodesFromInput(rawInput);
      const hasSameSkillCodes =
        nextSkillCodes.length === selectedSkillCodes.length &&
        nextSkillCodes.every((skillCode, index) => skillCode === selectedSkillCodes[index]);
      if (hasSameSkillCodes) {
        return;
      }
      setSelectedSkillCodes(nextSkillCodes);
    },
    [parseSkillCodesFromInput, selectedSkillCodes, setSelectedSkillCodes],
  );

  /**
   * 点击外部区域时自动关闭 MCP/技能下拉，避免面板残留遮挡输入区。
   */
  React.useEffect(() => {
    if (activeSelectorMode == null) {
      return undefined;
    }
    const closeSelectorPanels = (event: MouseEvent) => {
      if (selectorLayerRef.current?.contains(event.target as Node)) {
        return;
      }
      setActiveSelectorMode(null);
      setSkillSelectorSource(null);
    };
    document.addEventListener('mousedown', closeSelectorPanels);
    return () => {
      document.removeEventListener('mousedown', closeSelectorPanels);
    };
  }, [activeSelectorMode]);

  /**
   * 点击输入区外部时关闭环境/工作空间下拉，保持交互一致性。
   */
  React.useEffect(() => {
    if (activeRuntimeWorkspaceMenu == null) {
      return undefined;
    }
    const closeRuntimeWorkspaceMenus = (event: MouseEvent) => {
      if (runtimeWorkspaceLayerRef.current?.contains(event.target as Node)) {
        return;
      }
      setActiveRuntimeWorkspaceMenu(null);
    };
    document.addEventListener('mousedown', closeRuntimeWorkspaceMenus);
    return () => {
      document.removeEventListener('mousedown', closeRuntimeWorkspaceMenus);
    };
  }, [activeRuntimeWorkspaceMenu]);

  /**
   * 切换 MCP 启用状态，允许在弹层内直接管理本次会话可调用 MCP。
   * @param mcpCode 被切换 MCP 编码。
   * @param selectable 当前 MCP 是否可切换。
   */
  const toggleMcpSelection = React.useCallback(
    (mcpCode: string, selectable: boolean) => {
      if (!selectable) {
        return;
      }
      setSelectedMcpCodes((previous) => {
        if (previous.includes(mcpCode)) {
          return previous.filter((item) => item !== mcpCode);
        }
        return [...previous, mcpCode];
      });
      setMcpConnected(true);
    },
    [setSelectedMcpCodes, setMcpConnected],
  );

  /**
   * 按技能编码移除输入文本里的全部技能标记，并保持输入值与技能选择状态一致。
   * @param skillCode 待移除技能编码。
   */
  const removeSkillTokenFromInput = React.useCallback(
    (skillCode: string) => {
      const nextInput = removeSkillTokenByCode(inputValue, skillCode);
      if (nextInput === inputValue) {
        return;
      }
      setInputValue(nextInput);
      syncSelectedSkillCodesFromInput(nextInput);
      window.requestAnimationFrame(() => {
        const textarea = chatInputRef.current;
        if (!textarea) {
          return;
        }
        const nextCaret = Math.min(nextInput.length, textarea.selectionStart ?? nextInput.length);
        textarea.focus();
        textarea.setSelectionRange(nextCaret, nextCaret);
      });
    },
    [inputValue, setInputValue, syncSelectedSkillCodesFromInput],
  );

  /**
   * 切换技能选中状态：支持多选，已选中再次点击可取消。
   * @param skillCode 被选择技能编码。
   */
  const selectSkill = React.useCallback(
    (skillCode: string) => {
      const skillToken = `@${skillCode}`;
      const currentInput = inputValue;
      const isSkillAlreadySelected = selectedSkillCodes.includes(skillCode);
      let nextInput = currentInput;

      if (isSkillAlreadySelected) {
        nextInput = removeSkillTokenByCode(currentInput, skillCode);
      } else if (skillSelectorSource === 'slash') {
        // 步骤：斜杠只作为技能检索触发器，选中后替换为可编辑技能文本标记。
        const remainingText = currentInput.replace(/^\s*\/[^\s]*\s*/, '');
        nextInput = remainingText ? `${skillToken} ${remainingText}` : `${skillToken} `;
      } else {
        const textarea = chatInputRef.current;
        const selectionStart = textarea?.selectionStart ?? currentInput.length;
        const selectionEnd = textarea?.selectionEnd ?? currentInput.length;
        const prefix = currentInput.slice(0, selectionStart);
        const suffix = currentInput.slice(selectionEnd);
        const leftGap = prefix.length > 0 && !/\s$/.test(prefix) ? ' ' : '';
        const rightGap = suffix.length > 0 && !/^\s/.test(suffix) ? ' ' : '';
        const insertion = `${leftGap}${skillToken}${rightGap || ' '}`;
        nextInput = `${prefix}${insertion}${suffix}`;
        const nextCaretPosition = (prefix + insertion).length;
        window.requestAnimationFrame(() => {
          const nextTextarea = chatInputRef.current;
          if (!nextTextarea) {
            return;
          }
          nextTextarea.focus();
          nextTextarea.setSelectionRange(nextCaretPosition, nextCaretPosition);
        });
      }

      setInputValue(nextInput);
      syncSelectedSkillCodesFromInput(nextInput);
      if (skillSelectorSource === 'slash') {
        setActiveSelectorMode(null);
        setSkillSelectorSource(null);
        setSkillSearchKeyword('');
      }
    },
    [inputValue, selectedSkillCodes, setInputValue, skillSelectorSource, syncSelectedSkillCodesFromInput],
  );

  /**
   * 判断当前共享选择弹层是否打开。
   * @returns 共享选择弹层是否打开。
   */
  const isSelectorPanelOpen = activeSelectorMode !== null;
  // 步骤：统一云端/本地图标映射，确保切换器主按钮与下拉选项视觉语义一致。
  const runtimeDisplayLabel = activeRuntimeTarget === 'local' ? '本地' : '云端';
  const ActiveRuntimeIcon = activeRuntimeTarget === 'local' ? Monitor : Cloud;
  /**
   * 工作空间切换仅展示当前环境可选项，避免跨环境误切换。
   */
  const workspaceSwitcherOptions = React.useMemo(() => {
    const runtimeWorkspaceGroups = workspaceGroups.filter(
      (group) => group.runtimeTarget === activeRuntimeTarget && group.groupType !== 'history',
    );
    if (runtimeWorkspaceGroups.length > 0) {
      return runtimeWorkspaceGroups;
    }
    return [
      {
        partitionKey: 'fallback-workspace-option',
        workspacePath: activeRuntimeTarget === 'local' ? workspacePath ?? null : null,
        // 默认分组名称按运行环境拆分，避免云端与本地历史在下拉入口里共享同一标签。
        workspaceLabel:
          workspaceLabel || (activeRuntimeTarget === 'local' ? '本地历史记录' : '云端历史记录'),
        runtimeTarget: activeRuntimeTarget,
        lastOpenedAt: 0,
        activeConversationId: activeConversationId ?? null,
        conversations: [],
      },
    ];
  }, [workspaceGroups, activeRuntimeTarget, workspacePath, workspaceLabel, activeConversationId]);

  const selectedExpert = React.useMemo(
    () => safeAvailableExperts.find((expert) => expert.expertCode === selectedExpertCode) ?? null,
    [safeAvailableExperts, selectedExpertCode],
  );

  /**
   * 维护技能选择器触发来源的最新值，供仅依赖输入变化的 effect 读取。
   */
  React.useEffect(() => {
    skillSelectorSourceRef.current = skillSelectorSource;
  }, [skillSelectorSource]);

  /**
   * 监听输入框斜杠触发技能检索，支持“输入 / 即打开技能列表”。
   * 关键约束：仅在输入变化时触发，避免用户手动切换 MCP/技能后被自动逻辑抢回。
   */
  React.useEffect(() => {
    const normalizedInput = inputValue.trimStart();
    if (!normalizedInput.startsWith('/')) {
      if (skillSelectorSourceRef.current === 'slash') {
        setActiveSelectorMode((current) => (current === 'skill' ? null : current));
        setSkillSelectorSource(null);
        setSkillSearchKeyword('');
      }
      return;
    }
    const slashKeyword = normalizedInput.slice(1).split(/\s+/)[0] ?? '';
    setActiveSelectorMode('skill');
    setSkillSelectorSource('slash');
    setSkillSearchKeyword(slashKeyword);
  }, [inputValue]);

  /**
   * 兜底同步：当输入值由外部写入（示例问题、回放填充）时，同步修正技能选择状态。
   */
  React.useEffect(() => {
    syncSelectedSkillCodesFromInput(inputValue);
  }, [inputValue, syncSelectedSkillCodesFromInput]);

  /**
   * 判断滚动容器是否贴近底部。贴近底部时允许自动跟随最新消息，否则暂停自动滚动。
   * @param scrollElement 消息滚动容器。
   * @returns 是否在底部阈值范围内。
   */
  const isScrollNearBottom = React.useCallback((scrollElement: HTMLDivElement) => {
    const distanceToBottom =
      scrollElement.scrollHeight - (scrollElement.scrollTop + scrollElement.clientHeight);
    return distanceToBottom <= AUTO_SCROLL_BOTTOM_THRESHOLD;
  }, []);

  /**
   * 记录用户当前是否仍希望“跟随输出”；用户上滑后暂停，回到底部后自动恢复。
   * @param scrollElement 消息滚动容器。
   */
  const syncFollowLatestMessageState = React.useCallback(
    (scrollElement: HTMLDivElement | null) => {
      if (!scrollElement) {
        return;
      }
      shouldFollowLatestMessageRef.current = isScrollNearBottom(scrollElement);
    },
    [isScrollNearBottom],
  );

  /**
   * 响应用户滚动操作，实时更新是否继续自动跟随最新消息。
   */
  const handleChatScroll = React.useCallback(
    (event: React.UIEvent<HTMLDivElement>) => {
      syncFollowLatestMessageState(event.currentTarget);
    },
    [syncFollowLatestMessageState],
  );

  /**
   * 统一滚动到底部锚点，供发送动作和消息增量跟随复用。
   */
  const scrollToLatestMessage = React.useCallback(() => {
    if (typeof latestMessageAnchorRef.current?.scrollIntoView === 'function') {
      latestMessageAnchorRef.current.scrollIntoView({ block: 'end' });
      return;
    }
    const scrollRegion = chatScrollRegionRef.current;
    if (!scrollRegion) {
      return;
    }
    scrollRegion.scrollTop = scrollRegion.scrollHeight;
  }, []);

  React.useEffect(() => {
    if (!messages.length) {
      return;
    }
    if (!shouldFollowLatestMessageRef.current) {
      return;
    }
    scrollToLatestMessage();
  }, [messages, scrollToLatestMessage]);

  /**
   * 动态测量底部输入栏高度，并同步到消息滚动区底部留白。
   * 避免输入栏因多行文本增高后遮挡最后一条消息。
   */
  React.useEffect(() => {
    const inputDockElement = chatInputDockRef.current;
    if (!inputDockElement) {
      return undefined;
    }

    const syncChatInputSafeBottom = () => {
      const measuredHeight = inputDockElement.offsetHeight;
      const nextSafeBottom = Math.max(
        CHAT_INPUT_BASE_SAFE_BOTTOM,
        measuredHeight + CHAT_INPUT_EXTRA_SAFE_GAP,
      );
      setChatInputSafeBottom(nextSafeBottom);
    };

    syncChatInputSafeBottom();
    const resizeObserver = new ResizeObserver(syncChatInputSafeBottom);
    resizeObserver.observe(inputDockElement);
    window.addEventListener('resize', syncChatInputSafeBottom);
    return () => {
      resizeObserver.disconnect();
      window.removeEventListener('resize', syncChatInputSafeBottom);
    };
  }, []);

  /**
   * 根据输入内容动态计算输入框高度，保持 1 行起步、最多 9 行后内部滚动。
   */
  React.useEffect(() => {
    const textarea = chatInputRef.current;
    if (!textarea) {
      return;
    }
    // 步骤：先重置为 auto，确保读取 scrollHeight 时拿到真实内容高度。
    textarea.style.height = 'auto';
    const computedStyle = window.getComputedStyle(textarea);
    const lineHeight = Number.parseFloat(computedStyle.lineHeight || '24') || 24;
    const verticalPadding =
      Number.parseFloat(computedStyle.paddingTop || '0') +
      Number.parseFloat(computedStyle.paddingBottom || '0');
    const maxHeight = lineHeight * 9 + verticalPadding;
    const nextHeight = Math.min(textarea.scrollHeight, maxHeight);
    textarea.style.height = `${Math.max(nextHeight, lineHeight + verticalPadding)}px`;
    textarea.style.overflowY = textarea.scrollHeight > maxHeight ? 'auto' : 'hidden';
    const preview = inputPreviewRef.current;
    if (preview) {
      preview.scrollTop = textarea.scrollTop;
      preview.scrollLeft = textarea.scrollLeft;
    }
  }, [inputValue]);

  /**
   * 统一执行输入提交，供按钮、回车键与表单提交复用同一逻辑。
   */
  const submitCurrentInput = async () => {
    if (!inputValue.trim()) {
      return;
    }
    if (!isAuthenticated) {
      onRequireLogin();
      return;
    }
    // 业务意图：用户主动发送消息代表希望查看最新内容，先贴底并恢复自动跟随。
    shouldFollowLatestMessageRef.current = true;
    scrollToLatestMessage();
    await submitMessage();
  };

  /**
   * 统一处理底部输入提交。
   * @param event 表单事件。
   */
  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    await submitCurrentInput();
  };

  /**
   * 打开用户消息的内联编辑态，限制在已落库消息上，避免临时消息被重复提交。
   * @param message 待编辑用户消息。
   */
  const startEditingUserMessage = React.useCallback((message: ChatMessageItem) => {
    if (!PERSISTED_MESSAGE_ID_PATTERN.test(message.id)) {
      return;
    }
    setShareSelectionState({ isActive: false, selectedAssistantMessageIds: [] });
    setDeleteSelectionState({ isActive: false, selectedAssistantMessageIds: [] });
    setEditingUserMessage({
      messageId: message.id,
      content: message.content,
      isSubmitting: false,
      errorMessage: '',
    });
  }, []);

  /**
   * 提交编辑后的用户消息，交给工作台从该位置替换旧上下文并重新生成回答。
   */
  const confirmUserMessageEdit = React.useCallback(async () => {
    if (!editingUserMessage || editingUserMessage.isSubmitting) {
      return;
    }
    const nextContent = editingUserMessage.content.trim();
    if (!nextContent) {
      setEditingUserMessage((previousState) =>
        previousState ? { ...previousState, errorMessage: '消息不能为空' } : previousState,
      );
      return;
    }
    if (!isAuthenticated) {
      onRequireLogin();
      return;
    }
    setEditingUserMessage((previousState) =>
      previousState ? { ...previousState, isSubmitting: true, errorMessage: '' } : previousState,
    );
    try {
      await resendUserMessage(editingUserMessage.messageId, nextContent);
      setEditingUserMessage(null);
    } catch (error) {
      setEditingUserMessage((previousState) =>
        previousState
          ? {
              ...previousState,
              isSubmitting: false,
              errorMessage: error instanceof Error ? error.message : '再次发送失败',
            }
          : previousState,
      );
    }
  }, [editingUserMessage, isAuthenticated, onRequireLogin, resendUserMessage]);

  // 步骤：首页欢迎区应优先渲染，避免初始化接口慢时出现长时间空白屏。
  const showLandingState = activeConversationId == null && !messages.length;
  // 步骤：选中了历史会话但暂无消息时，显示会话级空态而不是回到首页。
  const showConversationEmptyState =
    activeConversationId != null && !messages.length && !isBootstrapping;
  // 业务约束：底部运行环境/工作空间切换仅在新建对话页显示，历史会话页不再重复渲染。
  const showRuntimeWorkspaceSwitcher = showLandingState;
  // 业务约束：云端环境下不展示“云端工作空间”下拉，避免出现无意义的同名选项。
  const showWorkspaceDropdownInSwitcher = showRuntimeWorkspaceSwitcher && activeRuntimeTarget === 'local';

  /**
   * 粘贴图片或文件时直接加入待发送附件队列。
   * @param event 粘贴事件。
   */
  const handlePasteAttachments = React.useCallback(
    async (event: React.ClipboardEvent<HTMLTextAreaElement>) => {
      const clipboardItems = Array.from(event.clipboardData?.items ?? []);
      if (clipboardItems.length === 0) {
        return;
      }
      const files = clipboardItems
        .filter((item) => item.kind === 'file')
        .map((item) => item.getAsFile())
        .filter((file): file is File => file != null);
      if (files.length === 0) {
        return;
      }
      event.preventDefault();
      await addPendingAttachments(files);
    },
    [addPendingAttachments],
  );

  /**
   * 处理系统文件选择器结果并加入待发送附件队列。
   * @param event 输入框 change 事件。
   */
  const handleFileInputChange = React.useCallback(
    async (event: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(event.target.files ?? []);
      if (files.length > 0) {
        await addPendingAttachments(files);
      }
      event.target.value = '';
    },
    [addPendingAttachments],
  );

  /**
   * 选择专家后立即写入当前工作区，并在存在示例问题时预填输入框。
   * @param expertCode 专家编码。
   */
  const selectExpert = React.useCallback(
    (expertCode: string | null) => {
      const nextExpertCode =
        expertCode && selectedExpertCode === expertCode ? null : expertCode;
      setSelectedExpertCode(nextExpertCode);
      if (!nextExpertCode) {
        return;
      }
      const matchedExpert = safeAvailableExperts.find(
        (expert) => expert.expertCode === nextExpertCode,
      );
      if (matchedExpert?.presetQuestion?.trim()) {
        setInputValue(matchedExpert.presetQuestion.trim());
      }
    },
    [safeAvailableExperts, selectedExpertCode, setInputValue, setSelectedExpertCode],
  );

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="relative flex h-full overflow-hidden bg-background"
    >
      <section className="relative flex min-w-0 flex-1 flex-col overflow-hidden">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(71,96,122,0.16),transparent_38%),radial-gradient(circle_at_80%_18%,rgba(188,75,0,0.12),transparent_26%)]" />
        <div className="absolute left-4 right-4 top-4 z-20 hidden items-center justify-between md:flex">
          <div className={`${isDesktopSidebarCollapsed ? '' : 'w-10'}`}>
            {/* 步骤：左上角预留壳层折叠按钮占位，避免与主内容视觉挤压；真实交互由 App 壳层负责。 */}
          </div>
          <div className="w-10" />
        </div>
        <div
          ref={chatScrollRegionRef}
          data-testid="chat-scroll-region"
          className="chat-scroll-region relative flex-1 overflow-y-auto px-4 pt-6 md:px-8 md:pt-20"
          onScroll={handleChatScroll}
          style={{
            paddingBottom: `${chatInputSafeBottom}px`,
            scrollPaddingTop: '96px',
            scrollPaddingBottom: `${chatInputSafeBottom}px`,
          }}
        >
          {showLandingState ? (
            <div className="mx-auto flex max-w-4xl flex-col items-center justify-center px-6 py-16 text-center">
              <h1 className="max-w-3xl text-4xl font-semibold tracking-tight text-foreground md:text-6xl">
                你好，我是 CodingX
              </h1>
              <div className="mt-10 grid w-full max-w-3xl gap-4 md:grid-cols-2">
                {(sampleQuestions.length
                  ? sampleQuestions.map((item, index) => ({
                      icon: [Globe2, Search, Database, FolderOpen][index % 4],
                      title: item.category || '示例问题',
                      desc: item.questionText,
                      iconClassName: [
                        'text-[#8fb3da]',
                        'text-[#ff8a24]',
                        'text-[#a78bfa]',
                        'text-[#f4f4f5]',
                      ][index % 4],
                      dataSource: 'api',
                    }))
                  : [
                      {
                        icon: Globe2,
                        title: '网页读取',
                        desc: '解析并总结外部网页内容',
                        iconClassName: 'text-[#8fb3da]',
                        dataSource: 'fallback',
                      },
                      {
                        icon: Search,
                        title: '调研分析',
                        desc: '深度搜索并生成研究报告',
                        iconClassName: 'text-[#ff8a24]',
                        dataSource: 'fallback',
                      },
                      {
                        icon: Database,
                        title: '数据挖掘',
                        desc: '结构化数据提取与清洗',
                        iconClassName: 'text-[#a78bfa]',
                        dataSource: 'fallback',
                      },
                      {
                        icon: FolderOpen,
                        title: '文件管理',
                        desc: '上传并与您的文档进行对话',
                        iconClassName: 'text-[#f4f4f5]',
                        dataSource: 'fallback',
                      },
                    ]
                ).map((item) => (
                  <button
                    key={`${item.title}-${item.desc}`}
                    type="button"
                    data-source={item.dataSource}
                    onClick={() => setInputValue(item.desc)}
                    className="rounded-[22px] border border-border bg-surface px-6 py-5 text-left shadow-sm transition-colors hover:border-border-active hover:bg-surface-container"
                  >
                    <div className="flex items-center gap-4">
                      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#2b2b31]">
                        <item.icon size={22} className={item.iconClassName} />
                      </div>
                      <div>
                        <div className="text-2xl font-semibold text-foreground">{item.title}</div>
                        <div className="mt-1 text-sm leading-6 text-muted">{item.desc}</div>
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          ) : shareSelectionState.isActive ? (
            <ShareSelectionShell
              mode="share"
              rounds={shareSelectionRounds}
              selectedAssistantMessageIds={shareSelectionState.selectedAssistantMessageIds}
              onToggleRound={toggleShareMessageSelection}
              onPreviewImage={(src, alt) => setPreviewAttachment({ src, alt })}
            />
          ) : deleteSelectionState.isActive ? (
            <ShareSelectionShell
              mode="delete"
              rounds={shareSelectionRounds}
              selectedAssistantMessageIds={deleteSelectionState.selectedAssistantMessageIds}
              onToggleRound={toggleDeleteMessageSelection}
              onPreviewImage={(src, alt) => setPreviewAttachment({ src, alt })}
            />
          ) : showConversationEmptyState ? (
            <div className="mx-auto flex max-w-3xl flex-col items-center justify-center px-6 py-24 text-center">
              <div className="rounded-full border border-border bg-surface-container px-4 py-2 font-mono text-[11px] uppercase tracking-[0.32em] text-muted">
                Conversation
              </div>
              <h2 className="mt-6 text-3xl font-semibold tracking-tight text-foreground">
                当前会话暂无消息
              </h2>
              <p className="mt-4 max-w-xl text-sm leading-7 text-muted">
                这个历史会话还没有可回放内容。你可以直接在下方输入，继续往当前会话追加新的对话。
              </p>
            </div>
          ) : (
            <div className="mx-auto flex max-w-4xl flex-col gap-6">
              {messages.map((message) => {
                const isAssistant = message.role === 'ASSISTANT';
                const isLatestMessage = message.id === messages[messages.length - 1]?.id;
                const messageContent =
                  message.content || (message.status === 'streaming' ? '正在生成回答...' : '');
                return (
                  <div
                    key={message.id}
                    data-message-status={message.status}
                    className={`flex items-start gap-3 ${isAssistant ? 'justify-start' : 'justify-end'}`}
                  >
                    <div
                      className={`min-w-0 px-1 py-1 ${
                        isAssistant ? 'max-w-3xl text-foreground' : 'w-full max-w-3xl'
                      }`}
                    >
                      {isAssistant ? (
                        <>
                          {message.processCards && message.processCards.length > 0 ? (
                            <ProcessTracePanel
                              messageId={message.id}
                              cards={message.processCards}
                            />
                          ) : null}
                          {message.attachments && message.attachments.length > 0 ? (
                            <MessageAttachmentList
                              attachments={message.attachments}
                              onPreviewImage={(src, alt) => setPreviewAttachment({ src, alt })}
                            />
                          ) : null}
                          <MarkdownMessage content={messageContent} messageId={message.id} />
                          <div className="mt-3 space-y-1.5">
                            <AssistantMessageActions
                              messageId={message.id}
                              conversationId={message.conversationId}
                              content={messageContent}
                              isLatestAssistantMessage={message.id === latestAssistantMessageId}
                              userVote={message.userVote}
                              onStartShareSelection={startShareSelection}
                              onRegenerateConversation={regenerateConversation}
                            />
                            {/* 业务意图：搜索来源必须紧跟在消息操作区之后，优先落在倒赞按钮后面，避免被后续提示打断阅读路径。 */}
                            {message.searchProgress?.items?.length ? (
                              <SearchProgressPanel
                                messageId={message.id}
                                progress={message.searchProgress}
                              />
                            ) : null}
                          </div>
                          {message.errorMessage ? (
                            <div className="mt-3 rounded-2xl border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs text-red-300">
                              {message.errorMessage}
                            </div>
                          ) : null}
                        </>
                      ) : (
                        <UserMessageBubble
                          message={message}
                          content={messageContent}
                          skillNameMap={availableSkillNameMap}
                          editState={
                            editingUserMessage?.messageId === message.id
                              ? editingUserMessage
                              : null
                          }
                          onStartEdit={() => startEditingUserMessage(message)}
                          onChangeEditContent={(nextContent) =>
                            setEditingUserMessage((previousState) =>
                              previousState?.messageId === message.id
                                ? {
                                    ...previousState,
                                    content: nextContent,
                                    errorMessage: '',
                                  }
                                : previousState,
                            )
                          }
                          onCancelEdit={() => setEditingUserMessage(null)}
                          onConfirmEdit={() => void confirmUserMessageEdit()}
                          onStartDelete={() => startDeleteSelection(message.id)}
                          onPreviewImage={(src, alt) => setPreviewAttachment({ src, alt })}
                        />
                      )}
                      {isLatestMessage ? (
                        // 步骤：尾部锚点固定高度为 0，确保滚动只对齐到真实内容末端，避免推高滚动高度。
                        <div
                          ref={latestMessageAnchorRef}
                          data-testid="latest-message-anchor"
                          className="h-0 scroll-mb-[160px] md:scroll-mb-[180px]"
                        />
                      ) : null}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
          {streamError ? (
            // 步骤：统一在消息滚动区尾部渲染一条错误提示，避免输入框上方与消息区重复提示。
            <div className="mx-auto mt-6 w-full max-w-4xl rounded-2xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-300">
              {streamError}
            </div>
          ) : null}
        </div>

        {!shareSelectionState.isActive && !deleteSelectionState.isActive ? (
          <div
            ref={chatInputDockRef}
            data-testid="chat-input-dock"
            className="absolute bottom-0 left-0 right-0 bg-background/88 px-4 pb-6 pt-4 backdrop-blur-xl md:px-8"
          >
            <div className="mx-auto max-w-4xl">
            {streamQueueState ? (
              <div className="mb-3 rounded-2xl border border-amber-500/40 bg-amber-500/12 px-4 py-3 text-sm text-amber-200">
                {streamQueueState.message}
              </div>
            ) : null}
            <form
              onSubmit={(event) => void handleSubmit(event)}
              className="rounded-[24px] border border-border bg-surface shadow-[0_20px_64px_rgba(0,0,0,0.12)]"
            >
              <div ref={selectorLayerRef} className="px-4 pb-2 pt-3">
                <div className="relative mb-2">
                  {isSelectorPanelOpen ? (
                    <div
                      data-testid={
                        activeSelectorMode === 'mcp'
                          ? 'mcp-selector-panel'
                          : activeSelectorMode === 'expert'
                            ? 'expert-selector-panel'
                            : 'skill-selector-panel'
                      }
                      // 步骤：弹层宽度在桌面端收敛为中等尺寸，移动端仍自适应屏宽，避免视觉占用过大。
                      className="absolute bottom-[calc(100%+10px)] left-0 z-30 w-[calc(100vw-2.5rem)] max-w-[480px] rounded-2xl border border-border bg-surface px-2 py-2 shadow-[0_18px_44px_rgba(0,0,0,0.25)] md:w-[480px]"
                    >
                      {activeSelectorMode === 'skill' ? (
                        <div className="mb-2 px-1">
                          <input
                            type="text"
                            value={skillSearchKeyword}
                            onChange={(event) => setSkillSearchKeyword(event.target.value)}
                            placeholder="搜索技能"
                            className="h-8 w-full rounded-lg border border-border bg-surface-container px-3 text-xs text-foreground outline-none placeholder:text-muted"
                          />
                        </div>
                      ) : activeSelectorMode === 'expert' ? (
                        <div className="mb-2 px-1">
                          <input
                            type="text"
                            value={expertSearchKeyword}
                            onChange={(event) => setExpertSearchKeyword(event.target.value)}
                            placeholder="搜索专家"
                            className="h-8 w-full rounded-lg border border-border bg-surface-container px-3 text-xs text-foreground outline-none placeholder:text-muted"
                          />
                        </div>
                      ) : null}
                      <div className="max-h-64 overflow-y-auto">
                        {activeSelectorMode === 'mcp' ? (
                          availableMcps.map((mcp) => {
                            const isSelected = selectedMcpCodes.includes(mcp.mcpCode);
                            const selectable = isMcpSelectable(mcp);
                            return (
                              <button
                                key={mcp.mcpCode}
                                type="button"
                                aria-label={`选择MCP ${mcp.displayName}`}
                                aria-disabled={!selectable}
                                disabled={!selectable}
                                onClick={() => toggleMcpSelection(mcp.mcpCode, selectable)}
                                className={`mb-1 flex w-full items-center justify-between rounded-xl px-3 py-2 text-left text-sm transition-colors last:mb-0 ${
                                  !selectable
                                    ? 'cursor-not-allowed bg-surface-container/65 text-muted opacity-60'
                                    : isSelected
                                    ? 'bg-surface-container text-foreground'
                                    : 'text-muted hover:bg-surface-container hover:text-foreground'
                                }`}
                              >
                                <span className="min-w-0">
                                  <span className="block truncate text-foreground">
                                    {mcp.displayName}
                                  </span>
                                  <span className="mt-1 block truncate font-mono text-[11px] text-muted">
                                    /{mcp.mcpCode}
                                  </span>
                                  {!selectable ? (
                                    <span className="mt-1 block text-[10px] text-muted">不可用</span>
                                  ) : null}
                                </span>
                                <span className="inline-flex items-center gap-2">
                                  <span className="sr-only">
                                    {!selectable ? '不可用' : isSelected ? '已启用' : '未启用'}
                                  </span>
                                  <span
                                    role="switch"
                                    aria-label={`切换MCP ${mcp.displayName}`}
                                    aria-checked={selectable && isSelected}
                                    aria-disabled={!selectable}
                                    data-testid={`mcp-switch-${mcp.mcpCode}`}
                                    className={`relative inline-flex h-5 w-9 shrink-0 items-center rounded-full border transition-colors ${
                                      selectable && isSelected
                                        ? 'border-foreground bg-foreground/90'
                                        : 'border-border bg-surface-high'
                                    }`}
                                  >
                                    <span
                                      className={`inline-block h-4 w-4 rounded-full bg-background shadow-sm transition-transform ${
                                        selectable && isSelected
                                          ? 'translate-x-[18px]'
                                          : 'translate-x-[1px]'
                                      }`}
                                    />
                                  </span>
                                </span>
                              </button>
                            );
                          })
                        ) : activeSelectorMode === 'expert' ? (
                          filteredExperts.length ? (
                            filteredExperts.map((expert) => {
                              const isSelected = selectedExpertCode === expert.expertCode;
                              return (
                                <button
                                  key={expert.expertCode}
                                  type="button"
                                  aria-label={`选择专家 ${expert.displayName}`}
                                  onClick={() => selectExpert(expert.expertCode)}
                                  className={`mb-1 flex w-full items-center justify-between rounded-xl px-3 py-2 text-left text-sm transition-colors last:mb-0 ${
                                    isSelected
                                      ? 'bg-surface-container text-foreground'
                                      : 'text-muted hover:bg-surface-container hover:text-foreground'
                                  }`}
                                >
                                  <span className="min-w-0">
                                    <span className="flex items-center gap-1.5">
                                      <Brain size={13} className="shrink-0 text-muted" />
                                      <span className="block truncate text-foreground">
                                        {expert.displayName}
                                      </span>
                                    </span>
                                    <span className="mt-1 block truncate font-mono text-[11px] text-muted">
                                      /{expert.expertCode}
                                    </span>
                                  </span>
                                  {isSelected ? (
                                    <span className="rounded-full border border-border px-2 py-0.5 text-[10px] text-muted">
                                      已选中
                                    </span>
                                  ) : null}
                                </button>
                              );
                            })
                          ) : (
                            <div className="rounded-xl bg-surface-container px-3 py-2 text-sm text-muted">
                              未匹配到专家
                            </div>
                          )
                        ) : filteredSkills.length ? (
                          filteredSkills.map((skill) => {
                            const isSelected = selectedSkillCodes.includes(skill.skillCode);
                            return (
                              <button
                                key={skill.skillCode}
                                type="button"
                                aria-label={`选择技能 ${skill.displayName}`}
                                onClick={() => selectSkill(skill.skillCode)}
                                className={`mb-1 flex w-full items-center justify-between rounded-xl px-3 py-2 text-left text-sm transition-colors last:mb-0 ${
                                  isSelected
                                    ? 'bg-surface-container text-foreground'
                                    : 'text-muted hover:bg-surface-container hover:text-foreground'
                                }`}
                              >
                                <span className="min-w-0">
                                  <span className="flex items-center gap-1.5">
                                    <Sparkles
                                      size={13}
                                      data-testid={`skill-option-icon-${skill.skillCode}`}
                                      className="shrink-0 text-muted"
                                    />
                                    <span className="block truncate text-foreground">
                                      {skill.displayName}
                                    </span>
                                  </span>
                                  <span className="mt-1 block truncate font-mono text-[11px] text-muted">
                                    /{skill.skillCode}
                                  </span>
                                </span>
                                {isSelected ? (
                                  <span className="rounded-full border border-border px-2 py-0.5 text-[10px] text-muted">
                                    已选中
                                  </span>
                                ) : null}
                              </button>
                            );
                          })
                        ) : (
                          <div className="rounded-xl bg-surface-container px-3 py-2 text-sm text-muted">
                            未匹配到技能
                          </div>
                        )}
                      </div>
                    </div>
                  ) : null}
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      aria-label="打开MCP列表"
                      aria-expanded={activeSelectorMode === 'mcp'}
                      onClick={() => {
                        setActiveSelectorMode((current) => (current === 'mcp' ? null : 'mcp'));
                        setSkillSelectorSource(null);
                      }}
                      className="inline-flex h-7 items-center gap-1 rounded-full border border-border bg-surface-container px-3 text-xs text-foreground transition-[box-shadow,border-color,background-color] duration-200 hover:border-border-active hover:bg-surface hover:shadow-[0_8px_18px_rgba(0,0,0,0.16)]"
                    >
                      <Database
                        size={12}
                        data-testid="mcp-trigger-icon"
                        className="text-muted"
                      />
                      <span className="truncate">MCP</span>
                      <ChevronDown
                        size={12}
                        data-testid="mcp-trigger-chevron"
                        className={`text-muted transition-transform ${
                          activeSelectorMode === 'mcp' ? 'rotate-180' : ''
                        }`}
                      />
                    </button>
                    <button
                      type="button"
                      aria-label="打开技能列表"
                      aria-expanded={activeSelectorMode === 'skill'}
                      onClick={() => {
                        if (activeSelectorMode === 'skill') {
                          setActiveSelectorMode(null);
                          setSkillSelectorSource(null);
                          return;
                        }
                        setActiveSelectorMode('skill');
                        setSkillSelectorSource('button');
                        setSkillSearchKeyword('');
                        if (!inputValue.trim()) {
                          // 步骤：点击技能按钮时自动补斜杠，降低触发门槛并统一交互预期。
                          setInputValue('/');
                          window.requestAnimationFrame(() => {
                            chatInputRef.current?.focus();
                          });
                        }
                      }}
                      className="inline-flex h-7 max-w-[180px] items-center gap-1 rounded-full border border-border bg-surface-container px-3 text-xs text-foreground transition-[box-shadow,border-color,background-color] duration-200 hover:border-border-active hover:bg-surface hover:shadow-[0_8px_18px_rgba(0,0,0,0.16)]"
                    >
                      <Sparkles size={12} data-testid="skill-trigger-icon" className="text-muted" />
                      <span className="truncate">技能</span>
                      <ChevronDown
                        size={12}
                        className={`transition-transform ${activeSelectorMode === 'skill' ? 'rotate-180' : ''}`}
                      />
                    </button>
                    <button
                      type="button"
                      aria-label="打开专家列表"
                      aria-expanded={activeSelectorMode === 'expert'}
                      onClick={() => {
                        if (activeSelectorMode === 'expert') {
                          setActiveSelectorMode(null);
                          return;
                        }
                        setActiveSelectorMode('expert');
                        setSkillSelectorSource(null);
                        setExpertSearchKeyword('');
                      }}
                      className="inline-flex h-7 max-w-[180px] items-center gap-1 rounded-full border border-border bg-surface-container px-3 text-xs text-foreground transition-[box-shadow,border-color,background-color] duration-200 hover:border-border-active hover:bg-surface hover:shadow-[0_8px_18px_rgba(0,0,0,0.16)]"
                    >
                      <Brain size={12} className="text-muted" />
                      <span className="truncate">{selectedExpert?.displayName ?? '专家'}</span>
                      <ChevronDown
                        size={12}
                        className={`transition-transform ${activeSelectorMode === 'expert' ? 'rotate-180' : ''}`}
                      />
                    </button>
                  </div>
                </div>
                <div className="space-y-3">
                  <div data-testid="chat-input-content-area" className="relative">
                    {pendingAttachments.length > 0 ? (
                      <div
                        data-testid="pending-attachment-list"
                        className="mb-2 flex flex-wrap items-start gap-2 rounded-xl border border-border bg-surface-container/70 p-2"
                      >
                        {pendingAttachments.map((attachment) => {
                          const isImage = attachment.file.type.startsWith('image/');
                          return (
                            <div
                              key={attachment.clientId}
                              data-testid={`pending-attachment-item-${attachment.clientId}`}
                              className={`group relative rounded-lg border border-border bg-surface p-1 text-left ${
                                isImage ? 'h-[78px] w-[78px] overflow-hidden' : 'min-w-[180px] pr-8'
                              }`}
                            >
                              <button
                                type="button"
                                aria-label={`删除附件 ${attachment.file.name}`}
                                data-testid={`remove-pending-attachment-${attachment.clientId}`}
                                onClick={(event) => {
                                  event.stopPropagation();
                                  removePendingAttachment(attachment.clientId);
                                }}
                                className="absolute right-1 top-1 z-10 rounded-full border border-border bg-surface p-0.5 text-muted transition-colors hover:text-foreground"
                              >
                                <X size={12} />
                              </button>
                              {isImage ? (
                                <button
                                  type="button"
                                  aria-label={`预览图片 ${attachment.file.name}`}
                                  onClick={() =>
                                    setPreviewAttachment({
                                      src: attachment.previewUrl,
                                      alt: attachment.file.name,
                                    })
                                  }
                                  className="h-full w-full"
                                >
                                  <img
                                    src={attachment.previewUrl}
                                    alt={attachment.file.name}
                                    className="h-full w-full rounded-md object-cover"
                                  />
                                </button>
                              ) : (
                                <span className="flex items-center gap-2 px-1 py-1 text-xs text-foreground">
                                  <File size={13} className="text-muted" />
                                  <span className="max-w-[128px] truncate">{attachment.file.name}</span>
                                </span>
                              )}
                              <span className="absolute bottom-1 left-1 rounded bg-black/60 px-1.5 py-0.5 text-[10px] text-white">
                                {attachment.uploadStatus === 'uploaded'
                                  ? '已上传'
                                  : attachment.uploadStatus === 'uploading'
                                    ? '上传中'
                                    : attachment.uploadStatus === 'failed'
                                      ? '失败'
                                      : '待发送'}
                              </span>
                            </div>
                          );
                        })}
                        <button
                          type="button"
                          aria-label="清空附件"
                          onClick={() => clearPendingAttachments()}
                          className="ml-auto rounded-lg border border-border bg-surface px-3 py-1.5 text-xs text-muted transition-[color,border-color,background-color] duration-200 hover:border-border-active hover:text-foreground"
                        >
                          清空
                        </button>
                      </div>
                    ) : null}
                    {/* 步骤：输入内容独占上层区域，避免文本增多时挤压底部工具栏。 */}
                    <div
                      data-testid="input-inline-skill-tokens"
                      data-max-lines="9"
                      className="max-h-[calc(1.5rem*9+1rem)] w-full overflow-x-hidden overflow-y-auto rounded-xl bg-transparent px-0.5 py-1"
                    >
                      {/* 步骤：输入文本即技能载体，技能标记直接写入文本，支持任意光标位置编辑与删除。 */}
                      <div
                        data-testid="input-inline-content-flow"
                        className="relative min-h-8 w-full text-[14px] leading-6 tracking-normal text-foreground"
                      >
                        <div
                          ref={inputPreviewRef}
                          data-testid="input-rich-preview"
                          aria-hidden="true"
                          className="pointer-events-none absolute inset-0 overflow-hidden py-1 whitespace-pre-wrap break-words text-[14px] leading-6 tracking-normal text-foreground"
                        >
                          {inputPreviewSegments.length > 0 ? (
                            inputPreviewSegments.map((segment, index) =>
                              segment.type === 'skill' ? (
                                <span
                                  key={`${segment.skillCode}-${segment.start}-${index}`}
                                  className="relative inline-flex max-w-full align-baseline"
                                >
                                  {/* 步骤：保留 token 原始宽度作为占位，确保 textarea 光标位置与技能气泡末尾对齐。 */}
                                  <span className="invisible whitespace-pre">{segment.rawToken}</span>
                                  <span
                                    data-testid={`selected-skill-chip-${segment.skillCode}`}
                                    className="pointer-events-auto absolute inset-y-0 left-0 right-0 inline-flex h-6 items-center overflow-hidden rounded-md border border-border/70 bg-surface-container/82 px-1.5 text-[12px] font-normal text-foreground"
                                  >
                                    <span className="min-w-0 truncate">{segment.rawToken}</span>
                                    <button
                                      type="button"
                                      aria-label={`删除技能 ${segment.displayName}`}
                                      data-testid={`remove-selected-skill-chip-${segment.skillCode}`}
                                      onMouseDown={(event) => {
                                        // 阻止按钮抢走 textarea 焦点，保持输入连续性。
                                        event.preventDefault();
                                      }}
                                      onClick={(event) => {
                                        event.preventDefault();
                                        event.stopPropagation();
                                        removeSkillTokenFromInput(segment.skillCode);
                                      }}
                                      className="ml-0.5 inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-muted transition-colors hover:bg-surface hover:text-foreground"
                                    >
                                      <X size={10} />
                                    </button>
                                  </span>
                                </span>
                              ) : (
                                <span key={`text-${segment.start}-${index}`}>{segment.value}</span>
                              ),
                            )
                          ) : (
                            <span className="text-transparent">.</span>
                          )}
                        </div>
                        <textarea
                          ref={chatInputRef}
                          value={inputValue}
                          onChange={(event) => {
                            const nextInput = event.target.value;
                            setInputValue(nextInput);
                            syncSelectedSkillCodesFromInput(nextInput);
                          }}
                          onKeyDown={(event) => {
                            if (
                              event.key === 'Backspace' &&
                              !event.shiftKey &&
                              !event.ctrlKey &&
                              !event.altKey &&
                              !event.metaKey
                            ) {
                              const tokenRemovalResult = removeSkillTokenBeforeCaret(
                                event.currentTarget.value,
                                event.currentTarget.selectionStart ?? 0,
                                event.currentTarget.selectionEnd ?? 0,
                                availableSkillCodeSet,
                              );
                              if (tokenRemovalResult) {
                                event.preventDefault();
                                setInputValue(tokenRemovalResult.nextInput);
                                syncSelectedSkillCodesFromInput(tokenRemovalResult.nextInput);
                                window.requestAnimationFrame(() => {
                                  const textarea = chatInputRef.current;
                                  if (!textarea) {
                                    return;
                                  }
                                  textarea.focus();
                                  textarea.setSelectionRange(
                                    tokenRemovalResult.nextCaretPosition,
                                    tokenRemovalResult.nextCaretPosition,
                                  );
                                });
                                return;
                              }
                            }
                            if (event.key === 'Enter' && !event.shiftKey) {
                              event.preventDefault();
                              void submitCurrentInput();
                            }
                          }}
                          onScroll={(event) => {
                            const preview = inputPreviewRef.current;
                            if (!preview) {
                              return;
                            }
                            preview.scrollTop = event.currentTarget.scrollTop;
                            preview.scrollLeft = event.currentTarget.scrollLeft;
                          }}
                          onPaste={(event) => {
                            void handlePasteAttachments(event);
                          }}
                          rows={1}
                          placeholder="输入问题，或先选择技能/MCP..."
                          // 步骤：输入仍由 textarea 驱动编辑，显示层负责把技能标记渲染为气泡样式。
                          className={`min-h-8 w-full min-w-0 resize-none overflow-x-hidden bg-transparent py-1 text-[14px] leading-6 tracking-normal [font-family:inherit] outline-none placeholder:overflow-hidden placeholder:whitespace-nowrap placeholder:text-muted ${
                            inputValue ? 'text-transparent caret-foreground selection:bg-border-active/35 selection:text-transparent' : 'text-foreground'
                          }`}
                        />
                      </div>
                    </div>
                  </div>
                  <div
                    data-testid="chat-input-toolbar"
                    className="flex items-center justify-between gap-3"
                >
                    <div data-testid="chat-input-toolbar-left" className="flex items-center">
                      <input
                        ref={fileInputRef}
                        type="file"
                        multiple
                        className="hidden"
                        onChange={(event) => {
                          void handleFileInputChange(event);
                        }}
                      />
                      <button
                        type="button"
                        aria-label="上传附件"
                        onClick={() => fileInputRef.current?.click()}
                        className="rounded-full border border-border bg-surface-container p-1.5 text-muted transition-[box-shadow,color,border-color,background-color] duration-200 hover:border-border-active hover:bg-surface hover:text-foreground hover:shadow-[0_8px_18px_rgba(0,0,0,0.16)]"
                      >
                        <Paperclip size={17} />
                      </button>
                    </div>
                    <div
                      data-testid="chat-input-toolbar-right"
                      className="flex shrink-0 items-center gap-2"
                    >
                      <button
                        type="button"
                        aria-label="切换深度思考"
                        onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
                        className={`rounded-full border px-3 py-1.5 text-xs font-medium transition-[box-shadow,color,border-color,background-color] duration-200 hover:shadow-[0_8px_18px_rgba(0,0,0,0.16)] ${
                          deepThinkingEnabled
                            ? 'border-foreground bg-foreground text-background hover:opacity-92'
                            : 'border-border bg-surface-container text-muted hover:border-border-active hover:bg-surface hover:text-foreground'
                        }`}
                      >
                        <span className="flex items-center gap-1.5">
                          <WandSparkles size={14} />
                          深度思考
                        </span>
                      </button>
                      {isStreaming ? (
                        <button
                          type="button"
                          aria-label="停止生成"
                          onClick={() => void cancelCurrentStream()}
                          disabled={isCancelling}
                          className="rounded-full bg-red-500 px-3 py-1.5 text-sm font-medium text-white transition-[opacity,box-shadow] duration-200 hover:shadow-[0_8px_18px_rgba(185,28,28,0.36)] disabled:opacity-60"
                        >
                          <span className="flex items-center gap-2">
                            <CircleStop size={16} />
                            {isCancelling ? '停止中' : '停止'}
                          </span>
                        </button>
                      ) : (
                        <button
                          type="submit"
                          aria-label="发送消息"
                          className="rounded-full bg-foreground p-2 text-background transition-[opacity,box-shadow] duration-200 hover:opacity-90 hover:shadow-[0_8px_18px_rgba(0,0,0,0.28)] disabled:opacity-60"
                          disabled={!inputValue.trim()}
                        >
                          <ArrowUp size={17} />
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </form>
            {showRuntimeWorkspaceSwitcher ? (
              <div
                ref={runtimeWorkspaceLayerRef}
                data-testid="chat-runtime-workspace-switcher"
                // 业务意图：仅在新建对话页保留环境切换入口，避免历史会话页底部信息噪声。
                className="mt-3 flex items-center gap-3 px-1 text-sm text-foreground"
              >
                <div className="relative">
                  <button
                    type="button"
                    aria-label="打开运行环境列表"
                    aria-expanded={activeRuntimeWorkspaceMenu === 'runtime'}
                    onClick={() =>
                      setActiveRuntimeWorkspaceMenu((current) => (current === 'runtime' ? null : 'runtime'))
                    }
                    className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1 text-sm transition-colors hover:bg-surface-container"
                  >
                    <ActiveRuntimeIcon
                      size={14}
                      data-testid={`runtime-active-icon-${activeRuntimeTarget}`}
                      className="shrink-0 text-muted"
                    />
                    <span>{runtimeDisplayLabel}</span>
                    <ChevronDown
                      size={14}
                      className={`transition-transform ${
                        activeRuntimeWorkspaceMenu === 'runtime' ? 'rotate-180' : ''
                      }`}
                    />
                  </button>
                  {activeRuntimeWorkspaceMenu === 'runtime' ? (
                    <div className="absolute bottom-[calc(100%+8px)] left-0 z-40 w-[170px] rounded-xl border border-border bg-surface p-1.5 shadow-[0_18px_44px_rgba(0,0,0,0.25)]">
                      {runtimeTargets.map((runtimeTarget) => {
                        const isActiveRuntime = runtimeTarget === activeRuntimeTarget;
                        const runtimeLabel = runtimeTarget === 'local' ? '本地' : '云端';
                        const RuntimeOptionIcon = runtimeTarget === 'local' ? Monitor : Cloud;
                        return (
                          <button
                            key={runtimeTarget}
                            type="button"
                            aria-label={`切换运行环境 ${runtimeLabel}`}
                            onClick={() => {
                              void setActiveRuntimeTarget(runtimeTarget);
                              setActiveRuntimeWorkspaceMenu(null);
                            }}
                            className={`mb-1 flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm transition-colors last:mb-0 ${
                              isActiveRuntime
                                ? 'bg-surface-container text-foreground'
                                : 'text-muted hover:bg-surface-container hover:text-foreground'
                            }`}
                          >
                            <span className="inline-flex items-center gap-1.5">
                              <RuntimeOptionIcon
                                size={14}
                                data-testid={`runtime-option-icon-${runtimeTarget}`}
                                className={isActiveRuntime ? 'text-foreground' : 'text-muted'}
                              />
                              {runtimeLabel}
                            </span>
                            {isActiveRuntime ? <CheckCircle2 size={14} className="text-foreground" /> : null}
                          </button>
                        );
                      })}
                    </div>
                  ) : null}
                </div>
                {showWorkspaceDropdownInSwitcher ? (
                  <div className="relative min-w-0">
                    <button
                      type="button"
                      aria-label="打开工作空间列表"
                      aria-expanded={activeRuntimeWorkspaceMenu === 'workspace'}
                      onClick={() =>
                        setActiveRuntimeWorkspaceMenu((current) =>
                          current === 'workspace' ? null : 'workspace',
                        )
                      }
                      className="inline-flex max-w-[320px] items-center gap-1.5 rounded-lg px-2 py-1 text-sm transition-colors hover:bg-surface-container"
                    >
                      <FolderOpen size={14} className="shrink-0 text-muted" />
                      <span className="truncate">{workspaceLabel}</span>
                      <ChevronDown
                        size={14}
                        className={`shrink-0 transition-transform ${
                          activeRuntimeWorkspaceMenu === 'workspace' ? 'rotate-180' : ''
                        }`}
                      />
                    </button>
                    {activeRuntimeWorkspaceMenu === 'workspace' ? (
                      <div className="absolute bottom-[calc(100%+8px)] left-0 z-40 w-[260px] rounded-xl border border-border bg-surface p-1.5 shadow-[0_18px_44px_rgba(0,0,0,0.25)]">
                        <div className="max-h-60 overflow-y-auto">
                          {workspaceSwitcherOptions.map((group) => {
                            const isActiveWorkspace = group.partitionKey === activeWorkspacePartitionKey;
                            const optionLabel = group.workspaceLabel;
                            return (
                              <button
                                key={group.partitionKey}
                                type="button"
                                aria-label={`切换工作空间 ${optionLabel}`}
                                onClick={() => {
                                  void setActiveWorkspacePath(group.workspacePath ?? null);
                                  setActiveRuntimeWorkspaceMenu(null);
                                }}
                                className={`mb-1 flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm transition-colors last:mb-0 ${
                                  isActiveWorkspace
                                    ? 'bg-surface-container text-foreground'
                                    : 'text-muted hover:bg-surface-container hover:text-foreground'
                                }`}
                              >
                                <span className="truncate">{optionLabel}</span>
                                {isActiveWorkspace ? (
                                  <CheckCircle2 size={14} className="text-foreground" />
                                ) : null}
                              </button>
                            );
                          })}
                        </div>
                        <button
                          type="button"
                          aria-label="选择新的本地工作空间"
                          onClick={() => {
                            void pickRepositoryDirectory();
                            setActiveRuntimeWorkspaceMenu(null);
                          }}
                          className="mt-1.5 flex w-full items-center justify-center gap-1.5 rounded-lg border border-border px-2 py-2 text-xs text-muted transition-colors hover:text-foreground"
                        >
                          <FolderOpen size={13} />
                          选择新的本地目录
                        </button>
                      </div>
                    ) : null}
                  </div>
                ) : null}
              </div>
            ) : null}
            </div>
          </div>
        ) : null}
      </section>

      {previewAttachment ? (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="图片预览弹窗"
          className="absolute inset-0 z-50 flex items-center justify-center bg-background/72 p-4 backdrop-blur-sm"
          onClick={() => setPreviewAttachment(null)}
        >
          <div
            className="relative w-full max-w-4xl rounded-2xl border border-border bg-surface p-3 shadow-[0_24px_80px_rgba(0,0,0,0.35)]"
            onClick={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              aria-label="关闭图片预览"
              className="absolute right-2 top-2 rounded-full border border-border bg-surface p-1 text-muted transition-colors hover:text-foreground"
              onClick={() => setPreviewAttachment(null)}
            >
              <X size={16} />
            </button>
            <img
              src={previewAttachment.src}
              alt={previewAttachment.alt}
              className="max-h-[76vh] w-full rounded-xl object-contain"
            />
          </div>
        </div>
      ) : null}

      {shareSelectionState.isActive ? (
        <ShareSelectionToolbar
          mode="share"
          selectedCount={shareSelectionState.selectedAssistantMessageIds.length}
          totalCount={shareSelectionRounds.length}
          onSelectAll={() =>
            setShareSelectionState({
              isActive: true,
              selectedAssistantMessageIds: shareSelectionRounds.map(
                (round) => round.assistantMessage.id,
              ),
            })
          }
          onCancel={cancelShareSelection}
          onConfirm={() => void confirmShareSelection()}
        />
      ) : null}

      {deleteSelectionState.isActive ? (
        <ShareSelectionToolbar
          mode="delete"
          selectedCount={deleteSelectionState.selectedAssistantMessageIds.length}
          totalCount={shareSelectionRounds.length}
          onSelectAll={() =>
            setDeleteSelectionState({
              isActive: true,
              selectedAssistantMessageIds: shareSelectionRounds.map(
                (round) => round.assistantMessage.id,
              ),
            })
          }
          onCancel={cancelDeleteSelection}
          onConfirm={() => void confirmDeleteSelection()}
        />
      ) : null}

      {shareDialogState.isOpen ? (
        <ShareConversationDialog
          state={shareDialogState}
          onClose={() =>
            setShareDialogState({
              isOpen: false,
              isLoading: false,
              url: '',
              previewMessages: [],
              errorMessage: '',
            })
          }
        />
      ) : null}

      {renameDialog.isOpen ? (
        <InlineDialog
          title="重命名对话"
          defaultValue={renameDialog.initialTitle}
          confirmLabel="确认"
          cancelLabel="取消"
          onCancel={renameDialog.close}
          onConfirm={async (nextTitle) => {
            if (renameDialog.conversationId) {
              await renameConversation(
                renameDialog.conversationId,
                nextTitle,
                renameDialog.actionContext,
              );
            }
            renameDialog.close();
          }}
        />
      ) : null}

      {deleteDialog.isOpen ? (
        <ConfirmDialog
          title="删除对话"
          description={`确认删除对话“${deleteDialog.title}”吗？`}
          confirmLabel="删除"
          cancelLabel="取消"
          onCancel={deleteDialog.close}
          onConfirm={async () => {
            if (deleteDialog.conversationId) {
              await deleteConversation(
                deleteDialog.conversationId,
                deleteDialog.actionContext,
              );
            }
            deleteDialog.close();
          }}
        />
      ) : null}
    </motion.div>
  );
}

/**
 * 渲染公开分享页，只读取分享接口并以只读方式展示选中消息。
 */
export function SharedChatView({
  shareToken,
  messageIds,
}: {
  shareToken: string;
  messageIds: string[];
}) {
  const [state, setState] = React.useState<{
    isLoading: boolean;
    data: SharedConversationPayload | null;
    errorMessage: string;
  }>({
    isLoading: true,
    data: null,
    errorMessage: '',
  });

  React.useEffect(() => {
    let cancelled = false;
    const loadSharedConversation = async () => {
      try {
        const data = await ChatApi.getSharedConversation(shareToken, messageIds);
        if (!cancelled) {
          setState({ isLoading: false, data, errorMessage: '' });
        }
      } catch (error) {
        if (!cancelled) {
          setState({
            isLoading: false,
            data: null,
            errorMessage: error instanceof Error ? error.message : '分享链接不存在或已失效',
          });
        }
      }
    };
    void loadSharedConversation();
    return () => {
      cancelled = true;
    };
  }, [shareToken, messageIds]);

  const sharedConversation = state.data?.conversation;
  return (
    <main className="min-h-screen bg-[#f7f2ea] px-4 py-8 text-[#191817] dark:bg-background dark:text-foreground">
      <div className="mx-auto max-w-3xl">
        <header className="flex items-center justify-between">
          <div className="text-xl font-bold">CodingX</div>
          <a
            href="/"
            className="rounded-full bg-[#191817] px-4 py-2 text-sm text-white dark:bg-foreground dark:text-background"
          >
            继续向 CodingX 提问
          </a>
        </header>
        <section className="mt-10 rounded-[32px] border border-black/8 bg-white px-6 py-7 shadow-[0_24px_80px_rgba(45,35,20,0.12)] dark:border-border dark:bg-surface">
          {state.isLoading ? (
            <p className="text-sm text-muted">正在加载分享对话...</p>
          ) : state.errorMessage ? (
            <p className="text-sm text-error">{state.errorMessage}</p>
          ) : (
            <>
              <div className="mb-8">
                <h1 className="text-3xl font-semibold tracking-tight">
                  {sharedConversation?.title || '分享对话'}
                </h1>
                <p className="mt-2 text-sm text-muted">
                  {sharedConversation?.lastMessageAt || '由 CodingX 分享'}
                </p>
              </div>
              <div className="space-y-6">
                {(state.data?.messages ?? []).map((message) => (
                  <article
                    key={message.id}
                    className={`flex ${message.role === 'USER' ? 'justify-end' : 'justify-start'}`}
                  >
                    <div
                      className={`max-w-[86%] rounded-[24px] px-5 py-4 text-sm leading-7 ${
                        message.role === 'USER'
                          ? 'bg-[#ede5d8] text-[#191817] dark:bg-surface-container dark:text-foreground'
                          : 'bg-transparent text-[#191817] dark:text-foreground'
                      }`}
                    >
                      <MarkdownMessage content={message.content} messageId={`shared-${message.id}`} />
                    </div>
                  </article>
                ))}
              </div>
            </>
          )}
        </section>
      </div>
    </main>
  );
}

type DirectoryListingEntry = {
  mode: string;
  date: string;
  time: string;
  length?: string;
  name: string;
  isDirectory: boolean;
};

type DirectoryListingPreview = {
  entries: DirectoryListingEntry[];
  folderCount: number;
  fileCount: number;
};

type SearchSourceDisplayItem = {
  id: string;
  title: string;
  url?: string;
  siteName?: string;
  snippet?: string;
};

/**
 * 将助手消息中的搜索来源渲染成消息尾部的可折叠列表，避免把标题、站点名和链接压缩成一行纯文本。
 * 这里直接使用消息内的 `searchProgress`，保证流式阶段和历史回放阶段都能看到一致的来源展示。
 */
function SearchProgressPanel({
  messageId,
  progress,
}: {
  messageId: string;
  progress: MessageSearchProgress;
}) {
  const visibleItems = progress.items.filter((item) => {
    return [item.title, item.url, item.siteName].some((value) => String(value ?? '').trim().length > 0);
  });
  // 业务意图：默认只占一行摘要，用户主动展开后再看来源明细，避免搜索面板挤占正文。
  const [isExpanded, setIsExpanded] = React.useState(false);
  const contentId = `search-progress-content-${messageId}`;

  if (visibleItems.length === 0) {
    return null;
  }

  return (
    <section data-testid={`search-progress-panel-${messageId}`} className="mb-1.5">
      <button
        type="button"
        data-testid={`search-progress-toggle-${messageId}`}
        aria-expanded={isExpanded}
        aria-controls={contentId}
        aria-label={isExpanded ? '折叠搜索来源列表' : '展开搜索来源列表'}
        onClick={() => setIsExpanded((current) => !current)}
        className="inline-flex max-w-full items-center gap-2 rounded-md px-0 py-1 text-xs font-medium text-muted transition-colors hover:text-foreground"
      >
        <ChevronDown
          size={14}
          className={`shrink-0 text-muted transition-transform duration-200 ${
            isExpanded ? 'rotate-180' : '-rotate-90'
          }`}
        />
        <span className="whitespace-nowrap">搜索来源</span>
        <span className="text-border">·</span>
        <span className="whitespace-nowrap text-foreground">{getSearchProgressStatusLabel(progress.status)}</span>
        <span className="text-border">·</span>
        <span className="whitespace-nowrap">{visibleItems.length} 条</span>
      </button>
      {isExpanded ? (
        <div id={contentId} className="pl-4 pt-2">
          <ul className="max-h-[280px] space-y-1.5 overflow-y-auto pl-3 pr-4 [scrollbar-gutter:stable]">
            {visibleItems.map((item) => (
              <li key={item.id}>
                <SearchSourceListItem messageId={messageId} item={item} />
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}

/**
 * 渲染单条来源列表项：标题、站点信息和链接都可见，点击后通过新窗口打开原始网页。
 * 列表项保留轻量行样式，避免再做成厚重卡片。
 */
function SearchSourceListItem({
  messageId,
  item,
}: {
  messageId: string;
  item: SearchSourceDisplayItem;
}) {
  const source = resolveSearchSourceMeta(item);
  const displayTitle = item.title?.trim() || source.siteLabel || source.displayUrl || '来源';
  const rowClasses =
    'group block min-w-0 text-left transition-colors hover:text-foreground focus-visible:outline-none focus-visible:text-foreground';
  const content = (
    <article className="flex min-w-0 items-start gap-2">
      <SearchSourceFavicon
        faviconUrl={source.faviconUrl}
        siteLabel={source.siteLabel}
        data-testid={`search-source-favicon-${messageId}-${item.id}`}
        className="mt-0.5 h-6 w-6 rounded-md"
      />
      <div className="min-w-0 flex-1 space-y-0.5">
        <div className="flex min-w-0 items-start justify-between gap-2">
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm font-medium text-foreground" title={displayTitle}>
              {displayTitle}
            </div>
            <div className="mt-0.5 flex min-w-0 flex-wrap items-center gap-x-2 gap-y-0.5 text-[11px] leading-4 text-muted">
              <span className="truncate">{source.siteLabel}</span>
              {source.hostname ? <span className="font-mono">{source.hostname}</span> : null}
            </div>
          </div>
          <ExternalLink
            size={12}
            className="mt-0.5 shrink-0 text-muted transition-colors group-hover:text-foreground"
          />
        </div>
        {source.displayUrl ? (
          <div
            className="truncate font-mono text-[11px] leading-4 text-accent-breeze"
            title={item.url}
          >
            {source.displayUrl}
          </div>
        ) : null}
      </div>
    </article>
  );

  if (source.href) {
    return (
      <a
        data-testid={`search-source-link-${messageId}-${item.id}`}
        href={source.href}
        target="_blank"
        rel="noreferrer noopener"
        aria-label={`打开来源 ${displayTitle}${source.siteLabel ? `，${source.siteLabel}` : ''}`}
        className={rowClasses}
      >
        {content}
      </a>
    );
  }

  return (
    <div
      data-testid={`search-source-row-${messageId}-${item.id}`}
      className={`${rowClasses} cursor-default opacity-80`}
    >
      {content}
    </div>
  );
}

/**
 * 来源列表左侧的站点图标优先使用 favicon，失败时回退为首字母。
 * 这样既能尽量保留网站识别度，也不会因为单个站点图标不可用导致列表项视觉破损。
 */
function SearchSourceFavicon({
  faviconUrl,
  siteLabel,
  ...props
}: {
  faviconUrl: string | null;
  siteLabel: string;
} & React.HTMLAttributes<HTMLDivElement>) {
  const [hasImageError, setHasImageError] = React.useState(false);
  const { className, ...restProps } = props;

  React.useEffect(() => {
    setHasImageError(false);
  }, [faviconUrl]);

  const fallbackLabel = siteLabel.trim().slice(0, 1).toUpperCase() || 'W';

  return (
    <div
      {...restProps}
      aria-hidden="true"
      className={[
        'flex h-6 w-6 shrink-0 items-center justify-center rounded-md border border-border bg-surface-container text-accent-breeze',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {faviconUrl && !hasImageError ? (
        <img
          src={faviconUrl}
          alt=""
          aria-hidden="true"
          className="h-3 w-3 rounded-sm object-contain"
          onError={() => setHasImageError(true)}
        />
      ) : (
        <span
          aria-hidden="true"
          className="flex h-full w-full items-center justify-center rounded-md bg-surface-container text-[9px] font-semibold text-accent-breeze"
        >
          {fallbackLabel}
        </span>
      )}
    </div>
  );
}

/**
 * 将搜索来源的链接信息解析为可展示、可点击的安全地址。
 * 只允许 http/https，避免把异常字符串直接渲染成可执行链接。
 */
function resolveSearchSourceMeta(item: SearchSourceDisplayItem): {
  href: string | null;
  hostname: string | null;
  faviconUrl: string | null;
  siteLabel: string;
  displayUrl: string | null;
} {
  const rawUrl = typeof item.url === 'string' ? item.url.trim() : '';
  let hostname: string | null = null;
  let href: string | null = null;

  if (rawUrl) {
    const candidateUrls = rawUrl.startsWith('http://') || rawUrl.startsWith('https://')
      ? [rawUrl]
      : [`https://${rawUrl}`, rawUrl];

    for (const candidateUrl of candidateUrls) {
      try {
        const parsed = new URL(candidateUrl);
        if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
          href = parsed.toString();
          hostname = parsed.hostname || null;
          break;
        }
      } catch {
        continue;
      }
    }
  }

  const siteLabel = item.siteName?.trim() || hostname || '来源站点';
  const faviconUrl = hostname
    ? `https://www.google.com/s2/favicons?domain=${encodeURIComponent(hostname)}&sz=64`
    : null;
  const displayUrl = href ? formatSearchSourceDisplayUrl(href) : null;

  return {
    href,
    hostname,
    faviconUrl,
    siteLabel,
    displayUrl,
  };
}

/**
 * 将完整链接压缩成更易扫读的展示文本，保留主机名和路径，避免把正文撑得过宽。
 */
function formatSearchSourceDisplayUrl(url: string): string {
  try {
    const parsed = new URL(url);
    const path = parsed.pathname === '/' ? '' : parsed.pathname.replace(/\/$/, '');
    return `${parsed.hostname}${path}${parsed.search}${parsed.hash}`;
  } catch {
    return url;
  }
}

/**
 * 搜索面板标题需要清楚表达当前阶段，避免用户把“正在加载”误解为结果为空。
 */
function getSearchProgressStatusLabel(status: MessageSearchProgress['status']): string {
  switch (status) {
    case 'running':
      return '正在检索来源';
    case 'completed':
      return '来源已获取';
    case 'cancelled':
      return '检索已取消';
    case 'error':
      return '检索出现异常';
    default:
      return '搜索来源';
  }
}

/**
 * 使用 Markdown 渲染助手消息，保证标题、列表、代码块等富文本结构按预期展示。
 * 对 PowerShell 目录清单这类高密度代码块，额外收敛成结构化结果面板，避免正文被原始对齐文本撑散。
 */
function MarkdownMessage({ content, messageId }: { content: string; messageId?: string }) {
  const directoryListing = parsePowerShellDirectoryListing(content);
  if (directoryListing) {
    return <DirectoryListingPanel listing={directoryListing} messageId={messageId} />;
  }

  return (
    <div className="chat-markdown min-w-0 [overflow-wrap:anywhere] text-sm leading-7 text-foreground">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ node: _node, ...props }) => (
            <h1 className="mb-4 text-3xl font-semibold tracking-tight" {...props} />
          ),
          h2: ({ node: _node, ...props }) => (
            <h2 className="mb-3 mt-6 text-2xl font-semibold tracking-tight" {...props} />
          ),
          h3: ({ node: _node, ...props }) => (
            <h3 className="mb-3 mt-6 text-xl font-semibold tracking-tight" {...props} />
          ),
          p: ({ node: _node, ...props }) => <p className="mb-4 last:mb-0" {...props} />,
          ul: ({ node: _node, ...props }) => (
            <ul className="mb-4 list-disc space-y-2 pl-6" {...props} />
          ),
          ol: ({ node: _node, ...props }) => (
            <ol className="mb-4 list-decimal space-y-2 pl-6" {...props} />
          ),
          li: ({ node: _node, ...props }) => <li className="pl-1" {...props} />,
          hr: ({ node: _node, ...props }) => <hr className="my-6 border-border" {...props} />,
          code: ({ node: _node, className, children, ...props }) => {
            const isBlockCode = className?.includes('language-');
            if (isBlockCode) {
              return (
                <code
                  className={`block overflow-x-auto rounded-2xl border border-border bg-surface-container px-4 py-3 font-mono text-[13px] leading-6 shadow-[0_12px_28px_rgba(0,0,0,0.14)] ${className}`}
                  {...props}
                >
                  {children}
                </code>
              );
            }
            return (
              <code
                className="rounded bg-surface-container px-1.5 py-0.5 font-mono text-[13px]"
                {...props}
              >
                {children}
              </code>
            );
          },
          pre: ({ node: _node, ...props }) => (
            <pre className="mb-4 overflow-x-auto whitespace-pre-wrap" {...props} />
          ),
          table: ({ node: _node, className, ...props }) => (
            // Markdown 对比表通常承载高密度信息，给它单独做成卡片式容器，避免内容直接挤在正文流里。
            <div className="chat-markdown-table-shell mb-4 overflow-x-auto">
              <table
                className={['chat-markdown-table min-w-full text-left', className].filter(Boolean).join(' ')}
                {...props}
              />
            </div>
          ),
          th: ({ node: _node, children, ...props }) => (
            // 表头单元格默认保留水平排版，短标题不应被强制拆成竖排。
            <th className="min-w-24 px-4 py-3 font-semibold text-foreground" {...props}>
              {children}
            </th>
          ),
          td: ({ node: _node, children, ...props }) => (
            // 数据单元格保持自然宽度，配合横向滚动而不是硬性压缩文本。
            <td className="min-w-24 px-4 py-3 align-top text-foreground" {...props}>
              {children}
            </td>
          ),
          blockquote: ({ node: _node, ...props }) => (
            <blockquote
              className="mb-4 border-l-2 border-border-active pl-4 text-muted"
              {...props}
            />
          ),
          strong: ({ node: _node, ...props }) => (
            <strong className="font-semibold text-foreground" {...props} />
          ),
          img: ({ node: _node, ...props }) => (
            <img
              className="chat-message-image mb-4 max-h-[460px] w-full rounded-2xl border border-border object-contain shadow-sm"
              loading="lazy"
              {...props}
            />
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}

/**
 * 识别 PowerShell `Get-ChildItem` 的目录输出，并将其转成更适合阅读的结构化数据。
 * 只接受非常明确的列格式，避免误把普通代码块当成文件清单。
 */
function parsePowerShellDirectoryListing(text: string): DirectoryListingPreview | null {
  const normalizedText = text.replace(/\r\n/g, '\n').trim();
  if (!normalizedText) {
    return null;
  }

  const lines = normalizedText
    .split('\n')
    .map((line) => line.trimEnd())
    .filter((line) => line.trim().length > 0);
  const headerIndex = lines.findIndex((line) => /^Mode\s+LastWriteTime\s+Length\s+Name$/i.test(line.trim()));
  if (headerIndex < 0) {
    return null;
  }

  const entries: DirectoryListingEntry[] = [];
  const rowPattern =
    /^(?<mode>\S+)\s+(?<date>\d{4}\/\d{1,2}\/\d{1,2})\s+(?<time>\d{1,2}:\d{2})(?:\s+(?<length>\d+))?\s+(?<name>.+)$/;

  for (const line of lines.slice(headerIndex + 1)) {
    if (/^-{3,}(\s+-{3,})*$/.test(line.trim())) {
      continue;
    }
    const match = rowPattern.exec(line);
    if (!match?.groups) {
      continue;
    }
    const mode = match.groups.mode;
    entries.push({
      mode,
      date: match.groups.date,
      time: match.groups.time,
      length: match.groups.length,
      name: match.groups.name,
      isDirectory: mode.startsWith('d'),
    });
  }

  if (entries.length === 0) {
    return null;
  }

  return {
    entries,
    folderCount: entries.filter((entry) => entry.isDirectory).length,
    fileCount: entries.filter((entry) => !entry.isDirectory).length,
  };
}

/**
 * 将目录清单渲染成更像“结果卡片”的结构，突出文件名和元数据，而不是原始对齐文本。
 */
function DirectoryListingPanel({
  listing,
  messageId,
}: {
  listing: DirectoryListingPreview;
  messageId?: string;
}) {
  const { entries, folderCount, fileCount } = listing;

  return (
    <div
      data-testid={messageId ? `directory-listing-panel-${messageId}` : 'directory-listing-panel'}
      className="mb-4 overflow-hidden rounded-2xl border border-border bg-surface shadow-[0_14px_34px_rgba(0,0,0,0.18)]"
    >
      <div className="flex items-center justify-between gap-3 border-b border-border bg-surface-container/80 px-4 py-3">
        <div className="flex min-w-0 items-center gap-2">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border border-border bg-surface text-accent-breeze">
            <FolderOpen size={16} />
          </div>
          <div className="min-w-0">
            <div className="text-[11px] font-medium uppercase tracking-[0.22em] text-muted">
              目录清单
            </div>
            <div className="truncate text-sm font-semibold text-foreground">PowerShell 输出</div>
          </div>
        </div>
        <div className="flex flex-wrap items-center justify-end gap-2 text-[11px] text-muted">
          <span className="rounded-full border border-border bg-surface px-2.5 py-1">
            共 {entries.length} 项
          </span>
          <span className="rounded-full border border-border bg-surface px-2.5 py-1">
            {folderCount} 文件夹
          </span>
          <span className="rounded-full border border-border bg-surface px-2.5 py-1">
            {fileCount} 文件
          </span>
        </div>
      </div>
      <div className="max-h-[420px] overflow-auto">
        <table className="min-w-[720px] table-fixed border-collapse text-left">
          <caption className="sr-only">PowerShell 目录输出</caption>
          <thead className="sticky top-0 z-[1] bg-surface-container/90 text-[11px] uppercase tracking-[0.16em] text-muted">
            <tr>
              <th className="w-[84px] border-b border-border px-4 py-2 font-semibold">Mode</th>
              <th className="w-[150px] border-b border-border px-4 py-2 font-semibold">
                LastWriteTime
              </th>
              <th className="w-[96px] border-b border-border px-4 py-2 font-semibold">Length</th>
              <th className="border-b border-border px-4 py-2 font-semibold">Name</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/70">
            {entries.map((entry, index) => (
              <tr
                key={`${entry.mode}-${entry.date}-${entry.time}-${entry.name}-${index}`}
                className="transition-colors odd:bg-surface even:bg-surface-container/35 hover:bg-surface-container/70"
              >
                <td className="whitespace-nowrap px-4 py-3 font-mono text-[12px] text-muted">
                  {entry.mode}
                </td>
                <td className="whitespace-nowrap px-4 py-3 font-mono text-[12px] text-muted">
                  {entry.date} {entry.time}
                </td>
                <td className="whitespace-nowrap px-4 py-3 font-mono text-[12px] text-muted">
                  <span
                    className={`inline-flex min-w-[72px] justify-center rounded-full border px-2.5 py-0.5 ${
                      entry.isDirectory
                        ? 'border-accent-breeze/30 bg-accent-breeze/10 text-accent-breeze'
                        : 'border-border bg-surface-container/70 text-muted'
                    }`}
                  >
                    {formatDirectoryListingSize(entry)}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex min-w-0 items-center gap-2">
                    <span
                      className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border ${
                        entry.isDirectory
                          ? 'border-accent-breeze/25 bg-accent-breeze/10 text-accent-breeze'
                          : 'border-border bg-surface-container text-muted'
                      }`}
                    >
                      {entry.isDirectory ? <FolderOpen size={14} /> : <FileText size={14} />}
                    </span>
                    <span className="min-w-0 truncate font-medium text-foreground" title={entry.name}>
                      {entry.name}
                    </span>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/**
 * 将长度字段转成更易扫读的单位，目录则保留为“目录”，避免空白单元格看起来像渲染失败。
 */
function formatDirectoryListingSize(entry: DirectoryListingEntry): string {
  if (entry.isDirectory) {
    return '目录';
  }

  const rawLength = Number(entry.length);
  if (!Number.isFinite(rawLength)) {
    return entry.length ?? '—';
  }
  if (rawLength < 1024) {
    return `${rawLength} B`;
  }

  const units = ['KB', 'MB', 'GB', 'TB'];
  let size = rawLength / 1024;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }

  const rounded = unitIndex === 0 || size >= 10 ? Math.round(size) : Number(size.toFixed(1));
  return `${rounded} ${units[unitIndex]}`;
}

type CopyMode = 'plain' | 'markdown';
type MessageReaction = 'up' | 'down' | null;
const PERSISTED_MESSAGE_ID_PATTERN = /^\d+$/;
type UserMessageEditState = {
  messageId: string;
  content: string;
  isSubmitting: boolean;
  errorMessage: string;
};
type ShareSelectionRound = {
  assistantMessage: ChatMessageItem;
  userMessage: ChatMessageItem | null;
  messageIds: string[];
};

/**
 * 只有已落库的消息才能进入分享范围，临时乐观消息只适合本地渲染。
 * @param message 消息对象。
 * @returns 是否可进入分享选择。
 */
function isShareableMessage(message: ChatMessageItem) {
  return message.role !== 'SYSTEM' && PERSISTED_MESSAGE_ID_PATTERN.test(message.id);
}

/**
 * 将会话消息按“用户问题 + 助手回答”构造成可分享轮次，只允许助手回答作为选择入口。
 * @param messages 当前消息列表。
 * @returns 可分享轮次列表。
 */
function buildShareSelectionRounds(messages: ChatMessageItem[]): ShareSelectionRound[] {
  return messages.flatMap((message, index) => {
    if (message.role !== 'ASSISTANT' || !isShareableMessage(message)) {
      return [];
    }
    const previousUserMessage = [...messages.slice(0, index)]
      .reverse()
      .find((candidate) => candidate.role === 'USER' && isShareableMessage(candidate));
    return [
      {
        assistantMessage: message,
        userMessage: previousUserMessage ?? null,
        messageIds: [previousUserMessage?.id, message.id].filter(
          (messageId): messageId is string =>
            Boolean(messageId) && PERSISTED_MESSAGE_ID_PATTERN.test(messageId),
        ),
      },
    ];
  });
}

/**
 * 根据触发消息解析默认分享轮次；从消息分享按钮进入时默认只选当前回答，从会话级分享进入时默认全选。
 * @param rounds 已构造的分享轮次。
 * @param defaultMessageId 触发分享的消息标识。
 * @returns 默认选中的助手消息标识。
 */
function resolveDefaultShareAssistantMessageIds(
  rounds: ShareSelectionRound[],
  defaultMessageId?: string,
) {
  const selectableAssistantMessageIds = rounds.map((round) => round.assistantMessage.id);
  if (!defaultMessageId) {
    return selectableAssistantMessageIds;
  }
  if (selectableAssistantMessageIds.includes(defaultMessageId)) {
    return [defaultMessageId];
  }
  return selectableAssistantMessageIds;
}

/**
 * 根据触发删除的消息找到所属问答轮次；从用户问题触发时删除该问题与后续回答。
 * @param rounds 已构造的问答轮次。
 * @param defaultMessageId 触发删除的消息标识。
 * @returns 默认选中的助手消息标识。
 */
function resolveDefaultDeleteAssistantMessageIds(
  rounds: ShareSelectionRound[],
  defaultMessageId?: string,
) {
  if (!defaultMessageId) {
    return rounds.map((round) => round.assistantMessage.id);
  }
  const matchedRound = rounds.find(
    (round) =>
      round.assistantMessage.id === defaultMessageId ||
      round.userMessage?.id === defaultMessageId,
  );
  return matchedRound ? [matchedRound.assistantMessage.id] : [];
}

/**
 * 将当前已选助手轮次展开为真实消息 ID 列表，保证提交给后端时仍是扁平 messageIds。
 * @param rounds 分享轮次列表。
 * @param selectedAssistantMessageIds 已选助手消息标识。
 * @returns 去重后的真实消息 ID。
 */
function resolveShareSelectionMessageIds(
  rounds: ShareSelectionRound[],
  selectedAssistantMessageIds: string[],
) {
  const selectedAssistantIdSet = new Set(selectedAssistantMessageIds);
  const orderedMessageIds: string[] = [];
  rounds.forEach((round) => {
    if (!selectedAssistantIdSet.has(round.assistantMessage.id)) {
      return;
    }
    round.messageIds.forEach((messageId) => {
      if (!orderedMessageIds.includes(messageId)) {
        orderedMessageIds.push(messageId);
      }
    });
  });
  return orderedMessageIds;
}

/**
 * 将当前已选轮次展开成弹窗预览消息，保持问答顺序稳定。
 * @param rounds 分享轮次列表。
 * @param selectedAssistantMessageIds 已选助手消息标识。
 * @returns 预览消息列表。
 */
function resolveShareSelectionPreviewMessages(
  rounds: ShareSelectionRound[],
  selectedAssistantMessageIds: string[],
) {
  const selectedAssistantIdSet = new Set(selectedAssistantMessageIds);
  const previewMessages: ChatMessageItem[] = [];
  rounds.forEach((round) => {
    if (!selectedAssistantIdSet.has(round.assistantMessage.id)) {
      return;
    }
    if (round.userMessage) {
      previewMessages.push(round.userMessage);
    }
    previewMessages.push(round.assistantMessage);
  });
  return previewMessages;
}

/**
 * 渲染分享模式下的整体卡片容器，让聊天区显式切换为“内容选择态”。
 */
function ShareSelectionShell({
  mode = 'share',
  rounds,
  selectedAssistantMessageIds,
  onToggleRound,
  onPreviewImage,
}: {
  mode?: 'share' | 'delete';
  rounds: ShareSelectionRound[];
  selectedAssistantMessageIds: string[];
  onToggleRound: (assistantMessageId: string) => void;
  onPreviewImage: (src: string, alt: string) => void;
}) {
  const isDeleteMode = mode === 'delete';
  return (
    <div
      data-testid={isDeleteMode ? 'delete-selection-shell' : 'share-selection-shell'}
      className="mx-auto w-full max-w-5xl"
    >
      <div className="overflow-hidden rounded-[32px] border border-border bg-surface shadow-[0_28px_90px_rgba(0,0,0,0.22)]">
        <div className="border-b border-border bg-surface-container/85 px-6 py-5">
          <div className="text-[11px] font-medium uppercase tracking-[0.28em] text-muted">
            {isDeleteMode ? '删除选择模式' : '分享选择模式'}
          </div>
          <h2 className="mt-3 text-2xl font-semibold tracking-tight text-foreground">
            {isDeleteMode ? '选择要删除的问答轮次' : '选择要分享的问答轮次'}
          </h2>
          <p className="mt-2 text-sm leading-6 text-muted">
            {isDeleteMode
              ? '勾选 AI 回复即可删除对应用户问题与回答，删除后不会再参与当前会话上下文。'
              : '只允许勾选 AI 回复；系统会自动附带对应用户问题，确保公开分享时上下文完整。'}
          </p>
        </div>
        <div className="space-y-4 px-5 py-5 md:px-6 md:py-6">
          {rounds.map((round) => {
            const isSelected = selectedAssistantMessageIds.includes(round.assistantMessage.id);
            return (
              <ShareSelectionRoundCard
                key={round.assistantMessage.id}
                mode={mode}
                round={round}
                isSelected={isSelected}
                onToggleRound={onToggleRound}
                onPreviewImage={onPreviewImage}
              />
            );
          })}
        </div>
      </div>
    </div>
  );
}

/**
 * 渲染单个问答轮次卡片：用户问题与助手回答统一收纳到一个容器里，只提供一次选择入口。
 */
function ShareSelectionRoundCard({
  mode = 'share',
  round,
  isSelected,
  onToggleRound,
  onPreviewImage,
}: {
  mode?: 'share' | 'delete';
  round: ShareSelectionRound;
  isSelected: boolean;
  onToggleRound: (assistantMessageId: string) => void;
  onPreviewImage: (src: string, alt: string) => void;
}) {
  const { assistantMessage, userMessage } = round;
  const assistantContent =
    assistantMessage.content || (assistantMessage.status === 'streaming' ? '正在生成回答...' : '');
  const selectionLabel = mode === 'delete' ? '选择删除轮次' : '选择分享轮次';

  return (
    <section
      data-testid={`share-round-card-${assistantMessage.id}`}
      data-selected={isSelected ? 'true' : 'false'}
      className={`rounded-[28px] border p-5 transition-[border-color,background-color,box-shadow] ${
        isSelected
          ? 'border-border-selected bg-surface-selected shadow-[0_20px_50px_rgba(0,0,0,0.18)]'
          : 'border-border bg-background/70'
      }`}
    >
      <div className="flex items-start gap-4">
        <label className="mt-1 inline-flex h-7 w-7 shrink-0 cursor-pointer items-center justify-center rounded-full border border-border bg-surface shadow-sm">
          <span className="sr-only">{`${selectionLabel} ${assistantMessage.content}`}</span>
          <input
            type="checkbox"
            aria-label={`${selectionLabel} ${assistantMessage.content}`}
            checked={isSelected}
            onChange={() => onToggleRound(assistantMessage.id)}
            className="h-4 w-4 accent-foreground"
          />
        </label>
        <div className="min-w-0 flex-1 space-y-4">
          {userMessage ? (
            <div className="flex justify-end">
              <div className="max-w-[78%] rounded-[20px] bg-background px-4 py-3 text-sm leading-6 text-foreground shadow-sm">
                {userMessage.attachments && userMessage.attachments.length > 0 ? (
                  <MessageAttachmentList
                    attachments={userMessage.attachments}
                    onPreviewImage={onPreviewImage}
                  />
                ) : null}
                <div className="whitespace-pre-wrap">{userMessage.content}</div>
              </div>
            </div>
          ) : null}
          <div className="rounded-[24px] bg-surface px-4 py-4 text-foreground shadow-[inset_0_1px_0_rgba(255,255,255,0.03)]">
            {assistantMessage.processCards && assistantMessage.processCards.length > 0 ? (
              <ProcessTracePanel
                messageId={assistantMessage.id}
                cards={assistantMessage.processCards}
              />
            ) : null}
            {assistantMessage.attachments && assistantMessage.attachments.length > 0 ? (
              <MessageAttachmentList
                attachments={assistantMessage.attachments}
                onPreviewImage={onPreviewImage}
              />
            ) : null}
            <MarkdownMessage content={assistantContent} messageId={assistantMessage.id} />
            {assistantMessage.searchProgress?.items?.length ? (
              <SearchProgressPanel
                messageId={assistantMessage.id}
                progress={assistantMessage.searchProgress}
              />
            ) : null}
            {assistantMessage.errorMessage ? (
              <div className="mt-3 rounded-2xl border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs text-red-300">
                {assistantMessage.errorMessage}
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
}

/**
 * 分享选择底部工具栏，提供全选、取消和生成链接入口。
 */
function ShareSelectionToolbar({
  mode = 'share',
  selectedCount,
  totalCount,
  onSelectAll,
  onCancel,
  onConfirm,
}: {
  mode?: 'share' | 'delete';
  selectedCount: number;
  totalCount: number;
  onSelectAll: () => void;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const isDeleteMode = mode === 'delete';
  return (
    <div
      role="toolbar"
      aria-label={isDeleteMode ? '删除选择工具栏' : '分享选择工具栏'}
      className="absolute bottom-6 left-1/2 z-30 flex -translate-x-1/2 items-center gap-3 rounded-2xl border border-border bg-surface/96 px-4 py-3 text-sm text-foreground shadow-[0_18px_48px_rgba(0,0,0,0.22)] backdrop-blur"
    >
      <button
        type="button"
        onClick={onSelectAll}
        className="rounded-xl px-3 py-2 text-muted transition-colors hover:bg-surface-container hover:text-foreground"
      >
        全选
      </button>
      <span className="text-muted">
        <span>已选{selectedCount}组对话</span>
        <span className="ml-1">/ 共{totalCount}组</span>
      </span>
      <button
        type="button"
        onClick={onCancel}
        className="rounded-xl px-3 py-2 text-muted transition-colors hover:bg-surface-container hover:text-foreground"
      >
        取消
      </button>
      <button
        type="button"
        aria-label={isDeleteMode ? '删除所选' : '生成分享链接'}
        disabled={selectedCount === 0}
        onClick={onConfirm}
        className={`rounded-xl px-4 py-2 transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50 ${
          isDeleteMode ? 'bg-red-600 text-white' : 'bg-foreground text-background'
        }`}
      >
        {isDeleteMode ? '删除所选' : '生成分享链接'}
      </button>
    </div>
  );
}

/**
 * 千问风格分享弹窗，展示选中内容预览与可复制公开链接。
 */
function ShareConversationDialog({
  state,
  onClose,
}: {
  state: {
    isLoading: boolean;
    url: string;
    previewMessages: ChatMessageItem[];
    errorMessage: string;
  };
  onClose: () => void;
}) {
  const [copyState, setCopyState] = React.useState<'idle' | 'copied' | 'error'>('idle');
  const copyShareUrl = async () => {
    if (!state.url) {
      return;
    }
    try {
      await navigator.clipboard.writeText(state.url);
      setCopyState('copied');
    } catch {
      setCopyState('error');
    }
  };
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="分享对话"
      className="fixed inset-0 z-[140] flex items-center justify-center bg-black/45 px-4 backdrop-blur-md"
    >
      <div className="w-full max-w-lg rounded-[28px] border border-border bg-surface p-5 text-foreground shadow-[0_30px_90px_rgba(0,0,0,0.34)]">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">分享对话</h2>
          <button
            type="button"
            aria-label="关闭分享弹窗"
            onClick={onClose}
            className="rounded-full p-1.5 text-muted transition-colors hover:bg-surface-container hover:text-foreground"
          >
            <X size={18} />
          </button>
        </div>
        <div className="mt-4 max-h-64 overflow-y-auto rounded-2xl border border-border bg-surface-container p-4">
          {state.previewMessages.map((message) => (
            <div key={message.id} className="mb-3 last:mb-0">
              <div className="mb-1 text-[11px] font-medium text-muted">
                {message.role === 'USER' ? '用户' : 'CodingX'}
              </div>
              <div className="whitespace-pre-wrap text-sm leading-6">{message.content}</div>
            </div>
          ))}
        </div>
        <div className="mt-4 flex gap-2 rounded-2xl border border-border bg-background p-2">
          <input
            readOnly
            value={state.isLoading ? '正在生成分享链接...' : state.url}
            className="min-w-0 flex-1 bg-transparent px-2 text-sm text-foreground outline-none"
          />
          <button
            type="button"
            aria-label="复制链接"
            disabled={!state.url}
            onClick={() => void copyShareUrl()}
            className="rounded-xl bg-foreground px-4 py-2 text-sm text-background transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            复制链接
          </button>
        </div>
        {state.errorMessage ? (
          <p className="mt-3 text-sm text-error">{state.errorMessage}</p>
        ) : null}
        {copyState === 'copied' ? <p className="mt-3 text-sm text-muted">分享链接已复制</p> : null}
        {copyState === 'error' ? <p className="mt-3 text-sm text-error">复制失败</p> : null}
      </div>
    </div>
  );
}

/**
 * 渲染用户消息气泡，并在悬浮时露出编辑、复制和删除操作。
 */
function UserMessageBubble({
  message,
  content,
  skillNameMap,
  editState,
  onStartEdit,
  onChangeEditContent,
  onCancelEdit,
  onConfirmEdit,
  onStartDelete,
  onPreviewImage,
}: {
  message: ChatMessageItem;
  content: string;
  skillNameMap: Map<string, string>;
  editState: UserMessageEditState | null;
  onStartEdit: () => void;
  onChangeEditContent: (content: string) => void;
  onCancelEdit: () => void;
  onConfirmEdit: () => void;
  onStartDelete: () => void;
  onPreviewImage: (src: string, alt: string) => void;
}) {
  return (
    <div className="chat-user-message-shell">
      <div className="chat-user-message-bubble rounded-[20px] px-4 py-2">
        {editState ? (
          <div className="min-w-[260px] space-y-3">
            <textarea
              aria-label="编辑用户消息"
              value={editState.content}
              onChange={(event) => onChangeEditContent(event.target.value)}
              className="min-h-24 w-full resize-y rounded-2xl border border-border bg-surface px-3 py-2 text-sm leading-6 text-foreground outline-none transition-colors placeholder:text-muted focus:border-border-active"
            />
            {editState.errorMessage ? (
              <p className="text-xs text-error">{editState.errorMessage}</p>
            ) : null}
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={onCancelEdit}
                disabled={editState.isSubmitting}
                className="rounded-xl px-3 py-2 text-sm text-muted transition-colors hover:bg-surface-container hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
              >
                取消
              </button>
              <button
                type="button"
                onClick={onConfirmEdit}
                disabled={editState.isSubmitting || !editState.content.trim()}
                className="rounded-xl bg-foreground px-4 py-2 text-sm text-background transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {editState.isSubmitting ? '发送中' : '再次发送'}
              </button>
            </div>
          </div>
        ) : (
          <>
            {message.skillCodes && message.skillCodes.length > 0 ? (
              <MessageSkillChips
                messageId={message.id}
                skillCodes={message.skillCodes}
                skillNameMap={skillNameMap}
              />
            ) : null}
            {message.attachments && message.attachments.length > 0 ? (
              <MessageAttachmentList
                attachments={message.attachments}
                onPreviewImage={onPreviewImage}
              />
            ) : null}
            <div className="whitespace-pre-wrap text-sm leading-6">
              {content}
            </div>
          </>
        )}
      </div>
      {!editState ? (
        <UserMessageActions
          messageId={message.id}
          content={content}
          onStartEdit={onStartEdit}
          onStartDelete={onStartDelete}
        />
      ) : null}
    </div>
  );
}

/**
 * 用户消息底部悬浮操作栏；编辑和删除仅允许落库后的消息触发。
 */
function UserMessageActions({
  messageId,
  content,
  onStartEdit,
  onStartDelete,
}: {
  messageId: string;
  content: string;
  onStartEdit: () => void;
  onStartDelete: () => void;
}) {
  const [copyState, setCopyState] = React.useState<'idle' | 'copied'>('idle');
  const canMutateMessage = PERSISTED_MESSAGE_ID_PATTERN.test(messageId);

  const copyContent = async () => {
    try {
      await navigator.clipboard.writeText(content);
      setCopyState('copied');
      window.setTimeout(() => {
        setCopyState('idle');
      }, 1200);
    } catch {
      setCopyState('idle');
    }
  };

  return (
    <div
      data-testid={`user-message-actions-${messageId}`}
      className="chat-user-message-actions"
    >
      <button
        type="button"
        data-testid={`edit-user-message-${messageId}`}
        aria-label="编辑消息"
        title="编辑"
        disabled={!canMutateMessage}
        onClick={onStartEdit}
        className="chat-message-action-button disabled:cursor-not-allowed disabled:opacity-50"
      >
        <Pencil size={15} />
      </button>
      <button
        type="button"
        data-testid={`copy-user-message-${messageId}`}
        aria-label="复制消息"
        title="复制"
        onClick={() => void copyContent()}
        className="chat-message-action-button"
      >
        <Copy size={15} />
      </button>
      <button
        type="button"
        data-testid={`delete-user-message-${messageId}`}
        aria-label="删除消息"
        title="删除"
        disabled={!canMutateMessage}
        onClick={onStartDelete}
        className="chat-message-action-button disabled:cursor-not-allowed disabled:opacity-50"
      >
        <Trash2 size={15} />
      </button>
      {copyState === 'copied' ? <span className="text-[11px] text-muted">已复制</span> : null}
    </div>
  );
}

/**
 * 渲染助手消息底部操作栏，恢复复制、分享、重新生成和反馈入口。
 */
function AssistantMessageActions({
  messageId,
  conversationId,
  content,
  isLatestAssistantMessage,
  userVote,
  onStartShareSelection,
  onRegenerateConversation,
}: {
  messageId: string;
  conversationId: string;
  content: string;
  isLatestAssistantMessage: boolean;
  userVote?: number | null;
  onStartShareSelection: (messageId: string) => void;
  onRegenerateConversation: (
    conversationId: string,
    options?: { assistantMessageId?: string },
  ) => Promise<void>;
}) {
  const [isMenuOpen, setIsMenuOpen] = React.useState(false);
  const [copiedMode, setCopiedMode] = React.useState<CopyMode | null>(null);
  // 业务约束：初始化时从 userVote 恢复已投票状态，保证刷新后仍显示之前的投票结果。
  const [reaction, setReaction] = React.useState<MessageReaction>(
    userVote === 1 ? 'up' : userVote === -1 ? 'down' : null,
  );
  const [reactionError, setReactionError] = React.useState('');
  const [isRegenerating, setIsRegenerating] = React.useState(false);
  const [regenerateError, setRegenerateError] = React.useState('');
  const menuContainerRef = React.useRef<HTMLDivElement | null>(null);
  const normalizedConversationId = String(conversationId ?? '').trim();
  // 关键约束：反馈接口当前仅接受数据库落库后的数值主键，乐观消息临时 ID 禁止提交反馈。
  const canSubmitReaction = PERSISTED_MESSAGE_ID_PATTERN.test(messageId);
  // 关键约束：分享与重新生成只能作用于已落库会话，临时 pending 会话没有稳定后端上下文。
  const canOperateOnConversation =
    normalizedConversationId.length > 0 && normalizedConversationId !== 'pending-conversation';
  const canRegenerateConversation =
    canOperateOnConversation &&
    isLatestAssistantMessage &&
    PERSISTED_MESSAGE_ID_PATTERN.test(messageId);

  React.useEffect(() => {
    if (!isMenuOpen) {
      return;
    }
    const closeMenuWhenClickOutside = (event: MouseEvent) => {
      if (menuContainerRef.current?.contains(event.target as Node)) {
        return;
      }
      setIsMenuOpen(false);
    };
    document.addEventListener('mousedown', closeMenuWhenClickOutside);
    return () => {
      document.removeEventListener('mousedown', closeMenuWhenClickOutside);
    };
  }, [isMenuOpen]);

  /**
   * 复制消息内容并短暂给出状态反馈。
   * @param mode 复制模式，区分纯文本和 Markdown。
   */
  const copyContent = async (mode: CopyMode) => {
    const value = mode === 'markdown' ? content : normalizeMessageForPlainCopy(content);
    try {
      await navigator.clipboard.writeText(value);
      setCopiedMode(mode);
      window.setTimeout(() => {
        setCopiedMode((previousMode) => (previousMode === mode ? null : previousMode));
      }, 1200);
    } catch {
      setCopiedMode(null);
    } finally {
      if (mode === 'markdown') {
        setIsMenuOpen(false);
      }
    }
  };

  /**
   * 同步本地反馈状态，确保点赞与倒赞互斥。
   * @param nextReaction 下一次反馈值。
   */
  const submitReaction = async (nextReaction: Exclude<MessageReaction, null>) => {
    if (!canSubmitReaction) {
      return;
    }
    const token = AuthStorage.getSession()?.token ?? null;
    if (!token) {
      return;
    }
    const previousReaction = reaction;
    setReaction(nextReaction);
    setReactionError('');
    try {
      await ChatApi.submitMessageFeedback(token, messageId, {
        conversationId: normalizedConversationId,
        vote: nextReaction === 'up' ? 1 : -1,
      });
    } catch (error) {
      setReaction(previousReaction);
      setReactionError(error instanceof Error ? error.message : '反馈提交失败');
    }
  };

  /**
   * 重新生成当前会话最后一条助手回复。
   * 只允许对当前会话尾部消息操作，避免旧消息触发“看不出变化”的无效重试。
   */
  const regenerateMessage = async () => {
    if (!canRegenerateConversation || isRegenerating) {
      return;
    }
    setIsRegenerating(true);
    setRegenerateError('');
    try {
      await onRegenerateConversation(normalizedConversationId, {
        assistantMessageId: messageId,
      });
    } catch {
      setRegenerateError('重新生成失败');
    } finally {
      setIsRegenerating(false);
    }
  };

  return (
    <div className="chat-message-actions flex items-center gap-1 text-muted">
      <div
        className="chat-message-action-button-group"
        data-testid={`copy-action-group-${messageId}`}
      >
        {/* 复制主按钮与更多菜单使用连续按钮组，去掉视觉分隔线并保持紧凑布局。 */}
        <button
          type="button"
          data-testid={`copy-message-${messageId}`}
          aria-label="复制消息"
          title="复制"
          onClick={() => void copyContent('plain')}
          className="chat-message-action-button chat-message-action-button-group-item"
        >
          <Copy size={15} />
        </button>
        <div ref={menuContainerRef} className="relative">
          <button
            type="button"
            data-testid={`copy-menu-toggle-${messageId}`}
            aria-label="复制更多"
            title="复制更多"
            aria-expanded={isMenuOpen}
            onClick={() => setIsMenuOpen((open) => !open)}
            className="chat-message-action-button chat-message-action-button-group-item"
          >
            <ChevronDown
              size={15}
              className={`transition-transform ${isMenuOpen ? 'rotate-180' : ''}`}
            />
          </button>
          {isMenuOpen ? (
            <div className="chat-copy-menu absolute bottom-11 left-0 min-w-[190px] rounded-2xl border border-border bg-surface px-2 py-2 shadow-[0_16px_40px_rgba(0,0,0,0.24)]">
              <button
                type="button"
                data-testid={`copy-markdown-${messageId}`}
                onClick={() => void copyContent('markdown')}
                className="chat-copy-menu-item"
              >
                复制为Markdown
              </button>
              <button
                type="button"
                onClick={() => void copyContent('plain')}
                className="chat-copy-menu-item"
              >
                复制为纯文本
              </button>
            </div>
          ) : null}
        </div>
      </div>
      <button
        type="button"
        data-testid={`share-message-${messageId}`}
        aria-label="分享消息"
        title="分享"
        disabled={!canOperateOnConversation}
        // 业务入口：助手消息分享只进入轮次选择，生成链接统一交给底部分享确认栏处理。
        onClick={() => onStartShareSelection(messageId)}
        className="chat-message-action-button disabled:cursor-not-allowed disabled:opacity-50"
      >
        <Share2 size={15} />
      </button>
      <button
        type="button"
        data-testid={`regenerate-message-${messageId}`}
        aria-label="重新生成"
        title="重新生成"
        onClick={() => void regenerateMessage()}
        disabled={!canRegenerateConversation || isRegenerating}
        className="chat-message-action-button disabled:cursor-not-allowed disabled:opacity-50"
      >
        <RotateCcw size={15} className={isRegenerating ? 'animate-spin' : ''} />
      </button>
      <button
        type="button"
        data-testid={`thumbs-up-${messageId}`}
        aria-label="点赞"
        title="点赞"
        aria-pressed={reaction === 'up'}
        disabled={!canSubmitReaction}
        onClick={() => void submitReaction('up')}
        className={`chat-message-action-button disabled:cursor-not-allowed disabled:opacity-50 ${
          reaction === 'up' ? 'chat-message-action-button-active' : ''
        }`}
      >
        <ThumbsUp size={15} />
      </button>
      <button
        type="button"
        data-testid={`thumbs-down-${messageId}`}
        aria-label="倒赞"
        title="倒赞"
        aria-pressed={reaction === 'down'}
        disabled={!canSubmitReaction}
        onClick={() => void submitReaction('down')}
        className={`chat-message-action-button disabled:cursor-not-allowed disabled:opacity-50 ${
          reaction === 'down' ? 'chat-message-action-button-active' : ''
        }`}
      >
        <ThumbsDown size={15} />
      </button>
      {copiedMode ? <span className="ml-2 text-[11px] text-muted">已复制</span> : null}
      {regenerateError ? <span className="ml-2 text-[11px] text-error">{regenerateError}</span> : null}
      {reactionError ? <span className="ml-2 text-[11px] text-error">{reactionError}</span> : null}
    </div>
  );
}

/**
 * 将 Markdown 消息转换为复制用纯文本，避免把语法标记原样拷贝给用户。
 * @param content Markdown 原文。
 * @returns 可读纯文本。
 */
function normalizeMessageForPlainCopy(content: string) {
  return content
    .replace(/\r\n/g, '\n')
    .replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '$1')
    .replace(/`{1,3}/g, '')
    .replace(/^[>\-#*]+\s*/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/**
 * 渲染消息中的附件列表，图片可点击预览，普通文件提供内联下载入口。
 */
function MessageAttachmentList({
  attachments,
  onPreviewImage,
}: {
  attachments: ChatAttachmentItem[];
  onPreviewImage: (src: string, alt: string) => void;
}) {
  if (attachments.length === 0) {
    return null;
  }
  return (
    <div className="mb-3 flex flex-wrap items-start gap-2">
      {attachments.map((attachment) => {
        const isImage = attachment.attachmentType === 'image' && !!attachment.previewUrl;
        if (isImage) {
          return (
            <button
              key={attachment.id}
              type="button"
              aria-label={`预览图片 ${attachment.fileName}`}
              data-testid={`message-image-attachment-${attachment.id}`}
              className="h-[88px] w-[88px] overflow-hidden rounded-lg border border-border bg-surface"
              onClick={() => onPreviewImage(attachment.previewUrl as string, attachment.fileName)}
            >
              <img
                src={attachment.previewUrl}
                alt={attachment.fileName}
                className="h-full w-full object-cover"
              />
            </button>
          );
        }
        return (
          <a
            key={attachment.id}
            href={attachment.previewUrl}
            target="_blank"
            rel="noreferrer"
            data-testid={`message-file-attachment-${attachment.id}`}
            className="inline-flex max-w-[260px] items-center gap-2 rounded-lg border border-border bg-surface px-2.5 py-1.5 text-xs text-foreground"
          >
            <File size={13} className="text-muted" />
            <span className="truncate">{attachment.fileName}</span>
          </a>
        );
      })}
    </div>
  );
}

/**
 * 渲染消息内技能气泡，保证历史回放时技能绑定信息可见且不依赖正文是否保留 @token。
 */
function MessageSkillChips({
  messageId,
  skillCodes,
  skillNameMap,
}: {
  messageId: string;
  skillCodes: string[];
  skillNameMap: Map<string, string>;
}) {
  if (skillCodes.length === 0) {
    return null;
  }
  return (
    <div className="mb-2 flex flex-wrap gap-1.5">
      {skillCodes.map((skillCode) => {
        const displayName = skillNameMap.get(skillCode) ?? skillCode;
        return (
          <span
            key={`${messageId}-${skillCode}`}
            data-testid={`message-skill-chip-${messageId}-${skillCode}`}
            className="inline-flex items-center rounded-md border border-border/70 bg-surface-container/82 px-2 py-0.5 text-[12px] text-foreground"
            title={displayName}
          >
            <Sparkles size={12} className="mr-1 shrink-0 text-muted" />
            <span className="whitespace-nowrap">@{skillCode}</span>
          </span>
        );
      })}
    </div>
  );
}

type InputPreviewSegment =
  | {
      type: 'text';
      value: string;
      start: number;
      end: number;
    }
  | {
      type: 'skill';
      rawToken: string;
      skillCode: string;
      displayName: string;
      start: number;
      end: number;
    };

type SkillTokenRange = {
  rawToken: string;
  skillCode: string;
  start: number;
  end: number;
};

type SkillTokenRemovalResult = {
  nextInput: string;
  nextCaretPosition: number;
};

/**
 * 将输入文本切分为“普通文本片段 + 技能标记片段”，供输入区气泡预览层渲染。
 * @param rawInput 原始输入文本。
 * @param skillNameMap 技能编码到展示名的映射。
 * @returns 预览片段列表。
 */
function tokenizeInputForPreview(
  rawInput: string,
  skillNameMap: Map<string, string>,
): InputPreviewSegment[] {
  if (!rawInput) {
    return [];
  }
  const segments: InputPreviewSegment[] = [];
  let cursor = 0;
  const tokenPattern = /@([a-zA-Z0-9_-]+)/g;
  for (const match of rawInput.matchAll(tokenPattern)) {
    const fullToken = match[0];
    const skillCode = match[1];
    const tokenStart = match.index ?? -1;
    const tokenEnd = tokenStart + fullToken.length;
    const displayName = skillNameMap.get(skillCode);
    if (tokenStart < 0 || !displayName) {
      continue;
    }
    if (tokenStart > cursor) {
      segments.push({
        type: 'text',
        value: rawInput.slice(cursor, tokenStart),
        start: cursor,
        end: tokenStart,
      });
    }
    segments.push({
      type: 'skill',
      rawToken: fullToken,
      skillCode,
      displayName,
      start: tokenStart,
      end: tokenEnd,
    });
    cursor = tokenEnd;
  }
  if (cursor < rawInput.length) {
    segments.push({
      type: 'text',
      value: rawInput.slice(cursor),
      start: cursor,
      end: rawInput.length,
    });
  }
  return segments;
}

/**
 * 提取输入文本中的技能 token 位置信息，供气泡渲染与删除逻辑复用。
 * @param rawInput 原始输入文本。
 * @returns token 列表，包含编码与字符区间。
 */
function extractSkillTokens(rawInput: string): SkillTokenRange[] {
  if (!rawInput) {
    return [];
  }
  const tokens: SkillTokenRange[] = [];
  const tokenPattern = /@([a-zA-Z0-9_-]+)/g;
  for (const match of rawInput.matchAll(tokenPattern)) {
    const fullToken = match[0];
    const tokenStart = match.index ?? -1;
    if (tokenStart < 0) {
      continue;
    }
    tokens.push({
      rawToken: fullToken,
      skillCode: match[1],
      start: tokenStart,
      end: tokenStart + fullToken.length,
    });
  }
  return tokens;
}

/**
 * 统一移除指定技能编码的文本 token，并清理多余空白，保持自然输入文案。
 * @param rawInput 原始输入文本。
 * @param skillCode 技能编码。
 * @returns 删除后的输入文本。
 */
function removeSkillTokenByCode(rawInput: string, skillCode: string): string {
  if (!rawInput.trim()) {
    return rawInput;
  }
  const escapedSkillCode = skillCode.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const removeTokenPattern = new RegExp(`(?:^|\\s)@${escapedSkillCode}(?=\\s|$)`, 'g');
  return rawInput.replace(removeTokenPattern, ' ').replace(/[ \t]{2,}/g, ' ').trimStart();
}

/**
 * 处理 Backspace 一键删除技能 token：当光标位于 token 后，删除整枚 token。
 * @param rawInput 当前输入文本。
 * @param selectionStart 光标起点。
 * @param selectionEnd 光标终点。
 * @param availableSkillCodeSet 当前可识别技能集合。
 * @returns 命中可删 token 时返回新输入与新光标位置，否则返回 null。
 */
function removeSkillTokenBeforeCaret(
  rawInput: string,
  selectionStart: number,
  selectionEnd: number,
  availableSkillCodeSet: Set<string>,
): SkillTokenRemovalResult | null {
  if (selectionStart !== selectionEnd || selectionStart <= 0) {
    return null;
  }
  const effectiveCaret = /\s/.test(rawInput.charAt(selectionStart - 1))
    ? selectionStart - 1
    : selectionStart;
  if (effectiveCaret <= 0) {
    return null;
  }
  const targetToken = extractSkillTokens(rawInput).find(
    (token) => token.end === effectiveCaret && availableSkillCodeSet.has(token.skillCode),
  );
  if (!targetToken) {
    return null;
  }
  const beforeToken = rawInput.slice(0, targetToken.start);
  const afterToken = rawInput.slice(targetToken.end);
  const nextInput = `${beforeToken}${afterToken}`.replace(/[ \t]{2,}/g, ' ').trimStart();
  const removedPrefixLength = beforeToken.length;
  const nextCaretPosition = Math.min(removedPrefixLength, nextInput.length);
  return { nextInput, nextCaretPosition };
}

/**
 * 统一渲染右栏分组面板。
 */
function Panel({
  title,
  icon: Icon,
  children,
}: {
  title: string;
  icon: React.ComponentType<{ size?: number; className?: string }>;
  children: React.ReactNode;
}) {
  return (
    <section className="mb-6">
      <div className="mb-3 flex items-center gap-2 text-foreground">
        <Icon size={16} className="text-accent-breeze" />
        <h4 className="text-sm font-semibold">{title}</h4>
      </div>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

/**
 * 统一渲染空态块。
 */
function EmptyBlock({ text }: { text: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-border bg-surface-container px-4 py-4 text-sm text-muted">
      {text}
    </div>
  );
}

/**
 * 渲染页面内重命名弹窗，替代浏览器原生 prompt。
 */
function InlineDialog({
  title,
  defaultValue,
  confirmLabel,
  cancelLabel,
  onCancel,
  onConfirm,
}: {
  title: string;
  defaultValue: string;
  confirmLabel: string;
  cancelLabel: string;
  onCancel: () => void;
  onConfirm: (value: string) => Promise<void>;
}) {
  const [value, setValue] = React.useState(defaultValue);
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');

  const trimmedValue = value.trim();

  /**
   * 统一处理重命名提交：提交中禁用二次点击，并在失败时直接透出后端错误文案。
   */
  const handleConfirm = async () => {
    if (isSubmitting || !trimmedValue) {
      return;
    }
    setIsSubmitting(true);
    setErrorMessage('');
    try {
      await onConfirm(trimmedValue);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '重命名失败，请稍后重试');
      setIsSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      className="fixed inset-0 z-[120] flex items-center justify-center bg-background/35 backdrop-blur-sm"
    >
      <div className="w-full max-w-md rounded-2xl border border-border bg-surface-container p-5 shadow-[0_24px_64px_rgba(0,0,0,0.26)]">
        <h3 className="text-base font-semibold text-foreground">{title}</h3>
        <input
          value={value}
          disabled={isSubmitting}
          onChange={(event) => {
            setValue(event.target.value);
            if (errorMessage) {
              setErrorMessage('');
            }
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
              event.preventDefault();
              void handleConfirm();
            }
          }}
          className="mt-4 h-24 w-full rounded-xl border border-border bg-surface px-4 py-3 text-sm text-foreground outline-none"
        />
        {errorMessage ? (
          <p className="mt-3 text-sm text-[#ff7b72]" role="alert" aria-live="polite">
            {errorMessage}
          </p>
        ) : null}
        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={isSubmitting}
            className="rounded-lg bg-surface px-4 py-2 text-sm text-muted transition-[opacity,color,background-color] hover:bg-surface-high hover:text-foreground disabled:opacity-60"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            disabled={isSubmitting || !trimmedValue}
            onClick={() => void handleConfirm()}
            className="rounded-lg bg-foreground px-4 py-2 text-sm text-background transition-opacity hover:opacity-90 disabled:opacity-60"
          >
            {isSubmitting ? '保存中...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * 渲染页面内删除确认弹窗，替代浏览器原生 confirm。
 */
function ConfirmDialog({
  title,
  description,
  confirmLabel,
  cancelLabel,
  onCancel,
  onConfirm,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel: string;
  onCancel: () => void;
  onConfirm: () => Promise<void>;
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      className="fixed inset-0 z-[120] flex items-center justify-center bg-background/35 backdrop-blur-sm"
    >
      <div className="w-full max-w-sm rounded-2xl border border-border bg-surface-container p-5 shadow-[0_24px_64px_rgba(0,0,0,0.26)]">
        <h3 className="text-base font-semibold text-foreground">{title}</h3>
        <p className="mt-3 text-sm leading-6 text-muted">{description}</p>
        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-lg bg-surface px-4 py-2 text-sm text-muted transition-colors hover:bg-surface-high hover:text-foreground"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={() => void onConfirm()}
            className="rounded-lg bg-[#ff5b57] px-4 py-2 text-sm text-white transition-opacity hover:opacity-90"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * 渲染 WorkBuddy 式消息内过程链路：思考、工具调用和工具结果按到达顺序直接铺开。
 */
function ProcessTracePanel({
  messageId,
  cards,
}: {
  messageId: string;
  cards: ProcessCardItem[];
}) {
  const traceSegments = groupProcessTraceSegments(cards);
  let hasRenderedAnalysisHeading = false;

  return (
    <section
      data-testid={`process-trace-panel-${messageId}`}
      className="mb-4 space-y-3 text-sm text-muted"
    >
      {traceSegments.map((segment, index) => {
        if (segment.type === 'tool') {
          return (
            <ProcessToolRow
              key={segment.card.id}
              messageId={messageId}
              card={segment.card}
            />
          );
        }
        if (segment.type === 'search_result_group') {
          return (
            <ProcessSearchSummary
              key={`search-result-group-${segment.cards[0]?.id ?? index}`}
              messageId={messageId}
              cards={segment.cards}
            />
          );
        }
        if (segment.type === 'command_group') {
          return (
            <ProcessCommandSummary
              key={`command-group-${segment.cards[0]?.id ?? index}`}
              messageId={messageId}
              cards={segment.cards}
            />
          );
        }
        const shouldShowAnalysisHeading =
          segment.card.type === 'analysis' && !hasRenderedAnalysisHeading;
        if (shouldShowAnalysisHeading) {
          hasRenderedAnalysisHeading = true;
        }
        return (
          <ProcessTraceText
            key={segment.card.id}
            card={segment.card}
            messageId={messageId}
            isFirstAnalysis={shouldShowAnalysisHeading}
          />
        );
      })}
    </section>
  );
}

type ProcessTraceSegment =
  | { type: 'text'; card: ProcessCardItem }
  | { type: 'tool'; card: ProcessCardItem }
  | { type: 'search_result_group'; cards: ProcessCardItem[] }
  | { type: 'command_group'; cards: ProcessCardItem[] };

/**
 * 按真实过程顺序输出消息内链路，工具调用不能再被合并成汇总行，否则用户看不到模型边思考边执行的节奏。
 */
function groupProcessTraceSegments(cards: ProcessCardItem[]): ProcessTraceSegment[] {
  const segments: ProcessTraceSegment[] = [];

  for (let index = 0; index < cards.length; index += 1) {
    const card = cards[index];
    if (card.type === 'tool_call' || card.type === 'tool_result') {
      if (isCommandProcessCard(card)) {
        const commandCards = [card];
        let nextIndex = index + 1;
        while (
          nextIndex < cards.length &&
          (cards[nextIndex].type === 'tool_call' || cards[nextIndex].type === 'tool_result') &&
          isCommandProcessCard(cards[nextIndex])
        ) {
          commandCards.push(cards[nextIndex]);
          nextIndex += 1;
        }
        const commandCount = countCommandCalls(commandCards);
        if (commandCount > 1) {
          segments.push({ type: 'command_group', cards: commandCards });
          index = nextIndex - 1;
          continue;
        }
      }
      if (card.type === 'tool_result' && isSearchProcessCard(card)) {
        const searchResultCards = [card];
        let nextIndex = index + 1;
        while (
          nextIndex < cards.length &&
          cards[nextIndex].type === 'tool_result' &&
          isSearchProcessCard(cards[nextIndex])
        ) {
          searchResultCards.push(cards[nextIndex]);
          nextIndex += 1;
        }
        if (searchResultCards.length > 1) {
          segments.push({ type: 'search_result_group', cards: searchResultCards });
          index = nextIndex - 1;
          continue;
        }
      }
      segments.push({ type: 'tool', card });
      continue;
    }
    if (card.type === 'synthesis' && isDisposableSynthesisTrace(card)) {
      continue;
    }
    if (card.summary.trim().length === 0) {
      continue;
    }
    segments.push({ type: 'text', card });
  }
  return segments;
}

/**
 * 连续搜索结果默认收起为 Codex 风格网页访问摘要，避免大批网页返回占满主消息区。
 */
function ProcessSearchSummary({
  messageId,
  cards,
}: {
  messageId: string;
  cards: ProcessCardItem[];
}) {
  const [isExpanded, setIsExpanded] = React.useState(false);
  const firstCardId = cards[0]?.id ?? 'search-results';
  const contentId = `process-search-summary-content-${messageId}-${firstCardId}`;

  return (
    <article
      data-testid={`process-search-summary-${messageId}-${firstCardId}`}
      className="space-y-2 border-l border-border pl-3"
    >
      <div className="flex min-w-0 items-start gap-2 text-sm leading-6 text-foreground">
        <Globe2 size={15} className="mt-1 shrink-0 text-muted" />
        <div className="min-w-0 flex-1">
          <div className="whitespace-pre-wrap [overflow-wrap:anywhere] text-muted">
            已搜索网页 {cards.length} 次
          </div>
          <button
            type="button"
            data-testid={`process-search-summary-toggle-${messageId}-${firstCardId}`}
            aria-expanded={isExpanded}
            aria-controls={contentId}
            aria-label={isExpanded ? '收起搜索来源' : '展开搜索来源'}
            onClick={() => setIsExpanded((current) => !current)}
            className="mt-1 inline-flex items-center gap-1 text-xs text-muted transition-colors hover:text-foreground"
          >
            <ChevronDown
              size={13}
              className={`transition-transform ${isExpanded ? 'rotate-180' : '-rotate-90'}`}
            />
            <span>{isExpanded ? '收起来源' : '展开来源'}</span>
          </button>
        </div>
      </div>
      {isExpanded ? (
        <div id={contentId} className="space-y-2">
          {cards.map((card) => (
            <ProcessToolRow key={card.id} messageId={messageId} card={card} />
          ))}
        </div>
      ) : null}
    </article>
  );
}

/**
 * 连续命令默认收起为一条运行摘要；展开后保留每次命令和输出，方便核对长任务过程。
 */
function ProcessCommandSummary({
  messageId,
  cards,
}: {
  messageId: string;
  cards: ProcessCardItem[];
}) {
  const [isExpanded, setIsExpanded] = React.useState(false);
  const firstCardId = cards[0]?.id ?? 'command-results';
  const contentId = `process-command-summary-content-${messageId}-${firstCardId}`;
  const commandRuns = buildCommandRuns(cards);

  return (
    <article
      data-testid={`process-command-summary-${messageId}-${firstCardId}`}
      className="space-y-2 border-l border-border pl-3"
    >
      <div className="flex min-w-0 items-start gap-2 text-sm leading-6 text-foreground">
        <CheckCircle2 size={15} className="mt-1 shrink-0 text-muted" />
        <div className="min-w-0 flex-1">
          <div className="whitespace-pre-wrap [overflow-wrap:anywhere] text-muted">
            已运行 {commandRuns.length} 条命令
          </div>
          <button
            type="button"
            data-testid={`process-command-summary-toggle-${messageId}-${firstCardId}`}
            aria-expanded={isExpanded}
            aria-controls={contentId}
            aria-label={isExpanded ? '收起命令输出' : '展开命令输出'}
            onClick={() => setIsExpanded((current) => !current)}
            className="mt-1 inline-flex items-center gap-1 text-xs text-muted transition-colors hover:text-foreground"
          >
            <ChevronDown
              size={13}
              className={`transition-transform ${isExpanded ? 'rotate-180' : '-rotate-90'}`}
            />
            <span>{isExpanded ? '收起命令' : '展开命令'}</span>
          </button>
        </div>
      </div>
      {isExpanded ? (
        <div id={contentId} className="space-y-2">
          {commandRuns.map((run, index) => (
            <ProcessCommandBlock key={`${firstCardId}-${index}-${run.command}`} run={run} />
          ))}
        </div>
      ) : null}
    </article>
  );
}

/**
 * 展开后的单条命令块，刻意使用不透明背景，保证暗色模式下长输出仍然可读。
 */
function ProcessCommandBlock({ run }: { run: CommandProcessRun }) {
  return (
    <div className="overflow-hidden rounded-md border border-border bg-surface-container">
      <div className="border-b border-border px-3 py-2 text-[11px] font-medium text-muted">Shell</div>
      <div className="space-y-3 px-3 py-3 font-mono text-xs leading-5 text-foreground">
        <div className="whitespace-pre-wrap [overflow-wrap:anywhere]">
          {`$ ${run.command}`}
        </div>
        {run.output ? (
          <pre className="max-h-64 overflow-y-auto whitespace-pre-wrap [overflow-wrap:anywhere] rounded bg-surface px-3 py-2 text-muted [scrollbar-gutter:stable]">
            {run.output}
          </pre>
        ) : null}
      </div>
      <div className="flex justify-end border-t border-border px-3 py-2 text-[11px] text-muted">
        {run.status === 'error' ? '失败' : '成功'}
      </div>
    </div>
  );
}

/**
 * 历史会话里可能已经持久化了“整理结论”占位节点；最终答案正文已承载结论，渲染层直接过滤这类无价值过程。
 */
function isDisposableSynthesisTrace(card: ProcessCardItem) {
  const normalizedTitle = card.title.trim();
  const normalizedSummary = card.summary.trim();
  return (
    normalizedTitle === '整理结论' ||
    normalizedSummary.includes('整理最终回答') ||
    normalizedSummary.includes('最终回答')
  );
}

/**
 * 渲染一段过程文本。深度思考默认展开，直接呈现模型在工具前后的判断过程。
 */
function ProcessTraceText({
  card,
  messageId,
  isFirstAnalysis,
}: {
  card: ProcessCardItem;
  messageId: string;
  isFirstAnalysis: boolean;
}) {
  if (card.type === 'analysis') {
    return (
      <ProcessAnalysisTrace
        card={card}
        messageId={messageId}
        isFirstAnalysis={isFirstAnalysis}
      />
    );
  }

  return (
    <div className="space-y-2">
      {isFirstAnalysis ? (
        <div className="text-xs font-medium text-muted">深度思考</div>
      ) : null}
      <p
        data-testid={card.type === 'analysis' ? `process-analysis-text-${messageId}` : undefined}
        className="whitespace-pre-wrap [overflow-wrap:anywhere] text-sm leading-7 text-foreground"
      >
        {card.summary}
      </p>
    </div>
  );
}

/**
 * 展示模型真实 thinking：默认展开，保留手动折叠能力，避免过程被误读成最终统一生成。
 */
function ProcessAnalysisTrace({
  card,
  messageId,
  isFirstAnalysis,
}: {
  card: ProcessCardItem;
  messageId: string;
  isFirstAnalysis: boolean;
}) {
  const isReactTrace = card.presentation === 'react';
  const [isExpanded, setIsExpanded] = React.useState(true);
  const contentId = `process-analysis-content-${messageId}-${card.id}`;
  const label = isReactTrace ? '深度思考' : isFirstAnalysis ? '深度思考' : card.title || '深度思考';

  React.useEffect(() => {
    // 新的 ReAct 展示目标是始终让过程可见，状态变化不再自动收起思考内容。
    setIsExpanded(true);
  }, [card.id]);

  return (
    <div className="space-y-2">
      <button
        type="button"
        data-testid={`process-analysis-toggle-${messageId}-${card.id}`}
        aria-expanded={isExpanded}
        aria-controls={contentId}
        aria-label={isExpanded ? `折叠${label}` : `展开${label}`}
        onClick={() => setIsExpanded((current) => !current)}
        className="inline-flex max-w-full items-center gap-2 rounded-md px-0 py-1 text-xs font-medium text-muted transition-colors hover:text-foreground"
      >
        <ChevronDown
          size={14}
          className={`shrink-0 transition-transform duration-200 ${isExpanded ? 'rotate-180' : '-rotate-90'}`}
        />
        <span className="whitespace-nowrap">{label}</span>
        <span className="text-border">·</span>
        <span className="whitespace-nowrap">{isExpanded ? '收起' : '展开'}</span>
      </button>
      {isExpanded ? (
        <div
          id={contentId}
          data-testid={`process-analysis-card-${messageId}`}
          className="border-l border-border pl-4"
        >
          <p
            data-testid={`process-analysis-text-${messageId}`}
            className="whitespace-pre-wrap [overflow-wrap:anywhere] text-sm leading-7 text-muted"
          >
            {card.summary}
          </p>
        </div>
      ) : null}
    </div>
  );
}

/**
 * 竖向展示单条工具过程，工具名和摘要默认可见，参数和结果明细由本行单独展开。
 */
function ProcessToolRow({
  messageId,
  card,
}: {
  messageId: string;
  card: ProcessCardItem;
}) {
  const [isExpanded, setIsExpanded] = React.useState(false);
  const Icon = card.type === 'tool_call' ? Globe2 : CheckCircle2;
  const searchResultItems = parseSearchResultDetails(card);
  const hasDetails = searchResultItems.length > 0 || (card.details?.length ?? 0) > 0;
  const contentId = `process-tool-detail-${messageId}-${card.id}`;
  const isReactTrace = card.presentation === 'react';

  return (
    <article data-testid={`process-tool-row-${messageId}-${card.id}`} className="space-y-2 border-l border-border pl-3">
      <div className="flex min-w-0 items-start gap-2 text-sm leading-6 text-foreground">
        <Icon size={15} className="mt-1 shrink-0 text-muted" />
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-1 whitespace-pre-wrap [overflow-wrap:anywhere]">
            {!isReactTrace ? <span>{card.title}</span> : null}
            {card.summary ? <span className="min-w-0 text-muted">{card.summary}</span> : null}
          </div>
          {hasDetails ? (
            <button
              type="button"
              data-testid={`process-tool-detail-toggle-${messageId}-${card.id}`}
              aria-expanded={isExpanded}
              aria-controls={contentId}
              aria-label={isExpanded ? '折叠工具明细' : '展开工具明细'}
              onClick={() => setIsExpanded((current) => !current)}
              className="mt-1 inline-flex items-center gap-1 text-xs text-muted transition-colors hover:text-foreground"
            >
              <ChevronDown
                size={13}
                className={`transition-transform ${isExpanded ? 'rotate-180' : '-rotate-90'}`}
              />
              <span>{isExpanded ? '收起明细' : '查看明细'}</span>
            </button>
          ) : null}
        </div>
      </div>
      {isExpanded && hasDetails ? (
        <div id={contentId}>
          {searchResultItems.length > 0 ? (
            <ProcessSearchResultList cardId={card.id} items={searchResultItems} />
          ) : card.details && card.details.length > 0 ? (
            <div className="space-y-2">
              {card.details.map((detail) => (
                <div key={`${card.id}-${detail.label}`} className="rounded-md bg-surface-container px-3 py-2">
                  <div className="mb-1 text-[11px] text-muted">{detail.label}</div>
                  <pre className="whitespace-pre-wrap [overflow-wrap:anywhere] text-xs leading-5 text-foreground">
                    {detail.content}
                  </pre>
                </div>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}
    </article>
  );
}

/**
 * 在工具结果行内展示搜索来源列表，把原始明细转换为可扫读的来源卡片。
 */
function ProcessSearchResultList({
  cardId,
  items,
}: {
  cardId: string;
  items: SearchSourceDisplayItem[];
}) {
  return (
    <div
      data-testid={`process-search-result-list-${cardId}`}
      className="max-h-[360px] overflow-y-auto rounded-xl border border-border bg-surface/88 p-2 [scrollbar-gutter:stable]"
    >
      <div className="mb-2 flex items-center justify-between gap-3 px-1">
        <div className="text-[11px] font-medium text-muted">搜索结果</div>
        <div className="rounded-full border border-border bg-surface-container px-2 py-0.5 text-[10px] text-muted">
          {items.length} 条来源
        </div>
      </div>
      <ol className="space-y-1.5">
        {items.map((item, index) => (
          <li key={`${item.id}-${index}`}>
            <ProcessSearchResultItem cardId={cardId} item={item} index={index} />
          </li>
        ))}
      </ol>
    </div>
  );
}

/**
 * 渲染工具结果中的单条搜索来源，保留标题、站点和原始链接三个阅读层级。
 */
function ProcessSearchResultItem({
  cardId,
  item,
  index,
}: {
  cardId: string;
  item: SearchSourceDisplayItem;
  index: number;
}) {
  const source = resolveSearchSourceMeta(item);
  const displayTitle = item.title.trim() || source.siteLabel || source.displayUrl || `来源 ${index + 1}`;
  const rowClassName =
    'group flex min-w-0 gap-2 rounded-lg px-2 py-2 text-left transition-colors hover:bg-surface-container focus-visible:bg-surface-container focus-visible:outline-none';
  const content = (
    <>
      <div className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border border-border bg-surface-container font-mono text-[10px] text-muted">
        {index + 1}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex min-w-0 items-start justify-between gap-2">
          <div className="min-w-0 text-sm font-medium leading-5 text-foreground" title={displayTitle}>
            {displayTitle}
          </div>
          {source.href ? (
            <ExternalLink
              size={12}
              className="mt-0.5 shrink-0 text-muted transition-colors group-hover:text-foreground"
            />
          ) : null}
        </div>
        <div className="mt-1 flex min-w-0 flex-wrap items-center gap-x-2 gap-y-0.5 text-[11px] leading-4 text-muted">
          <span className="max-w-[180px] truncate">{source.siteLabel}</span>
          {source.hostname ? <span className="font-mono">{source.hostname}</span> : null}
        </div>
        {source.displayUrl ? (
          <div
            className="mt-1 truncate font-mono text-[11px] leading-4 text-accent-breeze"
            title={item.url}
          >
            {source.displayUrl}
          </div>
        ) : null}
      </div>
    </>
  );

  if (source.href) {
    return (
      <a
        data-testid={`process-search-result-link-${cardId}-${index}`}
        href={source.href}
        target="_blank"
        rel="noreferrer noopener"
        aria-label={`打开搜索结果 ${displayTitle}`}
        className={rowClassName}
      >
        {content}
      </a>
    );
  }

  return (
    <div
      data-testid={`process-search-result-row-${cardId}-${index}`}
      className={`${rowClassName} cursor-default`}
    >
      {content}
    </div>
  );
}

/**
 * 识别搜索工具结果明细，兼容流式来源拼接和历史回放中的多种分隔格式。
 */
function parseSearchResultDetails(card: ProcessCardItem): SearchSourceDisplayItem[] {
  if (card.type !== 'tool_result' || !isSearchProcessCard(card)) {
    return [];
  }
  const rawDetails = card.details ?? [];
  const resultDetails = rawDetails.filter((detail) => detail.label.trim() === '结果');
  const parsedItems = resultDetails.flatMap((detail) => parseSearchResultDetailContent(detail.content));
  return dedupeSearchSourceItems(parsedItems);
}

/**
 * 判断过程卡片是否来自网页搜索，历史数据缺失 toolId 时用标题和摘要兜底识别。
 */
function isSearchProcessCard(card: ProcessCardItem): boolean {
  if (card.toolId === 'search') {
    return true;
  }
  const text = `${card.displayName ?? ''} ${card.title ?? ''} ${card.summary ?? ''}`;
  return text.includes('网页搜索') || text.includes('搜索结果') || text.includes('搜索来源');
}

interface CommandProcessRun {
  command: string;
  output: string;
  status: ProcessCardItem['status'];
}

/**
 * 识别本地命令工具；只聚合 shell 类工具，避免普通 MCP/业务工具被错误折叠为命令。
 */
function isCommandProcessCard(card: ProcessCardItem): boolean {
  if (isSearchProcessCard(card)) {
    return false;
  }
  const text = `${card.toolId ?? ''} ${card.displayName ?? ''} ${card.title ?? ''} ${card.summary ?? ''}`.toLowerCase();
  if (text.includes('shell_command') || text.includes('shell command') || text.includes('powershell')) {
    return true;
  }
  return getProcessCardDetailContent(card, '参数').some(hasExplicitShellCommandParameter);
}

/**
 * 统计命令动作数量；结果卡片只补充输出，不参与“运行 N 条命令”的计数。
 */
function countCommandCalls(cards: ProcessCardItem[]): number {
  const commandCards = cards.filter((card) => card.type === 'tool_call');
  if (commandCards.length > 0) {
    return commandCards.length;
  }
  return buildCommandRuns(cards).length;
}

/**
 * 将连续 shell 调用和结果配对成可展示命令块；结果缺失时仍保留命令，方便流式阶段观察正在运行的命令。
 */
function buildCommandRuns(cards: ProcessCardItem[]): CommandProcessRun[] {
  const runs: CommandProcessRun[] = [];
  let pendingRunIndex: number | null = null;
  for (const card of cards) {
    if (card.type === 'tool_call') {
      const command = extractCommandFromCard(card) || card.summary || card.displayName || card.title || '命令';
      runs.push({
        command,
        output: '',
        status: card.status,
      });
      pendingRunIndex = runs.length - 1;
      continue;
    }
    if (card.type === 'tool_result') {
      const output = extractOutputFromCard(card) || card.summary;
      const targetIndex = pendingRunIndex ?? runs.length - 1;
      if (targetIndex >= 0 && runs[targetIndex]) {
        runs[targetIndex] = {
          ...runs[targetIndex],
          output,
          status: card.status,
        };
      } else {
        runs.push({
          command: extractCommandFromCard(card) || card.displayName || card.title || '命令',
          output,
          status: card.status,
        });
      }
      pendingRunIndex = null;
    }
  }
  return runs.filter((run) => run.command.trim().length > 0 || run.output.trim().length > 0);
}

/**
 * 从过程卡片参数里提取 shell 命令，兼容 JSON 参数和纯文本参数。
 */
function extractCommandFromCard(card: ProcessCardItem): string {
  const parameterDetails = getProcessCardDetailContent(card, '参数');
  for (const content of parameterDetails) {
    const command = extractShellCommand(content);
    if (command) {
      return command;
    }
  }
  return '';
}

/**
 * 从过程卡片结果明细提取命令输出，避免把摘要当成完整输出。
 */
function extractOutputFromCard(card: ProcessCardItem): string {
  return getProcessCardDetailContent(card, '结果')[0] ?? getProcessCardDetailContent(card, '异常')[0] ?? '';
}

/**
 * 获取指定标签的明细内容，标签按包含匹配以兼容“参数/输入参数”等历史文案。
 */
function getProcessCardDetailContent(card: ProcessCardItem, label: string): string[] {
  return (card.details ?? [])
    .filter((detail) => detail.label.trim().includes(label))
    .map((detail) => detail.content.trim())
    .filter(Boolean);
}

/**
 * 从 JSON 或纯文本中读取命令字段；解析失败时返回原始文本，避免模型参数格式波动影响展示。
 */
function extractShellCommand(content: string): string {
  const trimmed = content.trim();
  if (!trimmed) {
    return '';
  }
  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (isObjectRecord(parsed)) {
      const commandValue = parsed.command ?? parsed.cmd ?? parsed.script;
      if (typeof commandValue === 'string' && commandValue.trim()) {
        return commandValue.trim();
      }
      return '';
    }
  } catch {
    // 非 JSON 参数常见于历史回放，直接把内容作为命令文本展示。
  }
  return trimmed;
}

/**
 * 只在参数明确像命令时用于自动识别，避免连续普通工具被误聚合成 shell 命令。
 */
function hasExplicitShellCommandParameter(content: string): boolean {
  const trimmed = content.trim();
  if (!trimmed) {
    return false;
  }
  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (!isObjectRecord(parsed)) {
      return false;
    }
    return ['command', 'cmd', 'script'].some((key) => {
      const value = parsed[key];
      return typeof value === 'string' && value.trim().length > 0;
    });
  } catch {
    return looksLikeShellCommandText(trimmed);
  }
}

/**
 * 兼容历史纯文本命令参数；只匹配常见 shell 前缀，不把普通搜索词当命令。
 */
function looksLikeShellCommandText(value: string): boolean {
  return /^(?:\$\s*)?(?:pwd|dir|ls|rg|grep|git|npm|pnpm|yarn|mvn|gradle|python|node|cd\b|cat\b|type\b|get-[a-z]+|set-[a-z]+|select-[a-z]+)/i.test(value.trim());
}

/**
 * 判断未知值是否为普通对象，避免 JSON 数组或 null 被误当作命令参数对象。
 */
function isObjectRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * 解析单段搜索结果文本；优先按竖线分隔读取标题、站点和链接，再兜底解析独立链接。
 */
function parseSearchResultDetailContent(content: string): SearchSourceDisplayItem[] {
  const blocks = content
    .split(/\n{2,}/)
    .map((block) => block.trim())
    .filter(Boolean);
  return blocks.flatMap((block, blockIndex) => {
    const blockItem = parseSearchResultBlock(block, blockIndex);
    if (blockItem) {
      return [blockItem];
    }
    return block
      .split(/\n{1,}/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line, lineIndex) => parseSearchResultLine(line, blockIndex * 100 + lineIndex))
      .filter((item): item is SearchSourceDisplayItem => item != null);
  });
}

/**
 * 解析实时搜索事件常见的三行块：标题、站点、链接。
 */
function parseSearchResultBlock(block: string, index: number): SearchSourceDisplayItem | null {
  const lines = block
    .split(/\n{1,}/)
    .map((line) => line.trim())
    .filter(Boolean);
  if (lines.length < 2 || lines.some((line) => line.includes('|'))) {
    return null;
  }
  const urlLineIndex = lines.findIndex((line) => looksLikeHttpUrl(line));
  if (urlLineIndex < 0) {
    return null;
  }
  const url = lines[urlLineIndex].replace(/[，。；;,.]+$/, '');
  const nonUrlLines = lines.filter((_, lineIndex) => lineIndex !== urlLineIndex);
  return {
    id: `process-search-${index}`,
    title: nonUrlLines[0] ?? url,
    siteName: nonUrlLines[1],
    url,
  };
}

/**
 * 将一行搜索结果拆成展示字段，避免把长 URL 留在纯文本里撑开布局。
 */
function parseSearchResultLine(line: string, index: number): SearchSourceDisplayItem | null {
  const pipeParts = line
    .split('|')
    .map((part) => part.trim())
    .filter(Boolean);
  if (pipeParts.length >= 2) {
    const urlPartIndex = pipeParts.findIndex((part) => looksLikeHttpUrl(part));
    const url = urlPartIndex >= 0 ? pipeParts[urlPartIndex] : undefined;
    const nonUrlParts = pipeParts.filter((_, partIndex) => partIndex !== urlPartIndex);
    return {
      id: `process-search-${index}`,
      title: nonUrlParts[0] ?? url ?? line,
      siteName: nonUrlParts[1],
      url,
    };
  }

  const urlMatch = line.match(/https?:\/\/\S+/);
  if (urlMatch) {
    const url = urlMatch[0].replace(/[，。；;,.]+$/, '');
    const title = line.replace(urlMatch[0], '').replace(/[|｜\-–—]+$/, '').trim();
    return {
      id: `process-search-${index}`,
      title: title || url,
      url,
    };
  }

  if (line.length < 4) {
    return null;
  }
  return {
    id: `process-search-${index}`,
    title: line,
  };
}

/**
 * 按 URL 或标题去重，避免多个搜索事件合并后重复展示同一来源。
 */
function dedupeSearchSourceItems(items: SearchSourceDisplayItem[]): SearchSourceDisplayItem[] {
  const seenKeys = new Set<string>();
  const dedupedItems: SearchSourceDisplayItem[] = [];
  for (const item of items) {
    const key = (item.url || item.title).trim().toLowerCase();
    if (!key || seenKeys.has(key)) {
      continue;
    }
    seenKeys.add(key);
    dedupedItems.push({
      ...item,
      id: item.id || `process-search-${dedupedItems.length}`,
    });
  }
  return dedupedItems;
}

/**
 * 判断文本是否像 HTTP 链接，用于从搜索结果分隔片段中定位 URL 字段。
 */
function looksLikeHttpUrl(value: string): boolean {
  return /^https?:\/\/\S+$/i.test(value.trim());
}


