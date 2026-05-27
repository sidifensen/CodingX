import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import SkillsView from '@/views/SkillsView';

const listSkillsMock = vi.fn();
const getSessionMock = vi.fn();

vi.mock('@/views/chat/chatApi', () => ({
  ChatApi: {
    listSkills: (...args: unknown[]) => listSkillsMock(...args),
  },
}));

vi.mock('@/utils/authStorage', () => ({
  AuthStorage: {
    getSession: () => getSessionMock(),
  },
}));

describe('SkillsView', () => {
  beforeEach(() => {
    getSessionMock.mockReturnValue({ token: 'token-123' });
    listSkillsMock.mockResolvedValue([
      {
        id: '7101',
        skillCode: 'sales_query',
        displayName: '销售查询',
        description: '查询销售汇总、排名、趋势与明细',
        category: '销售',
        sourceType: 'built-in',
        enabled: 1,
        sortNo: 1,
      },
      {
        id: '7102',
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
   * 技能库视图应从用户侧技能接口加载真实数据，而不是静态卡片。
   */
  it('loads installed skills from backend and shows real count', async () => {
    render(<SkillsView />);

    await waitFor(() => {
      expect(listSkillsMock).toHaveBeenCalledTimes(1);
      expect(listSkillsMock).toHaveBeenCalledWith('token-123');
    });

    expect(screen.getByText('销售查询')).toBeInTheDocument();
    expect(screen.getByText('工单查询')).toBeInTheDocument();
    expect(screen.getAllByText('查询销售汇总、排名、趋势与明细').length).toBeGreaterThan(0);
    expect(screen.getByText('查询工单状态、列表、优先级与解决率')).toBeInTheDocument();
    expect(screen.getByText('已安装 (2)')).toBeInTheDocument();
  });

  /**
   * 点击技能卡片后应切换到详情页，避免技能库只展示列表但无法查看完整信息。
   */
  it('opens skill detail page when installed skill card is clicked', async () => {
    render(<SkillsView />);

    const salesSkillCard = await screen.findByRole('button', { name: '查看技能详情 销售查询' });
    fireEvent.click(salesSkillCard);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '销售查询' })).toBeInTheDocument();
    expect(screen.getAllByText('查询销售汇总、排名、趋势与明细').length).toBeGreaterThan(0);
    expect(screen.getAllByText('/sales_query').length).toBeGreaterThan(0);
    expect(screen.getByText('销售')).toBeInTheDocument();
    expect(screen.getByText('built-in')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '返回技能库' }));
    expect(screen.getByRole('heading', { name: '技能库' })).toBeInTheDocument();
    expect(screen.getByText('已安装 (2)')).toBeInTheDocument();
  });
});
