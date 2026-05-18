import React, { useState, useEffect } from 'react';
import { Menu, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { AnimatePresence } from 'motion/react';

// 视图组件
import ChatView from './views/ChatView';
import AutomationView from './views/AutomationView';
import McpView from './views/McpView';
import SkillsView from './views/SkillsView';
import ExpertsView from './views/ExpertsView';
import Sidebar from './components/Sidebar';
import LoginModal from './components/auth/LoginModal';
import DesktopTitleBar from './components/DesktopTitleBar';
import { useAuth } from './hooks/useAuth';
import { useChatWorkspace } from './views/chat/useChatWorkspace';
import { useHostContext } from './host/useHostContext';

/**
 * 定义应用支持的主视图类型。
 */
export type ViewType = 'chat' | 'mcp' | 'skills' | 'experts' | 'automation';

/**
 * 渲染前端应用壳层，并管理视图、主题、移动端导航与登录弹窗状态。
 */
export default function App() {
  // 步骤：维护当前激活的主视图。
  const [activeView, setActiveView] = useState<ViewType>('chat');
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
  // 步骤：由应用壳层统一持有聊天工作区状态，确保 Sidebar 与主区共用同一份真实会话数据。
  const chatWorkspace = useChatWorkspace(isAuthenticated, {
    onUnauthorized: handleUnauthorized,
  });
  // 步骤：读取当前宿主能力上下文，为侧边栏和后续本地能力入口提供统一数据源。
  const {
    hostContext,
    isLoading: _isHostContextLoading,
    errorMessage: _hostContextError,
    pickRepositoryDirectory: _pickRepositoryDirectory,
  } = useHostContext();
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
  const handleConversationSelect = async (conversationId: string) => {
    setActiveView('chat');
    await chatWorkspace.selectConversation(conversationId, chatWorkspace.conversations);
  };

  /**
   * 统一处理侧边栏“新建对话”动作，回到聊天首页空态。
   */
  const handleStartNewConversation = async () => {
    setActiveView('chat');
    await chatWorkspace.startNewConversation();
  };

  /**
   * 统一处理会话重命名，保持主会话视图不切换。
   * @param conversationId 会话标识。
   * @param title 新标题。
   */
  const handleRenameConversation = async (conversationId: string, title: string) => {
    chatWorkspace.renameDialog.open(conversationId, title);
  };

  /**
   * 统一处理会话删除。
   * @param conversationId 会话标识。
   */
  const handleDeleteConversation = async (conversationId: string) => {
    const conversation = chatWorkspace.conversations.find((item) => item.id === conversationId);
    chatWorkspace.deleteDialog.open(conversationId, conversation?.title ?? '');
  };

  /**
   * 切换桌面端左侧边栏折叠状态。
   */
  const toggleDesktopSidebar = () => {
    setIsDesktopSidebarCollapsed((current) => !current);
  };

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
          setActiveView={setActiveView}
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
              {activeView === 'experts' && <ExpertsView key="experts" />}
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
