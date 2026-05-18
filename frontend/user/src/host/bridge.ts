import { CodingxHostBridge, HostContext, HostWindowState, LocalDirectoryEntry } from './types';

/**
 * 生成 Web 宿主 fallback 能力，确保无桌面注入时页面可稳定运行。
 */
function createWebFallbackBridge(): CodingxHostBridge {
  const windowState: HostWindowState = {
    isMaximized: false,
    isMinimized: false,
    isFullScreen: false,
  };
  return {
    async getContext(): Promise<HostContext> {
      return {
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
          windowControls: false,
        },
        localResource: {
          boundRepositoryPath: null,
          permissionGranted: false,
        },
      };
    },
    async getWindowState(): Promise<HostWindowState> {
      return windowState;
    },
    async minimizeWindow(): Promise<void> {
      return;
    },
    async toggleMaximizeWindow(): Promise<HostWindowState> {
      return windowState;
    },
    async closeWindow(): Promise<void> {
      return;
    },
    onWindowStateChanged(): () => void {
      return () => undefined;
    },
    async pickRepositoryDirectory(): Promise<string | null> {
      return null;
    },
    async bindRepositoryPath(): Promise<HostContext> {
      return this.getContext();
    },
    async requestFileAccess(): Promise<boolean> {
      return false;
    },
    async listDirectory(): Promise<LocalDirectoryEntry[]> {
      return [];
    },
  };
}

/**
 * 解析当前可用宿主桥接：桌面注入优先，缺失时回退到 Web 默认实现。
 */
export function resolveHostBridge(): CodingxHostBridge {
  if (typeof window !== 'undefined' && window.codingxHost) {
    return window.codingxHost;
  }
  return createWebFallbackBridge();
}
