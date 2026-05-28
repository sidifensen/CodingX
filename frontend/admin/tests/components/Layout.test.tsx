import '@testing-library/jest-dom/vitest';

import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { Layout } from '@/components/Layout';

describe('Layout', () => {
  beforeEachMatchMedia();

  it('keeps the admin viewport constrained and delegates overflow to the main content area', () => {
    render(
      <MemoryRouter initialEntries={['/intent-tree']}>
        <Routes>
          <Route path="/" element={<Layout onLogout={vi.fn().mockResolvedValue(undefined)} isAuthSubmitting={false} />}>
            <Route path="intent-tree" element={<div>意图树内容</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    const main = screen.getByRole('main');

    expect(main).toHaveClass('min-h-0');
    expect(main).toHaveClass('overflow-y-auto');
    expect(main).toHaveClass('overflow-x-hidden');
  });

  /**
   * 管理端导航应提供工作空间管理入口。
   */
  it('renders workspace management navigation item', () => {
    render(
      <MemoryRouter initialEntries={['/workspaces']}>
        <Routes>
          <Route path="/" element={<Layout onLogout={vi.fn().mockResolvedValue(undefined)} isAuthSubmitting={false} />}>
            <Route path="workspaces" element={<div>工作空间内容</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('link', { name: /工作空间/ })).toHaveAttribute('href', '/workspaces');
  });

  /**
   * 管理端布局应提供顶部搜索工具栏，支撑控制台与后续全局筛选入口。
   */
  it('renders topbar search input', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<Layout onLogout={vi.fn().mockResolvedValue(undefined)} isAuthSubmitting={false} />}>
            <Route index element={<div>首页内容</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByPlaceholderText('搜索知识库、会话或工作空间')).toBeInTheDocument();
  });
});

/**
 * Layout 初始化会读取系统主题偏好，测试中提供稳定的 matchMedia。
 */
function beforeEachMatchMedia() {
  beforeEach(() => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation(() => ({
        matches: false,
        media: '',
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  });
}
