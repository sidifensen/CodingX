import {
  DESKTOP_NOTIFICATION_ENABLED_STORAGE_KEY,
  isDesktopNotificationAllowed,
  readDesktopNotificationEnabled,
  writeDesktopNotificationEnabled,
} from '@/host/desktopNotificationPreference';

const desktopHostContext = {
  hostType: 'desktop' as const,
  executionTargets: ['cloud', 'local'] as Array<'cloud' | 'local'>,
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
    boundRepositoryPath: null,
    permissionGranted: false,
  },
};

describe('desktopNotificationPreference', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  /**
   * 系统通知开关默认打开，保证升级后用户仍能收到已接入的任务完成提醒。
   */
  it('默认启用桌面系统通知', () => {
    expect(readDesktopNotificationEnabled()).toBe(true);
    expect(isDesktopNotificationAllowed(desktopHostContext)).toBe(true);
  });

  /**
   * 用户关闭通知后只写入本地缓存，不依赖后端数据库或账号状态。
   */
  it('关闭通知后应写入本地缓存并阻止桌面通知', () => {
    writeDesktopNotificationEnabled(false);

    expect(window.localStorage.getItem(DESKTOP_NOTIFICATION_ENABLED_STORAGE_KEY)).toBe('false');
    expect(readDesktopNotificationEnabled()).toBe(false);
    expect(isDesktopNotificationAllowed(desktopHostContext)).toBe(false);
  });
});
