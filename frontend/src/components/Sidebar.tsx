import React from 'react';
import {
  SquareTerminal,
  Search,
  PlusCircle,
  Zap,
  Brain,
  Bot,
  MoreHorizontal,
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
}: {
  conversations: ConversationItem[];
  activeConversationId: string | null;
  onSelectConversation: (conversationId: string) => Promise<void>;
}) {
  const sections = groupConversationsByTime(conversations);

  return (
    <>
      {sections.map((section) => (
        <div key={section.key}>
          <div className="px-5 mb-2 font-mono text-[11px] text-muted tracking-widest uppercase">
            {section.label}
          </div>
          {section.items.length ? (
            <nav className="px-3 mb-4 space-y-[2px]">
              {section.items.map((conversation) => {
                const isActive = conversation.id === activeConversationId;
                return (
                  <button
                    key={conversation.id}
                    type="button"
                    onClick={() => void onSelectConversation(conversation.id)}
                    className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl transition-colors group ${
                      isActive
                        ? 'text-foreground bg-surface-container-high'
                        : 'text-muted hover:text-foreground hover:bg-surface-container'
                    }`}
                  >
                    <span className={`text-[14px] truncate pr-2 ${isActive ? 'font-medium' : ''}`}>{conversation.title}</span>
                    <div className={`flex items-center gap-1 shrink-0 transition-opacity ${isActive ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'}`}>
                      <div className="w-1.5 h-1.5 rounded-full bg-primary mr-1"></div>
                      <MoreHorizontal size={14} className="text-muted hover:text-foreground" />
                    </div>
                  </button>
                );
              })}
            </nav>
          ) : null}
        </div>
      ))}
      {!conversations.length ? (
        <div className="px-5 text-sm text-muted">
          暂无真实会话
        </div>
      ) : null}
    </>
  );
}

/**
 * 根据最近消息时间把真实会话分组到今天、最近七天和更早。
 * @param conversations 当前用户真实会话列表。
 * @returns Sidebar 所需分组结构。
 */
function groupConversationsByTime(conversations: ConversationItem[]) {
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const sevenDaysAgo = new Date(startOfToday);
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);

  const sections = [
    { key: 'today', label: '今天', items: [] as ConversationItem[] },
    { key: 'recent', label: '最近七天', items: [] as ConversationItem[] },
    { key: 'older', label: '更早', items: [] as ConversationItem[] },
  ];

  conversations.forEach((conversation) => {
    const conversationDate = parseConversationDate(conversation.lastMessageAt);
    if (conversationDate && conversationDate >= startOfToday) {
      sections[0].items.push(conversation);
      return;
    }
    if (conversationDate && conversationDate >= sevenDaysAgo) {
      sections[1].items.push(conversation);
      return;
    }
    sections[2].items.push(conversation);
  });

  return sections;
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
