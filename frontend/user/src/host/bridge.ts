import {
  CodingxHostBridge,
  DesktopNotificationPayload,
  HostContext,
  HostWindowState,
  LocalDirectoryEntry,
} from './types';

/**
 * 在开发态浏览器验证场景可通过全局开关注入桌面宿主模式，便于复用同一套标题栏交互。
 */
function shouldForceDesktopHost() {
  if (typeof window === 'undefined') {
    return false;
  }
  if (!import.meta.env.DEV) {
    return false;
  }
  const forcedByGlobalFlag =
    (window as Window & { __forceDesktopHost?: boolean }).__forceDesktopHost === true;
  if (forcedByGlobalFlag) {
    return true;
  }
  const query = new URLSearchParams(window.location.search);
  return query.get('desktopHostMock') === '1';
}

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
      if (shouldForceDesktopHost()) {
        return {
          hostType: 'desktop',
          executionTargets: ['cloud', 'local'],
          capabilities: {
            localFiles: false,
            localFolderPicker: false,
            shell: false,
            browserAutomation: true,
            desktopNotifications: false,
            officeInterop: false,
            localMcp: false,
            windowControls: true,
          },
          localResource: {
            boundRepositoryPath: null,
            permissionGranted: false,
          },
        };
      }
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
    async invokeDesktopMenuAction(): Promise<void> {
      return;
    },
    async showDesktopNotification(_payload: DesktopNotificationPayload): Promise<boolean> {
      // Web 宿主没有系统通知桥接，显式返回 false 方便调用方区分已忽略和已展示。
      return false;
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
