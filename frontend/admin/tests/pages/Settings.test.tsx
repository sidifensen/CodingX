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
    settingKey: 'ai.routing.default_model',
    settingValue: 'qwen-plus',
    valueType: 'STRING',
    categoryCode: 'ai.routing',
    description: '默认模型',
    sortNo: 3,
    restartRequired: true,
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

    fireEvent.click(screen.getByRole('button', { name: '紧凑表格' }));

    expect(await screen.findByText('配置键')).toBeInTheDocument();
    expect(screen.getByText('当前值')).toBeInTheDocument();
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
});
