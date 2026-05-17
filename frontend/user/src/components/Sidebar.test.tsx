import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Sidebar from './Sidebar';
import { HostContext } from '../host/types';

/**
 * 构建 Sidebar 测试所需的最小属性集。
 */
function createSidebarProps(overrides?: {
  hostContext?: HostContext;
  onPickRepositoryDirectory?: () => Promise<void>;
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
    onSelectConversation: vi.fn(async () => undefined),
    onStartNewConversation: vi.fn(async () => undefined),
    onRenameConversation: vi.fn(async () => undefined),
    onDeleteConversation: vi.fn(async () => undefined),
    hostContext: overrides?.hostContext,
    isHostContextLoading: false,
    hostContextError: '',
    onPickRepositoryDirectory: overrides?.onPickRepositoryDirectory ?? vi.fn(async () => undefined),
  };
}

describe('Sidebar local resource entry', () => {
  it('应在桌面宿主下展示本地资源入口并支持选择仓库', async () => {
    const onPickRepositoryDirectory = vi.fn(async () => undefined);
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
      onPickRepositoryDirectory,
    });

    render(<Sidebar {...props} />);

    expect(screen.getByText('桌面宿主')).toBeInTheDocument();
    const pickButton = screen.getByRole('button', { name: '选择本地仓库目录' });
    fireEvent.click(pickButton);

    expect(onPickRepositoryDirectory).toHaveBeenCalledTimes(1);
  });

  it('应在 Web 宿主下隐藏本地资源入口', () => {
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

    expect(screen.getByText('Web 宿主')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '选择本地仓库目录' })).not.toBeInTheDocument();
  });
});
