import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '../api/adminChatApi';
import { Skills } from './Skills';

vi.mock('../api/adminChatApi', () => ({
  AdminChatApi: {
    listSkills: vi.fn(),
  },
}));

describe('Skills page', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listSkills).mockResolvedValue([
      {
        id: 7101,
        skillCode: 'sales_query',
        displayName: '销售查询',
        description: '查询销售汇总、排名、趋势与明细',
        category: '销售',
        sourceType: 'built-in',
        enabled: 1,
        sortNo: 1,
      },
      {
        id: 7102,
        skillCode: 'ticket_query',
        displayName: '工单查询',
        description: '查询工单状态、列表、优先级与解决率',
        category: '工单',
        sourceType: 'built-in',
        enabled: 1,
        sortNo: 2,
      },
    ]);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  /**
   * 技能管理页应从后端加载技能数据，而不是继续使用本地 mock 常量。
   */
  it('loads skills from backend api and renders real rows', async () => {
    render(<Skills />);

    expect(await screen.findByRole('heading', { name: '技能管理 (Skills)' })).toBeInTheDocument();

    await waitFor(() => {
      expect(AdminChatApi.listSkills).toHaveBeenCalledTimes(1);
    });

    expect(screen.getByText('销售查询')).toBeInTheDocument();
    expect(screen.getByText('工单查询')).toBeInTheDocument();
    expect(screen.getByText('/sales_query')).toBeInTheDocument();
    expect(screen.getByText('/ticket_query')).toBeInTheDocument();
    expect(screen.getByText('销售')).toBeInTheDocument();
    expect(screen.getByText('工单')).toBeInTheDocument();
  });
});
