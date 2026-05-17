import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '../api/adminChatApi';
import { Skills } from './Skills';

vi.mock('../api/adminChatApi', () => ({
  AdminChatApi: {
    listSkills: vi.fn(),
    createSkill: vi.fn(),
    updateSkill: vi.fn(),
    uploadSkillPackage: vi.fn(),
  },
}));

const skillFixture = [
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
] as const;

describe('Skills page', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listSkills).mockResolvedValue([...skillFixture]);
    vi.mocked(AdminChatApi.createSkill).mockResolvedValue(skillFixture[0] as any);
    vi.mocked(AdminChatApi.updateSkill).mockResolvedValue(skillFixture[0] as any);
    vi.mocked(AdminChatApi.uploadSkillPackage).mockResolvedValue({
      id: 7110,
      skillCode: 'pdf-processing',
      displayName: 'pdf-processing',
      description: '处理 PDF 文档',
      category: '文档处理',
      sourceType: 'uploaded',
      enabled: 1,
      sortNo: 3,
    } as any);
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

  /**
   * 技能管理页应支持新增技能弹窗，并在提交后调用后端创建接口。
   */
  it('supports create skill in skills page', async () => {
    render(<Skills />);
    await screen.findByText('/sales_query');

    fireEvent.click(screen.getByRole('button', { name: '创建新技能' }));
    const dialog = await screen.findByRole('dialog', { name: '新增技能' });
    fireEvent.change(within(dialog).getByLabelText('技能编码'), { target: { value: 'weather_query' } });
    fireEvent.change(within(dialog).getByLabelText('技能名称'), { target: { value: '天气查询' } });
    fireEvent.change(within(dialog).getByLabelText('分类'), { target: { value: '天气' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '创建技能' }));

    await waitFor(() => {
      expect(AdminChatApi.createSkill).toHaveBeenCalledWith(
        expect.objectContaining({
          skillCode: 'weather_query',
          displayName: '天气查询',
          category: '天气',
        }),
      );
    });
    await waitFor(() => {
      expect(AdminChatApi.listSkills).toHaveBeenCalledTimes(2);
    });
  });

  /**
   * 技能管理页应支持编辑技能弹窗，并在提交后调用后端更新接口。
   */
  it('supports edit skill in skills page', async () => {
    render(<Skills />);

    const editButton = await screen.findByRole('button', { name: '编辑技能 sales_query' });
    fireEvent.click(editButton);

    const dialog = await screen.findByRole('dialog', { name: '编辑技能' });
    expect(within(dialog).getByLabelText('技能编码')).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText('技能名称'), { target: { value: '销售查询增强版' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存修改' }));

    await waitFor(() => {
      expect(AdminChatApi.updateSkill).toHaveBeenCalledWith(
        7101,
        expect.objectContaining({
          skillCode: 'sales_query',
          displayName: '销售查询增强版',
        }),
      );
    });
    await waitFor(() => {
      expect(AdminChatApi.listSkills).toHaveBeenCalledTimes(2);
    });
  });

  /**
   * 技能管理页应支持上传技能包并调用后端上传接口。
   */
  it('supports upload skill package in skills page', async () => {
    render(<Skills />);
    await screen.findByText('/sales_query');

    fireEvent.click(screen.getByRole('button', { name: '上传技能包' }));
    const dialog = await screen.findByRole('dialog', { name: '上传技能包' });
    const fileInput = within(dialog).getByLabelText('技能包文件') as HTMLInputElement;
    const file = new File(['zip-binary'], 'pdf-processing.skill', { type: 'application/octet-stream' });
    fireEvent.change(fileInput, { target: { files: [file] } });
    fireEvent.change(within(dialog).getByLabelText('分类（可选）'), { target: { value: '文档处理' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '确认上传' }));

    await waitFor(() => {
      expect(AdminChatApi.uploadSkillPackage).toHaveBeenCalledWith(file, '文档处理');
    });
    await waitFor(() => {
      expect(AdminChatApi.listSkills).toHaveBeenCalledTimes(2);
    });
  });
});
