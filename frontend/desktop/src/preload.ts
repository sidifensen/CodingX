import { contextBridge, ipcRenderer } from 'electron';
import { DesktopMenuAction, HostContext, HostWindowState, LocalDirectoryEntry } from './types';

/**
 * 通过 contextBridge 向渲染层暴露受控宿主能力接口，避免直接暴露 Node 权限。
 */
contextBridge.exposeInMainWorld('codingxHost', {
  getContext: (): Promise<HostContext> => ipcRenderer.invoke('host:get-context'),
  getWindowState: (): Promise<HostWindowState | null> => ipcRenderer.invoke('host:get-window-state'),
  minimizeWindow: (): Promise<void> => ipcRenderer.invoke('host:window-minimize'),
  toggleMaximizeWindow: (): Promise<HostWindowState | null> =>
    ipcRenderer.invoke('host:window-maximize-toggle'),
  closeWindow: (): Promise<void> => ipcRenderer.invoke('host:window-close'),
  invokeDesktopMenuAction: (action: DesktopMenuAction): Promise<void> =>
    ipcRenderer.invoke('host:menu-action', action),
  onWindowStateChanged: (listener: (state: HostWindowState) => void): (() => void) => {
    const channel = 'host:window-state-changed';
    const handler = (_event: Electron.IpcRendererEvent, state: HostWindowState) => {
      listener(state);
    };
    ipcRenderer.on(channel, handler);
    return () => {
      ipcRenderer.removeListener(channel, handler);
    };
  },
  pickRepositoryDirectory: (): Promise<string | null> => ipcRenderer.invoke('host:pick-repository-directory'),
  bindRepositoryPath: (path: string): Promise<HostContext> => ipcRenderer.invoke('host:bind-repository-path', path),
  requestFileAccess: (path: string): Promise<boolean> => ipcRenderer.invoke('host:request-file-access', path),
  listDirectory: (path: string): Promise<LocalDirectoryEntry[]> => ipcRenderer.invoke('host:list-directory', path),
});
