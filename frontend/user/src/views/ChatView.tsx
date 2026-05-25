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
  Share2,
  ThumbsDown,
  ThumbsUp,
  RotateCcw,
  WandSparkles,
} from 'lucide-react';
import { AuthStorage } from '../utils/authStorage';
import { ChatApi } from './chat/chatApi';
import {
  ChatAttachmentItem,
  ChatWorkspaceController,
  MessageSearchProgress,
  MessageSearchProgressItem,
  McpItem,
  PendingAttachmentItem,
  ProcessCardItem,
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
    regenerateConversation,
    pickRepositoryDirectory,
    renameDialog,
    deleteDialog,
    renameConversation,
    deleteConversation,
    setActiveWorkspacePath,
  } = workspace;
  const safeAvailableExperts = availableExperts ?? [];
  const safeCurrentExperts = currentExperts ?? [];
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
        workspaceLabel:
          workspaceLabel || '历史记录',
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
                    className={`flex ${isAssistant ? 'justify-start' : 'justify-end'}`}
                  >
                    <div
                      className={`min-w-0 max-w-3xl px-1 py-1 ${
                        isAssistant
                          ? 'text-foreground'
                          : 'rounded-[20px] bg-background px-4 py-2 text-foreground'
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
                          {message.searchProgress?.items?.length ? (
                            <SearchProgressPanel
                              messageId={message.id}
                              progress={message.searchProgress}
                            />
                          ) : null}
                          <MarkdownMessage content={messageContent} messageId={message.id} />
                          <AssistantMessageActions
                            messageId={message.id}
                            conversationId={message.conversationId}
                            content={messageContent}
                            isLatestAssistantMessage={message.id === latestAssistantMessageId}
                            userVote={message.userVote}
                            onShareConversation={shareConversation}
                            onRegenerateConversation={regenerateConversation}
                          />
                        </>
                      ) : (
                        <>
                          {message.skillCodes && message.skillCodes.length > 0 ? (
                            <MessageSkillChips
                              messageId={message.id}
                              skillCodes={message.skillCodes}
                              skillNameMap={availableSkillNameMap}
                            />
                          ) : null}
                          {message.attachments && message.attachments.length > 0 ? (
                            <MessageAttachmentList
                              attachments={message.attachments}
                              onPreviewImage={(src, alt) => setPreviewAttachment({ src, alt })}
                            />
                          ) : null}
                          <div className="whitespace-pre-wrap text-sm leading-6">
                            {messageContent}
                          </div>
                        </>
                      )}
                      {message.errorMessage ? (
                        <div className="mt-3 rounded-2xl border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs text-red-300">
                          {message.errorMessage}
                        </div>
                      ) : null}
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

/**
 * 将助手消息中的搜索来源渲染成可折叠列表，避免把标题、站点名和链接压缩成一行纯文本。
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
  item: MessageSearchProgressItem;
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
function resolveSearchSourceMeta(item: MessageSearchProgressItem): {
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

/**
 * 渲染助手消息底部操作栏，统一提供复制、分享、重新生成与点赞反馈入口。
 */
function AssistantMessageActions({
  messageId,
  conversationId,
  content,
  isLatestAssistantMessage,
  userVote,
  onShareConversation,
  onRegenerateConversation,
}: {
  messageId: string;
  conversationId: string;
  content: string;
  isLatestAssistantMessage: boolean;
  userVote?: number | null;
  onShareConversation: (conversationId: string) => Promise<string>;
  onRegenerateConversation: (conversationId: string) => Promise<void>;
}) {
  const [isMenuOpen, setIsMenuOpen] = React.useState(false);
  const [copiedMode, setCopiedMode] = React.useState<CopyMode | null>(null);
  const [shareState, setShareState] = React.useState<'idle' | 'copying' | 'copied' | 'error'>('idle');
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
  const canRegenerateConversation = canOperateOnConversation && isLatestAssistantMessage && PERSISTED_MESSAGE_ID_PATTERN.test(messageId);

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
   * 生成分享链接并复制到剪贴板，成功后给出短暂状态提示。
   * 这里不直接暴露后端返回的相对路径，避免用户复制后无法在当前站点打开。
   */
  const shareMessage = async () => {
    if (shareState === 'copying' || !canOperateOnConversation) {
      return;
    }
    setShareState('copying');
    try {
      const shareUrl = await onShareConversation(normalizedConversationId);
      if (!shareUrl) {
        setShareState('error');
        return;
      }
      await navigator.clipboard.writeText(shareUrl);
      setShareState('copied');
      window.setTimeout(() => {
        setShareState((current) => (current === 'copied' ? 'idle' : current));
      }, 1400);
    } catch {
      setShareState('error');
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
      await onRegenerateConversation(normalizedConversationId);
    } catch {
      setRegenerateError('重新生成失败');
    } finally {
      setIsRegenerating(false);
    }
  };

  return (
    <div className="chat-message-actions mt-3 flex items-center gap-1 text-muted">
      <div
        className="chat-message-action-button-group"
        data-testid={`copy-action-group-${messageId}`}
      >
        {/* 复制主按钮与更多菜单使用连续按钮组，去掉视觉分隔线并保持紧凑布局。 */}
        <button
          type="button"
          data-testid={`copy-message-${messageId}`}
          aria-label="复制消息"
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
                data-testid={`copy-plain-${messageId}`}
                onClick={() => void copyContent('plain')}
                className="chat-copy-menu-item"
              >
                复制
              </button>
            </div>
          ) : null}
        </div>
      </div>
      <button
        type="button"
        data-testid={`share-message-${messageId}`}
        aria-label="分享消息"
        disabled={!canOperateOnConversation || shareState === 'copying'}
        onClick={() => void shareMessage()}
        className="chat-message-action-button disabled:cursor-not-allowed disabled:opacity-60"
      >
        <Share2 size={15} />
      </button>
      <button
        type="button"
        data-testid={`regenerate-message-${messageId}`}
        aria-label="重新生成"
        disabled={!canRegenerateConversation || isRegenerating}
        onClick={() => void regenerateMessage()}
        className="chat-message-action-button disabled:cursor-not-allowed disabled:opacity-60"
      >
        <RotateCcw size={15} className={isRegenerating ? 'animate-spin' : ''} />
      </button>
      <button
        type="button"
        data-testid={`thumbs-up-${messageId}`}
        aria-label="点赞"
        aria-pressed={reaction === 'up'}
        disabled={!canSubmitReaction}
        onClick={() => void submitReaction('up')}
        className={`chat-message-action-button ${reaction === 'up' ? 'chat-message-action-button-active' : ''}`}
      >
        <ThumbsUp size={15} />
      </button>
      <button
        type="button"
        data-testid={`thumbs-down-${messageId}`}
        aria-label="倒赞"
        aria-pressed={reaction === 'down'}
        disabled={!canSubmitReaction}
        onClick={() => void submitReaction('down')}
        className={`chat-message-action-button ${reaction === 'down' ? 'chat-message-action-button-active' : ''}`}
      >
        <ThumbsDown size={15} />
      </button>
      {copiedMode ? (
        <span className="ml-2 text-[11px] text-muted">
          {copiedMode === 'markdown' ? '已复制 Markdown' : '已复制'}
        </span>
      ) : null}
      {shareState === 'copied' ? <span className="ml-2 text-[11px] text-muted">分享链接已复制</span> : null}
      {shareState === 'error' ? <span className="ml-2 text-[11px] text-error">分享失败</span> : null}
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
 * 渲染 WorkBuddy 式消息内过程链路：文本过程直接内联，工具明细集中到一条轻量折叠行。
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
        if (segment.type === 'tools') {
          return (
            <ProcessToolGroup
              key={`tools-${index}-${segment.cards.map((card) => card.id).join('-')}`}
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
  | { type: 'tools'; cards: ProcessCardItem[] };

/**
 * 按真实过程顺序聚合消息内链路：连续工具节点合并为一个可展开组，分析文本保留原始前后位置。
 */
function groupProcessTraceSegments(cards: ProcessCardItem[]): ProcessTraceSegment[] {
  const segments: ProcessTraceSegment[] = [];
  let pendingToolCards: ProcessCardItem[] = [];
  const flushToolCards = () => {
    if (pendingToolCards.length === 0) {
      return;
    }
    segments.push({ type: 'tools', cards: pendingToolCards });
    pendingToolCards = [];
  };

  for (const card of cards) {
    if (card.type === 'tool_call' || card.type === 'tool_result') {
      pendingToolCards.push(card);
      continue;
    }
    if (card.type === 'synthesis' && isDisposableSynthesisTrace(card)) {
      continue;
    }
    if (card.summary.trim().length === 0) {
      continue;
    }
    flushToolCards();
    segments.push({ type: 'text', card });
  }
  flushToolCards();
  return segments;
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
 * 渲染一段过程文本。深度思考默认折叠，避免长 thinking 在正文前占据整屏空间。
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
 * 折叠展示模型真实 thinking：默认只露出入口，展开后才显示完整原文，避免伪摘要或截断。
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
  const [isExpanded, setIsExpanded] = React.useState(card.status === 'running');
  const contentId = `process-analysis-content-${messageId}-${card.id}`;
  const label = isFirstAnalysis ? '深度思考' : card.title || '深度思考';

  React.useEffect(() => {
    // 流式阶段保持展开，收口后自动折叠，让用户先看到完整思考过程，再回到精简视图。
    setIsExpanded(card.status === 'running');
  }, [card.status]);

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
          className="rounded-xl border border-border bg-surface-container px-4 py-3"
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
 * 将工具调用和工具结果合并为一条可展开过程行，避免主消息区出现多张厚重卡片。
 */
function ProcessToolGroup({
  messageId,
  cards,
}: {
  messageId: string;
  cards: ProcessCardItem[];
}) {
  const [isExpanded, setIsExpanded] = React.useState(false);
  const contentId = `process-tool-group-content-${messageId}`;
  const toolCallCount = cards.filter((card) => card.type === 'tool_call').length;
  const resultCount = cards.filter((card) => card.type === 'tool_result').length;
  const hasRunning = cards.some((card) => card.status === 'running');
  const hasError = cards.some((card) => card.status === 'error');

  return (
    <div data-testid={`process-tool-group-${messageId}`} className="space-y-2">
      <button
        type="button"
        data-testid={`process-tool-group-toggle-${messageId}`}
        aria-expanded={isExpanded}
        aria-controls={contentId}
        aria-label={isExpanded ? '折叠工具参数和结果' : '展开工具参数和结果'}
        onClick={() => setIsExpanded((current) => !current)}
        className="inline-flex max-w-full items-center gap-2 rounded-md px-0 py-1 text-xs text-muted transition-colors hover:text-foreground"
      >
        <ChevronDown
          size={14}
          className={`shrink-0 transition-transform duration-200 ${isExpanded ? 'rotate-180' : '-rotate-90'}`}
        />
        <span className="whitespace-nowrap">工具调用 {toolCallCount}</span>
        <span className="text-border">·</span>
        <span className="whitespace-nowrap">过程消息 {resultCount}</span>
        <span className="truncate">
          {hasError ? '调用异常' : hasRunning ? '进行中' : '已完成'}
        </span>
      </button>
      {isExpanded ? (
        <div id={contentId} className="space-y-2 pl-6">
          {cards.map((card) => (
            <ProcessToolRow key={card.id} card={card} showDetails={true} />
          ))}
        </div>
      ) : null}
    </div>
  );
}

/**
 * 竖向展示单条工具过程，参数和结果跟随各自工具行展开。
 */
function ProcessToolRow({ card, showDetails }: { card: ProcessCardItem; showDetails: boolean }) {
  const Icon = card.type === 'tool_call' ? Globe2 : CheckCircle2;

  return (
    <article className="space-y-2 border-l border-border pl-3">
      <div className="flex min-w-0 items-start gap-2 text-sm leading-6 text-foreground">
        <Icon size={15} className="mt-1 shrink-0 text-muted" />
        <div className="min-w-0 flex-1">
          <div className="whitespace-pre-wrap [overflow-wrap:anywhere]">
            {card.title}
            {card.summary ? <span className="text-muted"> {card.summary}</span> : null}
          </div>
        </div>
      </div>
      {showDetails && card.details && card.details.length > 0 ? (
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
    </article>
  );
}


