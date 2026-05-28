import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  Check,
  Download,
  File,
  SquareTerminal,
  Search,
  PlusCircle,
  Plus,
  PlugZap,
  Zap,
  Brain,
  Bot,
  MoreHorizontal,
  PencilLine,
  Trash2,
  ChevronDown,
  Cloud,
  Folder,
  History,
  LoaderCircle,
  Pin,
  PinOff,
  Share2,
  Layers3,
  X,
} from 'lucide-react';
import { ViewType } from '../App';
import { AuthSession } from '../types/auth';
import ProfileMenu from './sidebar/ProfileMenu';
import LoginEntry from './sidebar/LoginEntry';
import {
  ConversationActionContext,
  ConversationExportFormat,
  ConversationItem,
  WorkspaceConversationCreateContext,
  WorkspaceConversationGroup,
  WorkspaceConversationSelectionContext,
} from '../views/chat/types';

/**
 * 约定侧边栏会话列表每次展开的条目数，保证交互节奏稳定。
 */
const CONVERSATION_PAGE_SIZE = 5;
const CONVERSATION_MENU_WIDTH = 176;
const CONVERSATION_MENU_OFFSET = 8;
const CONVERSATION_MENU_SAFE_PADDING = 12;
const CONVERSATION_MENU_ESTIMATED_HEIGHT = 332;
const BATCH_SELECTION_LIMIT = 100;
const QIANWEN_EXPORT_FORMATS: Array<{ label: string; value: ConversationExportFormat }> = [
  { label: 'Word', value: 'word' },
  { label: 'PDF', value: 'pdf' },
  { label: 'TXT', value: 'txt' },
  { label: 'Json', value: 'json' },
];

/**
 * 定义 Sidebar 组件需要的输入属性。
 */
interface SidebarProps {
  activeView: ViewType;
  setActiveView: (view: ViewType) => void;
  isMobileMenuOpen: boolean;
  setIsMobileMenuOpen: (isOpen: boolean) => void;
  isDesktopCollapsed: boolean;
  isDarkMode: boolean;
  toggleTheme: () => void;
  authSession: AuthSession | null;
  isAuthSubmitting: boolean;
  onOpenLogin: () => void;
  onLogout: () => Promise<void>;
  conversations: ConversationItem[];
  activeConversationId: string | null;
  onSelectConversation: (
    conversationId: string,
    selectionContext: WorkspaceConversationSelectionContext,
  ) => Promise<void>;
  onStartNewConversation: (
    createContext?: WorkspaceConversationCreateContext,
  ) => Promise<void>;
  onRenameConversation: (
    conversationId: string,
    title: string,
    actionContext?: ConversationActionContext,
  ) => Promise<void>;
  onDeleteConversation: (
    conversationId: string,
    actionContext?: ConversationActionContext,
  ) => Promise<void>;
  onShareConversation: (
    conversationId: string,
    actionContext?: ConversationActionContext,
  ) => Promise<string>;
  onToggleConversationPin: (
    conversationId: string,
    actionContext: ConversationActionContext,
  ) => Promise<boolean>;
  onExportConversation: (
    conversationId: string,
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  onExportConversations: (
    conversationIds: string[],
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  onDeleteConversations: (
    conversationIds: string[],
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  workspaceGroups: WorkspaceConversationGroup[];
  activeWorkspacePartitionKey: string | null;
  workspaceLabel?: string;
  onSelectWorkspacePath: (workspacePath: string | null) => Promise<void>;
  onPickRepositoryDirectory?: () => Promise<void>;
}

/**
 * 渲染桌面端与移动端共用的侧边栏导航。
 */
export default function Sidebar({
  activeView,
  setActiveView,
  isMobileMenuOpen: _isMobileMenuOpen,
  setIsMobileMenuOpen,
  isDesktopCollapsed,
  isDarkMode,
  toggleTheme,
  authSession,
  isAuthSubmitting,
  onOpenLogin,
  onLogout,
  conversations,
  activeConversationId,
  onSelectConversation,
  onStartNewConversation,
  onRenameConversation,
  onDeleteConversation,
  onShareConversation,
  onToggleConversationPin,
  onExportConversation,
  onExportConversations,
  onDeleteConversations,
  workspaceGroups,
  activeWorkspacePartitionKey,
  workspaceLabel: _workspaceLabel,
  onSelectWorkspacePath,
  onPickRepositoryDirectory: _onPickRepositoryDirectory,
}: SidebarProps) {
  const NavItem = ({
    id,
    label,
    icon: Icon,
  }: {
    id: ViewType | 'new';
    label: string;
    icon: any;
  }) => {
    const isActive = activeView === id;
    const isNew = id === 'new';

    return (
      <button
        onClick={() => {
          if (id === 'new') setActiveView('chat');
          else setActiveView(id);
          setIsMobileMenuOpen(false);
        }}
        className={`flex w-full cursor-pointer items-center gap-3 px-4 py-2 transition-all duration-200 active:scale-95 ${
          isActive && !isNew
            ? 'rounded-full border border-border bg-surface-container-high text-foreground shadow-sm'
            : isNew
              ? 'rounded-full border border-border bg-surface-container text-foreground shadow-sm hover:border-border-active'
              : 'rounded-lg text-muted hover:bg-surface-container hover:text-foreground'
        }`}
      >
        <Icon size={20} className={isActive && !isNew ? 'text-primary' : ''} />
        <span className={`text-sm ${isActive && !isNew ? 'font-semibold' : 'font-medium'}`}>
          {label}
        </span>
      </button>
    );
  };

  return (
    <aside
      className={`absolute left-[-18rem] z-50 flex h-full shrink-0 flex-col overflow-hidden border-r border-border bg-surface text-foreground shadow-2xl [contain:layout_paint] transition-[width,padding,opacity,border-color] duration-300 will-change-[width,opacity] md:relative md:left-0 md:shadow-none ${
        isDesktopCollapsed
          ? 'md:w-0 md:border-r-0 md:px-0 md:pb-0 md:pt-0 md:opacity-0 md:pointer-events-none'
          : 'w-72 md:w-64'
      }`}
      aria-hidden={isDesktopCollapsed}
    >
      <div
        className={`flex h-full flex-col transition-[opacity,transform] duration-200 ${
          isDesktopCollapsed ? 'translate-x-4 opacity-0' : 'translate-x-0 opacity-100'
        }`}
      >
        {/* 顶部品牌区补充上内边距，避免在桌面端视觉上贴顶。 */}
        <div className="mb-4 flex items-center justify-between px-6 pt-5">
          <div className="mb-1 flex items-center gap-3">
            <div className="flex h-8 w-8 items-center justify-center rounded bg-foreground">
              <SquareTerminal size={18} className="text-background" />
            </div>
            <h1 className="text-xl font-bold leading-none tracking-tight text-foreground">
              CodingX
            </h1>
          </div>
          <Search size={22} className="text-foreground md:hidden" />
        </div>

        <div className="mb-2 px-5">
          <button
            onClick={() => {
              void onStartNewConversation();
              setIsMobileMenuOpen(false);
            }}
            className="flex w-full cursor-pointer items-center justify-center gap-2 rounded-2xl border border-border bg-background py-2 text-[14px] font-bold text-foreground shadow-sm transition-all hover:bg-surface-high active:scale-95"
          >
            <PlusCircle size={18} className="text-muted" />
            新建对话
          </button>
        </div>

        <nav className="mb-0 space-y-0.5 px-3">
          <NavItem id="mcp" label="MCP 管理" icon={PlugZap} />
          <NavItem id="skills" label="技能与套件" icon={Zap} />
          <NavItem id="experts" label="专家团队" icon={Brain} />
          <NavItem id="automation" label="自动化" icon={Bot} />
        </nav>

        <div className="sidebar-scrollbar flex-1 overflow-y-auto pb-4">
          {authSession ? (
            <ConversationHistory
              workspaceGroups={workspaceGroups}
              activeWorkspacePartitionKey={activeWorkspacePartitionKey}
              conversations={conversations}
              activeConversationId={activeConversationId}
              onSelectConversation={onSelectConversation}
              onStartNewConversation={onStartNewConversation}
              onRenameConversation={onRenameConversation}
              onDeleteConversation={onDeleteConversation}
              onShareConversation={onShareConversation}
              onToggleConversationPin={onToggleConversationPin}
              onExportConversation={onExportConversation}
              onExportConversations={onExportConversations}
              onDeleteConversations={onDeleteConversations}
              onSelectWorkspacePath={onSelectWorkspacePath}
            />
          ) : null}
        </div>

        {authSession ? (
          <ProfileMenu
            isDarkMode={isDarkMode}
            isSubmitting={isAuthSubmitting}
            displayName={authSession.displayName}
            onToggleTheme={toggleTheme}
            onLogout={onLogout}
          />
        ) : (
          <LoginEntry onClick={onOpenLogin} />
        )}
      </div>
    </aside>
  );
}

/**
 * 按工作空间分组渲染真实会话历史。
 */
function ConversationHistory({
  workspaceGroups,
  activeWorkspacePartitionKey,
  conversations,
  activeConversationId,
  onSelectConversation,
  onStartNewConversation,
  onRenameConversation,
  onDeleteConversation,
  onShareConversation,
  onToggleConversationPin,
  onExportConversation,
  onExportConversations,
  onDeleteConversations,
  onSelectWorkspacePath,
}: {
  workspaceGroups: WorkspaceConversationGroup[];
  activeWorkspacePartitionKey: string | null;
  conversations: ConversationItem[];
  activeConversationId: string | null;
  onSelectConversation: (
    conversationId: string,
    selectionContext: WorkspaceConversationSelectionContext,
  ) => Promise<void>;
  onStartNewConversation: (
    createContext?: WorkspaceConversationCreateContext,
  ) => Promise<void>;
  onRenameConversation: (
    conversationId: string,
    title: string,
    actionContext?: ConversationActionContext,
  ) => Promise<void>;
  onDeleteConversation: (
    conversationId: string,
    actionContext?: ConversationActionContext,
  ) => Promise<void>;
  onShareConversation: (
    conversationId: string,
    actionContext?: ConversationActionContext,
  ) => Promise<string>;
  onToggleConversationPin: (
    conversationId: string,
    actionContext: ConversationActionContext,
  ) => Promise<boolean>;
  onExportConversation: (
    conversationId: string,
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  onExportConversations: (
    conversationIds: string[],
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  onDeleteConversations: (
    conversationIds: string[],
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  onSelectWorkspacePath: (workspacePath: string | null) => Promise<void>;
}) {
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [activeExportConversationId, setActiveExportConversationId] = useState<string | null>(null);
  const [hoveredActionId, setHoveredActionId] = useState<string | null>(null);
  const [expandedCountMap, setExpandedCountMap] = useState<Record<string, number>>({});
  const [collapsedGroupMap, setCollapsedGroupMap] = useState<Record<string, boolean>>({});
  const [batchModePartitionKey, setBatchModePartitionKey] = useState<string | null>(null);
  const [selectedConversationIds, setSelectedConversationIds] = useState<string[]>([]);
  const [isBatchDeleteDialogOpen, setIsBatchDeleteDialogOpen] = useState(false);
  const menuLayerRef = useRef<HTMLDivElement | null>(null);
  const menuTriggerRefs = useRef<Record<string, HTMLButtonElement | null>>({});
  const touchTimerRef = useRef<number | null>(null);
  const [menuPosition, setMenuPosition] = useState<{ top: number; left: number } | null>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (menuLayerRef.current?.contains(target)) {
        return;
      }
      const activeTrigger = openMenuId ? menuTriggerRefs.current[openMenuId] : null;
      if (activeTrigger?.contains(target)) {
        return;
      }
      setOpenMenuId(null);
      setActiveExportConversationId(null);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [openMenuId]);

  /**
   * 将会话菜单锚定到触发按钮，并对视口边缘进行安全收敛，避免被裁剪或超出屏幕。
   */
  const syncConversationMenuPosition = React.useCallback(() => {
    if (!openMenuId) {
      setMenuPosition(null);
      return;
    }
    const trigger = menuTriggerRefs.current[openMenuId];
    if (!trigger) {
      setMenuPosition(null);
      return;
    }
    const triggerRect = trigger.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    const preferredLeft = triggerRect.right - CONVERSATION_MENU_WIDTH;
    const minLeft = CONVERSATION_MENU_SAFE_PADDING;
    const maxLeft = viewportWidth - CONVERSATION_MENU_SAFE_PADDING - CONVERSATION_MENU_WIDTH;
    const preferredTop = triggerRect.bottom + CONVERSATION_MENU_OFFSET;
    const minTop = CONVERSATION_MENU_SAFE_PADDING;
    const maxTop =
      viewportHeight - CONVERSATION_MENU_SAFE_PADDING - CONVERSATION_MENU_ESTIMATED_HEIGHT;
    setMenuPosition({
      left: Math.max(minLeft, Math.min(preferredLeft, maxLeft)),
      top: Math.max(minTop, Math.min(preferredTop, maxTop)),
    });
  }, [openMenuId]);

  useEffect(() => {
    if (!openMenuId) {
      return undefined;
    }
    syncConversationMenuPosition();
    // 关键约束：菜单使用 portal 挂载到 body 后，需要监听全局滚动/窗口缩放实时重算锚点。
    const updatePosition = () => syncConversationMenuPosition();
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);
    return () => {
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [openMenuId, syncConversationMenuPosition]);

  const startLongPress = (conversationId: string) => {
    clearLongPress();
    touchTimerRef.current = window.setTimeout(() => {
      setOpenMenuId(conversationId);
    }, 450);
  };

  const clearLongPress = () => {
    if (touchTimerRef.current != null) {
      window.clearTimeout(touchTimerRef.current);
      touchTimerRef.current = null;
    }
  };

  /**
   * 根据分组与会话拼出动作上下文，确保菜单动作在多工作空间场景下仍作用于正确分区。
   * @param group 会话所属分组。
   * @returns 可复用动作上下文。
   */
  const buildActionContext = (group: WorkspaceConversationGroup): ConversationActionContext => ({
    partitionKey: group.partitionKey,
    runtimeTarget: group.runtimeTarget,
    workspacePath: group.workspacePath,
    groupType: group.groupType,
  });

  /**
   * 进入某个分组的批量管理模式，并清空上一个分组的勾选结果。
   * @param partitionKey 目标分组键。
   */
  const enterBatchMode = (partitionKey: string) => {
    setBatchModePartitionKey(partitionKey);
    setSelectedConversationIds([]);
    setOpenMenuId(null);
    setActiveExportConversationId(null);
  };

  /**
   * 退出批量管理模式，清空勾选与确认弹窗状态。
   */
  const exitBatchMode = () => {
    setBatchModePartitionKey(null);
    setSelectedConversationIds([]);
    setIsBatchDeleteDialogOpen(false);
  };

  /**
   * 切换单个会话的批量勾选状态。
   * @param conversationId 会话标识。
   */
  const toggleBatchSelection = (conversationId: string) => {
    setSelectedConversationIds((previousIds) =>
      previousIds.includes(conversationId)
        ? previousIds.filter((item) => item !== conversationId)
        : previousIds.length >= BATCH_SELECTION_LIMIT
          ? previousIds
          : [...previousIds, conversationId],
    );
  };

  /**
   * 千问批量管理最多勾选 100 条，超过部分保留未选中状态。
   * @param conversations 当前抽屉可见会话。
   */
  const selectBatchConversations = (conversations: ConversationItem[]) => {
    setSelectedConversationIds(conversations.slice(0, BATCH_SELECTION_LIMIT).map((conversation) => conversation.id));
  };

  /**
   * 追加当前工作空间可见会话条数，每次固定增加 5 条。
   * @param partitionKey 工作空间分区键。
   * @param totalConversations 当前分区会话总数。
   */
  const handleExpandConversations = (partitionKey: string, totalConversations: number) => {
    setExpandedCountMap((previousMap) => {
      const previousCount = previousMap[partitionKey] ?? CONVERSATION_PAGE_SIZE;
      const nextCount = Math.min(totalConversations, previousCount + CONVERSATION_PAGE_SIZE);
      return {
        ...previousMap,
        [partitionKey]: nextCount,
      };
    });
  };

  /**
   * 收起当前工作空间会话列表，回到默认展示 5 条。
   * @param partitionKey 工作空间分区键。
   */
  const handleCollapseConversations = (partitionKey: string) => {
    setExpandedCountMap((previousMap) => ({
      ...previousMap,
      [partitionKey]: CONVERSATION_PAGE_SIZE,
    }));
  };

  /**
   * 切换工作空间分组折叠状态，用于快速隐藏或展示该分组会话。
   * @param partitionKey 工作空间分区键。
   */
  const handleToggleGroupCollapse = (partitionKey: string) => {
    setCollapsedGroupMap((previousMap) => ({
      ...previousMap,
      [partitionKey]: !previousMap[partitionKey],
    }));
  };

  /**
   * 统一工作空间运行环境图标，避免在左侧列表重复出现“云端/本地”文字占位。
   * @param runtimeTarget 运行环境类型。
   * @returns 对应的图标组件。
   */
  const getWorkspaceRuntimeIcon = (
    runtimeTarget: WorkspaceConversationGroup['runtimeTarget'],
    groupType: WorkspaceConversationGroup['groupType'],
  ) => {
    if (groupType === 'history') {
      return History;
    }
    return runtimeTarget === 'local' ? Folder : Cloud;
  };

  return (
    <>
      {workspaceGroups.length ? (
        <div className="space-y-2 px-3">
          {workspaceGroups.map((group) => {
            const totalConversations = group.conversations.length;
            const visibleConversationCount = Math.min(
              totalConversations,
              expandedCountMap[group.partitionKey] ?? CONVERSATION_PAGE_SIZE,
            );
            const visibleConversations = group.conversations.slice(0, visibleConversationCount);
            const hasMoreConversations = visibleConversationCount < totalConversations;
            const canCollapseConversations =
              totalConversations > CONVERSATION_PAGE_SIZE && !hasMoreConversations;
            const isGroupCollapsed = Boolean(collapsedGroupMap[group.partitionKey]);
            const isBatchMode = batchModePartitionKey === group.partitionKey;
            const actionContext = buildActionContext(group);
            const RuntimeIcon = getWorkspaceRuntimeIcon(group.runtimeTarget, group.groupType);
            return (
              <section key={group.partitionKey}>
                <div className="group/workspace-header flex min-h-11 w-full items-center justify-between gap-3 rounded-lg bg-surface-container/55 px-1 py-1.5 transition-colors hover:bg-surface-container">
                  <button
                    type="button"
                    aria-label={`${isGroupCollapsed ? '展开' : '折叠'}工作空间 ${group.workspaceLabel} 会话`}
                    onClick={() => {
                      // 历史分组不绑定具体目录，点击分组头仅折叠/展开，避免触发无效路径切换。
                      if (group.groupType !== 'history') {
                        void onSelectWorkspacePath(group.workspacePath);
                      }
                      handleToggleGroupCollapse(group.partitionKey);
                    }}
                    className="flex min-w-0 flex-1 cursor-pointer items-center gap-2 rounded-md px-0 py-0 text-left"
                  >
                    <RuntimeIcon
                      size={14}
                      className="ml-1 shrink-0 text-muted"
                      data-testid={
                        group.groupType === 'history'
                          ? 'workspace-runtime-icon-history'
                          : `workspace-runtime-icon-${group.runtimeTarget}`
                      }
                      aria-hidden="true"
                    />
                    <span className="truncate text-sm font-semibold text-foreground">
                      {group.workspaceLabel}
                    </span>
                    <span
                      className="rounded-md p-1 text-muted transition-colors hover:bg-surface-container hover:text-foreground"
                      aria-hidden="true"
                    >
                      <ChevronDown
                        size={16}
                        className={`transition-transform duration-200 ${
                          isGroupCollapsed ? '-rotate-90' : 'rotate-0'
                        }`}
                      />
                    </span>
                  </button>
                  <button
                    type="button"
                    aria-label={`在工作空间 ${group.workspaceLabel} 新建对话`}
                    onClick={() =>
                      void onStartNewConversation({
                        partitionKey: group.partitionKey,
                        runtimeTarget: group.runtimeTarget,
                        workspacePath: group.workspacePath,
                        groupType: group.groupType,
                      })
                    }
                    className="mr-1 inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-md text-muted transition-colors hover:bg-surface-container-high hover:text-foreground"
                  >
                    <Plus size={15} />
                  </button>
                </div>

                {/* 使用 grid-rows 过渡折叠高度，避免列表展开/收起时突兀跳变，并保持分组头与首条会话的纵向间距更紧凑。 */}
                <div
                  className={`grid transition-[grid-template-rows,opacity,margin-top] duration-250 ease-out ${
                    isGroupCollapsed
                      ? 'mt-0 grid-rows-[0fr] opacity-0 pointer-events-none'
                      : 'mt-1 grid-rows-[1fr] opacity-100'
                  }`}
                  aria-hidden={isGroupCollapsed}
                >
                  <div className="min-h-0 overflow-hidden">
                    <div className="space-y-1">
                      {visibleConversations.length ? (
                        visibleConversations.map((conversation) => {
                          // 关键约束：会话高亮必须同时命中“当前分区 + 当前会话”，避免同 ID 跨分组双高亮。
                          const isActive =
                            group.partitionKey === activeWorkspacePartitionKey &&
                            conversation.id === activeConversationId;
                          const isMenuOpen = openMenuId === conversation.id;
                          const isTaskRunning =
                            String(conversation.activeTaskStatus ?? '').trim().toUpperCase() === 'RUNNING';
                          const shouldShowActionMenu =
                            (hoveredActionId === conversation.id || isMenuOpen) &&
                            !(conversation.hasUnreadTaskCompletion && !isMenuOpen);
                          const statusSlotClass = shouldShowActionMenu
                            ? 'opacity-0 pointer-events-none'
                            : 'opacity-100 pointer-events-auto';
                          const actionButtonClass = shouldShowActionMenu
                            ? 'opacity-100 pointer-events-auto'
                            : 'opacity-0 pointer-events-none';
                          const selectConversation = () =>
                            onSelectConversation(conversation.id, {
                              partitionKey: group.partitionKey,
                              runtimeTarget: group.runtimeTarget,
                              workspacePath: group.workspacePath,
                              groupType: group.groupType,
                            });
                          return (
                            <div
                              key={conversation.id}
                              className="relative"
                              onMouseLeave={() => setHoveredActionId(null)}
                            >
                              <div
                                className={`group flex cursor-pointer items-center justify-between gap-3 rounded-xl border px-3 py-1.5 transition-[border-color,background-color,box-shadow] duration-200 ${
                                  isActive
                                    ? 'border-border-selected bg-surface-selected shadow-sm'
                                    : 'border-transparent hover:border-border-active hover:bg-surface-selected active:border-border-active active:bg-surface-selected'
                                }`}
                              >
                                {/* 让会话标题与工作空间标题文字起点对齐，并增强选中态可辨识度。 */}
                                {isBatchMode ? (
                                  <label
                                    className="flex h-6 w-6 shrink-0 cursor-pointer items-center justify-center"
                                    aria-label={`选择侧栏对话 ${conversation.title}`}
                                  >
                                    <input
                                      type="checkbox"
                                      checked={selectedConversationIds.includes(conversation.id)}
                                      onChange={() => toggleBatchSelection(conversation.id)}
                                      className="sr-only"
                                    />
                                    <span
                                      className={`flex h-4.5 w-4.5 items-center justify-center rounded border transition-colors ${
                                        selectedConversationIds.includes(conversation.id)
                                          ? 'border-border-selected bg-foreground text-background'
                                          : 'border-border bg-surface text-transparent'
                                      }`}
                                    >
                                      <Check size={12} />
                                    </span>
                                  </label>
                                ) : null}
                                <button
                                  type="button"
                                  onClick={() =>
                                    void selectConversation()
                                  }
                                  disabled={isBatchMode}
                                  className={`min-w-0 flex-1 cursor-pointer pl-3 text-left ${
                                    isActive ? 'text-foreground' : 'text-muted hover:text-foreground'
                                  }`}
                                >
                                  <div className="flex min-w-0 items-center justify-between gap-3">
                                    <div className={`flex min-w-0 items-center gap-2 ${isActive ? 'font-medium' : ''}`}>
                                      {conversation.isPinned ? (
                                        <Pin size={12} className="shrink-0 text-accent-breeze" aria-hidden="true" />
                                      ) : null}
                                      <span className="truncate text-[14px]">{conversation.title}</span>
                                    </div>
                                  </div>
                                </button>
                                {isBatchMode ? (
                                  <span className="text-[11px] text-muted">
                                    {selectedConversationIds.includes(conversation.id) ? '已选择' : ''}
                                  </span>
                                ) : (
                                  <div
                                    className="relative flex h-5 min-w-[56px] shrink-0 items-center justify-end whitespace-nowrap"
                                    onMouseEnter={() => setHoveredActionId(conversation.id)}
                                    onMouseLeave={() => setHoveredActionId(null)}
                                    onTouchStart={() => startLongPress(conversation.id)}
                                    onTouchEnd={clearLongPress}
                                    onTouchCancel={clearLongPress}
                                    onClick={() => void selectConversation()}
                                  >
                                    {isTaskRunning ? (
                                      <span
                                        role="status"
                                        aria-label={`会话 ${conversation.title} 正在后台执行`}
                                        className={`absolute right-0 inline-flex h-5 w-5 items-center justify-center text-muted transition-opacity ${statusSlotClass}`}
                                      >
                                        <LoaderCircle size={15} className="animate-spin" />
                                      </span>
                                    ) : (
                                      <span
                                        className={`absolute right-0 inline-flex items-center gap-1.5 whitespace-nowrap text-[12px] text-muted transition-opacity ${statusSlotClass}`}
                                      >
                                        <span>{formatRelativeTime(conversation.lastMessageAt)}</span>
                                        {conversation.hasUnreadTaskCompletion ? (
                                          <span
                                            role="status"
                                            aria-label={`会话 ${conversation.title} 有后台任务完成提醒`}
                                            className="h-1.5 w-1.5 rounded-full bg-accent-breeze shadow-[0_0_0_3px_color-mix(in_srgb,var(--accent-breeze)_18%,transparent)]"
                                          />
                                        ) : null}
                                      </span>
                                    )}
                                    <button
                                      type="button"
                                      aria-label={`打开会话菜单 ${conversation.title}`}
                                      onClick={(event) => {
                                        // 菜单按钮和状态槽重叠；阻止冒泡，避免打开菜单时误切换会话。
                                        event.stopPropagation();
                                        const nextIsOpen = !(openMenuId === conversation.id);
                                        setOpenMenuId(nextIsOpen ? conversation.id : null);
                                        setActiveExportConversationId(null);
                                      }}
                                      ref={(element) => {
                                        menuTriggerRefs.current[conversation.id] = element;
                                      }}
                                      className={`absolute right-0 cursor-pointer rounded-md p-1 text-muted transition-opacity hover:text-foreground ${actionButtonClass}`}
                                    >
                                      <MoreHorizontal size={16} />
                                    </button>
                                  </div>
                                )}
                              </div>

                              {isMenuOpen ? (
                                menuPosition
                                  ? createPortal(
                                      <div
                                        ref={menuLayerRef}
                                        className="fixed z-[130] w-44 rounded-xl border border-border bg-surface-container p-2 shadow-[0_16px_40px_rgba(0,0,0,0.24)]"
                                        style={{ top: `${menuPosition.top}px`, left: `${menuPosition.left}px` }}
                                      >
                                        <button
                                          type="button"
                                          aria-label="重命名"
                                          data-testid="conversation-action-menu-item"
                                          onClick={() => {
                                            void onRenameConversation(
                                              conversation.id,
                                              conversation.title,
                                              actionContext,
                                            );
                                            setOpenMenuId(null);
                                            setActiveExportConversationId(null);
                                          }}
                                          className="flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
                                        >
                                          <PencilLine size={16} />
                                          重命名
                                        </button>
                                        <button
                                          type="button"
                                          aria-label={conversation.isPinned ? '取消置顶' : '置顶此对话'}
                                          data-testid="conversation-action-menu-item"
                                          onClick={() => {
                                            void onToggleConversationPin(conversation.id, actionContext);
                                            setOpenMenuId(null);
                                            setActiveExportConversationId(null);
                                          }}
                                          className="mt-1 flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
                                        >
                                          {conversation.isPinned ? <PinOff size={16} /> : <Pin size={16} />}
                                          {conversation.isPinned ? '取消置顶' : '置顶此对话'}
                                        </button>
                                        <button
                                          type="button"
                                          aria-label="分享此对话"
                                          data-testid="conversation-action-menu-item"
                                          onClick={() => {
                                            void onShareConversation(conversation.id, actionContext);
                                            setOpenMenuId(null);
                                            setActiveExportConversationId(null);
                                          }}
                                          className="mt-1 flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
                                        >
                                          <Share2 size={16} />
                                          分享此对话
                                        </button>
                                        <button
                                          type="button"
                                          aria-label="批量管理"
                                          data-testid="conversation-action-menu-item"
                                          onClick={() => enterBatchMode(group.partitionKey)}
                                          className="mt-1 flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
                                        >
                                          <Layers3 size={16} />
                                          批量管理
                                        </button>
                                        <button
                                          type="button"
                                          aria-label="导出对话"
                                          data-testid="conversation-action-menu-item"
                                          onClick={() =>
                                            setActiveExportConversationId((currentId) =>
                                              currentId === conversation.id ? null : conversation.id,
                                            )
                                          }
                                          className="mt-1 flex w-full cursor-pointer items-center justify-between rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
                                        >
                                          <span className="inline-flex items-center gap-3">
                                            <Download size={16} />
                                            导出对话
                                          </span>
                                          <ChevronDown size={14} className="-rotate-90 text-muted" />
                                        </button>
                                        {activeExportConversationId === conversation.id ? (
                                          <div
                                            role="menu"
                                            aria-label="导出格式"
                                            className="absolute left-[calc(100%-4px)] top-[206px] w-28 space-y-1 rounded-xl border border-border bg-surface-container px-2 py-2 shadow-[0_16px_40px_rgba(0,0,0,0.24)]"
                                          >
                                            {QIANWEN_EXPORT_FORMATS.map((format) => (
                                              <button
                                                key={format.value}
                                                type="button"
                                                aria-label={format.label}
                                                onClick={() => {
                                                  void onExportConversation(
                                                    conversation.id,
                                                    format.value,
                                                    actionContext,
                                                  );
                                                  setOpenMenuId(null);
                                                  setActiveExportConversationId(null);
                                                }}
                                                className="flex w-full cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-left text-xs text-foreground transition-colors hover:bg-surface-high"
                                              >
                                                <File size={14} />
                                                {format.label}
                                              </button>
                                            ))}
                                          </div>
                                        ) : null}
                                        <button
                                          type="button"
                                          aria-label="删除此对话"
                                          data-testid="conversation-action-menu-item"
                                          onClick={() => {
                                            void onDeleteConversation(conversation.id, actionContext);
                                            setOpenMenuId(null);
                                            setActiveExportConversationId(null);
                                          }}
                                          className="mt-1 flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-[#ff5b57] transition-colors hover:bg-[#ff5b57]/10"
                                        >
                                          <Trash2 size={16} />
                                          删除此对话
                                        </button>
                                      </div>,
                                      document.body,
                                    )
                                  : null
                              ) : null}
                            </div>
                          );
                        })
                      ) : (
                        <div className="rounded-xl bg-surface-container pl-[22px] pr-3 py-2 text-sm text-muted">
                          暂无会话
                        </div>
                      )}
                      {hasMoreConversations ? (
                        <button
                          type="button"
                          aria-label="展开显示"
                          onClick={() => handleExpandConversations(group.partitionKey, totalConversations)}
                          className="ml-5 inline-flex cursor-pointer items-center rounded-lg bg-surface-container px-2.5 py-1.5 text-xs font-medium text-muted transition-colors hover:bg-surface-container-high hover:text-foreground"
                        >
                          展开显示
                        </button>
                      ) : null}
                      {canCollapseConversations ? (
                        <button
                          type="button"
                          aria-label="收起显示"
                          onClick={() => handleCollapseConversations(group.partitionKey)}
                          className="ml-5 inline-flex cursor-pointer items-center rounded-lg bg-surface-container px-2.5 py-1.5 text-xs font-medium text-muted transition-colors hover:bg-surface-container-high hover:text-foreground"
                        >
                          收起显示
                        </button>
                      ) : null}
                    </div>
                  </div>
                </div>
              </section>
            );
          })}
        </div>
      ) : (
        <div className="px-5 text-sm text-muted">暂无工作空间</div>
      )}
      {batchModePartitionKey ? (
        (() => {
          const batchGroup = workspaceGroups.find((group) => group.partitionKey === batchModePartitionKey);
          const batchConversations = (batchGroup?.conversations ?? []).slice(0, BATCH_SELECTION_LIMIT);
          const allSelected =
            batchConversations.length > 0 &&
            batchConversations.every((conversation) => selectedConversationIds.includes(conversation.id));
          return (
            <div className="fixed inset-0 z-[132] bg-background/35 backdrop-blur-sm">
              <section
                role="dialog"
                aria-modal="true"
                aria-label="对话批量管理"
                className="flex h-full w-full max-w-[360px] flex-col border-r border-border bg-surface text-foreground shadow-[24px_0_60px_rgba(0,0,0,0.18)]"
              >
                <div className="flex items-center justify-between border-b border-border px-5 py-4">
                  <h2 className="text-base font-semibold">对话批量管理</h2>
                  <button
                    type="button"
                    aria-label="关闭批量管理"
                    onClick={exitBatchMode}
                    className="rounded-full p-1.5 text-muted transition-colors hover:bg-surface-container hover:text-foreground"
                  >
                    <X size={18} />
                  </button>
                </div>
                <label className="flex cursor-pointer items-center justify-between border-b border-border px-5 py-3 text-sm text-foreground">
                  <span>全选</span>
                  <input
                    type="checkbox"
                    aria-label="全选"
                    checked={allSelected}
                    onChange={(event) => {
                      if (event.target.checked) {
                        selectBatchConversations(batchConversations);
                        return;
                      }
                      setSelectedConversationIds([]);
                    }}
                    className="h-4 w-4 accent-foreground"
                  />
                </label>
                <div className="flex-1 overflow-y-auto px-3 py-3">
                  {batchConversations.map((conversation) => (
                    <label
                      key={conversation.id}
                      className="flex cursor-pointer items-center justify-between gap-3 rounded-xl px-3 py-2.5 text-sm text-foreground transition-colors hover:bg-surface-container"
                    >
                      <span className="min-w-0 truncate">{conversation.title}</span>
                      <input
                        type="checkbox"
                        aria-label={`选择对话 ${conversation.title}`}
                        checked={selectedConversationIds.includes(conversation.id)}
                        onChange={() => toggleBatchSelection(conversation.id)}
                        className="h-4 w-4 shrink-0 accent-foreground"
                      />
                    </label>
                  ))}
                </div>
                <div className="flex items-center gap-3 border-t border-border px-5 py-4">
                  <button
                    type="button"
                    aria-label="取消"
                    onClick={exitBatchMode}
                    className="rounded-xl border border-border px-4 py-2 text-sm text-muted transition-colors hover:bg-surface-container hover:text-foreground"
                  >
                    取消
                  </button>
                  <span className="ml-auto text-sm text-muted">
                    已选{selectedConversationIds.length}/{BATCH_SELECTION_LIMIT}
                  </span>
                  <button
                    type="button"
                    aria-label="删除"
                    disabled={selectedConversationIds.length === 0}
                    onClick={() => setIsBatchDeleteDialogOpen(true)}
                    className="rounded-xl bg-[#ff5b57] px-4 py-2 text-sm text-white transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-45"
                  >
                    删除
                  </button>
                </div>
              </section>
            </div>
          );
        })()
      ) : null}
      {isBatchDeleteDialogOpen ? (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="批量删除对话"
          className="fixed inset-0 z-[135] flex items-center justify-center bg-background/35 backdrop-blur-sm"
        >
          <div className="w-full max-w-sm rounded-2xl border border-border bg-surface-container p-5 shadow-[0_24px_64px_rgba(0,0,0,0.26)]">
            <h3 className="text-base font-semibold text-foreground">批量删除对话</h3>
            <p className="mt-3 text-sm leading-6 text-muted">
              确认删除已选择的 {selectedConversationIds.length} 个对话吗？
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setIsBatchDeleteDialogOpen(false)}
                className="rounded-lg bg-surface px-4 py-2 text-sm text-muted transition-colors hover:bg-surface-high hover:text-foreground"
              >
                取消
              </button>
              <button
                type="button"
                aria-label="确认批量删除"
                onClick={() => {
                  const currentGroup = workspaceGroups.find(
                    (group) => group.partitionKey === batchModePartitionKey,
                  );
                  if (currentGroup && selectedConversationIds.length > 0) {
                    void onDeleteConversations(
                      selectedConversationIds,
                      buildActionContext(currentGroup),
                    );
                  }
                  exitBatchMode();
                }}
                className="rounded-lg bg-[#ff5b57] px-4 py-2 text-sm text-white transition-opacity hover:opacity-90"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

/**
 * 解析后端返回的会话时间字符串，失败时返回 null 以便安全降级。
 * @param value 后端返回的最近消息时间。
 * @returns 可比较的 Date 或 null。
 */
function parseConversationDate(value?: string) {
  if (!value) {
    return null;
  }
  const normalizedValue = value.includes('T') ? value : value.replace(' ', 'T');
  const nextDate = new Date(normalizedValue);
  if (Number.isNaN(nextDate.getTime())) {
    return null;
  }
  return nextDate;
}

/**
 * 将最后消息时间转换为“几小时前/几天前”的相对时间文案。
 * @param value 后端返回的最近消息时间。
 * @returns 适合展示在会话标题下方的相对时间。
 */
function formatRelativeTime(value?: string) {
  const conversationDate = parseConversationDate(value);
  if (!conversationDate) {
    return '刚刚';
  }
  const diffMs = Date.now() - conversationDate.getTime();
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  if (diffHours < 1) {
    const diffMinutes = Math.max(1, Math.floor(diffMs / (1000 * 60)));
    return `${diffMinutes} 分钟前`;
  }
  if (diffHours < 24) {
    return `${diffHours} 小时前`;
  }
  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays} 天前`;
}
