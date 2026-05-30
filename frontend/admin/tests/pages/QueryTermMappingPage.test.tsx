import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '@/api/adminChatApi';
import { QueryTermMappingPage } from '@/pages/QueryTermMappingPage';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    listMappingsPage: vi.fn(),
    createMapping: vi.fn(),
    updateMapping: vi.fn(),
    deleteMapping: vi.fn(),
    listTools: vi.fn(),
    createTool: vi.fn(),
    updateTool: vi.fn(),
    deleteTool: vi.fn(),
  },
}));

const mappingFixture = [
  {
    id: 5001,
    sourceTerm: 'oa',
    targetTerm: 'OA系统',
    matchType: 1,
    priority: 10,
    enabled: true,
    remark: '办公自动化系统',
    createTime: '2026-05-16 10:00:00',
    updateTime: '2026-05-16 12:00:00',
  },
  {
    id: 5002,
    sourceTerm: 'rag',
    targetTerm: '检索增强生成',
    matchType: 1,
    priority: 20,
    enabled: false,
    remark: '',
    createTime: '2026-05-16 10:30:00',
    updateTime: '2026-05-16 12:30:00',
  },
] as const;

describe('QueryTermMappingPage', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listMappingsPage).mockResolvedValue({
      records: [...mappingFixture],
      total: 2,
      size: 10,
      current: 1,
      pages: 1,
    });
    vi.mocked(AdminChatApi.createMapping).mockResolvedValue(mappingFixture[0] as any);
    vi.mocked(AdminChatApi.updateMapping).mockResolvedValue(mappingFixture[0] as any);
    vi.mocked(AdminChatApi.deleteMapping).mockResolvedValue(undefined);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('loads mapping page data and renders table rows', async () => {
    const { container } = render(<QueryTermMappingPage />);

    await screen.findByText('oa');
    expect(AdminChatApi.listMappingsPage).toHaveBeenCalledWith(1, 10, undefined);
    expect(screen.getByText('检索增强生成')).toBeInTheDocument();
    expect(screen.getByTestId('mapping-total')).toHaveTextContent('2');
    expect(container.querySelector('section.rounded-xl.border.border-border-hairline.bg-surface-container-lowest.shadow-sm')).toBeInTheDocument();
  });

  it('searches by keyword', async () => {
    render(<QueryTermMappingPage />);
    await screen.findByText('oa');

    fireEvent.change(screen.getByTestId('mapping-search-input'), {
      target: { value: 'rag' },
    });
    fireEvent.click(screen.getByTestId('mapping-search-btn'));

    await waitFor(() => {
      expect(AdminChatApi.listMappingsPage).toHaveBeenLastCalledWith(1, 10, 'rag');
    });
  });

  it('creates mapping rule through dialog', async () => {
    render(<QueryTermMappingPage />);
    await screen.findByText('oa');

    fireEvent.click(screen.getByTestId('mapping-create-btn'));
    const dialog = await screen.findByTestId('mapping-edit-dialog');

    fireEvent.change(within(dialog).getByTestId('mapping-source-term-input'), { target: { value: 'llm' } });
    fireEvent.change(within(dialog).getByTestId('mapping-target-term-input'), { target: { value: '大语言模型' } });
    fireEvent.change(within(dialog).getByTestId('mapping-priority-input'), { target: { value: '5' } });
    fireEvent.change(within(dialog).getByTestId('mapping-remark-input'), { target: { value: '模型缩写' } });
    fireEvent.click(within(dialog).getByTestId('mapping-save-btn'));

    await waitFor(() => {
      expect(AdminChatApi.createMapping).toHaveBeenCalledWith(
        expect.objectContaining({
          sourceTerm: 'llm',
          targetTerm: '大语言模型',
          priority: 5,
          enabled: true,
          remark: '模型缩写',
        }),
      );
    });
  });

  it('updates mapping rule through dialog', async () => {
    render(<QueryTermMappingPage />);
    await screen.findByText('oa');

    fireEvent.click(screen.getByTestId('mapping-edit-5001'));
    const dialog = await screen.findByTestId('mapping-edit-dialog');

    fireEvent.change(within(dialog).getByTestId('mapping-target-term-input'), { target: { value: 'OA平台' } });
    fireEvent.mouseDown(within(dialog).getByTestId('mapping-enabled-select').querySelector('.ant-select-selector')!);
    fireEvent.click(await screen.findByTitle('禁用'));
    fireEvent.click(within(dialog).getByTestId('mapping-save-btn'));

    await waitFor(() => {
      expect(AdminChatApi.updateMapping).toHaveBeenCalledWith(
        5001,
        expect.objectContaining({
          sourceTerm: 'oa',
          targetTerm: 'OA平台',
          enabled: false,
        }),
      );
    });
  });

  it('deletes mapping rule through custom confirm dialog', async () => {
    render(<QueryTermMappingPage />);
    await screen.findByText('oa');

    fireEvent.click(screen.getByTestId('mapping-delete-5001'));
    const dialog = await screen.findByTestId('mapping-delete-dialog');

    fireEvent.click(within(dialog).getByTestId('mapping-delete-confirm-btn'));

    await waitFor(() => {
      expect(AdminChatApi.deleteMapping).toHaveBeenCalledWith(5001);
    });
  });
});
