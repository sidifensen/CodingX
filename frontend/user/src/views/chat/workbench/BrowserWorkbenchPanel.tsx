import React from 'react';
import {
  ArrowLeft,
  ArrowRight,
  Camera,
  ExternalLink,
  Globe2,
  MoreVertical,
  Plus,
  RefreshCw,
} from 'lucide-react';
import { resolveHostBridge } from '../../../host/bridge';

/**
 * 浏览器式面板使用 iframe 承载可嵌入页面；被站点拒绝时仍提供外部打开入口。
 */
export function BrowserWorkbenchPanel({
  workspaceLabel,
}: {
  workspaceLabel: string;
}) {
  const hostBridge = React.useMemo(() => resolveHostBridge(), []);
  const browserViewportRef = React.useRef<HTMLDivElement | null>(null);
  const [addressInput, setAddressInput] = React.useState('http://localhost:5002/');
  const [browserUrl, setBrowserUrl] = React.useState('http://localhost:5002/');
  const [historyStack, setHistoryStack] = React.useState(['http://localhost:5002/']);
  const [historyIndex, setHistoryIndex] = React.useState(0);
  const [frameKey, setFrameKey] = React.useState(0);
  const [zoomPercent, setZoomPercent] = React.useState(100);
  const [isMoreMenuOpen, setIsMoreMenuOpen] = React.useState(false);
  const [browserActionMessage, setBrowserActionMessage] = React.useState('');
  const [browserActionError, setBrowserActionError] = React.useState('');
  const moreMenuRef = React.useRef<HTMLDivElement | null>(null);

  const navigateToAddress = React.useCallback((rawAddress: string) => {
    const nextUrl = normalizeBrowserWorkbenchUrl(rawAddress);
    setBrowserUrl(nextUrl);
    setAddressInput(nextUrl);
    setHistoryStack((currentHistory) => {
      const trimmedHistory = currentHistory.slice(0, historyIndex + 1);
      return [...trimmedHistory, nextUrl];
    });
    setHistoryIndex((currentIndex) => currentIndex + 1);
    setFrameKey((currentKey) => currentKey + 1);
  }, [historyIndex]);

  const navigateHistory = React.useCallback((offset: number) => {
    const nextIndex = historyIndex + offset;
    const nextUrl = historyStack[nextIndex];
    if (!nextUrl) {
      return;
    }
    setHistoryIndex(nextIndex);
    setBrowserUrl(nextUrl);
    setAddressInput(nextUrl);
    setFrameKey((currentKey) => currentKey + 1);
  }, [historyIndex, historyStack]);

  const openExternalBrowser = React.useCallback(async () => {
    setBrowserActionMessage('');
    setBrowserActionError('');
    try {
      await hostBridge.openExternalUrl(browserUrl);
      setBrowserActionMessage('已在默认浏览器中打开');
    } catch (error) {
      setBrowserActionError(error instanceof Error ? error.message : '打开默认浏览器失败');
    }
  }, [browserUrl, hostBridge]);

  const captureBrowserScreenshot = React.useCallback(async () => {
    setBrowserActionMessage('');
    setBrowserActionError('');
    const viewportRect = browserViewportRef.current?.getBoundingClientRect();
    try {
      const copied = await hostBridge.captureWorkbenchBrowserScreenshot(
        viewportRect && viewportRect.width > 0 && viewportRect.height > 0
          ? {
              x: viewportRect.x,
              y: viewportRect.y,
              width: viewportRect.width,
              height: viewportRect.height,
            }
          : undefined,
      );
      setBrowserActionMessage(copied ? '已截图到剪贴板' : '当前环境不支持截图到剪贴板');
    } catch (error) {
      setBrowserActionError(error instanceof Error ? error.message : '截图到剪贴板失败');
    }
  }, [hostBridge]);

  const openDeveloperTools = React.useCallback(async () => {
    setBrowserActionMessage('');
    setBrowserActionError('');
    try {
      await hostBridge.invokeDesktopMenuAction('toggle-dev-tools');
      setBrowserActionMessage('已打开开发者工具');
      setIsMoreMenuOpen(false);
    } catch (error) {
      setBrowserActionError(error instanceof Error ? error.message : '打开开发者工具失败');
    }
  }, [hostBridge]);

  React.useEffect(() => {
    if (!isMoreMenuOpen) {
      return undefined;
    }
    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target;
      if (target instanceof Node && moreMenuRef.current?.contains(target)) {
        return;
      }
      setIsMoreMenuOpen(false);
    };
    document.addEventListener('mousedown', handlePointerDown);
    return () => document.removeEventListener('mousedown', handlePointerDown);
  }, [isMoreMenuOpen]);

  return (
    <div
      data-testid="browser-workbench-panel"
      className="grid h-full min-h-0 grid-rows-[auto_1fr] bg-surface text-foreground"
    >
      <div className="border-b border-border px-3 py-3">
        <div className="flex items-center gap-2">
          <button
            type="button"
            aria-label="浏览器后退"
            disabled={historyIndex <= 0}
            onClick={() => navigateHistory(-1)}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-container hover:text-foreground disabled:opacity-40"
          >
            <ArrowLeft size={16} />
          </button>
          <button
            type="button"
            aria-label="浏览器前进"
            disabled={historyIndex >= historyStack.length - 1}
            onClick={() => navigateHistory(1)}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-container hover:text-foreground disabled:opacity-40"
          >
            <ArrowRight size={16} />
          </button>
          <button
            type="button"
            aria-label="刷新浏览器"
            onClick={() => setFrameKey((currentKey) => currentKey + 1)}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-container hover:text-foreground"
          >
            <RefreshCw size={16} />
          </button>
          <form
            className="min-w-0 flex-1"
            onSubmit={(event) => {
              event.preventDefault();
              navigateToAddress(addressInput);
            }}
          >
            <label className="relative block">
              <Globe2 size={14} className="pointer-events-none absolute left-3 top-2.5 text-muted" />
              <input
                aria-label="浏览器地址栏"
                value={addressInput}
                onChange={(event) => setAddressInput(event.target.value)}
                className="h-9 w-full rounded-xl border border-border bg-background pl-9 pr-10 font-mono text-sm text-foreground outline-none"
              />
              <button
                type="button"
                aria-label="在默认浏览器中打开"
                onClick={() => void openExternalBrowser()}
                className="absolute right-1 top-1 inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-container hover:text-foreground"
              >
                <ExternalLink size={14} />
              </button>
            </label>
          </form>
          <button
            type="button"
            aria-label="截图到剪贴板"
            onClick={() => void captureBrowserScreenshot()}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-container hover:text-foreground"
          >
            <Camera size={16} />
          </button>
          <button
            type="button"
            aria-label="新增浏览器页"
            onClick={() => navigateToAddress('http://localhost:5002/')}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-container hover:text-foreground"
          >
            <Plus size={16} />
          </button>
          <div ref={moreMenuRef} className="relative">
            <button
              type="button"
              aria-label="浏览器更多选项"
              aria-haspopup="menu"
              aria-expanded={isMoreMenuOpen}
              onClick={() => setIsMoreMenuOpen((current) => !current)}
              className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-container hover:text-foreground"
            >
              <MoreVertical size={16} />
            </button>
            {isMoreMenuOpen ? (
              <div
                role="menu"
                aria-label="浏览器更多功能"
                className="absolute right-0 top-10 z-30 w-72 rounded-xl border border-border bg-surface p-3 text-sm text-foreground shadow-[0_18px_44px_rgba(0,0,0,0.28)]"
              >
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => setFrameKey((currentKey) => currentKey + 1)}
                  className="block w-full rounded-lg px-2 py-2 text-left hover:bg-surface-container"
                >
                  强制重新加载
                </button>
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => void openDeveloperTools()}
                  className="block w-full rounded-lg px-2 py-2 text-left hover:bg-surface-container"
                >
                  打开开发者工具
                </button>
                <div className="my-2 border-t border-border" />
                <div className="flex items-center justify-between gap-3 px-2 py-1">
                  <span>缩放</span>
                  <div className="flex items-center rounded-lg border border-border">
                    <button
                      type="button"
                      aria-label="缩小浏览器"
                      onClick={() => setZoomPercent((value) => Math.max(50, value - 10))}
                      className="h-8 w-8 text-muted hover:text-foreground"
                    >
                      -
                    </button>
                    <span className="w-14 text-center font-mono text-xs">{zoomPercent}%</span>
                    <button
                      type="button"
                      aria-label="放大浏览器"
                      onClick={() => setZoomPercent((value) => Math.min(200, value + 10))}
                      className="h-8 w-8 text-muted hover:text-foreground"
                    >
                      +
                    </button>
                  </div>
                </div>
                <div className="my-2 border-t border-border" />
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setHistoryStack(['http://localhost:5002/']);
                    setHistoryIndex(0);
                    setBrowserUrl('http://localhost:5002/');
                    setAddressInput('http://localhost:5002/');
                    setFrameKey((currentKey) => currentKey + 1);
                  }}
                  className="block w-full rounded-lg px-2 py-2 text-left hover:bg-surface-container"
                >
                  清除 Cookie
                </button>
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => setFrameKey((currentKey) => currentKey + 1)}
                  className="block w-full rounded-lg px-2 py-2 text-left hover:bg-surface-container"
                >
                  清除缓存
                </button>
              </div>
            ) : null}
          </div>
        </div>
        {browserActionMessage || browserActionError ? (
          <div
            className={`mt-2 rounded-lg px-3 py-2 text-xs ${
              browserActionError
                ? 'border border-error/30 bg-error/10 text-error'
                : 'border border-success/20 bg-success/10 text-success'
            }`}
          >
            {browserActionError || browserActionMessage}
          </div>
        ) : null}
      </div>
      <div ref={browserViewportRef} className="min-h-0 overflow-hidden bg-background">
        <iframe
          key={frameKey}
          title={`内置浏览器：${workspaceLabel || 'CodingX'}`}
          src={browserUrl}
          className="h-full w-full border-0 bg-background"
          style={{ transform: `scale(${zoomPercent / 100})`, transformOrigin: 'top left', width: `${10000 / zoomPercent}%`, height: `${10000 / zoomPercent}%` }}
        />
      </div>
    </div>
  );
}

/**
 * 地址栏输入可写裸域名、localhost 或完整 URL；普通文本兜底为搜索地址。
 */
function normalizeBrowserWorkbenchUrl(rawAddress: string) {
  const trimmedAddress = rawAddress.trim();
  if (!trimmedAddress) {
    return 'about:blank';
  }
  if (/^(https?:|about:)/i.test(trimmedAddress)) {
    return trimmedAddress;
  }
  if (/^(localhost|127\.0\.0\.1|\[::1\]|[\w-]+\.[\w.-]+)/i.test(trimmedAddress)) {
    return `http://${trimmedAddress}`;
  }
  return `https://www.bing.com/search?q=${encodeURIComponent(trimmedAddress)}`;
}
