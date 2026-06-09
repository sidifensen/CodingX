import React, { useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import clsx from 'clsx';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  ApartmentOutlined,
  AppstoreOutlined,
  BellOutlined,
  BulbOutlined,
  CodeOutlined,
  DashboardOutlined,
  FolderOpenOutlined,
  LikeOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MoonOutlined,
  NodeIndexOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SettingOutlined,
  SunOutlined,
  TeamOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { Avatar, Button, Dropdown } from 'antd';
import type { MenuProps } from 'antd';

const navItems = [
  { path: '/', icon: <DashboardOutlined />, label: '工作台' },
  { path: '/users', icon: <TeamOutlined />, label: '用户管理' },
  { path: '/tasks', icon: <ProfileOutlined />, label: '会话管理' },
  { path: '/workspaces', icon: <FolderOpenOutlined />, label: '工作空间' },
  { path: '/skills', icon: <AppstoreOutlined />, label: '技能管理' },
  { path: '/experts', icon: <BulbOutlined />, label: '专家管理' },
  { path: '/tools', icon: <ToolOutlined />, label: '工具管理' },
  { path: '/governance', icon: <SafetyCertificateOutlined />, label: '治理中心' },
  { path: '/mcp', icon: <CodeOutlined />, label: 'MCP管理' },
  { path: '/traces', icon: <NodeIndexOutlined />, label: 'Trace管理' },
  { path: '/feedbacks', icon: <LikeOutlined />, label: '反馈管理' },
  { path: '/intent-tree', icon: <ApartmentOutlined />, label: '意图树' },
  { path: '/query-term-mappings', icon: <SearchOutlined />, label: '关键词映射' },
  { path: '/settings', icon: <SettingOutlined />, label: '系统配置' },
  { path: '/notifications', icon: <BellOutlined />, label: '通知中心' },
];

/**
 * 管理端侧边栏品牌图标复用浏览器页 favicon，保持用户端、管理端与桌面壳品牌识别一致。
 */
const ADMIN_BRAND_ICON_SRC = '/brand-favicon.svg';

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
  const userMenuItems: MenuProps['items'] = [
    {
      key: 'theme',
      icon: isDarkMode ? <SunOutlined /> : <MoonOutlined />,
      label: isDarkMode ? '切换为浅色模式' : '切换为深色模式',
    },
    {
      key: 'logout',
      danger: true,
      disabled: isAuthSubmitting,
      icon: <LogoutOutlined />,
      label: isAuthSubmitting ? '退出中...' : '退出登录',
    },
  ];

  /**
   * AntD Dropdown 统一承载主题与登出操作，避免侧栏共享壳层继续维护自绘浮层。
   */
  const handleUserMenuClick: MenuProps['onClick'] = async ({ key }) => {
    if (key === 'theme') {
      toggleTheme();
      return;
    }
    if (key === 'logout') {
      await onLogout();
    }
  };

  return (
    <aside
      className={clsx(
        'h-screen fixed left-0 top-0 bg-surface border-r border-border-hairline flex flex-col py-md z-50 transition-all duration-300',
        isCollapsed ? 'w-[68px]' : 'w-[200px]',
      )}
    >
      <div className={clsx('mb-xl flex h-[52px] items-center', isCollapsed ? 'justify-between px-1' : 'justify-between px-md')}>
        <div className="flex min-w-0 items-center gap-xs">
          <img
            src={ADMIN_BRAND_ICON_SRC}
            alt=""
            aria-hidden="true"
            data-testid="admin-brand-logo"
            className={clsx('shrink-0 rounded-md object-contain', isCollapsed ? 'h-7 w-7' : 'h-8 w-8')}
          />
          <div
            className={clsx(
              'flex flex-col justify-center overflow-hidden whitespace-nowrap transition-all duration-300',
              isCollapsed ? 'w-0 opacity-0' : 'w-[120px] opacity-100',
            )}
          >
            <h1 className="font-headline-sm text-headline-sm font-bold text-ink tracking-tight">CodingX</h1>
            <p className="font-label-caps text-label-caps text-secondary uppercase tracking-widest mt-1">管理后台</p>
          </div>
        </div>
        <Button
          aria-label={isCollapsed ? '展开菜单' : '收起菜单'}
          icon={isCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          onClick={() => setIsCollapsed(!isCollapsed)}
          className={clsx('shrink-0', isCollapsed && 'h-7 w-7 p-0')}
          title={isCollapsed ? '展开菜单' : '收起菜单'}
          type="text"
        />
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
                <span className={clsx('text-[20px] shrink-0', !isActive && 'group-hover:text-ink transition-colors')}>
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
        <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenuClick }} placement="topLeft" trigger={['click']}>
          <Button
            aria-label="打开管理员菜单"
            block
            className={clsx('h-auto justify-start p-xs', isCollapsed && 'justify-center px-0')}
            type="text"
          >
            <Avatar
              alt="Admin"
              className="shrink-0 border border-border-hairline"
              size={32}
              src="https://lh3.googleusercontent.com/aida-public/AB6AXuC2qit7t9_Op2BaG4q_XjoEvUAYkJURwqCFfB87WntE80FvjvZIhLKxM6ywbUA4jcYsmW8YKSzsMu4OhCy55GnLGcY1R3ZhovU6LrpiG-x1RvceypvpHAz11cx-MWR5GBBQWcla3xMHN5Pwp7_vWacYmOakdSnmHSvK0BXO2ps2b6qAZn4hTwNSopDeHE17Kt8tEuYKdDeEJZeSuOrVJbDz23qACFMPNAgIlgBlAS8wUOI_wiGtPh7SAGbvDa0sj6mfJEp1Mslec_A"
            />
            <div className={clsx('overflow-hidden whitespace-nowrap transition-all duration-300', isCollapsed ? 'w-0 opacity-0' : 'w-[120px] opacity-100')}>
              <p className="font-bold text-ink font-body-sm text-body-sm truncate">管理员</p>
              <p className="text-[10px] text-secondary truncate">admin@codingx.io</p>
            </div>
          </Button>
        </Dropdown>
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
        data-testid="admin-main-shell"
        className={clsx(
          'relative flex h-screen min-h-0 min-w-0 flex-col transition-all duration-300',
          // 侧边栏固定定位不参与文档流，主壳层必须扣除侧栏宽度，避免 w-full + margin-left 撑出页面级横向溢出。
          isCollapsed ? 'ml-[68px] w-[calc(100vw-68px)]' : 'ml-[200px] w-[calc(100vw-200px)]',
        )}
      >
        <main className="relative min-h-0 min-w-0 flex-1 overflow-y-auto overflow-x-hidden">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
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
