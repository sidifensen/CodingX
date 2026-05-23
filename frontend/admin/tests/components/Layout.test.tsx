import '@testing-library/jest-dom/vitest';

import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { Layout } from '@/components/Layout';

describe('Layout', () => {
  it('keeps the admin viewport constrained and delegates overflow to the main content area', () => {
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
});
