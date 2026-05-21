import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

import { AdminChatApi } from '../api/adminChatApi';
import { Dashboard } from './Dashboard';

vi.mock('../api/adminChatApi', () => ({
  AdminChatApi: {
    getDashboard: vi.fn(),
    getRuntimeDashboard: vi.fn(),
  },
}));

describe('Dashboard page', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.getDashboard).mockResolvedValue({
      traceCount: 9,
      runningTraceCount: 2,
      intentNodeCount: 12,
      mappingCount: 6,
      sampleQuestionCount: 4,
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
   * 首页应展示聊天运行时观测卡片，并渲染队列与线程池关键指标。
   */
  it('renders runtime queue and executor metrics', async () => {
    render(
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>,
    );

    expect(await screen.findByText('聊天运行时观测')).toBeInTheDocument();
    expect(screen.getByText('队列门控')).toBeInTheDocument();
    expect(screen.getByText('线程池状态')).toBeInTheDocument();
    expect(screen.getByText('chatStreamExecutor')).toBeInTheDocument();
    expect(screen.getByText('searchExecutor')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    await waitFor(() => {
      expect(AdminChatApi.getRuntimeDashboard).toHaveBeenCalledTimes(1);
    });
  });
});
