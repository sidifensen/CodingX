import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Minus, Square, X } from 'lucide-react';
import { resolveHostBridge } from '../host/bridge';
import { DesktopMenuAction, HostContext, HostWindowState } from '../host/types';

interface DesktopMenuItem {
  id: string;
  label: string;
  action: DesktopMenuAction;
}

interface DesktopMenuGroup {
  key: string;
  label: string;
  items: DesktopMenuItem[];
}

const DESKTOP_MENU_GROUPS: DesktopMenuGroup[] = [
  {
    key: 'edit',
    label: '编辑',
    items: [
      { id: 'undo', label: '撤销', action: 'undo' },
      { id: 'redo', label: '重做', action: 'redo' },
      { id: 'cut', label: '剪切', action: 'cut' },
      { id: 'copy', label: '复制', action: 'copy' },
      { id: 'paste', label: '粘贴', action: 'paste' },
      { id: 'select-all', label: '全选', action: 'select-all' },
    ],
  },
  {
    key: 'window',
    label: '窗口',
    items: [
      { id: 'window-minimize', label: '最小化', action: 'window-minimize' },
      { id: 'window-maximize-toggle', label: '最大化/还原', action: 'window-maximize-toggle' },
      { id: 'window-close', label: '关闭窗口', action: 'window-close' },
    ],
  },
  {
    key: 'help',
    label: '帮助',
    items: [{ id: 'toggle-dev-tools', label: '开发工具', action: 'toggle-dev-tools' }],
  },
];

/**
 * 渲染桌面端自定义标题栏，并托管窗口最小化/最大化/关闭行为。
 */
export default function DesktopTitleBar({ hostContext }: { hostContext: HostContext | null }) {
  const bridge = useMemo(() => resolveHostBridge(), []);
  const [windowState, setWindowState] = useState<HostWindowState>({
    isMaximized: false,
    isMinimized: false,
    isFullScreen: false,
  });
  const [activeMenuKey, setActiveMenuKey] = useState<string | null>(null);
  const menuContainerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let cancelled = false;

    const syncWindowState = async () => {
      const state = await bridge.getWindowState();
      if (!cancelled && state) {
        setWindowState(state);
      }
    };

    void syncWindowState();
    const off = bridge.onWindowStateChanged((state) => {
      if (!cancelled) {
        setWindowState(state);
      }
    });

    return () => {
      cancelled = true;
      off();
    };
  }, [bridge]);

  useEffect(() => {
    const handlePointerDownOutside = (event: MouseEvent) => {
      if (!menuContainerRef.current) {
        return;
      }
      if (!menuContainerRef.current.contains(event.target as Node)) {
        setActiveMenuKey(null);
      }
    };

    document.addEventListener('mousedown', handlePointerDownOutside);
    return () => {
      document.removeEventListener('mousedown', handlePointerDownOutside);
    };
  }, []);

  if (hostContext?.hostType !== 'desktop' || !hostContext.capabilities.windowControls) {
    return null;
  }

  /**
   * 切换一级菜单展开状态，保证同一时刻只打开一个菜单组。
   * @param menuKey 菜单组标识。
   */
  const handleToggleMenu = (menuKey: string) => {
    setActiveMenuKey((currentKey) => (currentKey === menuKey ? null : menuKey));
  };

  /**
   * 触发菜单动作后立即收起下拉层，避免遮挡用户后续操作。
   * @param action 需要转发到主进程执行的动作标识。
   */
  const handleMenuItemClick = async (action: DesktopMenuAction) => {
    await bridge.invokeDesktopMenuAction(action);
    setActiveMenuKey(null);
  };

  return (
    <header
      className="desktop-titlebar flex h-9 shrink-0 items-center justify-between border-b border-border bg-surface text-foreground transition-colors"
      data-tauri-drag-region="true"
    >
      <div className="desktop-titlebar-drag flex h-full min-w-0 items-center pl-3">
        <span className="truncate text-sm font-medium tracking-tight">CodingX</span>
        <div ref={menuContainerRef} className="desktop-titlebar-no-drag ml-5 flex h-full items-center">
          {DESKTOP_MENU_GROUPS.map((menuGroup) => {
            const isMenuOpen = activeMenuKey === menuGroup.key;
            return (
              <div key={menuGroup.key} className="relative flex h-full items-center">
                <button
                  type="button"
                  aria-label={menuGroup.label}
                  aria-expanded={isMenuOpen}
                  onClick={() => handleToggleMenu(menuGroup.key)}
                  className="desktop-titlebar-menu-trigger flex h-full items-center px-3 text-sm text-foreground transition-colors hover:bg-surface-container-high"
                >
                  {menuGroup.label}
                </button>
                {isMenuOpen ? (
                  <div className="desktop-titlebar-menu-panel absolute left-0 top-[calc(100%+2px)] z-40 min-w-[148px] rounded-xl border border-border bg-surface px-1.5 py-1.5 shadow-[0_14px_36px_rgba(0,0,0,0.22)]">
                    {menuGroup.items.map((menuItem) => (
                      <button
                        key={menuItem.id}
                        type="button"
                        aria-label={menuItem.label}
                        onClick={() => void handleMenuItemClick(menuItem.action)}
                        className="desktop-titlebar-menu-item block w-full rounded-lg px-3 py-1.5 text-left text-sm text-foreground transition-colors hover:bg-surface-container-high"
                      >
                        {menuItem.label}
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
      </div>
      <div className="desktop-titlebar-no-drag flex h-full items-center">
        <button
          type="button"
          aria-label="最小化窗口"
          onClick={() => void bridge.minimizeWindow()}
          className="desktop-titlebar-control flex h-full w-12 items-center justify-center text-foreground transition-colors hover:bg-surface-container-high"
        >
          <Minus size={16} />
        </button>
        <button
          type="button"
          aria-label={windowState.isMaximized ? '还原窗口' : '最大化窗口'}
          onClick={() => void bridge.toggleMaximizeWindow()}
          className="desktop-titlebar-control flex h-full w-12 items-center justify-center text-foreground transition-colors hover:bg-surface-container-high"
        >
          <Square size={13} />
        </button>
        <button
          type="button"
          aria-label="关闭窗口"
          onClick={() => void bridge.closeWindow()}
          className="desktop-titlebar-control flex h-full w-12 items-center justify-center text-foreground transition-colors hover:bg-[#d62828] hover:text-white"
        >
          <X size={16} />
        </button>
      </div>
    </header>
  );
}
