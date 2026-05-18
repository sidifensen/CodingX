import React, { useEffect, useMemo, useState } from 'react';
import { Minus, Square, X } from 'lucide-react';
import { resolveHostBridge } from '../host/bridge';
import { HostContext, HostWindowState } from '../host/types';

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

  if (hostContext?.hostType !== 'desktop' || !hostContext.capabilities.windowControls) {
    return null;
  }

  return (
    <header
      className="desktop-titlebar flex h-9 shrink-0 items-center justify-between border-b border-border bg-surface text-foreground transition-colors"
      data-tauri-drag-region="true"
    >
      <div className="desktop-titlebar-drag flex h-full min-w-0 items-center pl-3">
        <span className="truncate text-sm font-medium tracking-tight">CodingX</span>
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
