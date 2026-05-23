import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '@/api/adminChatApi';
import { Settings } from '@/pages/Settings';

const mockSettings = [
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
});
