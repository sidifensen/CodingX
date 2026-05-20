import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Sidebar from '@/components/Sidebar';
import { HostContext } from '@/host/types';
import { WorkspaceConversationSelectionContext } from '@/views/chat/types';

/**
 * 构建 Sidebar 测试所需的最小属性集。
 */
function createSidebarProps(overrides?: {
  hostContext?: HostContext;
}) {
  return {
    activeView: 'chat' as const,
    setActiveView: vi.fn(),
    isMobileMenuOpen: false,
    setIsMobileMenuOpen: vi.fn(),
    isDesktopCollapsed: false,
    isDarkMode: true,
    toggleTheme: vi.fn(),
    authSession: null,
    isAuthSubmitting: false,
    onOpenLogin: vi.fn(),
    onLogout: vi.fn(async () => undefined),
    conversations: [],
    activeConversationId: null,
    onSelectConversation: vi.fn(
      async (
        _conversationId: string,
        _selectionContext: WorkspaceConversationSelectionContext,
      ) => undefined,
    ),
    onStartNewConversation: vi.fn(async () => undefined),
    onRenameConversation: vi.fn(async () => undefined),
    onDeleteConversation: vi.fn(async () => undefined),
    workspaceGroups: [],
    activeWorkspacePartitionKey: null,
    onSelectWorkspacePath: vi.fn(async () => undefined),
    hostContext: overrides?.hostContext,
    isHostContextLoading: false,
    hostContextError: '',
  };
}

describe('Sidebar host capability panel', () => {
  it('应移除宿主能力信息卡片展示', () => {
    const props = createSidebarProps({
      hostContext: {
        hostType: 'desktop',
        executionTargets: ['cloud', 'local'],
        capabilities: {
          localFiles: true,
          localFolderPicker: true,
          shell: true,
          browserAutomation: true,
          desktopNotifications: true,
          officeInterop: true,
          localMcp: true,
        },
        localResource: {
          boundRepositoryPath: null,
          permissionGranted: false,
        },
      },
    });

    render(<Sidebar {...props} />);

    expect(screen.queryByText('宿主能力')).not.toBeInTheDocument();
    expect(screen.queryByText('桌面宿主')).not.toBeInTheDocument();
    expect(screen.queryByText('执行目标：')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '选择本地仓库目录' })).not.toBeInTheDocument();
  });

  it('应在 Web 宿主下同样不展示宿主信息文案', () => {
    const props = createSidebarProps({
      hostContext: {
        hostType: 'web',
        executionTargets: ['cloud'],
        capabilities: {
          localFiles: false,
          localFolderPicker: false,
          shell: false,
          browserAutomation: true,
          desktopNotifications: false,
          officeInterop: false,
          localMcp: false,
        },
      },
    });

    render(<Sidebar {...props} />);

    expect(screen.queryByText('Web 宿主')).not.toBeInTheDocument();
    expect(screen.queryByText('执行目标：cloud')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '选择本地仓库目录' })).not.toBeInTheDocument();
  });
});
