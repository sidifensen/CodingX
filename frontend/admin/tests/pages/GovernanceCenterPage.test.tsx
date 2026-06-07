import '@testing-library/jest-dom/vitest';

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

import { AdminChatApi } from '@/api/adminChatApi';
import { GovernanceCenterPage } from '@/pages/GovernanceCenterPage';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    listPermissionPolicies: vi.fn(),
    createPermissionPolicy: vi.fn(),
    updatePermissionPolicy: vi.fn(),
    deletePermissionPolicy: vi.fn(),
    listPermissionAudits: vi.fn(),
    listHookRules: vi.fn(),
    createHookRule: vi.fn(),
    updateHookRule: vi.fn(),
    deleteHookRule: vi.fn(),
    listHookAudits: vi.fn(),
    listProjectProfiles: vi.fn(),
    scanProjectProfile: vi.fn(),
    listLongTermMemories: vi.fn(),
    updateLongTermMemoryStatus: vi.fn(),
    listGovernanceSlashCommands: vi.fn(),
    createGovernanceSlashCommand: vi.fn(),
    updateGovernanceSlashCommand: vi.fn(),
    deleteGovernanceSlashCommand: vi.fn(),
  },
}));

describe('GovernanceCenterPage', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listPermissionPolicies).mockResolvedValue([
      {
        id: 1,
        policyCode: 'deny-dangerous-delete',
        policyName: '禁止危险删除',
        toolCode: 'shell_command',
        commandPattern: 'rm -rf',
        action: 'DENY',
        riskLevel: 'HIGH',
        enabled: 1,
        sortNo: 10,
      },
    ]);
    vi.mocked(AdminChatApi.listHookRules).mockResolvedValue([
      {
        id: 2,
        hookCode: 'audit-before-tool',
        hookName: '审计工具调用',
        triggerPoint: 'BEFORE_TOOL_CALL',
        actionType: 'AUDIT',
        enabled: 1,
        sortNo: 1,
      },
    ]);
    vi.mocked(AdminChatApi.listProjectProfiles).mockResolvedValue([
      {
        id: 3,
        workspaceId: 3001,
        workspacePath: 'D:/code/CodingX',
        summary: 'Maven + Vite workspace',
        techStackJson: '["Maven","Vite"]',
        verificationCommandsJson: '["mvn test","npm run build"]',
        moduleMapJson: '[{"name":"backend","path":"backend"}]',
        testCommandsJson: '["mvn test","npm run build"]',
        keyEntrypointsJson: '["backend/src/main/java/com/codingx/CodingXApplication.java"]',
        riskPointsJson: '["缺少端到端测试"]',
        agentContext: '项目包含后端、用户端和管理端。',
        status: 'COMPLETED',
      },
    ]);
    vi.mocked(AdminChatApi.listLongTermMemories).mockResolvedValue([
      {
        id: 9001,
        memoryScope: 'PROJECT',
        userId: 1002,
        workspaceId: 3001,
        content: '以后都按项目注释规范编写 Java 注释',
        status: 'ACTIVE',
        sourceConversationId: 2001,
        keywordJson: '["注释规范"]',
      },
    ]);
    vi.mocked(AdminChatApi.updateLongTermMemoryStatus).mockResolvedValue({
      id: 9001,
      memoryScope: 'PROJECT',
      userId: 1002,
      workspaceId: 3001,
      content: '以后都按项目注释规范编写 Java 注释',
      status: 'ACTIVE',
    });
    vi.mocked(AdminChatApi.listGovernanceSlashCommands).mockResolvedValue([
      {
        id: 4,
        commandCode: 'review',
        displayName: '/review',
        description: '执行代码审查',
        commandType: 'BUILTIN',
        enabled: 1,
        sortNo: 1,
      },
    ]);
    vi.mocked(AdminChatApi.listPermissionAudits).mockResolvedValue([
      {
        id: 5,
        toolCode: 'shell_command',
        matchedPolicyCode: 'deny-dangerous-delete',
        decision: 'DENY',
        result: 'DENIED',
        message: '权限策略拒绝执行',
      },
    ]);
    vi.mocked(AdminChatApi.listHookAudits).mockResolvedValue([
      {
        id: 6,
        hookCode: 'audit-before-tool',
        triggerPoint: 'BEFORE_TOOL_CALL',
        result: 'AUDITED',
        message: '已记录 Hook 审计',
      },
    ]);
    vi.mocked(AdminChatApi.updatePermissionPolicy).mockResolvedValue({
      id: 1,
      policyCode: 'deny-dangerous-delete',
      policyName: '禁止危险删除',
      action: 'DENY',
      riskLevel: 'HIGH',
      enabled: 1,
      sortNo: 10,
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  /**
   * 治理中心首屏应加载权限策略、Hook、画像、Slash Command 和审计数据，形成管理端统一入口。
   */
  it('loads governance dashboard data and renders tabbed workbench', async () => {
    const { container } = render(
      <MemoryRouter>
        <GovernanceCenterPage />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: '治理中心' })).toBeInTheDocument();
    expect(screen.getByText('禁止危险删除')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '权限策略' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Hook' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '项目画像' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '长期记忆' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Slash Command' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '审计' })).toBeInTheDocument();
    expect(container.querySelector('.ant-table')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: 'Hook' }));
    expect(await screen.findByText('审计工具调用')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: '项目画像' }));
    expect(await screen.findByText('Maven + Vite workspace')).toBeInTheDocument();
    expect(await screen.findByText('backend')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: '长期记忆' }));
    expect(await screen.findByText('以后都按项目注释规范编写 Java 注释')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: 'Slash Command' }));
    expect(await screen.findByText('/review')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: '审计' }));
    expect(await screen.findByText('权限策略拒绝执行')).toBeInTheDocument();
    expect(screen.getByText('已记录 Hook 审计')).toBeInTheDocument();
  });

  /**
   * 编辑策略应使用项目内弹窗并调用治理接口，不能依赖浏览器原生确认框。
   */
  it('opens policy edit dialog and saves through governance api', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm');
    render(
      <MemoryRouter>
        <GovernanceCenterPage />
      </MemoryRouter>,
    );
    await screen.findByText('禁止危险删除');

    fireEvent.click(screen.getByRole('button', { name: '编辑策略 deny-dangerous-delete' }));
    fireEvent.change(screen.getByLabelText('策略名称'), {
      target: { value: '禁止危险删除命令' },
    });
    fireEvent.click(screen.getByRole('button', { name: '保存策略' }));

    await waitFor(() => {
      expect(AdminChatApi.updatePermissionPolicy).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          policyName: '禁止危险删除命令',
        }),
      );
    });
    expect(confirmSpy).not.toHaveBeenCalled();
  });

  /**
   * 管理员应能在长期记忆页签停用已生效记忆，管理动作通过自定义按钮调用后端接口。
   */
  it('disables active long-term memory from governance tab', async () => {
    render(
      <MemoryRouter>
        <GovernanceCenterPage />
      </MemoryRouter>,
    );
    await screen.findByText('禁止危险删除');

    fireEvent.click(screen.getByRole('tab', { name: '长期记忆' }));
    expect(await screen.findByText('已启用')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '确认长期记忆 9001' })).not.toBeInTheDocument();
    fireEvent.click(await screen.findByRole('button', { name: '停用长期记忆 9001' }));

    await waitFor(() => {
      expect(AdminChatApi.updateLongTermMemoryStatus).toHaveBeenCalledWith(9001, 'REJECTED');
    });
  });
});
