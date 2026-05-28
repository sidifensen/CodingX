import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

import { AdminChatApi } from '@/api/adminChatApi';
import { Dashboard } from '@/pages/Dashboard';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    getDashboard: vi.fn(),
    getRuntimeDashboard: vi.fn(),
  },
}));

vi.mock('@ant-design/plots', () => ({
  Area: (props: { ['data-testid']?: string }) => <div data-testid={props['data-testid'] ?? 'mock-area-chart'} />,
  Line: (props: { ['data-testid']?: string }) => <div data-testid={props['data-testid'] ?? 'mock-line-chart'} />,
  Column: (props: { ['data-testid']?: string }) => <div data-testid={props['data-testid'] ?? 'mock-column-chart'} />,
  Pie: (props: { ['data-testid']?: string }) => <div data-testid={props['data-testid'] ?? 'mock-pie-chart'} />,
}));

describe('Dashboard page', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.getDashboard).mockResolvedValue({
      window: '24h',
      generatedAt: '2026-05-28T15:52:32',
      kpis: {
        activeUserCount: 12,
        conversationCount: 18,
        messageCount: 86,
        workspaceCount: 6,
        traceCount: 42,
        runningTraceCount: 5,
      },
      resources: {
        skillCount: 9,
        toolCount: 11,
        expertCount: 3,
        mcpCount: 4,
        intentNodeCount: 22,
        mappingCount: 7,
        sampleQuestionCount: 5,
      },
      performance: {
        successRate: 83.3,
        failureRate: 8.3,
        runningRate: 8.4,
        avgTraceDurationMs: 9200,
        p95TraceDurationMs: 15000,
      },
      trendBuckets: [
        {
          label: '16:00',
          bucketStart: '2026-05-28T16:00:00',
          conversationCount: 1,
          messageCount: 11,
          activeUserCount: 1,
          traceCount: 2,
          successCount: 1,
          failedCount: 1,
          avgDurationMs: 9200,
        },
      ],
    });
    vi.mocked(AdminChatApi.getRuntimeDashboard).mockResolvedValue({
      queue: {
        mode: 'redis',
        maxConcurrent: 2,
        activeCount: 1,
        waitingCount: 3,
        availablePermits: 1,
      },
      executor: {
        streamActiveCount: 1,
        streamPoolSize: 2,
        streamQueueSize: 3,
        streamQueueRemainingCapacity: 253,
        searchActiveCount: 2,
        searchPoolSize: 4,
        searchQueueSize: 0,
        searchQueueRemainingCapacity: 256,
      },
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  /**
   * 控制台首页应展示核心指标、右侧健康卡和运行时观测入口。
   */
  it('renders console dashboard sections', async () => {
    render(
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getByText('核心指标')).toBeInTheDocument();
    expect(screen.getByText('流量概览')).toBeInTheDocument();
    expect(screen.getByText('趋势分析')).toBeInTheDocument();
    expect(screen.getByText('AI 性能')).toBeInTheDocument();
    expect(screen.getByText('运营洞察')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '7d' })).toBeInTheDocument();
    await waitFor(() => {
      expect(AdminChatApi.getDashboard).toHaveBeenCalledTimes(1);
      expect(AdminChatApi.getRuntimeDashboard).toHaveBeenCalledTimes(1);
    });
  });

  /**
   * 切换时间窗口后应重新请求对应 Dashboard 数据。
   */
  it('reloads dashboard when time window changes', async () => {
    render(
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>,
    );

    fireEvent.click(await screen.findByRole('button', { name: '7d' }));

    await waitFor(() => {
      expect(AdminChatApi.getDashboard).toHaveBeenLastCalledWith('7d');
    });
  });
});
