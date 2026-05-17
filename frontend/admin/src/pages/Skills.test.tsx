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
    listSkillPackageEntries: vi.fn(),
    getSkillPackageFileContent: vi.fn(),
    listTools: vi.fn(),
    createTool: vi.fn(),
    updateTool: vi.fn(),
    deleteTool: vi.fn(),
  },
}));

const skillFixture = [
  {
    id: 7101,
    skillCode: 'sales_query',
    displayName: '销售查询',
    description: '查询销售汇总、排名、趋势与明细',
    category: '销售',
    sourceType: 'uploaded',
    enabled: 1,
    sortNo: 1,
    storageKey: 'chat-skills/packages/sales-query.zip',
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
    vi.mocked(AdminChatApi.listSkills).mockResolvedValue({
      records: [...skillFixture],
      total: 22,
      size: 10,
      current: 1,
      pages: 3,
    } as any);
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
    vi.mocked(AdminChatApi.listSkillPackageEntries).mockResolvedValue([
      { path: 'SKILL.md', name: 'SKILL.md', directory: false, size: 1200 },
      { path: 'templates', name: 'templates', directory: true, size: null },
      { path: 'templates/prompt.txt', name: 'prompt.txt', directory: false, size: 200 },
    ] as any);
    vi.mocked(AdminChatApi.getSkillPackageFileContent).mockResolvedValue({
      path: 'SKILL.md',
      content: '# Skill Manifest',
      truncated: false,
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
      expect(AdminChatApi.listSkills).toHaveBeenCalledWith(expect.objectContaining({ current: 1, size: 10 }));
    });

    expect(screen.getByText('销售查询')).toBeInTheDocument();
    expect(screen.getByText('工单查询')).toBeInTheDocument();
    expect(screen.getByText('/sales_query')).toBeInTheDocument();
    expect(screen.getByText('/ticket_query')).toBeInTheDocument();
    expect(screen.getByText('销售')).toBeInTheDocument();
    expect(screen.getByText('工单')).toBeInTheDocument();
    expect(screen.getByText('第 1 / 3 页，共 22 条')).toBeInTheDocument();
  });

  /**
   * 技能管理分页应支持点击下一页后按新页码请求。
   */
  it('supports paged navigation in skills page', async () => {
    vi.mocked(AdminChatApi.listSkills)
      .mockResolvedValueOnce({
        records: [...skillFixture],
        total: 22,
        size: 10,
        current: 1,
        pages: 3,
      } as any)
      .mockResolvedValueOnce({
        records: [
          {
            id: 7201,
            skillCode: 'archive-helper',
            displayName: '归档助手',
            description: '处理归档任务',
            category: '流程',
            sourceType: 'uploaded',
            enabled: 1,
            sortNo: 11,
          },
        ],
        total: 22,
        size: 10,
        current: 2,
        pages: 3,
      } as any);

    render(<Skills />);
    await screen.findByText('/sales_query');
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));

    await waitFor(() => {
      expect(AdminChatApi.listSkills).toHaveBeenLastCalledWith(expect.objectContaining({ current: 2, size: 10 }));
    });
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

  /**
   * 技能管理页应支持列表模式，并可打开资源预览展示技能包文件内容。
   */
  it('supports list view and package preview explorer', async () => {
    render(<Skills />);
    await screen.findByText('/sales_query');

    fireEvent.click(screen.getByRole('button', { name: '列表视图' }));
    expect(screen.getByText('编码')).toBeInTheDocument();
    expect(screen.getByText('状态')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '资源预览 sales_query' }));
    const dialog = await screen.findByRole('dialog', { name: '技能包资源预览 sales_query' });
    expect(within(dialog).getByText('文件目录')).toBeInTheDocument();

    await waitFor(() => {
      expect(AdminChatApi.listSkillPackageEntries).toHaveBeenCalledWith(7101);
    });

    expect((await within(dialog).findAllByText('SKILL.md')).length).toBeGreaterThan(0);
    await waitFor(() => {
      expect(AdminChatApi.getSkillPackageFileContent).toHaveBeenCalledWith(7101, 'SKILL.md');
    });
    expect(within(dialog).getByText('# Skill Manifest')).toBeInTheDocument();
  });
});
