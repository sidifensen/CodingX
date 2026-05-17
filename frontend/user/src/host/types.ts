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
  };
  localResource?: {
    boundRepositoryPath: string | null;
    permissionGranted: boolean;
  };
}

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
  pickRepositoryDirectory: () => Promise<string | null>;
  bindRepositoryPath: (path: string) => Promise<HostContext>;
  requestFileAccess: (path: string) => Promise<boolean>;
  listDirectory: (path: string) => Promise<LocalDirectoryEntry[]>;
}
