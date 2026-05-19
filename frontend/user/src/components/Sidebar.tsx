import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  SquareTerminal,
  Search,
  PlusCircle,
  PlugZap,
  Zap,
  Brain,
  Bot,
  MoreHorizontal,
  PencilLine,
  Trash2,
  FolderOpen,
} from 'lucide-react';
import { ViewType } from '../App';
import { AuthSession } from '../types/auth';
import ProfileMenu from './sidebar/ProfileMenu';
import LoginEntry from './sidebar/LoginEntry';
import { ConversationItem, WorkspaceConversationGroup } from '../views/chat/types';

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
  onSelectConversation: (conversationId: string) => Promise<void>;
  onStartNewConversation: () => Promise<void>;
  onRenameConversation: (conversationId: string, title: string) => Promise<void>;
  onDeleteConversation: (conversationId: string) => Promise<void>;
  workspaceGroups: WorkspaceConversationGroup[];
  activeWorkspacePartitionKey: string | null;
  workspaceLabel: string;
  onSelectWorkspacePath: (workspacePath: string | null) => Promise<void>;
  onPickRepositoryDirectory: () => Promise<void>;
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
  workspaceGroups,
  activeWorkspacePartitionKey,
  workspaceLabel,
  onSelectWorkspacePath,
  onPickRepositoryDirectory,
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
        className={`flex w-full cursor-pointer items-center gap-3 px-4 py-3 transition-all duration-200 active:scale-95 ${
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
        <div className="mb-6 flex items-center justify-between px-6">
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

        <div className="mb-4 px-5">
          <button
            onClick={() => {
              void onStartNewConversation();
              setIsMobileMenuOpen(false);
            }}
            className="flex w-full cursor-pointer items-center justify-center gap-2 rounded-2xl border border-border bg-background py-3.5 text-[15px] font-bold text-foreground shadow-sm transition-all hover:bg-surface-high active:scale-95"
          >
            <PlusCircle size={20} className="text-muted" />
            新建对话
          </button>
        </div>

        <div className="mb-2 px-5 font-mono text-[10px] uppercase tracking-widest text-muted">
          我的空间
        </div>
        <nav className="mb-6 space-y-1 px-3">
          <NavItem id="mcp" label="MCP 管理" icon={PlugZap} />
          <NavItem id="skills" label="技能与套件" icon={Zap} />
          <NavItem id="experts" label="专家团队" icon={Brain} />
          <NavItem id="automation" label="自动化" icon={Bot} />
        </nav>

        <div className="flex items-center justify-between px-5 pb-2">
          <div>
            <div className="font-mono text-[10px] uppercase tracking-widest text-muted">
              工作空间
            </div>
            <div className="mt-1 text-sm font-semibold text-foreground">{workspaceLabel}</div>
          </div>
          <button
            type="button"
            aria-label="选择本地仓库目录"
            onClick={() => void onPickRepositoryDirectory()}
            className="flex h-9 w-9 items-center justify-center rounded-xl border border-border bg-surface-container text-muted transition-colors hover:text-foreground"
          >
            <FolderOpen size={16} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto pb-4">
          {authSession ? (
            <ConversationHistory
              workspaceGroups={workspaceGroups}
              activeWorkspacePartitionKey={activeWorkspacePartitionKey}
              conversations={conversations}
              activeConversationId={activeConversationId}
              onSelectConversation={onSelectConversation}
              onRenameConversation={onRenameConversation}
              onDeleteConversation={onDeleteConversation}
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
  onRenameConversation,
  onDeleteConversation,
  onSelectWorkspacePath,
}: {
  workspaceGroups: WorkspaceConversationGroup[];
  activeWorkspacePartitionKey: string | null;
  conversations: ConversationItem[];
  activeConversationId: string | null;
  onSelectConversation: (conversationId: string) => Promise<void>;
  onRenameConversation: (conversationId: string, title: string) => Promise<void>;
  onDeleteConversation: (conversationId: string) => Promise<void>;
  onSelectWorkspacePath: (workspacePath: string | null) => Promise<void>;
}) {
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [hoveredActionId, setHoveredActionId] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const touchTimerRef = useRef<number | null>(null);
  const activeGroup = workspaceGroups.find((group) => group.partitionKey === activeWorkspacePartitionKey);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) {
        setOpenMenuId(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

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

  return (
    <>
      <div className="px-3 pb-3">
        <div className="rounded-2xl border border-border bg-surface-container px-4 py-3">
          <div className="text-[11px] uppercase tracking-[0.24em] text-muted">当前工作空间</div>
          <div className="mt-1 text-sm font-medium text-foreground">
            {activeGroup?.workspaceLabel ?? '云端工作空间'}
          </div>
          <div className="mt-3 flex items-center gap-2">
            <button
              type="button"
              className="rounded-lg border border-border bg-surface px-3 py-1.5 text-xs text-foreground transition-colors hover:border-border-active"
              onClick={() => void onSelectWorkspacePath(null)}
            >
              查看全部
            </button>
            <button
              type="button"
              className="rounded-lg border border-border bg-surface px-3 py-1.5 text-xs text-foreground transition-colors hover:border-border-active"
              onClick={() => void onPickRepositoryDirectory()}
            >
              新增工作空间
            </button>
          </div>
        </div>
      </div>
      {workspaceGroups.length ? (
        <div className="space-y-4 px-3">
          {workspaceGroups.map((group) => {
            const isActiveGroup = group.partitionKey === activeWorkspacePartitionKey;
            return (
              <section
                key={group.partitionKey}
                className={`rounded-2xl border px-3 py-3 ${
                  isActiveGroup ? 'border-border bg-surface-container/40' : 'border-border bg-surface'
                }`}
              >
                <button
                  type="button"
                  className="flex w-full items-start justify-between gap-3 text-left"
                  onClick={() => void onSelectWorkspacePath(group.workspacePath)}
                >
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold text-foreground">
                      {group.workspaceLabel}
                    </div>
                    <div className="mt-1 text-[11px] text-muted">
                      {group.workspacePath ?? '云端'}
                    </div>
                  </div>
                  <div className="text-[11px] text-muted">{formatWorkspaceTime(group.lastOpenedAt)}</div>
                </button>

                <div className="mt-3 space-y-[6px]">
                  {group.conversations.length ? (
                    group.conversations.map((conversation) => {
                      const isActive = conversation.id === activeConversationId;
                      const isMenuOpen = openMenuId === conversation.id;
                      return (
                        <div
                          key={conversation.id}
                          className="relative"
                          ref={isMenuOpen ? menuRef : null}
                          onMouseLeave={() => setHoveredActionId(null)}
                        >
                          <div
                            className={`group flex items-center justify-between gap-3 rounded-xl px-3 py-1.5 transition-colors ${
                              isActive ? 'bg-surface-container-high' : 'hover:bg-surface-container'
                            }`}
                          >
                            <button
                              type="button"
                              onClick={() => void onSelectConversation(conversation.id)}
                              className={`min-w-0 flex-1 cursor-pointer text-left ${
                                isActive ? 'text-foreground' : 'text-muted hover:text-foreground'
                              }`}
                            >
                              <div className="flex min-w-0 items-center justify-between gap-3">
                                <div className={`truncate text-[14px] ${isActive ? 'font-medium' : ''}`}>
                                  {conversation.title}
                                </div>
                              </div>
                            </button>
                            <div
                              className="relative flex h-5 min-w-[56px] shrink-0 items-center justify-end whitespace-nowrap"
                              onMouseEnter={() => setHoveredActionId(conversation.id)}
                              onMouseLeave={() => setHoveredActionId(null)}
                              onTouchStart={() => startLongPress(conversation.id)}
                              onTouchEnd={clearLongPress}
                              onTouchCancel={clearLongPress}
                            >
                              <span
                                className={`absolute right-0 whitespace-nowrap text-[12px] text-muted transition-opacity ${
                                  hoveredActionId === conversation.id || isMenuOpen
                                    ? 'opacity-0'
                                    : 'opacity-100'
                                }`}
                              >
                                {formatRelativeTime(conversation.lastMessageAt)}
                              </span>
                              <button
                                type="button"
                                aria-label={`打开会话菜单 ${conversation.title}`}
                                onClick={() => setOpenMenuId(isMenuOpen ? null : conversation.id)}
                                className={`absolute right-0 cursor-pointer rounded-md p-1 text-muted transition-opacity hover:text-foreground ${
                                  hoveredActionId === conversation.id || isMenuOpen
                                    ? 'opacity-100'
                                    : 'opacity-0'
                                }`}
                              >
                                <MoreHorizontal size={16} />
                              </button>
                            </div>
                          </div>

                          {isMenuOpen ? (
                            <div className="absolute right-0 top-[calc(100%+6px)] z-20 w-44 rounded-xl border border-border bg-surface-container p-2 shadow-[0_16px_40px_rgba(0,0,0,0.24)]">
                              <button
                                type="button"
                                aria-label="重命名对话"
                                onClick={() => {
                                  void onRenameConversation(conversation.id, conversation.title);
                                  setOpenMenuId(null);
                                }}
                                className="flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
                              >
                                <PencilLine size={16} />
                                重命名对话
                              </button>
                              <button
                                type="button"
                                aria-label="删除对话"
                                onClick={() => {
                                  void onDeleteConversation(conversation.id);
                                  setOpenMenuId(null);
                                }}
                                className="mt-1 flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-[#ff5b57] transition-colors hover:bg-[#ff5b57]/10"
                              >
                                <Trash2 size={16} />
                                删除对话
                              </button>
                            </div>
                          ) : null}
                        </div>
                      );
                    })
                  ) : (
                    <div className="rounded-xl bg-surface-container px-3 py-2 text-sm text-muted">
                      暂无会话
                    </div>
                  )}
                </div>
              </section>
            );
          })}
        </div>
      ) : (
        <div className="px-5 text-sm text-muted">暂无工作空间</div>
      )}
      {!conversations.length ? <div className="px-5 pt-4 text-sm text-muted">暂无真实会话</div> : null}
    </>
  );
}

/**
 * 格式化工作空间最近打开时间。
 * @param value 最近打开时间戳。
 * @returns 适合展示的时间文案。
 */
function formatWorkspaceTime(value: number) {
  if (!value) {
    return '刚刚';
  }
  const diffMs = Date.now() - value;
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
