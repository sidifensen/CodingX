import { app, BrowserWindow, dialog, ipcMain } from 'electron';
import { readdir } from 'node:fs/promises';
import path from 'node:path';
import { HostContext, LocalDirectoryEntry } from './types';

let mainWindow: BrowserWindow | null = null;

const hostState: {
  boundRepositoryPath: string | null;
  permissionGranted: boolean;
} = {
  boundRepositoryPath: null,
  permissionGranted: false,
};

/**
 * 组装当前桌面宿主上下文，供前端能力识别与入口渲染。
 */
function buildHostContext(): HostContext {
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
    },
    localResource: {
      boundRepositoryPath: hostState.boundRepositoryPath,
      permissionGranted: hostState.permissionGranted,
    },
  };
}

/**
 * 创建桌面窗口并加载用户前端页面。
 */
async function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1366,
    height: 900,
    minWidth: 1024,
    minHeight: 720,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  mainWindow.once('ready-to-show', () => {
    mainWindow?.show();
  });

  await mainWindow.loadURL(process.env.CODINGX_USER_URL ?? 'http://localhost:5002');
}

/**
 * 注册宿主能力与本地资源相关 IPC 处理器。
 */
function registerIpcHandlers() {
  ipcMain.handle('host:get-context', async () => buildHostContext());

  ipcMain.handle('host:pick-repository-directory', async () => {
    const result = await dialog.showOpenDialog({
      title: '选择本地仓库目录',
      properties: ['openDirectory', 'createDirectory'],
    });
    if (result.canceled || result.filePaths.length === 0) {
      return null;
    }
    return result.filePaths[0];
  });

  ipcMain.handle('host:request-file-access', async (_event, targetPath: string) => {
    const result = await dialog.showMessageBox({
      type: 'question',
      buttons: ['允许', '拒绝'],
      defaultId: 0,
      cancelId: 1,
      title: '本地文件访问授权',
      message: '是否允许 CodingX 访问该本地目录？',
      detail: targetPath,
    });
    const granted = result.response === 0;
    hostState.permissionGranted = granted;
    return granted;
  });

  ipcMain.handle('host:bind-repository-path', async (_event, targetPath: string) => {
    hostState.boundRepositoryPath = targetPath;
    return buildHostContext();
  });

  ipcMain.handle('host:list-directory', async (_event, targetPath: string) => {
    const entries = await readdir(targetPath, { withFileTypes: true });
    return entries.map((entry): LocalDirectoryEntry => ({
      name: entry.name,
      path: path.join(targetPath, entry.name),
      entryType: entry.isDirectory() ? 'directory' : 'file',
    }));
  });
}

app.whenReady().then(async () => {
  registerIpcHandlers();
  await createWindow();

  app.on('activate', async () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      await createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
