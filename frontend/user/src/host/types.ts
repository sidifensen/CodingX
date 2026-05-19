/**
 * 统一定义宿主能力上下文，保障 Web 与桌面端按能力渲染同一套前端。
 */
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
  localResource?: {
    boundRepositoryPath: string | null;
    permissionGranted: boolean;
  };
}

/**
 * 描述桌面窗口当前状态，供自定义标题栏同步按钮展示。
 */
export interface HostWindowState {
  isMaximized: boolean;
  isMinimized: boolean;
  isFullScreen: boolean;
}

/**
 * 定义桌面标题栏可触发的菜单动作枚举，确保渲染层与主进程协议一致。
 */
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
 * 描述本地目录浏览结果项。
 */
export interface LocalDirectoryEntry {
  name: string;
  path: string;
  entryType: 'file' | 'directory';
}

/**
 * 定义前端可调用的宿主桥接能力。
 */
export interface CodingxHostBridge {
  getContext: () => Promise<HostContext>;
  getWindowState: () => Promise<HostWindowState | null>;
  minimizeWindow: () => Promise<void>;
  toggleMaximizeWindow: () => Promise<HostWindowState | null>;
  closeWindow: () => Promise<void>;
  invokeDesktopMenuAction: (action: DesktopMenuAction) => Promise<void>;
  onWindowStateChanged: (listener: (state: HostWindowState) => void) => (() => void);
  pickRepositoryDirectory: () => Promise<string | null>;
  bindRepositoryPath: (path: string) => Promise<HostContext>;
  requestFileAccess: (path: string) => Promise<boolean>;
  listDirectory: (path: string) => Promise<LocalDirectoryEntry[]>;
}
