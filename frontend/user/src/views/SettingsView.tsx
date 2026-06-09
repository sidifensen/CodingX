import React from 'react';
import { Bell, MonitorCog } from 'lucide-react';
import { HostContext } from '../host/types';
import {
  readDesktopNotificationEnabled,
  writeDesktopNotificationEnabled,
} from '../host/desktopNotificationPreference';

/**
 * 设置页输入属性。
 */
interface SettingsViewProps {
  hostContext: HostContext | null;
}

/**
 * 渲染当前设备级设置；桌面系统通知偏好只存储在用户本机 localStorage。
 */
export default function SettingsView({ hostContext }: SettingsViewProps) {
  const [desktopNotificationEnabled, setDesktopNotificationEnabled] = React.useState(
    () => readDesktopNotificationEnabled(),
  );
  const isDesktopNotificationSupported =
    hostContext?.hostType === 'desktop' && hostContext.capabilities.desktopNotifications;

  /**
   * 切换系统通知偏好并立即写入本地缓存，任务 Hook 后续会按该值决定是否弹通知。
   */
  const toggleDesktopNotification = () => {
    const nextEnabled = !desktopNotificationEnabled;
    setDesktopNotificationEnabled(nextEnabled);
    writeDesktopNotificationEnabled(nextEnabled);
  };

  return (
    <section className="h-full overflow-y-auto bg-background text-foreground">
      <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-10 md:px-10">
        <header className="border-b border-border pb-5">
          <div className="mb-3 inline-flex h-10 w-10 items-center justify-center rounded-lg border border-border bg-surface text-foreground">
            <MonitorCog size={20} />
          </div>
          <h2 className="text-2xl font-semibold tracking-tight">设置</h2>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
            管理当前设备上的桌面端偏好。这里的配置只保存在本地缓存，不会同步到数据库。
          </p>
        </header>

        <div className="rounded-lg border border-border bg-surface">
          <div className="flex flex-col gap-4 border-b border-border px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex min-w-0 gap-3">
              <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-surface-container text-foreground">
                <Bell size={18} />
              </div>
              <div className="min-w-0">
                <h3 className="text-base font-semibold">系统通知</h3>
                <p className="mt-1 text-sm leading-6 text-muted">
                  自动化任务完成后，通过 Windows 系统通知提醒当前桌面端用户。
                </p>
              </div>
            </div>

            <button
              type="button"
              role="switch"
              aria-label="系统通知"
              aria-checked={desktopNotificationEnabled}
              disabled={!isDesktopNotificationSupported}
              onClick={toggleDesktopNotification}
              className={`relative h-7 w-12 shrink-0 rounded-full border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-border-active disabled:cursor-not-allowed disabled:opacity-50 ${
                desktopNotificationEnabled
                  ? 'border-primary bg-primary'
                  : 'border-border bg-surface-container-high'
              }`}
            >
              <span
                className={`absolute top-0.5 h-6 w-6 rounded-full bg-background shadow-sm transition-transform ${
                  desktopNotificationEnabled ? 'translate-x-5' : 'translate-x-0.5'
                }`}
              />
            </button>
          </div>

          <div className="px-5 py-4 text-sm leading-6 text-muted">
            {isDesktopNotificationSupported
              ? '开启后，任务完成 Hook 会调用桌面宿主并弹出系统通知；关闭后仍会在会话内写入任务结果。'
              : '当前宿主不支持系统通知，此开关只在 CodingX 桌面端生效。'}
          </div>
        </div>
      </div>
    </section>
  );
}
