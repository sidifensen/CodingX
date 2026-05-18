import { app, BrowserWindow, dialog, ipcMain } from 'electron';
import { existsSync } from 'node:fs';
import { readdir } from 'node:fs/promises';
import path from 'node:path';
import dotenv from 'dotenv';
import { HostContext, HostWindowState, LocalDirectoryEntry } from './types';

let mainWindow: BrowserWindow | null = null;

const hostState: {
  boundRepositoryPath: string | null;
  permissionGranted: boolean;
} = {
  boundRepositoryPath: null,
  permissionGranted: false,
};

const DEFAULT_CODINGX_USER_URL = 'http://localhost:5002';

/**
 * 桌面主进程按环境优先级加载 .env 配置，并兜底关键入口地址默认值。
 * 打包应用固定读取 production 配置，开发态固定读取 development 配置。
 * dotenv 默认不会覆盖系统已存在同名变量，避免污染 CI/外部启动参数。
 */
function loadDesktopEnv() {
  const desktopRoot = path.resolve(__dirname, '..');
  const runtimeEnv = app.isPackaged ? 'production' : 'development';
  const envFiles = [
    `.env.${runtimeEnv}`,
    '.env',
  ];

  for (const envFile of envFiles) {
    const envPath = path.resolve(desktopRoot, envFile);
    if (existsSync(envPath)) {
      dotenv.config({ path: envPath });
    }
  }

  if (!app.isPackaged && !process.env.CODINGX_USER_URL?.trim()) {
    process.env.CODINGX_USER_URL = DEFAULT_CODINGX_USER_URL;
  }
}

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
      windowControls: true,
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
    frame: false,
    titleBarStyle: 'hidden',
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  mainWindow.setMenuBarVisibility(false);

  mainWindow.once('ready-to-show', () => {
    mainWindow?.show();
  });

  const configuredUserUrl = process.env.CODINGX_USER_URL?.trim();

  // 打包态优先使用生产配置地址；未配置时回退到应用内置静态页面。
  if (app.isPackaged) {
    if (configuredUserUrl) {
      await mainWindow.loadURL(configuredUserUrl);
      return;
    }
    const packagedUserEntry = path.join(process.resourcesPath, 'user-dist', 'index.html');
    await mainWindow.loadFile(packagedUserEntry);
    return;
  }

  // 开发态连接本地 user 前端，默认值由 loadDesktopEnv 提供。
  await mainWindow.loadURL(configuredUserUrl ?? DEFAULT_CODINGX_USER_URL);
}

/**
 * 汇总当前窗口状态，供前端渲染自定义标题栏按钮状态。
 */
function getWindowState(targetWindow: BrowserWindow): HostWindowState {
  return {
    isMaximized: targetWindow.isMaximized(),
    isMinimized: targetWindow.isMinimized(),
    isFullScreen: targetWindow.isFullScreen(),
  };
}

/**
 * 向渲染层广播窗口状态变化，保持标题栏按钮与真实窗口状态一致。
 */
function emitWindowState(targetWindow: BrowserWindow) {
  const state = getWindowState(targetWindow);
  targetWindow.webContents.send('host:window-state-changed', state);
}

/**
 * 注册宿主能力与本地资源相关 IPC 处理器。
 */
function registerIpcHandlers() {
  ipcMain.handle('host:get-context', async () => buildHostContext());
  ipcMain.handle('host:get-window-state', async () =>
    mainWindow ? getWindowState(mainWindow) : null,
  );

  ipcMain.handle('host:window-minimize', async () => {
    mainWindow?.minimize();
  });

  ipcMain.handle('host:window-maximize-toggle', async () => {
    if (!mainWindow) {
      return null;
    }
    if (mainWindow.isMaximized()) {
      mainWindow.unmaximize();
    } else {
      mainWindow.maximize();
    }
    return getWindowState(mainWindow);
  });

  ipcMain.handle('host:window-close', async () => {
    mainWindow?.close();
  });

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
  loadDesktopEnv();
  registerIpcHandlers();
  await createWindow();
  if (mainWindow) {
    const syncWindowState = () => emitWindowState(mainWindow as BrowserWindow);
    mainWindow.on('maximize', syncWindowState);
    mainWindow.on('unmaximize', syncWindowState);
    mainWindow.on('minimize', syncWindowState);
    mainWindow.on('restore', syncWindowState);
    mainWindow.on('enter-full-screen', syncWindowState);
    mainWindow.on('leave-full-screen', syncWindowState);
  }

  app.on('activate', async () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      await createWindow();
      if (mainWindow) {
        emitWindowState(mainWindow);
      }
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
