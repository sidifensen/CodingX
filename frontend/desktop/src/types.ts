export interface HostContext {
  hostType: 'web' | 'desktop';
  executionTargets: Array<'cloud' | 'local'>;
  capabilities: {
    localFiles: boolean;
    localFolderPicker: boolean;
    shell: boolean;
    browserAutomation: boolean;
    desktopNotifications: boolean;
    officeInterop: boolean;
    localMcp: boolean;
    windowControls: boolean;
  };
  localResource: {
    boundRepositoryPath: string | null;
    workspaceId?: string | null;
    workspaceName?: string | null;
    permissionGranted: boolean;
  };
}

export interface LocalDirectoryEntry {
  name: string;
  path: string;
  entryType: 'file' | 'directory';
}

export interface HostWindowState {
  isMaximized: boolean;
  isMinimized: boolean;
  isFullScreen: boolean;
}

export type DesktopMenuAction =
  | 'undo'
  | 'redo'
  | 'cut'
  | 'copy'
  | 'paste'
  | 'select-all'
  | 'window-minimize'
  | 'window-maximize-toggle'
  | 'window-close'
  | 'toggle-dev-tools';

/**
 * 渲染层请求桌面系统通知时传入的载荷；字段主要来自后端 Hook SSE。
 */
export interface DesktopNotificationPayload {
  title?: string;
  body?: string;
  conversationId?: string | number | null;
  runId?: string | number | null;
  hookCode?: string | null;
  hookName?: string | null;
  triggerPoint?: string | null;
  actionType?: string | null;
  contextText?: string | null;
  toolCode?: string | null;
}
