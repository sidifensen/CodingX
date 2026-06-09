import { app, BrowserWindow, dialog, ipcMain, Notification, session } from 'electron';
import { existsSync } from 'node:fs';
import { readdir } from 'node:fs/promises';
import path from 'node:path';
import dotenv from 'dotenv';
import {
  DesktopMenuAction,
  DesktopNotificationPayload,
  HostContext,
  HostWindowState,
  LocalDirectoryEntry,
} from './types';
import { resolvePackagedApiRedirectUrl } from './apiProxy';
import { normalizeDesktopNotificationPayload } from './desktopNotification';

let mainWindow: BrowserWindow | null = null;
let packagedApiProxyRegistered = false;

const hostState: {
  boundRepositoryPath: string | null;
  workspaceId: string | null;
  workspaceName: string | null;
  permissionGranted: boolean;
} = {
  boundRepositoryPath: null,
  workspaceId: null,
  workspaceName: null,
  permissionGranted: false,
};

const DEFAULT_CODINGX_USER_URL = 'http://localhost:5002';

/**
 * 解析桌面窗口图标路径，开发态读取源码资产，打包态读取 electron-builder 注入的 resources 资产。
 * Windows 任务栏和窗口缩略图依赖该 PNG；安装包与 EXE 图标由 package.json 的 win.icon 使用 ICO。
 */
function resolveDesktopWindowIconPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'assets', 'icon.png');
  }
  return path.resolve(__dirname, '..', 'assets', 'icon.png');
}

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
 * 打包态加载内置 user-dist 时没有 Vite 代理，主进程需要把 file 页面发起的 `/api` 请求转到后端服务。
 */
function registerPackagedApiProxy() {
  if (packagedApiProxyRegistered) {
    return;
  }
  packagedApiProxyRegistered = true;
  session.defaultSession.webRequest.onBeforeRequest((details, callback) => {
    const redirectURL = resolvePackagedApiRedirectUrl(
      details.url,
      process.env.CODINGX_API_BASE_URL,
    );
    if (redirectURL) {
      callback({ redirectURL });
      return;
    }
    callback({});
  });
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
      workspaceId: hostState.workspaceId,
      workspaceName: hostState.workspaceName,
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
    icon: resolveDesktopWindowIconPath(),
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
    registerPackagedApiProxy();
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
 * 执行桌面标题栏菜单动作，统一由主进程承接系统级能力调用。
 * @param action 渲染层触发的菜单动作标识。
 */
async function handleDesktopMenuAction(action: DesktopMenuAction) {
  if (!mainWindow) {
    return;
  }

  switch (action) {
    case 'undo':
      mainWindow.webContents.undo();
      return;
    case 'redo':
      mainWindow.webContents.redo();
      return;
    case 'cut':
      mainWindow.webContents.cut();
      return;
    case 'copy':
      mainWindow.webContents.copy();
      return;
    case 'paste':
      mainWindow.webContents.paste();
      return;
    case 'select-all':
      mainWindow.webContents.selectAll();
      return;
    case 'window-minimize':
      mainWindow.minimize();
      return;
    case 'window-maximize-toggle':
      if (mainWindow.isMaximized()) {
        mainWindow.unmaximize();
      } else {
        mainWindow.maximize();
      }
      return;
    case 'window-close':
      mainWindow.close();
      return;
    case 'toggle-dev-tools':
      mainWindow.webContents.toggleDevTools();
      return;
    default:
      return;
  }
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

  ipcMain.handle('host:menu-action', async (_event, action: DesktopMenuAction) => {
    await handleDesktopMenuAction(action);
  });

  ipcMain.handle('host:show-desktop-notification', async (_event, payload: DesktopNotificationPayload) => {
    if (!mainWindow || !Notification.isSupported()) {
      return false;
    }
    // 步骤 1：主进程统一清洗通知文本，避免渲染层或后端传入空标题导致系统通知不可读。
    const normalizedPayload = normalizeDesktopNotificationPayload(payload);
    const notification = new Notification({
      title: normalizedPayload.title,
      body: normalizedPayload.body,
      silent: false,
    });
    // 步骤 2：点击系统通知时只聚焦现有窗口，不在主进程中擅自改路由或切换会话。
    notification.on('click', () => {
      if (!mainWindow) {
        return;
      }
      if (mainWindow.isMinimized()) {
        mainWindow.restore();
      }
      mainWindow.show();
      mainWindow.focus();
    });
    notification.show();
    return true;
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

  ipcMain.handle('host:bind-repository-path', async (_event, targetPath: string, workspaceContext?: { workspaceId?: string; workspaceName?: string }) => {
    hostState.boundRepositoryPath = targetPath;
    hostState.workspaceId = workspaceContext?.workspaceId ?? null;
    hostState.workspaceName = workspaceContext?.workspaceName ?? null;
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
  if (process.platform === 'win32') {
    // Windows 通知中心需要稳定 AppUserModelId 才能把通知归到 CodingX 应用名下。
    app.setAppUserModelId('com.codingx.desktop');
  }
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
