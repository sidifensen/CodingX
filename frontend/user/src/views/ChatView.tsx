import React from 'react';
import { motion } from 'motion/react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  ArrowUp,
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
  PanelRightClose,
  PanelRightOpen,
  Paperclip,
  FolderOpen,
  Monitor,
  Search,
  ThumbsDown,
  ThumbsUp,
  WandSparkles,
} from 'lucide-react';
import { AuthStorage } from '../utils/authStorage';
import { ChatApi } from './chat/chatApi';
import {
  ChatAttachmentItem,
  ChatWorkspaceController,
  McpItem,
  McpCallItem,
  PendingAttachmentItem,
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
    pickRepositoryDirectory,
    renameDialog,
    deleteDialog,
    renameConversation,
    deleteConversation,
    setActiveWorkspacePath,
  } = workspace;
  const safeAvailableExperts = availableExperts ?? [];
  const safeCurrentExperts = currentExperts ?? [];
  const latestMessageAnchorRef = React.useRef<HTMLDivElement | null>(null);
  const chatScrollRegionRef = React.useRef<HTMLDivElement | null>(null);
  const shouldFollowLatestMessageRef = React.useRef(true);
  // 步骤：右侧工作区默认折叠，是否展开完全由用户手动控制。
  const [isWorkspacePanelCollapsed, setIsWorkspacePanelCollapsed] = React.useState(true);
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
  // 步骤：首页只保留欢迎内容和输入框，右侧执行回放仅在真实会话上下文中展示。
  const showWorkspacePanel = !showLandingState;
  // 业务约束：底部运行环境/工作空间切换仅在新建对话页显示，历史会话页不再重复渲染。
  const showRuntimeWorkspaceSwitcher = showLandingState;
  // 业务约束：云端环境下不展示“云端工作空间”下拉，避免出现无意义的同名选项。
  const showWorkspaceDropdownInSwitcher = showRuntimeWorkspaceSwitcher && activeRuntimeTarget === 'local';
  // 步骤：右侧栏保留挂载以支持宽度过渡动画，面板内容在收起后不再渲染。
  const isWorkspacePanelVisible = showWorkspacePanel && !isWorkspacePanelCollapsed;

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
          {showWorkspacePanel ? (
            <button
              type="button"
              aria-label={isWorkspacePanelCollapsed ? '展开右侧工作区' : '折叠右侧工作区'}
              onClick={() => setIsWorkspacePanelCollapsed((current) => !current)}
              className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-xl border border-border bg-surface/92 text-foreground shadow-[0_14px_30px_rgba(0,0,0,0.18)] backdrop-blur"
            >
              {isWorkspacePanelCollapsed ? (
                <PanelRightOpen size={18} />
              ) : (
                <PanelRightClose size={18} />
              )}
            </button>
          ) : (
            <div className="w-10" />
          )}
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
                          {message.thinkingContent ? (
                            <ThinkingPanel
                              messageId={message.id}
                              content={message.thinkingContent}
                            />
                          ) : null}
                          {message.mcpCalls && message.mcpCalls.length > 0 ? (
                            <McpCallPanel
                              messageId={message.id}
                              calls={message.mcpCalls}
                              messageStatus={message.status}
                            />
                          ) : null}
                          {message.searchProgress ? (
                            <SearchProgressPanel
                              messageId={message.id}
                              progress={message.searchProgress}
                            />
                          ) : null}
                          {message.attachments && message.attachments.length > 0 ? (
                            <MessageAttachmentList
                              attachments={message.attachments}
                              onPreviewImage={(src, alt) => setPreviewAttachment({ src, alt })}
                            />
                          ) : null}
                          <MarkdownMessage content={messageContent} />
                          <AssistantMessageActions
                            messageId={message.id}
                            conversationId={message.conversationId}
                            content={messageContent}
                            userVote={message.userVote}
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

      {showWorkspacePanel ? (
        <aside
          className={`hidden overflow-hidden border-l bg-surface/96 [contain:layout_paint] will-change-[width,opacity] transition-[width,opacity,border-color] duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] md:flex md:flex-col ${
            isWorkspacePanelVisible
              ? 'w-[340px] border-border opacity-100'
              : 'w-0 border-transparent opacity-0 pointer-events-none'
          }`}
          aria-hidden={!isWorkspacePanelVisible}
        >
          <div
            className={`flex h-full flex-col transition-[opacity,transform] duration-200 ${
              isWorkspacePanelVisible ? 'translate-x-0 opacity-100' : 'translate-x-4 opacity-0'
            }`}
          >
            <div className="border-b border-border px-5 py-5">
              <p className="font-mono text-[11px] uppercase tracking-[0.3em] text-muted">
                Workspace
              </p>
              <h3 className="mt-2 text-lg font-semibold text-foreground">执行回放</h3>
            </div>
            <div className="workspace-replay-scroll-region flex-1 overflow-y-auto px-4 py-4">
              <Panel title="执行步骤" icon={CheckCircle2}>
                {executionSteps.length ? (
                  executionSteps.map((step) => (
                    <div
                      key={step.id}
                      className="rounded-2xl border border-border bg-surface-container px-4 py-3"
                    >
                      <div className="text-sm font-medium text-foreground">{step.stepTitle}</div>
                      {step.content ? (
                        <div className="mt-3 text-sm leading-6 text-muted">{step.content}</div>
                      ) : null}
                    </div>
                  ))
                ) : (
                  <EmptyBlock text="当前会话暂无步骤回放" />
                )}
              </Panel>

              <Panel title="参考来源" icon={Globe2}>
                {references.length ? (
                  references.map((reference) => (
                    <a
                      key={reference.id}
                      href={reference.url}
                      target="_blank"
                      rel="noreferrer"
                      className="block rounded-2xl border border-border bg-surface-container px-4 py-3 transition-colors hover:border-border-active"
                    >
                      <div className="text-sm font-medium text-foreground">{reference.title}</div>
                      {/* 右侧来源回放只保留标题和地址，避免长摘要挤占面板空间。 */}
                      <div className="mt-2 break-all text-[12px] text-muted">
                        {reference.url || '地址未知'}
                      </div>
                    </a>
                  ))
                ) : (
                  <EmptyBlock text="当前会话暂无来源回放" />
                )}
              </Panel>

              <Panel title="生成产物" icon={FileText}>
                {artifacts.length ? (
                  artifacts.map((artifact) => (
                    <div
                      key={artifact.id}
                      className="rounded-2xl border border-border bg-surface-container px-4 py-3"
                    >
                      <div className="text-sm font-medium text-foreground">{artifact.name}</div>
                      {artifact.contentPreview ? (
                        <div className="mt-3 text-sm leading-6 text-muted">
                          {artifact.contentPreview}
                        </div>
                      ) : null}
                    </div>
                  ))
                ) : (
                  <EmptyBlock text="当前会话暂无产物回放" />
                )}
              </Panel>

              <Panel title="当前专家" icon={Brain}>
                {safeCurrentExperts.length ? (
                  safeCurrentExperts.map((expert) => (
                    <div
                      key={expert.expertCode}
                      className="rounded-2xl border border-border bg-surface-container px-4 py-3"
                    >
                      <div className="text-sm font-medium text-foreground">{expert.displayName}</div>
                      <div className="mt-2 text-[12px] uppercase tracking-[0.2em] text-muted">
                        {expert.category || expert.expertCode}
                      </div>
                      {expert.description ? (
                        <div className="mt-3 text-sm leading-6 text-muted">{expert.description}</div>
                      ) : null}
                    </div>
                  ))
                ) : (
                  <EmptyBlock text="当前会话暂无专家回放" />
                )}
              </Panel>

            </div>
          </div>
        </aside>
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
              await renameConversation(renameDialog.conversationId, nextTitle);
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
              await deleteConversation(deleteDialog.conversationId);
            }
            deleteDialog.close();
          }}
        />
      ) : null}
    </motion.div>
  );
}

/**
 * 渲染助手“思考过程”折叠面板，默认展开并通过过渡类提供展开/收起动画。
 */
function ThinkingPanel({ messageId, content }: { messageId: string; content: string }) {
  // 步骤：思考面板在首次渲染时默认展开，减少用户额外点击成本。
  const [isExpanded, setIsExpanded] = React.useState(true);
  const contentId = `thinking-content-panel-${messageId}`;

  return (
    <section
      data-testid={`thinking-panel-${messageId}`}
      // 步骤：折叠态改为自适应宽度胶囊，且展开/折叠保持同一内边距，避免标题产生位移抖动。
      className={`mb-3 rounded-2xl border border-border bg-surface-container text-sm text-muted transition-[width,padding] duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] ${
        isExpanded ? 'w-full px-4 py-3' : 'w-fit px-4 py-3'
      }`}
    >
      <div
        data-testid={`thinking-summary-${messageId}`}
        className="flex items-center gap-3 font-medium text-foreground"
      >
        <span>思考过程</span>
        <button
          type="button"
          data-testid={`thinking-toggle-button-${messageId}`}
          aria-expanded={isExpanded}
          aria-controls={contentId}
          aria-label={isExpanded ? '折叠思考过程' : '展开思考过程'}
          onClick={() => setIsExpanded((current) => !current)}
          className="flex h-7 w-7 items-center justify-center rounded-full border border-border bg-surface text-muted transition-colors hover:text-foreground"
        >
          <span
            data-testid={`thinking-toggle-icon-${messageId}`}
            aria-hidden="true"
            className="flex h-7 w-7 items-center justify-center"
          >
            <ChevronDown
              size={15}
              className={`transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`}
            />
          </span>
        </button>
      </div>
      <div
        id={contentId}
        data-testid={`thinking-content-${messageId}`}
        // 步骤：内容区保持挂载，通过高度/透明度过渡实现展开与折叠动画，避免闪烁和重排跳变。
        className={`overflow-hidden transition-all duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] ${
          isExpanded
            ? 'mt-3 max-h-[640px] max-w-full translate-y-0 opacity-100'
            : 'mt-0 max-h-0 max-w-0 -translate-y-1 opacity-0 pointer-events-none'
        }`}
      >
        <div className="whitespace-pre-wrap leading-6">{content}</div>
      </div>
    </section>
  );
}

/**
 * 渲染助手消息中的 MCP 调用折叠面板，展示工具、入参与返回片段。
 */
function McpCallPanel({
  messageId,
  calls,
  messageStatus,
}: {
  messageId: string;
  calls: McpCallItem[];
  messageStatus: string;
}) {
  // 步骤：调用状态至少保留短暂可见时长，避免“调用中”在完成瞬间闪烁造成不可感知。
  const MCP_CALL_RUNNING_MIN_VISIBLE_MS = 1000;
  const [isExpanded, setIsExpanded] = React.useState(true);
  const hasRunningCall = calls.some((call) => call.status === 'running' || call.phase === 'start');
  const hasErrorCall = calls.some((call) => call.status === 'error' || call.phase === 'error');
  const targetStatus: 'running' | 'completed' | 'error' =
    hasRunningCall || messageStatus === 'streaming'
      ? 'running'
      : hasErrorCall
        ? 'error'
        : 'completed';
  const [displayStatus, setDisplayStatus] = React.useState<'running' | 'completed' | 'error'>(
    targetStatus,
  );
  const runningStartedAtRef = React.useRef<number | null>(
    targetStatus === 'running' ? Date.now() : null,
  );
  const contentId = `mcp-call-content-panel-${messageId}`;
  const isRunning = displayStatus === 'running';
  const isError = displayStatus === 'error';

  React.useEffect(() => {
    // 步骤：基于消息流状态驱动调用徽标，并在完成后维持最小时长再切到“调用完成”。
    if (targetStatus === 'running') {
      if (runningStartedAtRef.current == null) {
        runningStartedAtRef.current = Date.now();
      }
      setDisplayStatus('running');
      return;
    }
    if (targetStatus === 'error') {
      runningStartedAtRef.current = null;
      setDisplayStatus('error');
      return;
    }
    const runningStartedAt = runningStartedAtRef.current;
    if (runningStartedAt == null) {
      setDisplayStatus('completed');
      return;
    }
    const elapsed = Date.now() - runningStartedAt;
    const remaining = Math.max(0, MCP_CALL_RUNNING_MIN_VISIBLE_MS - elapsed);
    if (remaining === 0) {
      runningStartedAtRef.current = null;
      setDisplayStatus('completed');
      return;
    }
    const timer = window.setTimeout(() => {
      runningStartedAtRef.current = null;
      setDisplayStatus('completed');
    }, remaining);
    return () => window.clearTimeout(timer);
  }, [targetStatus]);

  return (
    <section
      data-testid={`mcp-call-panel-${messageId}`}
      // 步骤：折叠态保持与展开态一致的内边距，避免标题在宽度过渡时产生视觉收缩。
      className={`mb-3 rounded-2xl border border-border bg-surface-container text-sm text-muted transition-[width,padding] duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] ${
        isExpanded ? 'w-full px-4 py-3' : 'w-fit px-4 py-3'
      }`}
    >
      <div className="flex items-center gap-3 font-medium text-foreground">
        <span className="shrink-0 whitespace-nowrap">MCP 调用</span>
        <span className="shrink-0 rounded-full border border-border bg-surface px-2 py-0.5 text-[11px] text-muted">
          {calls.length}
        </span>
        <span
          data-testid={`mcp-call-status-${messageId}`}
          className={`shrink-0 rounded-full border px-2 py-0.5 text-[11px] ${
            isRunning
              ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300'
              : isError
                ? 'border-red-500/30 bg-red-500/10 text-red-300'
                : 'border-border bg-surface text-muted'
          }`}
        >
          {isRunning ? '调用中' : isError ? '调用异常' : '调用完成'}
        </span>
        <button
          type="button"
          data-testid={`mcp-call-toggle-button-${messageId}`}
          aria-expanded={isExpanded}
          aria-controls={contentId}
          aria-label={isExpanded ? '折叠MCP调用详情' : '展开MCP调用详情'}
          onClick={() => setIsExpanded((current) => !current)}
          className="shrink-0 flex h-7 w-7 items-center justify-center rounded-full border border-border bg-surface text-muted transition-colors hover:text-foreground"
        >
          <ChevronDown
            size={15}
            className={`transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`}
          />
        </button>
      </div>
      <div
        id={contentId}
        className={`overflow-hidden transition-[margin,max-height,opacity,transform] duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] ${
          // 业务意图：MCP 面板展开时完整展示结果，避免固定高度导致长结果被截断。
          // 关键约束：max-width 不参与过渡，收起时立即置零，避免容器宽度在中间态被内容宽度“卡住”。
          isExpanded
            ? 'mt-3 max-h-none max-w-full translate-y-0 opacity-100'
            : 'mt-0 max-h-0 max-w-0 -translate-y-1 opacity-0 pointer-events-none'
        }`}
      >
        <div className="space-y-3">
          {calls.map((call, index) => (
            <article
              key={`${call.toolId}-${index}`}
              data-testid={`mcp-call-item-${messageId}-${index}`}
              className="rounded-xl border border-border bg-surface px-3 py-2.5"
            >
              {/*
                业务意图：优先展示 MCP 真实参数与原始结果，避免与用户提问/助手回答内容重复。
              */}
              {(() => {
                const parameterText = formatStructuredPayload(call.params ?? call.input);
                const rawResultText = formatStructuredPayload(call.rawResult ?? call.content);
                const metadataText = formatStructuredPayload(call.resultMetadata ?? call.metadata);
                const progressText = formatStructuredPayload(call.progressText ?? call.progressDetail);
                return (
                  <>
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0">
                        <div className="truncate text-sm font-medium text-foreground">
                          {call.displayName || call.toolId}
                        </div>
                        <div className="mt-1 font-mono text-[11px] text-muted">/{call.toolId}</div>
                      </div>
                    </div>
                    {parameterText ? (
                      <div className="mt-2 rounded-lg bg-surface-container px-2.5 py-2">
                        <div className="text-[11px] uppercase tracking-[0.16em] text-muted">参数</div>
                        <pre className="mt-1 whitespace-pre-wrap text-xs leading-5 text-foreground">
                          {parameterText}
                        </pre>
                      </div>
                    ) : null}
                    {progressText && call.status === 'running' ? (
                      <div className="mt-2 rounded-lg bg-surface-container px-2.5 py-2">
                        <div className="text-[11px] uppercase tracking-[0.16em] text-muted">
                          进度
                        </div>
                        <pre className="mt-1 whitespace-pre-wrap text-xs leading-5 text-foreground">
                          {progressText}
                        </pre>
                      </div>
                    ) : null}
                    {rawResultText ? (
                      <div className="mt-2 rounded-lg bg-surface-container px-2.5 py-2">
                        <div className="text-[11px] uppercase tracking-[0.16em] text-muted">
                          原始结果
                        </div>
                        <pre className="mt-1 whitespace-pre-wrap text-xs leading-5 text-foreground">
                          {rawResultText}
                        </pre>
                      </div>
                    ) : null}
                    {metadataText ? (
                      <div className="mt-2 rounded-lg bg-surface-container px-2.5 py-2">
                        <div className="text-[11px] uppercase tracking-[0.16em] text-muted">
                          元数据
                        </div>
                        <pre className="mt-1 whitespace-pre-wrap text-xs leading-5 text-foreground">
                          {metadataText}
                        </pre>
                      </div>
                    ) : null}
                  </>
                );
              })()}
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

/**
 * 把结构化对象格式化为可读 JSON，字符串保持原样，供 MCP 参数/结果展示复用。
 * @param value 原始字段值。
 * @returns 可展示文本。
 */
function formatStructuredPayload(value: unknown): string {
  if (value == null) {
    return '';
  }
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
 * 在助手消息中展示联网检索进度，避免长耗时检索期间用户无反馈。
 */
function SearchProgressPanel({
  messageId,
  progress,
}: {
  messageId: string;
  progress: NonNullable<ChatWorkspaceController['messages'][number]['searchProgress']>;
}) {
  const [isExpanded, setIsExpanded] = React.useState(true);
  const contentId = `search-progress-content-panel-${messageId}`;
  const normalizedStatus =
    progress.status === 'running'
      ? 'running'
      : progress.status === 'completed'
        ? 'completed'
        : progress.status === 'cancelled'
          ? 'cancelled'
          : 'error';
  const statusLabel =
    normalizedStatus === 'running'
      ? '搜索中'
      : normalizedStatus === 'completed'
        ? '搜索完成'
        : normalizedStatus === 'cancelled'
          ? '已停止'
          : '搜索异常';
  const countLabel =
    normalizedStatus === 'running'
      ? `已检索 ${progress.items.length} 个网站`
      : `共检索 ${progress.items.length} 个网站`;

  return (
    <section
      data-testid={`search-progress-panel-${messageId}`}
      className={`mb-3 rounded-2xl border border-border bg-surface-container text-sm text-muted transition-[width,padding] duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] ${
        isExpanded ? 'w-full px-4 py-3' : 'w-fit px-3 py-2'
      }`}
    >
      <div className="flex flex-wrap items-center gap-2.5 font-medium text-foreground">
        <span>联网搜索</span>
        <span
          data-testid={`search-progress-count-${messageId}`}
          className="rounded-full border border-border bg-surface px-2 py-0.5 text-[11px] text-muted"
        >
          {progress.items.length}
        </span>
        <span
          data-testid={`search-progress-status-${messageId}`}
          className={`rounded-full border px-2 py-0.5 text-[11px] ${
            normalizedStatus === 'running'
              ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300'
              : normalizedStatus === 'error'
                ? 'border-red-500/30 bg-red-500/10 text-red-300'
                : normalizedStatus === 'cancelled'
                  ? 'border-amber-500/30 bg-amber-500/10 text-amber-300'
                  : 'border-border bg-surface text-muted'
          }`}
        >
          {statusLabel}
        </span>
        <span className="text-xs text-muted">{countLabel}</span>
        <button
          type="button"
          data-testid={`search-progress-toggle-button-${messageId}`}
          aria-expanded={isExpanded}
          aria-controls={contentId}
          aria-label={isExpanded ? '折叠联网搜索详情' : '展开联网搜索详情'}
          onClick={() => setIsExpanded((current) => !current)}
          className="ml-auto flex h-7 w-7 items-center justify-center rounded-full border border-border bg-surface text-muted transition-colors hover:text-foreground"
        >
          <ChevronDown
            size={15}
            className={`transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`}
          />
        </button>
      </div>
      <div
        id={contentId}
        className={`overflow-hidden transition-all duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] ${
          // 业务意图：搜索面板展开时按内容完整展示，减少二级滚动带来的阅读割裂。
          isExpanded
            ? 'mt-3 max-h-none max-w-full translate-y-0 opacity-100'
            : 'mt-0 max-h-0 max-w-0 -translate-y-1 opacity-0 pointer-events-none'
        }`}
      >
        {progress.items.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border bg-surface px-3 py-2 text-xs text-muted">
            正在等待检索结果...
          </div>
        ) : (
          <ol className="space-y-2">
            {progress.items.map((item, index) => (
              <li
                key={item.id}
                data-testid={`search-progress-item-${messageId}-${index}`}
                className="rounded-xl border border-border bg-surface px-3 py-2"
              >
                <div className="flex items-start gap-2 text-xs leading-5 text-muted">
                  <span className="inline-flex min-w-[24px] justify-center rounded-full border border-border bg-surface-container px-1 py-0.5 font-mono text-[11px]">
                    {index + 1}
                  </span>
                  <div className="min-w-0">
                    <div className="truncate text-sm text-foreground">
                      {item.title || item.siteName || '未命名网页'}
                    </div>
                    <div className="mt-1 flex flex-wrap gap-x-2 gap-y-1 text-[11px] text-muted">
                      {item.siteName ? <span>{item.siteName}</span> : null}
                      {item.url ? <span className="truncate">{item.url}</span> : null}
                    </div>
                  </div>
                </div>
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  );
}

/**
 * 使用 Markdown 渲染助手消息，保证标题、列表、代码块等富文本结构按预期展示。
 */
function MarkdownMessage({ content }: { content: string }) {
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
                  className={`block overflow-x-auto rounded-2xl border border-border bg-surface-container px-4 py-3 font-mono text-[13px] leading-6 ${className}`}
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
          table: ({ node: _node, ...props }) => (
            <div className="mb-4 overflow-x-auto rounded-2xl border border-border">
              <table className="min-w-full border-collapse text-left text-sm" {...props} />
            </div>
          ),
          thead: ({ node: _node, ...props }) => (
            <thead className="bg-surface-container" {...props} />
          ),
          th: ({ node: _node, ...props }) => (
            <th className="border-b border-border px-3 py-2 font-semibold" {...props} />
          ),
          td: ({ node: _node, ...props }) => (
            <td className="border-b border-border px-3 py-2 align-top last:border-b-0" {...props} />
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

type CopyMode = 'plain' | 'markdown';
type MessageReaction = 'up' | 'down' | null;

/**
 * 渲染助手消息底部操作栏，统一提供复制、复制 Markdown 与点赞反馈入口。
 */
function AssistantMessageActions({
  messageId,
  conversationId,
  content,
  userVote,
}: {
  messageId: string;
  conversationId: string;
  content: string;
  userVote?: number | null;
}) {
  const [isMenuOpen, setIsMenuOpen] = React.useState(false);
  const [copiedMode, setCopiedMode] = React.useState<CopyMode | null>(null);
  // 业务约束：初始化时从 userVote 恢复已投票状态，保证刷新后仍显示之前的投票结果。
  const [reaction, setReaction] = React.useState<MessageReaction>(
    userVote === 1 ? 'up' : userVote === -1 ? 'down' : null,
  );
  const [reactionError, setReactionError] = React.useState('');
  const menuContainerRef = React.useRef<HTMLDivElement | null>(null);

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
    const token = AuthStorage.getSession()?.token ?? null;
    if (!token) {
      return;
    }
    const previousReaction = reaction;
    setReaction(nextReaction);
    setReactionError('');
    try {
      await ChatApi.submitMessageFeedback(token, messageId, {
        conversationId,
        vote: nextReaction === 'up' ? 1 : -1,
      });
    } catch (error) {
      setReaction(previousReaction);
      setReactionError(error instanceof Error ? error.message : '反馈提交失败');
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
        data-testid={`thumbs-up-${messageId}`}
        aria-label="点赞"
        aria-pressed={reaction === 'up'}
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


