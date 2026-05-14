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
            setActiveView('chat');
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
      <div className="px-5 mb-2 font-mono text-[11px] text-muted tracking-widest uppercase">
        今天
      </div>
      <nav className="px-3 mb-4 space-y-0.5">
        <button className="w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-foreground bg-surface-container-high transition-colors group">
          <span className="text-[14px] truncate pr-2 font-medium">量子力学是什么</span>
          <div className="flex items-center gap-1 shrink-0">
            <div className="w-1.5 h-1.5 rounded-full bg-primary mr-1"></div>
            <MoreHorizontal size={14} className="text-muted hover:text-foreground" />
          </div>
        </button>
      </nav>

      <div className="px-5 mb-2 font-mono text-[11px] text-muted tracking-widest uppercase">
        最近一周
      </div>
      <nav className="flex-1 px-3 space-y-[2px] overflow-y-auto pb-4 custom-scrollbar">
        {[
          'Lumina战略方向: 深色模式设...',
          '了解Lynx浏览器',
          '超级代理平台设计初稿总结',
          '新时代社会矛盾及中国式现代化特...',
          '国内AI算力卡市场份额分析',
        ].map((task, i) => (
          <button
            key={i}
            className="w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-muted hover:text-foreground hover:bg-surface-container transition-colors group"
          >
            <span className="text-[14px] truncate pr-2">{task}</span>
            <div className="flex items-center gap-1 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
              <div className="w-1.5 h-1.5 rounded-full bg-primary mr-1"></div>
              <MoreHorizontal size={14} className="text-muted hover:text-foreground" />
            </div>
          </button>
        ))}
      </nav>

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
