import React, { useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import clsx from 'clsx';
import { NavLink, Outlet, useLocation } from 'react-router-dom';

const navItems = [
  { path: '/', icon: 'dashboard', label: '工作台' },
  { path: '/users', icon: 'group', label: '用户管理' },
  { path: '/tasks', icon: 'assignment', label: '任务管理' },
  { path: '/skills', icon: 'extension', label: '技能管理' },
  { path: '/mcp', icon: 'terminal', label: 'MCP管理' },
  { path: '/traces', icon: 'account_tree', label: 'Trace管理' },
  { path: '/feedbacks', icon: 'thumbs_up_down', label: '反馈管理' },
  { path: '/intent-tree', icon: 'schema', label: '意图树' },
  { path: '/query-term-mappings', icon: 'manage_search', label: '关键词映射' },
  { path: '/settings', icon: 'settings', label: '系统配置' },
  { path: '/notifications', icon: 'notifications', label: '通知中心' },
];

interface SidebarProps {
  isCollapsed: boolean;
  setIsCollapsed: (value: boolean) => void;
  isDarkMode: boolean;
  toggleTheme: () => void;
  onLogout: () => Promise<void>;
  isAuthSubmitting: boolean;
}

/**
 * 渲染管理端侧边栏，并承载主题切换与退出登录入口。
 */
function Sidebar({ isCollapsed, setIsCollapsed, isDarkMode, toggleTheme, onLogout, isAuthSubmitting }: SidebarProps) {
  const [showUserMenu, setShowUserMenu] = useState(false);

  return (
    <aside
      className={clsx(
        'h-screen fixed left-0 top-0 bg-surface border-r border-border-hairline flex flex-col py-md z-50 transition-all duration-300',
        isCollapsed ? 'w-[68px]' : 'w-[200px]',
      )}
    >
      <div className={clsx('mb-xl flex items-center h-[52px]', isCollapsed ? 'justify-center px-1' : 'justify-between px-md')}>
        <div
          className={clsx(
            'overflow-hidden whitespace-nowrap transition-all duration-300 flex flex-col justify-center',
            isCollapsed ? 'w-0 opacity-0' : 'w-[120px] opacity-100',
          )}
        >
          <h1 className="font-headline-sm text-headline-sm font-bold text-ink tracking-tight">CodingX</h1>
          <p className="font-label-caps text-label-caps text-secondary uppercase tracking-widest mt-1">管理后台</p>
        </div>
        <button
          onClick={() => setIsCollapsed(!isCollapsed)}
          className="text-secondary hover:text-ink transition-colors p-1 flex items-center justify-center rounded-md hover:bg-surface-container shrink-0"
          title={isCollapsed ? '展开菜单' : '收起菜单'}
        >
          <span className="material-symbols-outlined">{isCollapsed ? 'menu' : 'menu_open'}</span>
        </button>
      </div>
      <nav className="flex-1 space-y-1">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              clsx(
                'flex items-center transition-all duration-200 group py-2 relative overflow-hidden',
                isCollapsed ? 'justify-center mx-2 rounded-md' : 'px-sm gap-xs mx-2 rounded-md',
                isActive
                  ? 'bg-surface-container-low text-primary border-primary font-bold shadow-sm'
                  : 'text-secondary hover:bg-surface-container',
              )
            }
            title={isCollapsed ? item.label : undefined}
          >
            {({ isActive }) => (
              <>
                <span
                  className={clsx('material-symbols-outlined text-[20px] shrink-0', !isActive && 'group-hover:text-ink transition-colors')}
                  style={{ fontVariationSettings: isActive ? "'FILL' 1" : "'FILL' 0" }}
                >
                  {item.icon}
                </span>
                <span
                  className={clsx(
                    'font-body-sm text-body-sm whitespace-nowrap overflow-hidden transition-all duration-300',
                    isCollapsed ? 'w-0 opacity-0 ml-0' : 'w-[100px] opacity-100',
                    !isActive && 'group-hover:text-ink transition-colors',
                  )}
                >
                  {item.label}
                </span>
              </>
            )}
          </NavLink>
        ))}
      </nav>
      <div className={clsx('mt-auto pt-md border-t border-border-hairline', isCollapsed ? 'px-2' : 'px-sm')}>
        <div className="relative">
          <div
            className={clsx('flex items-center gap-sm p-xs rounded hover:bg-surface-container transition-colors cursor-pointer', isCollapsed && 'justify-center px-0')}
            onClick={() => setShowUserMenu(!showUserMenu)}
          >
            <img
              alt="Admin"
              className="w-8 h-8 rounded-full border border-border-hairline object-cover shrink-0"
              src="https://lh3.googleusercontent.com/aida-public/AB6AXuC2qit7t9_Op2BaG4q_XjoEvUAYkJURwqCFfB87WntE80FvjvZIhLKxM6ywbUA4jcYsmW8YKSzsMu4OhCy55GnLGcY1R3ZhovU6LrpiG-x1RvceypvpHAz11cx-MWR5GBBQWcla3xMHN5Pwp7_vWacYmOakdSnmHSvK0BXO2ps2b6qAZn4hTwNSopDeHE17Kt8tEuYKdDeEJZeSuOrVJbDz23qACFMPNAgIlgBlAS8wUOI_wiGtPh7SAGbvDa0sj6mfJEp1Mslec_A"
            />
            <div className={clsx('overflow-hidden whitespace-nowrap transition-all duration-300', isCollapsed ? 'w-0 opacity-0' : 'w-[120px] opacity-100')}>
              <p className="font-bold text-ink font-body-sm text-body-sm truncate">管理员</p>
              <p className="text-[10px] text-secondary truncate">admin@codingx.io</p>
            </div>
          </div>
          <AnimatePresence>
            {showUserMenu && (
              <motion.div
                initial={{ opacity: 0, y: 10, scale: 0.95 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 10, scale: 0.95 }}
                transition={{ duration: 0.15 }}
                className={clsx(
                  'absolute bottom-full mb-2 bg-surface-container-lowest border border-border-hairline rounded-lg shadow-lg py-1 z-50',
                  isCollapsed ? 'left-0 w-48' : 'left-0 w-full',
                )}
              >
                <button
                  onClick={() => {
                    toggleTheme();
                    setShowUserMenu(false);
                  }}
                  className="w-full text-left px-4 py-2 hover:bg-surface-container-low text-ink flex items-center gap-2 transition-colors"
                >
                  <span className="material-symbols-outlined text-[18px]">{isDarkMode ? 'light_mode' : 'dark_mode'}</span>
                  {isDarkMode ? '切换为浅色模式' : '切换为深色模式'}
                </button>
                <button
                  onClick={async () => {
                    setShowUserMenu(false);
                    await onLogout();
                  }}
                  disabled={isAuthSubmitting}
                  className="w-full text-left px-4 py-2 hover:bg-surface-container-low text-error flex items-center gap-2 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  <span className="material-symbols-outlined text-[18px]">logout</span>
                  {isAuthSubmitting ? '退出中...' : '退出登录'}
                </button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </aside>
  );
}

interface LayoutProps {
  onLogout: () => Promise<void>;
  isAuthSubmitting: boolean;
}

/**
 * 渲染管理端主布局，并承载侧边栏与页面内容切换动画。
 */
export function Layout({ onLogout, isAuthSubmitting }: LayoutProps) {
  const location = useLocation();
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [isDarkMode, setIsDarkMode] = useState(() => document.documentElement.classList.contains('dark'));

  React.useEffect(() => {
    const isDark =
      localStorage.getItem('theme') === 'dark' ||
      (!('theme' in localStorage) && window.matchMedia('(prefers-color-scheme: dark)').matches);
    if (isDark) {
      document.documentElement.classList.add('dark');
      setIsDarkMode(true);
    } else {
      document.documentElement.classList.remove('dark');
      setIsDarkMode(false);
    }
  }, []);

  const toggleTheme = () => {
    const newDark = !isDarkMode;
    setIsDarkMode(newDark);
    if (newDark) {
      document.documentElement.classList.add('dark');
      localStorage.setItem('theme', 'dark');
    } else {
      document.documentElement.classList.remove('dark');
      localStorage.setItem('theme', 'light');
    }
  };

  return (
    <div className="bg-surface font-body-sm text-body-sm text-on-surface selection:bg-sky-wash-start flex h-screen overflow-hidden transition-colors duration-300">
      <Sidebar
        isCollapsed={isCollapsed}
        setIsCollapsed={setIsCollapsed}
        isDarkMode={isDarkMode}
        toggleTheme={toggleTheme}
        onLogout={onLogout}
        isAuthSubmitting={isAuthSubmitting}
      />
      <div
        className={clsx(
          'relative flex h-screen min-h-0 w-full flex-1 flex-col transition-all duration-300',
          isCollapsed ? 'ml-[68px]' : 'ml-[200px]',
        )}
      >
        <main className="relative flex-1 min-h-0 w-full overflow-y-auto overflow-x-hidden">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.2 }}
              // 步骤：为需要锁定视口高度的页面提供稳定的 100% 高度参照，同时保留普通页面在 main 内继续滚动的能力。
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
