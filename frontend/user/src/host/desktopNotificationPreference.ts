import { HostContext } from './types';

/**
 * 桌面系统通知开关的本地缓存键；该偏好只属于当前设备，不进入后端数据库。
 */
export const DESKTOP_NOTIFICATION_ENABLED_STORAGE_KEY =
  'codingx.desktop.notifications.enabled';

/**
 * 读取当前设备的系统通知偏好，未设置时默认开启以兼容已有自动化通知行为。
 * @returns true 表示允许桌面宿主弹出系统通知。
 */
export function readDesktopNotificationEnabled() {
  if (typeof window === 'undefined') {
    return true;
  }
  return window.localStorage.getItem(DESKTOP_NOTIFICATION_ENABLED_STORAGE_KEY) !== 'false';
}

/**
 * 写入当前设备的系统通知偏好。
 * @param enabled 是否启用系统通知。
 */
export function writeDesktopNotificationEnabled(enabled: boolean) {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.setItem(
    DESKTOP_NOTIFICATION_ENABLED_STORAGE_KEY,
    enabled ? 'true' : 'false',
  );
}

/**
 * 判断当前宿主上下文和本地偏好是否允许弹出桌面系统通知。
 * @param hostContext 当前宿主上下文。
 * @returns true 表示可以调用桌面通知桥接。
 */
export function isDesktopNotificationAllowed(hostContext: HostContext | null | undefined) {
  return (
    hostContext?.hostType === 'desktop' &&
    hostContext.capabilities.desktopNotifications &&
    readDesktopNotificationEnabled()
  );
}
