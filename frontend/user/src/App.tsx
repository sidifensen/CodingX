import React, { useState, useEffect } from 'react';
import { Menu, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { AnimatePresence } from 'motion/react';

// 视图组件
import ChatView, { SharedChatView } from './views/ChatView';
import AutomationView from './views/AutomationView';
import McpView from './views/McpView';
import SkillsView from './views/SkillsView';
import ExpertsView from './views/ExpertsView';
import CliLoginView from './views/CliLoginView';
import Sidebar from './components/Sidebar';
import LoginModal from './components/auth/LoginModal';
import DesktopTitleBar from './components/DesktopTitleBar';
import { useAuth } from './hooks/useAuth';
import { useChatWorkspace } from './views/chat/useChatWorkspace';
import { useHostContext } from './host/useHostContext';
import {
  ConversationActionContext,
  ConversationExportFormat,
  WorkspaceConversationCreateContext,
  WorkspaceConversationSelectionContext,
} from './views/chat/types';

/**
 * 定义应用支持的主视图类型。
 */
export type ViewType = 'chat' | 'mcp' | 'skills' | 'experts' | 'automation';

const VIEW_ROUTE_PATHS: Record<ViewType, string> = {
  chat: '/',
  mcp: '/mcp',
  skills: '/skills',
  experts: '/experts',
  automation: '/automation',
};
const PATH_VIEW_MAP: Record<string, ViewType> = Object.entries(VIEW_ROUTE_PATHS).reduce(
  (viewMap, [view, path]) => ({
    ...viewMap,
    [path]: view as ViewType,
  }),
  {} as Record<string, ViewType>,
);
const CONVERSATION_ID_QUERY_KEY = 'conversationId';

export default function App() {
  // CLI 登录页是独立授权面，不启动聊天工作区，避免无关接口请求干扰终端登录。
  if (isCliLoginRoute(window.location.pathname)) {
    return <CliLoginView />;
  }
  return <MainApp />;
}

/**
 * 渲染前端应用壳层，并管理视图、主题、移动端导航与登录弹窗状态。
 */
function MainApp() {
  // 步骤：维护当前激活的主视图，首次进入时从地址栏路径恢复页面级路由。
  const [activeView, setActiveView] = useState<ViewType>(() => parseViewRoute(window.location.pathname));
  // 步骤：维护当前深色主题开关状态。
  const [isDarkMode, setIsDarkMode] = useState(true);
  // 步骤：维护移动端侧边栏开关状态。
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  // 步骤：维护桌面端左侧边栏折叠状态，保证聊天内容区可获得更大可视宽度。
  const [isDesktopSidebarCollapsed, setIsDesktopSidebarCollapsed] = useState(false);
  // 步骤：维护登录弹窗显示状态。
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  // 步骤：在开发环境预填默认账号密码，降低本地联调成本。
  const isDevelopmentMode = import.meta.env.DEV;
  const loginDefaultUsername = isDevelopmentMode ? 'admin' : '';
  const loginDefaultPassword = isDevelopmentMode ? '123456' : '';
  const sharedRoute = parseSharedChatRoute(window.location.pathname, window.location.search);

  // 步骤：聚合认证相关状态和操作，复用组件化登录流程。
  const {
    session,
    isAuthenticated,
    isSubmitting,
    errorMessage,
    login,
    logout,
    clearErrorMessage,
    invalidateSession,
  } = useAuth();
  /**
   * 聊天接口返回未登录时，统一回收登录态并立刻拉起登录弹窗。
   */
  const handleUnauthorized = () => {
    invalidateSession();
    setIsLoginModalOpen(true);
  };
  // 步骤：读取当前宿主能力上下文，为侧边栏和后续本地能力入口提供统一数据源。
  const {
    hostContext,
    isLoading: _isHostContextLoading,
    errorMessage: _hostContextError,
    pickRepositoryDirectory: _pickRepositoryDirectory,
    bindWorkspacePath: _bindWorkspacePath,
  } = useHostContext();
  // 步骤：把宿主上下文与本地选择能力注入聊天工作区，避免多份状态分裂。
  const chatWorkspace = useChatWorkspace(isAuthenticated, {
    onUnauthorized: handleUnauthorized,
    hostContext,
    pickRepositoryDirectory: _pickRepositoryDirectory,
    bindWorkspacePath: _bindWorkspacePath,
  });
  // 步骤：仅在桌面宿主且支持窗口控制时启用自定义标题栏占位高度。
  const hasDesktopTitleBar =
    hostContext?.hostType === 'desktop' && Boolean(hostContext.capabilities.windowControls);

  useEffect(() => {
    // 步骤：初始化主题样式。
    if (isDarkMode) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }, [isDarkMode]);

  useEffect(() => {
    // 浏览器前进/后退只改变 history，必须同步回 React 视图状态。
    const handlePopState = () => {
      setActiveView(parseViewRoute(window.location.pathname));
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  /**
   * 切换页面级视图并同步浏览器地址栏，避免会话参数残留在功能页路由上。
   * @param nextView 目标主视图。
   */
  const navigateToView = (nextView: ViewType) => {
    setActiveView(nextView);
    writeViewRouteToUrl(nextView);
  };

  /**
   * 切换当前主题模式。
   */
  const toggleTheme = () => {
    // 步骤：翻转主题布尔值，触发全局主题切换。
    setIsDarkMode(!isDarkMode);
  };

  /**
   * 打开登录弹窗并重置历史错误。
   */
  const openLoginModal = () => {
    // 步骤：先清空历史认证错误，再打开弹窗。
    clearErrorMessage();
    setIsLoginModalOpen(true);
  };

  /**
   * 关闭登录弹窗并清理错误提示。
   */
  const closeLoginModal = () => {
    // 步骤：关闭弹窗时同步清理错误，避免下次打开时残留旧提示。
    setIsLoginModalOpen(false);
    clearErrorMessage();
  };

  /**
   * 提交登录表单并在成功后关闭弹窗。
   * @param payload 登录账号与密码。
   */
  const handleLoginSubmit = async (payload: { username: string; password: string }) => {
    // 步骤：调用统一登录能力；仅成功时关闭弹窗，失败时保留弹窗供用户修改。
    try {
      await login(payload);
      setIsLoginModalOpen(false);
    } catch {
      // 步骤：登录失败时错误文案已由 useAuth 维护，这里无需额外处理。
    }
  };

  /**
   * 统一处理侧边栏点击真实会话后的主区切换。
   * @param conversationId 被点击的会话标识。
   */
  const handleConversationSelect = async (
    conversationId: string,
    selectionContext: WorkspaceConversationSelectionContext,
  ) => {
    navigateToView('chat');
    // 先切到会话所属空间，再加载目标会话，避免不同空间会话互相串线。
    await chatWorkspace.selectConversationInWorkspace(conversationId, selectionContext);
  };

  /**
   * 统一处理侧边栏“新建对话”动作，回到聊天首页空态。
   */
  const handleStartNewConversation = async (
    createContext?: WorkspaceConversationCreateContext,
  ) => {
    navigateToView('chat');
    await chatWorkspace.startNewConversation(createContext);
  };

  /**
   * 统一处理会话重命名，保持主会话视图不切换。
   * @param conversationId 会话标识。
   * @param title 新标题。
   */
  const handleRenameConversation = async (
    conversationId: string,
    title: string,
    actionContext?: ConversationActionContext,
  ) => {
    chatWorkspace.renameDialog.open(conversationId, title, actionContext);
  };

  /**
   * 统一处理会话删除。
   * @param conversationId 会话标识。
   */
  const handleDeleteConversation = async (
    conversationId: string,
    actionContext?: ConversationActionContext,
  ) => {
    const sourceGroup = actionContext
      ? chatWorkspace.workspaceGroups.find((group) => group.partitionKey === actionContext.partitionKey)
      : null;
    const sourceConversations = sourceGroup?.conversations ?? chatWorkspace.conversations;
    const conversation = sourceConversations.find((item) => item.id === conversationId);
    chatWorkspace.deleteDialog.open(conversationId, conversation?.title ?? '', actionContext);
  };

  /**
   * 统一处理会话分享，复用工作区分享链路，避免侧栏菜单重复拼接链接。
   * @param conversationId 会话标识。
   */
  const handleShareConversation = async (
    conversationId: string,
    actionContext?: ConversationActionContext,
  ) => {
    navigateToView('chat');
    if (actionContext) {
      // 侧栏可能从非当前分区触发分享，必须先打开目标会话再让聊天区进入轮次选择。
      await chatWorkspace.selectConversationInWorkspace(conversationId, actionContext);
    } else if (chatWorkspace.activeConversationId !== conversationId) {
      await chatWorkspace.selectConversation(conversationId, chatWorkspace.conversations);
    }
    window.dispatchEvent(
      new CustomEvent('codingx:start-share-conversation', {
        detail: {
          conversationId,
        },
      }),
    );
    return '';
  };

  /**
   * 统一处理会话置顶切换，确保侧栏动作带上真实分组上下文。
   * @param conversationId 会话标识。
   * @param actionContext 会话所属分组上下文。
   */
  const handleToggleConversationPin = async (
    conversationId: string,
    actionContext: ConversationActionContext,
  ) => {
    return chatWorkspace.toggleConversationPin(conversationId, actionContext);
  };

  /**
   * 统一处理单会话导出。
   * @param conversationId 会话标识。
   * @param format 导出格式。
   * @param actionContext 会话所属分组上下文。
   */
  const handleExportConversation = async (
    conversationId: string,
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => {
    await chatWorkspace.exportConversation(conversationId, format, actionContext);
  };

  /**
   * 统一处理批量删除。
   * @param conversationIds 会话标识列表。
   * @param actionContext 会话所属分组上下文。
   */
  const handleDeleteConversations = async (
    conversationIds: string[],
    actionContext: ConversationActionContext,
  ) => {
    await chatWorkspace.deleteConversations(conversationIds, actionContext);
  };

  /**
   * 统一处理批量导出。
   * @param conversationIds 会话标识列表。
   * @param format 导出格式。
   * @param actionContext 会话所属分组上下文。
   */
  const handleExportConversations = async (
    conversationIds: string[],
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => {
    await chatWorkspace.exportConversations(conversationIds, format, actionContext);
  };

  /**
   * 切换桌面端左侧边栏折叠状态。
   */
  const toggleDesktopSidebar = () => {
    setIsDesktopSidebarCollapsed((current) => !current);
  };

  if (sharedRoute) {
    return <SharedChatView shareToken={sharedRoute.shareToken} messageIds={sharedRoute.messageIds} />;
  }

  return (
    <div className="relative h-screen w-full overflow-hidden bg-background text-foreground transition-colors duration-300">
      <DesktopTitleBar hostContext={hostContext} />
      {/* 抽屉推动容器 */}
      <div
        className={`flex w-full transition-transform duration-400 ease-[cubic-bezier(0.32,0.72,0,1)] ${
          hasDesktopTitleBar ? 'h-[calc(100%-2.25rem)]' : 'h-full'
        } ${
          isMobileMenuOpen ? 'translate-x-72 md:translate-x-0' : 'translate-x-0'
        }`}
      >
          <Sidebar
            activeView={activeView}
            setActiveView={navigateToView}
          isMobileMenuOpen={isMobileMenuOpen}
          setIsMobileMenuOpen={setIsMobileMenuOpen}
          isDesktopCollapsed={isDesktopSidebarCollapsed}
          isDarkMode={isDarkMode}
          toggleTheme={toggleTheme}
          authSession={session}
          isAuthSubmitting={isSubmitting}
          onOpenLogin={openLoginModal}
          onLogout={logout}
          conversations={chatWorkspace.conversations}
          activeConversationId={chatWorkspace.activeConversationId}
          onSelectConversation={handleConversationSelect}
          onStartNewConversation={handleStartNewConversation}
          onRenameConversation={handleRenameConversation}
          onDeleteConversation={handleDeleteConversation}
          onShareConversation={handleShareConversation}
          onToggleConversationPin={handleToggleConversationPin}
          onExportConversation={handleExportConversation}
          onExportConversations={handleExportConversations}
          onDeleteConversations={handleDeleteConversations}
          workspaceGroups={chatWorkspace.workspaceGroups}
          activeWorkspacePartitionKey={chatWorkspace.activeWorkspacePartitionKey}
          onSelectWorkspacePath={chatWorkspace.setActiveWorkspacePath}
          onPickRepositoryDirectory={chatWorkspace.pickRepositoryDirectory}
          workspaceLabel={chatWorkspace.workspaceLabel}
        />

        {/* 主内容区域 */}
        <main className="flex-1 min-w-[100vw] md:min-w-0 flex flex-col h-full bg-background overflow-hidden relative shadow-[0_0_40px_rgba(0,0,0,0.1)] md:shadow-none transition-transform">
          {/* 移动端遮罩层 */}
          <div
            className={`absolute inset-0 bg-background/5 backdrop-blur-[2px] z-40 md:hidden transition-all duration-400 ${
              isMobileMenuOpen ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
            }`}
            onClick={() => setIsMobileMenuOpen(false)}
          />

          {/* 移动端顶部栏 */}
          <header className="md:hidden flex items-center justify-between px-4 h-14 border-b border-border bg-background z-30 shrink-0">
            <button
              onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
              className="p-1 -ml-1 text-foreground active:scale-90 transition-all rounded"
            >
              <Menu size={24} />
            </button>
            <span className="font-bold tracking-tight text-foreground text-[17px] absolute left-1/2 -translate-x-1/2">
              CodingX
            </span>
            <div className="w-8 flex justify-end"></div>
          </header>

          {/* 动态视图内容 */}
          <div className="flex-1 overflow-hidden relative">
            <button
              type="button"
              aria-label={isDesktopSidebarCollapsed ? '展开左侧边栏' : '折叠左侧边栏'}
              onClick={toggleDesktopSidebar}
              className="absolute left-4 top-4 z-30 hidden h-10 w-10 cursor-pointer items-center justify-center rounded-xl border border-border bg-surface/92 text-foreground shadow-[0_14px_30px_rgba(0,0,0,0.18)] backdrop-blur md:flex"
            >
              {isDesktopSidebarCollapsed ? (
                <PanelLeftOpen size={18} />
              ) : (
                <PanelLeftClose size={18} />
              )}
            </button>
            <AnimatePresence mode="wait">
              {activeView === 'chat' && (
                <ChatView
                  key="chat"
                  isAuthenticated={isAuthenticated}
                  onRequireLogin={openLoginModal}
                  isDesktopSidebarCollapsed={isDesktopSidebarCollapsed}
                  workspace={chatWorkspace}
                />
              )}
              {activeView === 'mcp' && (
                <McpView
                  key="mcp"
                  availableMcps={chatWorkspace.availableMcps}
                  selectedMcpCodes={chatWorkspace.selectedMcpCodes}
                  mcpConnected={chatWorkspace.mcpConnected}
                  setSelectedMcpCodes={chatWorkspace.setSelectedMcpCodes}
                  setMcpConnected={chatWorkspace.setMcpConnected}
                />
              )}
              {activeView === 'automation' && <AutomationView key="automation" />}
              {activeView === 'skills' && <SkillsView key="skills" />}
              {activeView === 'experts' && (
                <ExpertsView
                  key="experts"
                  workspace={chatWorkspace}
                  onSelectExpert={() => navigateToView('chat')}
                />
              )}
            </AnimatePresence>
          </div>
        </main>
      </div>

      {/* 登录弹窗 */}
      <LoginModal
        isOpen={isLoginModalOpen}
        isSubmitting={isSubmitting}
        errorMessage={errorMessage}
        defaultUsername={loginDefaultUsername}
        defaultPassword={loginDefaultPassword}
        onClose={closeLoginModal}
        onSubmit={handleLoginSubmit}
      />
    </div>
  );
}

/**
 * 根据路径解析页面级视图，未知路径回落到聊天页，避免刷新后空白。
 * @param pathname 当前浏览器路径。
 * @returns 可渲染的主视图类型。
 */
function parseViewRoute(pathname: string): ViewType {
  return PATH_VIEW_MAP[pathname] ?? 'chat';
}

/**
 * 将页面级视图写入地址栏；非聊天页必须移除会话参数，避免功能页看起来仍停留在某个会话。
 * @param view 目标主视图。
 */
function writeViewRouteToUrl(view: ViewType) {
  const nextUrl = new URL(window.location.href);
  nextUrl.pathname = VIEW_ROUTE_PATHS[view];
  if (view !== 'chat') {
    nextUrl.searchParams.delete(CONVERSATION_ID_QUERY_KEY);
  } else if (nextUrl.pathname !== window.location.pathname) {
    nextUrl.searchParams.delete(CONVERSATION_ID_QUERY_KEY);
  }
  window.history.pushState(window.history.state, '', nextUrl.toString());
}

/**
 * 从浏览器地址解析公开分享路由，兼容千问风格 `/share/chat/{token}`。
 * 分享链接本身只保留 token；`messages` 查询参数仅作为旧链接和手工传入的兼容入口。
 * @param pathname 当前路径。
 * @param search 查询参数。
 * @returns 分享路由参数或 null。
 */
function parseSharedChatRoute(pathname: string, search: string) {
  const matched = pathname.match(/^\/share\/chat\/([^/]+)$/);
  if (!matched) {
    return null;
  }
  const searchParams = new URLSearchParams(search);
  const messageIds = (searchParams.get('messages') ?? '')
    .split(',')
    .map((messageId) => messageId.trim())
    .filter(Boolean);
  return {
    shareToken: decodeURIComponent(matched[1] ?? ''),
    messageIds,
  };
}

/**
 * 判断当前路径是否为 CLI 专用登录授权页。
 * @param pathname 当前浏览器路径。
 * @returns true 表示渲染 CLI 登录页。
 */
function isCliLoginRoute(pathname: string): boolean {
  return pathname === '/cli-login';
}
