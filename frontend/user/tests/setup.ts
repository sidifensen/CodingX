import '@testing-library/jest-dom';

/**
 * 为 JSDOM 补齐 ResizeObserver，避免依赖该 API 的布局测量逻辑在测试环境报错。
 */
class MockResizeObserver {
  observe() {}

  unobserve() {}

  disconnect() {}
}

if (typeof globalThis.ResizeObserver === 'undefined') {
  Object.defineProperty(globalThis, 'ResizeObserver', {
    configurable: true,
    writable: true,
    value: MockResizeObserver,
  });
}
