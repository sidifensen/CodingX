import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useHostContext } from './useHostContext';
import { ChatApi } from '../views/chat/chatApi';

const bridgeMock = {
  getContext: vi.fn(),
  getWindowState: vi.fn(),
  minimizeWindow: vi.fn(),
  toggleMaximizeWindow: vi.fn(),
  closeWindow: vi.fn(),
  invokeDesktopMenuAction: vi.fn(),
  onWindowStateChanged: vi.fn(() => () => undefined),
  pickRepositoryDirectory: vi.fn(),
  bindRepositoryPath: vi.fn(),
  requestFileAccess: vi.fn(),
  listDirectory: vi.fn(),
};

vi.mock('./bridge', () => ({
  resolveHostBridge: () => bridgeMock,
}));

vi.mock('../utils/authStorage', () => ({
  AuthStorage: {
    getSession: () => ({
      token: 'token-123',
      userId: '1001',
      username: 'admin',
      displayName: '管理员',
      userType: 'ADMIN',
    }),
  },
}));

describe('useHostContext', () => {
  /**
   * 选择目录并授权后，应同步调用后端工作区绑定接口。
   */
  it('should bind repository path to backend after desktop binding', async () => {
    bridgeMock.getContext.mockResolvedValue({
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
    });
    bridgeMock.pickRepositoryDirectory.mockResolvedValue('D:/code/codingx');
    bridgeMock.requestFileAccess.mockResolvedValue(true);
    bridgeMock.bindRepositoryPath.mockResolvedValue({
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
        boundRepositoryPath: 'D:/code/codingx',
        workspaceId: '3001',
        workspaceName: 'codingx',
        permissionGranted: true,
      },
    });
    const bindSpy = vi
      .spyOn(ChatApi, 'bindWorkspaceRepository')
      .mockResolvedValue({ repositoryPath: 'D:/code/codingx', workspaceId: '3001', workspaceName: 'codingx' });

    const { result } = renderHook(() => useHostContext());

    await act(async () => {
      await result.current.pickRepositoryDirectory();
    });

    expect(bindSpy).toHaveBeenCalledWith('token-123', 'D:/code/codingx');
    expect(bridgeMock.bindRepositoryPath).toHaveBeenCalledWith('D:/code/codingx', {
      workspaceId: '3001',
      workspaceName: 'codingx',
    });
  });
});
