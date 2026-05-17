import { contextBridge, ipcRenderer } from 'electron';
import { HostContext, LocalDirectoryEntry } from './types';

/**
 * 通过 contextBridge 向渲染层暴露受控宿主能力接口，避免直接暴露 Node 权限。
 */
contextBridge.exposeInMainWorld('codingxHost', {
  getContext: (): Promise<HostContext> => ipcRenderer.invoke('host:get-context'),
  pickRepositoryDirectory: (): Promise<string | null> => ipcRenderer.invoke('host:pick-repository-directory'),
  bindRepositoryPath: (path: string): Promise<HostContext> => ipcRenderer.invoke('host:bind-repository-path', path),
  requestFileAccess: (path: string): Promise<boolean> => ipcRenderer.invoke('host:request-file-access', path),
  listDirectory: (path: string): Promise<LocalDirectoryEntry[]> => ipcRenderer.invoke('host:list-directory', path),
});
