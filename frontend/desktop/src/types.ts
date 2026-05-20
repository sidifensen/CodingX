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
