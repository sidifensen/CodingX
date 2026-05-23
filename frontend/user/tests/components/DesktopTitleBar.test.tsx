import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import DesktopTitleBar from '@/components/DesktopTitleBar';
import { HostContext } from '@/host/types';

const bridgeMock = {
  getContext: vi.fn(),
  getWindowState: vi.fn(async () => ({
    isMaximized: false,
    isMinimized: false,
    isFullScreen: false,
  })),
  minimizeWindow: vi.fn(async () => undefined),
  toggleMaximizeWindow: vi.fn(async () => ({
    isMaximized: true,
    isMinimized: false,
    isFullScreen: false,
  })),
  closeWindow: vi.fn(async () => undefined),
  onWindowStateChanged: vi.fn(() => () => undefined),
  pickRepositoryDirectory: vi.fn(async () => null),
  bindRepositoryPath: vi.fn(async () => ({
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
      windowControls: true,
    },
    localResource: {
      boundRepositoryPath: null,
      permissionGranted: false,
    },
  })),
  requestFileAccess: vi.fn(async () => false),
  listDirectory: vi.fn(async () => []),
  invokeDesktopMenuAction: vi.fn(async () => undefined),
};

vi.mock('@/host/bridge', () => ({
  resolveHostBridge: () => bridgeMock,
}));

function createDesktopHostContext(): HostContext {
  return {
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
      windowControls: true,
    },
    localResource: {
      boundRepositoryPath: 'D:/code/CodingX',
      permissionGranted: true,
    },
  };
}

describe('DesktopTitleBar desktop menu', () => {
  it('应在桌面宿主展示编辑、窗口、帮助菜单入口', () => {
    render(<DesktopTitleBar hostContext={createDesktopHostContext()} />);

    expect(screen.getByRole('button', { name: '编辑' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '窗口' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '帮助' })).toBeInTheDocument();
  });

  it('应在帮助菜单点击开发工具后触发宿主菜单动作', async () => {
    render(<DesktopTitleBar hostContext={createDesktopHostContext()} />);

    fireEvent.click(screen.getByRole('button', { name: '帮助' }));
    fireEvent.click(await screen.findByRole('button', { name: '开发工具' }));

    await waitFor(() => {
      expect(bridgeMock.invokeDesktopMenuAction).toHaveBeenCalledWith('toggle-dev-tools');
    });
  });
});
