import React from 'react';
import {
  BookMarked,
  Bot,
  Brain,
  PlugZap,
  PlusCircle,
  Search,
  Zap,
} from 'lucide-react';
import { ViewType } from '../App';
import { AuthSession } from '../types/auth';
import LoginEntry from './sidebar/LoginEntry';
import ProfileMenu from './sidebar/ProfileMenu';
import ConversationHistory from './sidebar/ConversationHistory';
import {
  ConversationActionContext,
  ConversationExportFormat,
  ConversationItem,
  WorkspaceConversationCreateContext,
  WorkspaceConversationGroup,
  WorkspaceConversationSelectionContext,
} from '../views/chat/types';

/**
 * 侧边栏品牌图标复用浏览器页 favicon，保持 Web、IDEA 与 Electron 图标来源一致。
 */
const BRAND_ICON_SRC = '/brand-favicon.svg';
const DESKTOP_SIDEBAR_MIN_WIDTH = 220;
const DESKTOP_SIDEBAR_MAX_WIDTH = 360;
const DESKTOP_SIDEBAR_KEYBOARD_STEP = 16;

/**
 * 限制桌面端侧栏宽度，避免拖拽后挤压主内容或导致侧栏控件不可读。
 */
function clampDesktopSidebarWidth(nextWidth: number) {
  return Math.min(
    Math.max(nextWidth, DESKTOP_SIDEBAR_MIN_WIDTH),
    DESKTOP_SIDEBAR_MAX_WIDTH,
  );
}

/**
 * 定义 Sidebar 组件需要的输入属性。
 */
interface SidebarProps {
  activeView: ViewType;
  setActiveView: (view: ViewType) => void;
  isMobileMenuOpen: boolean;
  setIsMobileMenuOpen: (isOpen: boolean) => void;
  isDesktopCollapsed: boolean;
  /**
   * 桌面端侧边栏当前宽度，由 App 持有以便折叠/展开与主内容布局保持同一个状态源。
   */
  desktopWidth: number;
  /**
   * 桌面端拖拽或键盘调整宽度时回传给 App，移动端抽屉不消费该值。
   */
  onDesktopWidthChange: (width: number) => void;
  isDarkMode: boolean;
  toggleTheme: () => void;
  authSession: AuthSession | null;
  isAuthSubmitting: boolean;
  onOpenLogin: () => void;
  onOpenSettings: () => void;
  onLogout: () => Promise<void>;
  /**
   * 兼容旧调用方仍传入当前会话列表；实际侧栏渲染以 workspaceGroups 为准，避免多分区历史串线。
   */
  conversations?: ConversationItem[];
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
  onLoadMoreConversations: (
    selectionContext: WorkspaceConversationSelectionContext,
  ) => Promise<void>;
  workspaceGroups: WorkspaceConversationGroup[];
  activeWorkspacePartitionKey: string | null;
  workspaceLabel?: string;
  onSelectWorkspacePath: (workspacePath: string | null) => Promise<void>;
  onPickRepositoryDirectory?: () => Promise<void>;
}

/**
 * 渲染桌面端与移动端共用的侧边栏导航；历史列表已拆到 ConversationHistory，避免外壳组件继续承载行级交互。
 */
export default function Sidebar({
  activeView,
  setActiveView,
  isMobileMenuOpen: _isMobileMenuOpen,
  setIsMobileMenuOpen,
  isDesktopCollapsed,
  desktopWidth,
  onDesktopWidthChange,
  isDarkMode,
  toggleTheme,
  authSession,
  isAuthSubmitting,
  onOpenLogin,
  onOpenSettings,
  onLogout,
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
  onLoadMoreConversations,
  workspaceGroups,
  activeWorkspacePartitionKey,
  workspaceLabel: _workspaceLabel,
  onSelectWorkspacePath,
  onPickRepositoryDirectory: _onPickRepositoryDirectory,
}: SidebarProps) {
  const [isDesktopResizing, setIsDesktopResizing] = React.useState(false);
  const resolvedDesktopWidth = clampDesktopSidebarWidth(desktopWidth);
  const sidebarStyle = isDesktopCollapsed
    ? undefined
    : ({
        '--codingx-sidebar-width': `${resolvedDesktopWidth}px`,
      } as React.CSSProperties);

  const startDesktopResize = React.useCallback(
    (event: React.MouseEvent<HTMLButtonElement>) => {
      event.preventDefault();
      setIsDesktopResizing(true);
      const startX = event.clientX;
      const startWidth = resolvedDesktopWidth;

      const handleMouseMove = (moveEvent: MouseEvent) => {
        // 左侧栏固定在左侧，鼠标向右移动时增加宽度，向左移动时减少宽度。
        onDesktopWidthChange(
          clampDesktopSidebarWidth(startWidth + (moveEvent.clientX - startX)),
        );
      };

      const stopResize = () => {
        setIsDesktopResizing(false);
        window.removeEventListener('mousemove', handleMouseMove);
        window.removeEventListener('mouseup', stopResize);
      };

      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', stopResize);
    },
    [onDesktopWidthChange, resolvedDesktopWidth],
  );

  const handleDesktopResizeKeyDown = React.useCallback(
    (event: React.KeyboardEvent<HTMLButtonElement>) => {
      if (event.key === 'ArrowLeft') {
        event.preventDefault();
        onDesktopWidthChange(
          clampDesktopSidebarWidth(resolvedDesktopWidth - DESKTOP_SIDEBAR_KEYBOARD_STEP),
        );
      }
      if (event.key === 'ArrowRight') {
        event.preventDefault();
        onDesktopWidthChange(
          clampDesktopSidebarWidth(resolvedDesktopWidth + DESKTOP_SIDEBAR_KEYBOARD_STEP),
        );
      }
      if (event.key === 'Home') {
        event.preventDefault();
        onDesktopWidthChange(DESKTOP_SIDEBAR_MIN_WIDTH);
      }
      if (event.key === 'End') {
        event.preventDefault();
        onDesktopWidthChange(DESKTOP_SIDEBAR_MAX_WIDTH);
      }
    },
    [onDesktopWidthChange, resolvedDesktopWidth],
  );

  const NavItem = ({
    id,
    label,
    icon: Icon,
  }: {
    id: ViewType | 'new';
    label: string;
    icon: React.ComponentType<{ size?: number; className?: string }>;
  }) => {
    const isActive = activeView === id;
    const isNew = id === 'new';

    return (
      <button
        onClick={() => {
          if (id === 'new') {
            setActiveView('chat');
          } else {
            setActiveView(id);
          }
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
          : 'w-72 md:w-[var(--codingx-sidebar-width)]'
      } ${
        isDesktopResizing ? 'transition-none duration-0' : ''
      }`}
      aria-hidden={isDesktopCollapsed}
      style={sidebarStyle}
    >
      {!isDesktopCollapsed ? (
        <button
          type="button"
          data-testid="desktop-sidebar-resize-handle"
          role="separator"
          aria-label="调整左侧边栏宽度"
          aria-orientation="vertical"
          aria-valuemin={DESKTOP_SIDEBAR_MIN_WIDTH}
          aria-valuemax={DESKTOP_SIDEBAR_MAX_WIDTH}
          aria-valuenow={resolvedDesktopWidth}
          onMouseDown={startDesktopResize}
          onKeyDown={handleDesktopResizeKeyDown}
          className="absolute right-0 top-0 z-20 hidden h-full w-2 cursor-col-resize border-r border-transparent transition-colors hover:border-foreground/40 focus-visible:border-foreground focus-visible:outline-none md:block"
        />
      ) : null}
      <div
        className={`flex h-full flex-col transition-[opacity,transform] duration-200 ${
          isDesktopCollapsed ? 'translate-x-4 opacity-0' : 'translate-x-0 opacity-100'
        }`}
      >
        {/* 顶部品牌区补充上内边距，并复用站点 favicon 作为左上角品牌标识。 */}
        <div className="mb-4 flex items-center justify-between px-6 pt-5">
          <div className="mb-1 flex items-center gap-3">
            <img
              src={BRAND_ICON_SRC}
              alt=""
              aria-hidden="true"
              className="h-8 w-8 shrink-0 rounded-md object-contain"
            />
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
          <NavItem id="memories" label="记忆管理" icon={BookMarked} />
          <NavItem id="automation" label="自动化" icon={Bot} />
        </nav>

        <div
          data-testid="sidebar-conversation-history-panel"
          className="sidebar-scrollbar mt-2 flex-1 overflow-y-auto pb-4"
        >
          {authSession ? (
            <ConversationHistory
              workspaceGroups={workspaceGroups}
              activeWorkspacePartitionKey={activeWorkspacePartitionKey}
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
              onLoadMoreConversations={onLoadMoreConversations}
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
            onOpenSettings={onOpenSettings}
            onLogout={onLogout}
          />
        ) : (
          <LoginEntry onClick={onOpenLogin} />
        )}
      </div>
    </aside>
  );
}
