import '@testing-library/jest-dom/vitest';

import { vi } from 'vitest';

const originalGetComputedStyle = window.getComputedStyle.bind(window);

/**
 * Ant Design 响应式观察器依赖 matchMedia；jsdom 默认不实现，需要在测试环境补齐。
 */
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

/**
 * Ant Design 部分样式读取会传入伪元素参数，jsdom 会打印未实现噪声；测试中降级到普通读取即可。
 */
window.getComputedStyle = ((element: Element) => originalGetComputedStyle(element)) as typeof window.getComputedStyle;
