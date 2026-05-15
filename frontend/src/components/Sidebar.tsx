import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  SquareTerminal,
  Search,
  PlusCircle,
  Zap,
  Brain,
  Bot,
  MoreHorizontal,
  PencilLine,
  Trash2,
} from 'lucide-react';
import { ViewType } from '../App';
import { AuthSession } from '../types/auth';
import ProfileMenu from './sidebar/ProfileMenu';
import LoginEntry from './sidebar/LoginEntry';
import { ConversationItem } from '../views/chat/types';

/**
 * 定义 Sidebar 组件需要的输入属性。
 */
interface SidebarProps {
  activeView: ViewType;
  setActiveView: (view: ViewType) => void;
  isMobileMenuOpen: boolean;
  setIsMobileMenuOpen: (isOpen: boolean) => void;
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
}

/**
 * 渲染桌面端与移动端共用的侧边栏导航。
 */
export default function Sidebar({
  activeView,
  setActiveView,
  isMobileMenuOpen,
  setIsMobileMenuOpen,
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
        className={`flex items-center gap-3 w-full px-4 py-3 transition-all duration-200 active:scale-95 ${
          isActive && !isNew
            ? 'bg-surface-container-high text-foreground rounded-full border border-border shadow-sm'
            : isNew
              ? 'bg-surface-container text-foreground rounded-full border border-border shadow-sm hover:border-border-active'
              : 'text-muted hover:text-foreground hover:bg-surface-container rounded-lg'
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
      className={`absolute left-[-18rem] md:relative md:left-0 flex flex-col h-full pt-6 pb-4 w-72 md:w-64 border-r border-border bg-surface z-50 shrink-0 shadow-2xl md:shadow-none overflow-hidden text-foreground`}
    >
      {/* Brand Header */}
      <div className="px-6 mb-8 flex items-center justify-between">
        <div className="flex items-center gap-3 mb-1">
          <div className="w-8 h-8 bg-foreground rounded flex items-center justify-center">
            <SquareTerminal size={18} className="text-background" />
          </div>
          <h1 className="text-xl font-bold tracking-tight leading-none text-foreground">CodingX</h1>
        </div>
        <Search size={22} className="text-foreground md:hidden" />
      </div>

      {/* Action Button: 新建对话 */}
      <div className="px-5 mb-6">
        <button
          onClick={() => {
            void onStartNewConversation();
            setIsMobileMenuOpen(false);
          }}
          className="w-full bg-background border border-border text-foreground hover:bg-surface-high py-3.5 rounded-2xl text-[15px] font-bold active:scale-95 transition-all flex items-center justify-center gap-2 shadow-sm"
        >
          <PlusCircle size={20} className="text-muted" />
          新建对话
        </button>
      </div>

      {/* Navigation */}
      <div className="px-5 mb-2 font-mono text-[10px] text-muted tracking-widest uppercase mt-2">
        我的空间
      </div>
      <nav className="px-3 space-y-1 mb-6">
        <NavItem id="skills" label="技能与套件" icon={Zap} />
        <NavItem id="experts" label="专家团队" icon={Brain} />
        <NavItem id="automation" label="自动化" icon={Bot} />
      </nav>

      {/* History / Tasks */}
      <div className="flex-1 overflow-y-auto pb-4">
        {authSession ? (
          <ConversationHistory
            conversations={conversations}
            activeConversationId={activeConversationId}
            onSelectConversation={onSelectConversation}
            onRenameConversation={onRenameConversation}
            onDeleteConversation={onDeleteConversation}
          />
        ) : null}
      </div>

      {/* Footer actions */}
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
    </aside>
  );
}

/**
 * 按时间分组渲染真实会话历史，替换原有静态假数据列表。
 */
function ConversationHistory({
  conversations,
  activeConversationId,
  onSelectConversation,
  onRenameConversation,
  onDeleteConversation,
}: {
  conversations: ConversationItem[];
  activeConversationId: string | null;
  onSelectConversation: (conversationId: string) => Promise<void>;
  onRenameConversation: (conversationId: string, title: string) => Promise<void>;
  onDeleteConversation: (conversationId: string) => Promise<void>;
}) {
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const items = useMemo(
    () =>
      conversations.map((conversation) => ({
        ...conversation,
        relativeTimeText: formatRelativeTime(conversation.lastMessageAt),
      })),
    [conversations],
  );

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) {
        setOpenMenuId(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <>
      {items.length ? (
        <nav className="px-3 mb-4 space-y-[6px]">
          {items.map((conversation) => {
            const isActive = conversation.id === activeConversationId;
            const isMenuOpen = openMenuId === conversation.id;
            return (
              <div key={conversation.id} className="relative" ref={isMenuOpen ? menuRef : null}>
                <div
                  className={`flex items-start justify-between gap-3 rounded-xl px-3 py-2.5 transition-colors ${
                    isActive
                      ? 'bg-surface-container-high'
                      : 'hover:bg-surface-container'
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => void onSelectConversation(conversation.id)}
                    className={`min-w-0 flex-1 text-left ${
                      isActive ? 'text-foreground' : 'text-muted hover:text-foreground'
                    }`}
                  >
                    <div className="flex min-w-0 items-center justify-between gap-3">
                      <div className={`truncate text-[14px] ${isActive ? 'font-medium' : ''}`}>{conversation.title}</div>
                      <div className="shrink-0 text-[12px] text-muted">{conversation.relativeTimeText}</div>
                    </div>
                  </button>
                  <button
                    type="button"
                    aria-label={`打开会话菜单 ${conversation.title}`}
                    onClick={() => {
                      setOpenMenuId(isMenuOpen ? null : conversation.id);
                    }}
                    className={`mt-0.5 rounded-md p-1 text-muted transition-colors hover:bg-surface-container-high hover:text-foreground ${
                      isMenuOpen ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
                    }`}
                  >
                    <MoreHorizontal size={16} />
                  </button>
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
                      className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
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
                      className="mt-1 flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-[#ff5b57] transition-colors hover:bg-[#ff5b57]/10"
                    >
                      <Trash2 size={16} />
                      删除对话
                    </button>
                  </div>
                ) : null}
              </div>
            );
          })}
        </nav>
      ) : null}
      {!conversations.length ? (
        <div className="px-5 text-sm text-muted">
          暂无真实会话
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
