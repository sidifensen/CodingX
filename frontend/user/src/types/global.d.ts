import type { CodingxHostBridge } from '../host/types';

/**
 * 扩展全局 Window 类型，声明 Electron preload 注入的宿主桥接对象。
 */
declare global {
  interface Window {
    codingxHost?: CodingxHostBridge;
    __forceDesktopHost?: boolean;
  }
}

export {};
