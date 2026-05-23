import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '@/api/adminChatApi';
import { Experts } from '@/pages/Experts';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    listExperts: vi.fn(),
    createExpert: vi.fn(),
    updateExpert: vi.fn(),
    deleteExpert: vi.fn(),
  },
}));

const expertFixture = [
  {
    id: 8301,
    expertCode: 'solution-architect',
    displayName: '解决方案架构师',
    description: '擅长业务澄清、系统分层与落地架构取舍',
    category: '研发架构',
    presetQuestion: '请帮我把一个 SaaS 项目拆成可落地的系统架构方案',
    systemPrompt: '你是一名企业级解决方案架构师',
    enabled: 1,
    sortNo: 1,
  },
  {
    id: 8302,
    expertCode: 'backend-engineer',
    displayName: '后端工程师',
    description: '专注接口设计、领域建模、性能与可靠性',
    category: '研发交付',
    presetQuestion: '请帮我设计一个订单服务的后端接口和表结构',
    systemPrompt: '你是一名资深后端工程师',
    enabled: 1,
    sortNo: 2,
  },
] as const;

describe('Experts page', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listExperts).mockResolvedValue({
      records: [...expertFixture],
      total: 20,
      size: 10,
      current: 1,
      pages: 2,
    } as any);
    vi.mocked(AdminChatApi.createExpert).mockResolvedValue(expertFixture[0] as any);
    vi.mocked(AdminChatApi.updateExpert).mockResolvedValue(expertFixture[0] as any);
    vi.mocked(AdminChatApi.deleteExpert).mockResolvedValue(undefined);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('loads experts from backend api and renders rows', async () => {
    render(<Experts />);

    expect(await screen.findByRole('heading', { name: '专家管理 (Experts)' })).toBeInTheDocument();
    expect(screen.getByText('解决方案架构师')).toBeInTheDocument();
    expect(screen.getByText('后端工程师')).toBeInTheDocument();
    expect(screen.getByText('/solution-architect')).toBeInTheDocument();
    expect(screen.getByText('第 1 / 2 页，共 20 条')).toBeInTheDocument();
  });

  it('supports create expert in experts page', async () => {
    render(<Experts />);
    await screen.findByText('/solution-architect');

    fireEvent.click(screen.getByRole('button', { name: '创建新专家' }));
    const dialog = await screen.findByRole('dialog', { name: '新增专家' });
    fireEvent.change(within(dialog).getByLabelText('专家编码'), { target: { value: 'qa-lead' } });
    fireEvent.change(within(dialog).getByLabelText('专家名称'), { target: { value: '测试负责人' } });
    fireEvent.change(within(dialog).getByLabelText('系统提示词'), { target: { value: '你是一名测试负责人' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '创建专家' }));

    await waitFor(() => {
      expect(AdminChatApi.createExpert).toHaveBeenCalledWith(
        expect.objectContaining({
          expertCode: 'qa-lead',
          displayName: '测试负责人',
          systemPrompt: '你是一名测试负责人',
        }),
      );
    });
  });

  it('supports edit expert in experts page', async () => {
    render(<Experts />);
    const editButton = await screen.findByRole('button', { name: '编辑专家 solution-architect' });
    fireEvent.click(editButton);

    const dialog = await screen.findByRole('dialog', { name: '编辑专家' });
    fireEvent.change(within(dialog).getByLabelText('专家名称'), { target: { value: '首席解决方案架构师' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存修改' }));

    await waitFor(() => {
      expect(AdminChatApi.updateExpert).toHaveBeenCalledWith(
        8301,
        expect.objectContaining({
          expertCode: 'solution-architect',
          displayName: '首席解决方案架构师',
        }),
      );
    });
  });

  it('supports delete expert in experts page', async () => {
    render(<Experts />);
    const deleteButton = await screen.findByRole('button', { name: '删除专家 backend-engineer' });
    fireEvent.click(deleteButton);

    await waitFor(() => {
      expect(AdminChatApi.deleteExpert).toHaveBeenCalledWith(8302);
    });
  });
});
