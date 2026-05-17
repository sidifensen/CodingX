import '@testing-library/jest-dom/vitest';

import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { Pagination } from './Pagination';

describe('Pagination', () => {
  it('renders paged window with ellipsis and triggers page change', () => {
    const onChange = vi.fn();
    render(<Pagination current={1} pages={8} onChange={onChange} />);

    expect(screen.getByRole('button', { name: '第 1 页' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '第 2 页' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '第 3 页' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '第 8 页' })).toBeInTheDocument();
    expect(screen.getByText('...')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '第 3 页' }));
    expect(onChange).toHaveBeenCalledWith(3);
  });

  it('supports goto page input and button', () => {
    const onChange = vi.fn();
    render(<Pagination current={2} pages={8} onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('前往页码'), { target: { value: '6' } });
    fireEvent.click(screen.getByRole('button', { name: '前往' }));
    expect(onChange).toHaveBeenCalledWith(6);
  });
});
