import React, { useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import clsx from 'clsx';
import { NavLink, Outlet, useLocation } from 'react-router-dom';

const navItems = [
  { path: '/', icon: 'dashboard', label: 'Dashboard' },
  { path: '/users', icon: 'group', label: '用户管理' },
  { path: '/tasks', icon: 'assignment', label: '会话管理' },
  { path: '/workspaces', icon: 'workspaces', label: '工作空间' },
  { path: '/skills', icon: 'extension', label: '技能管理' },
  { path: '/experts', icon: 'psychology', label: '专家管理' },
  { path: '/tools', icon: 'build_circle', label: '工具管理' },
  { path: '/mcp', icon: 'terminal', label: 'MCP 管理' },
  { path: '/traces', icon: 'account_tree', label: 'Trace 管理' },
  { path: '/feedbacks', icon: 'thumbs_up_down', label: '反馈管理' },
  { path: '/intent-tree', icon: 'schema', label: '意图树' },
  { path: '/query-term-mappings', icon: 'manage_search', label: '关键词映射' },
  { path: '/settings', icon: 'settings', label: '系统配置' },
  { path: '/notifications', icon: 'notifications', label: '通知中心' },
];

interface SidebarProps {
  isCollapsed: boolean;
  setIsCollapsed: (value: boolean) => void;
}

/**
 * 管理端深色侧边栏，统一承载主路由入口并保持参考控制台的纵向导航节奏。
 */
function Sidebar({ isCollapsed, setIsCollapsed }: SidebarProps) {
  return (
    <aside
      className={clsx(
        'fixed left-0 top-0 z-40 flex h-screen flex-col overflow-hidden border-r border-white/8 bg-[#151d2d] text-white shadow-[0_24px_64px_rgba(7,10,20,0.35)] transition-all duration-300',
        isCollapsed ? 'w-[84px]' : 'w-[240px]',
      )}
    >
      <div className={clsx('flex items-center gap-3 border-b border-white/8 px-4 py-5', isCollapsed && 'justify-center px-3')}>
        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-500 to-indigo-500 text-lg font-bold shadow-[0_12px_28px_rgba(99,102,241,0.35)]">
          R
        </div>
        {!isCollapsed ? (
          <div className="min-w-0">
            <h1 className="truncate text-[18px] font-semibold leading-none text-white">CodingX 管理后台</h1>
            <p className="mt-1 text-[11px] uppercase tracking-[0.22em] text-white/50">Knowledge Console</p>
          </div>
        ) : null}
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4">
        <div className="mb-3 px-2 text-[11px] uppercase tracking-[0.18em] text-white/30">
          {!isCollapsed ? '导航' : ''}
        </div>
        <div className="space-y-1.5">
          {navItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              title={isCollapsed ? item.label : undefined}
              className={({ isActive }) =>
                clsx(
                  'group relative flex items-center gap-3 rounded-2xl px-3 py-3 text-[14px] transition-all duration-200',
                  isCollapsed && 'justify-center px-2',
                  isActive
                    ? 'bg-indigo-500/18 text-white shadow-[inset_0_0_0_1px_rgba(129,140,248,0.22)]'
                    : 'text-white/62 hover:bg-white/6 hover:text-white',
                )
              }
            >
              {({ isActive }) => (
                <>
                  {!isCollapsed ? (
                    <span
                      className={clsx(
                        'absolute left-0 top-1/2 h-8 w-1 -translate-y-1/2 rounded-r-full bg-indigo-400 transition-opacity',
                        isActive ? 'opacity-100' : 'opacity-0',
                      )}
                    />
                  ) : null}
                  <span
                    className={clsx(
                      'material-symbols-outlined shrink-0 text-[20px]',
                      !isActive && 'group-hover:text-white',
                    )}
                    style={{ fontVariationSettings: isActive ? "'FILL' 1" : "'FILL' 0" }}
                  >
                    {item.icon}
                  </span>
                  {!isCollapsed ? <span className="truncate">{item.label}</span> : null}
                </>
              )}
            </NavLink>
          ))}
        </div>
      </nav>

      <div className="border-t border-white/8 px-3 py-4">
        <button
          type="button"
          onClick={() => setIsCollapsed(!isCollapsed)}
          className={clsx(
            'flex w-full items-center gap-3 rounded-2xl border border-white/10 bg-white/4 px-3 py-3 text-[13px] text-white/68 transition-colors hover:bg-white/8 hover:text-white',
            isCollapsed && 'justify-center px-2',
          )}
        >
          <span className="material-symbols-outlined text-[18px]">{isCollapsed ? 'keyboard_double_arrow_right' : 'keyboard_double_arrow_left'}</span>
          {!isCollapsed ? <span>收起侧边栏</span> : null}
        </button>
      </div>
    </aside>
  );
}

interface TopbarProps {
  isCollapsed: boolean;
  isDarkMode: boolean;
  toggleTheme: () => void;
  onLogout: () => Promise<void>;
  isAuthSubmitting: boolean;
}

/**
 * 顶部工具栏负责承载控制台全局搜索、主题切换、返回聊天和用户操作入口。
 */
function Topbar({ isCollapsed, isDarkMode, toggleTheme, onLogout, isAuthSubmitting }: TopbarProps) {
  const [showUserMenu, setShowUserMenu] = useState(false);

  return (
    <header className="sticky top-0 z-30 border-b border-border-hairline bg-surface-container-lowest/88 px-6 py-4 backdrop-blur-xl">
      <div className="flex items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-4">
          <div className="relative max-w-[480px] flex-1">
            <span className="material-symbols-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[20px] text-secondary">
              search
            </span>
            <input
              placeholder="搜索知识库、会话或工作空间"
              className="h-11 w-full rounded-2xl border border-border-hairline bg-surface px-11 pr-28 text-body-sm text-ink outline-none transition-colors placeholder:text-secondary/75 focus:border-border-strong"
            />
            <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 rounded-full border border-border-hairline bg-surface-container-low px-2 py-1 text-[11px] text-secondary">
              Ctrl K
            </span>
          </div>
          <div className="hidden text-[12px] text-secondary xl:block">
            {isCollapsed ? '紧凑导航' : '完整导航'}
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            className="inline-flex items-center gap-2 rounded-2xl border border-border-hairline bg-surface px-4 py-2 text-[13px] text-ink transition-colors hover:bg-surface-container-low"
          >
            <span className="material-symbols-outlined text-[18px]">forum</span>
            返回聊天
          </button>
          <button
            type="button"
            onClick={toggleTheme}
            className="inline-flex h-11 w-11 items-center justify-center rounded-2xl border border-border-hairline bg-surface text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
            title={isDarkMode ? '切换为浅色模式' : '切换为深色模式'}
          >
            <span className="material-symbols-outlined text-[20px]">{isDarkMode ? 'light_mode' : 'dark_mode'}</span>
          </button>
          <div className="relative">
            <button
              type="button"
              onClick={() => setShowUserMenu((previous) => !previous)}
              className="inline-flex items-center gap-3 rounded-full border border-border-hairline bg-surface px-2.5 py-1.5 text-[13px] text-ink shadow-sm transition-colors hover:bg-surface-container-low"
            >
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-amber-100 to-orange-100 text-[13px] font-semibold text-[#7a3d00]">
                A
              </span>
              <span className="hidden min-w-0 text-left sm:block">
                <span className="block truncate font-medium">admin</span>
                <span className="block truncate text-[11px] text-secondary">管理员</span>
              </span>
              <span className="material-symbols-outlined text-[18px] text-secondary">expand_more</span>
            </button>
            <AnimatePresence>
              {showUserMenu ? (
                <motion.div
                  initial={{ opacity: 0, y: 10, scale: 0.96 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: 10, scale: 0.96 }}
                  transition={{ duration: 0.16 }}
                  className="absolute right-0 top-[calc(100%+10px)] w-52 rounded-2xl border border-border-hairline bg-surface-container-lowest p-2 shadow-[0_18px_40px_rgba(15,23,42,0.16)]"
                >
                  <button
                    type="button"
                    onClick={() => {
                      setShowUserMenu(false);
                      toggleTheme();
                    }}
                    className="flex w-full items-center gap-3 rounded-xl px-3 py-2 text-left text-[13px] text-ink transition-colors hover:bg-surface-container-low"
                  >
                    <span className="material-symbols-outlined text-[18px] text-secondary">{isDarkMode ? 'light_mode' : 'dark_mode'}</span>
                    {isDarkMode ? '切换为浅色模式' : '切换为深色模式'}
                  </button>
                  <button
                    type="button"
                    onClick={async () => {
                      setShowUserMenu(false);
                      await onLogout();
                    }}
                    disabled={isAuthSubmitting}
                    className="mt-1 flex w-full items-center gap-3 rounded-xl px-3 py-2 text-left text-[13px] text-error transition-colors hover:bg-error-container disabled:opacity-60"
                  >
                    <span className="material-symbols-outlined text-[18px]">logout</span>
                    {isAuthSubmitting ? '退出中...' : '退出登录'}
                  </button>
                </motion.div>
              ) : null}
            </AnimatePresence>
          </div>
        </div>
      </div>
    </header>
  );
}

interface LayoutProps {
  onLogout: () => Promise<void>;
  isAuthSubmitting: boolean;
}

/**
 * 渲染管理端主布局，并把参考控制台样式统一收口到侧边栏与顶部工具栏中。
 */
export function Layout({ onLogout, isAuthSubmitting }: LayoutProps) {
  const location = useLocation();
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [isDarkMode, setIsDarkMode] = useState(() => document.documentElement.classList.contains('dark'));

  React.useEffect(() => {
    const isDark =
      localStorage.getItem('theme') === 'dark'
      || (!('theme' in localStorage) && window.matchMedia('(prefers-color-scheme: dark)').matches);
    if (isDark) {
      document.documentElement.classList.add('dark');
      setIsDarkMode(true);
    } else {
      document.documentElement.classList.remove('dark');
      setIsDarkMode(false);
    }
  }, []);

  const toggleTheme = () => {
    const nextDarkMode = !isDarkMode;
    setIsDarkMode(nextDarkMode);
    if (nextDarkMode) {
      document.documentElement.classList.add('dark');
      localStorage.setItem('theme', 'dark');
    } else {
      document.documentElement.classList.remove('dark');
      localStorage.setItem('theme', 'light');
    }
  };

  return (
    <div className="flex h-screen overflow-hidden bg-background text-on-background transition-colors duration-300">
      <Sidebar isCollapsed={isCollapsed} setIsCollapsed={setIsCollapsed} />
      <div
        className={clsx(
          'relative flex h-screen min-h-0 flex-1 flex-col transition-all duration-300',
          isCollapsed ? 'ml-[84px]' : 'ml-[240px]',
        )}
      >
        <Topbar
          isCollapsed={isCollapsed}
          isDarkMode={isDarkMode}
          toggleTheme={toggleTheme}
          onLogout={onLogout}
          isAuthSubmitting={isAuthSubmitting}
        />
        <main className="relative flex-1 min-h-0 w-full overflow-y-auto overflow-x-hidden bg-[radial-gradient(circle_at_top_left,rgba(96,165,250,0.08),transparent_26%),linear-gradient(180deg,var(--theme-surface-container-low)_0%,var(--theme-background)_100%)]">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.18 }}
              className="h-full min-h-full"
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
    </div>
  );
}
