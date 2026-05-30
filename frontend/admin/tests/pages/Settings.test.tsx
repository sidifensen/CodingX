import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '@/api/adminChatApi';
import { Settings } from '@/pages/Settings';

const mockSettings = [
  {
    id: '0',
    settingKey: 'chat.executor.stream_core_pool_size',
    settingValue: '2',
    valueType: 'INTEGER',
    categoryCode: 'chat.executor',
    description: '聊天入口线程池核心线程数',
    sortNo: 0,
    restartRequired: true,
  },
  {
    id: '1',
    settingKey: 'chat.memory.summary_enabled',
    settingValue: 'true',
    valueType: 'BOOLEAN',
    categoryCode: 'chat.memory',
    description: '是否启用聊天历史摘要',
    sortNo: 1,
    restartRequired: false,
  },
  {
    id: '2',
    settingKey: 'search.top_k',
    settingValue: '5',
    valueType: 'INTEGER',
    categoryCode: 'search',
    description: '搜索召回数量',
    sortNo: 2,
    restartRequired: false,
  },
  {
    id: '3',
    settingKey: 'ai.routing.first_packet_timeout_ms',
    settingValue: '60000',
    valueType: 'LONG',
    categoryCode: 'ai.routing',
    description: '首包超时毫秒',
    sortNo: 3,
    restartRequired: false,
  },
  {
    id: '4',
    settingKey: 'chat.intent.guidance.enabled',
    settingValue: 'true',
    valueType: 'BOOLEAN',
    categoryCode: 'chat.intent.guidance',
    description: '是否启用聊天歧义引导',
    sortNo: 4,
    restartRequired: false,
  },
  {
    id: '5',
    settingKey: 'chat.intent.guidance.ambiguity_score_ratio',
    settingValue: '0.8',
    valueType: 'DECIMAL',
    categoryCode: 'chat.intent.guidance',
    description: '歧义引导分数比值阈值',
    sortNo: 5,
    restartRequired: false,
  },
  {
    id: '6',
    settingKey: 'ai.providers.siliconflow.api_key',
    settingValue: '',
    valueType: 'STRING',
    categoryCode: 'ai.providers',
    description: '硅基流动接口密钥',
    sortNo: 6,
    restartRequired: false,
    secret: true,
    maskedValue: 'sk-****',
  },
  {
    id: '7',
    settingKey: 'ai.chat.candidates.10.id',
    settingValue: 'siliconflow-deepseek-v4-flash',
    valueType: 'STRING',
    categoryCode: 'ai.candidates',
    description: '候选 10 模型ID',
    sortNo: 10,
    restartRequired: false,
  },
  {
    id: '8',
    settingKey: 'ai.chat.candidates.10.provider',
    settingValue: 'siliconflow',
    valueType: 'STRING',
    categoryCode: 'ai.candidates',
    description: '候选 10 提供商',
    sortNo: 20,
    restartRequired: false,
  },
  {
    id: '9',
    settingKey: 'ai.chat.candidates.10.model',
    settingValue: 'deepseek-ai/DeepSeek-V4-Flash',
    valueType: 'STRING',
    categoryCode: 'ai.candidates',
    description: '候选 10 模型名称',
    sortNo: 30,
    restartRequired: false,
  },
  {
    id: '10',
    settingKey: 'ai.chat.candidates.10.priority',
    settingValue: '1',
    valueType: 'INTEGER',
    categoryCode: 'ai.candidates',
    description: '候选 10 优先级',
    sortNo: 40,
    restartRequired: false,
  },
  {
    id: '11',
    settingKey: 'ai.chat.candidates.20.id',
    settingValue: 'qwen-plus',
    valueType: 'STRING',
    categoryCode: 'ai.candidates',
    description: '候选 20 模型ID',
    sortNo: 50,
    restartRequired: false,
  },
  {
    id: '12',
    settingKey: 'ai.chat.candidates.20.provider',
    settingValue: 'bailian',
    valueType: 'STRING',
    categoryCode: 'ai.candidates',
    description: '候选 20 提供商',
    sortNo: 60,
    restartRequired: false,
  },
  {
    id: '13',
    settingKey: 'ai.chat.candidates.20.model',
    settingValue: 'qwen-plus-latest',
    valueType: 'STRING',
    categoryCode: 'ai.candidates',
    description: '候选 20 模型名称',
    sortNo: 70,
    restartRequired: false,
  },
  {
    id: '14',
    settingKey: 'ai.chat.candidates.20.priority',
    settingValue: '2',
    valueType: 'INTEGER',
    categoryCode: 'ai.candidates',
    description: '候选 20 优先级',
    sortNo: 80,
    restartRequired: false,
  },
] as const;

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    listSettings: vi.fn(),
    saveSettings: vi.fn(),
  },
}));

describe('Settings page', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listSettings).mockResolvedValue([...mockSettings] as never);
    vi.mocked(AdminChatApi.saveSettings).mockResolvedValue([...mockSettings] as never);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('switches between display modes', async () => {
    render(<Settings />);

    await screen.findByRole('heading', { name: '系统配置' });
    expect(screen.getByText('聊天执行器')).toBeInTheDocument();
    expect(screen.getByTestId('settings-navigator-layout')).toHaveClass('admin-settings-navigator');

    const searchCategoryButton = screen.getByRole('button', { name: /搜索链路/ });
    expect(searchCategoryButton).toHaveClass('admin-settings-category-button');
    fireEvent.click(searchCategoryButton);

    expect(screen.getByTestId('settings-active-field-grid')).toHaveClass('admin-settings-field-grid');
    expect(screen.getByTestId('setting-value-search.top_k').closest('.admin-settings-field-card')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '分组卡片' }));
    expect(screen.getByTestId('settings-card-view')).toHaveClass('admin-settings-card-view');
    expect(screen.getByRole('button', { name: /模型候选池/ })).toHaveClass('admin-settings-category-toggle');
    expect(screen.getByTestId('settings-category-grid-search')).toHaveClass('admin-settings-field-grid');

    fireEvent.click(screen.getByRole('button', { name: '紧凑表格' }));

    expect((await screen.findAllByText('配置键')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('当前值').length).toBeGreaterThan(0);
  });

  it('tracks edits and saves the modified draft', async () => {
    render(<Settings />);

    await screen.findByRole('heading', { name: '系统配置' });
    fireEvent.click(screen.getByRole('button', { name: '紧凑表格' }));

    const input = screen.getByTestId('setting-value-search.top_k');
    fireEvent.change(input, { target: { value: '8' } });

    expect(screen.getByText('已修改')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '保存覆盖配置' }));

    await waitFor(() => {
      expect(AdminChatApi.saveSettings).toHaveBeenCalledWith(
        expect.arrayContaining([
          expect.objectContaining({
            settingKey: 'search.top_k',
            settingValue: '8',
          }),
        ]),
      );
    });
  });

  it('keeps the focused setting input active after editing a value', async () => {
    render(<Settings />);

    await screen.findByRole('heading', { name: '系统配置' });
    fireEvent.click(screen.getByRole('button', { name: /搜索链路/ }));

    const input = await screen.findByTestId('setting-value-search.top_k');
    input.focus();

    fireEvent.change(input, { target: { value: '9' } });

    // 系统配置页输入常用于逐字修改密钥和数值，编辑后必须保持焦点避免打断连续输入。
    expect(screen.getByTestId('setting-value-search.top_k')).toHaveFocus();
  });

  it('renders the intent guidance settings with a Chinese category name', async () => {
    render(<Settings />);

    await screen.findByRole('heading', { name: '系统配置' });

    fireEvent.click(screen.getByText('歧义引导'));

    expect(screen.getByText('是否启用聊天歧义引导')).toBeInTheDocument();
    expect(screen.getByDisplayValue('0.8')).toHaveAttribute('type', 'number');
  });

  it('shows AI provider category and keeps secret inputs empty with preserve hint', async () => {
    render(<Settings />);

    await screen.findByRole('heading', { name: '系统配置' });

    fireEvent.click(screen.getByRole('button', { name: /AI 提供商/ }));

    expect(screen.getByText('AI 提供商表格编辑')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('留空表示保持不变，当前已配置：sk-****')).toBeInTheDocument();
  });

  it('renders candidate slots as structured cards', async () => {
    render(<Settings />);

    await screen.findByRole('heading', { name: '系统配置' });
    fireEvent.click(screen.getByRole('button', { name: /模型候选池/ }));

    expect(screen.getByText('候选模型表格编辑')).toBeInTheDocument();
    expect(screen.getByTestId('setting-value-ai.chat.candidates.10.id')).toHaveValue('siliconflow-deepseek-v4-flash');
    expect(screen.getByTestId('setting-value-ai.chat.candidates.20.id')).toHaveValue('qwen-plus');
  });

  it('reorders other candidates when one priority is raised', async () => {
    render(<Settings />);

    await screen.findByRole('heading', { name: '系统配置' });
    fireEvent.click(screen.getByRole('button', { name: /模型候选池/ }));

    const priorityInput = screen.getByTestId('setting-value-ai.chat.candidates.20.priority');
    fireEvent.change(priorityInput, { target: { value: '1' } });

    expect(screen.getByTestId('setting-value-ai.chat.candidates.20.priority')).toHaveValue(1);
    expect(screen.getByTestId('setting-value-ai.chat.candidates.10.priority')).toHaveValue(2);
  });
});
