import { describe, expect, it } from 'vitest';
import { resolveHostBridge } from '@/host/bridge';

/**
 * 校验通过开发态 URL 参数或全局开关可强制启用桌面宿主能力，便于浏览器验收桌面标题栏交互。
 */
describe('resolveHostBridge desktop host mock', () => {
  it('应在 URL 含 desktopHostMock=1 时返回桌面宿主能力', async () => {
    const originalHref = window.location.href;
    window.history.replaceState({}, '', '/?desktopHostMock=1');
    try {
      const bridge = resolveHostBridge();
      const context = await bridge.getContext();

      expect(context.hostType).toBe('desktop');
      expect(context.capabilities.windowControls).toBe(true);
    } finally {
      window.history.replaceState({}, '', originalHref);
    }
  });

  it('应在开发态全局开关开启时返回桌面宿主能力', async () => {
    const previousFlag = window.__forceDesktopHost;
    window.__forceDesktopHost = true;
    try {
      const bridge = resolveHostBridge();
      const context = await bridge.getContext();

      expect(context.hostType).toBe('desktop');
      expect(context.capabilities.windowControls).toBe(true);
    } finally {
      window.__forceDesktopHost = previousFlag;
    }
  });
});
