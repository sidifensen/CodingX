import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Sidebar from './Sidebar';
import { WorkspaceConversationGroup } from '../views/chat/types';

/**
 * 构建 Sidebar 测试所需的最小属性集。
 */
function createSidebarProps(overrides?: {
  workspaceGroups?: WorkspaceConversationGroup[];
}) {
  return {
    activeView: 'chat' as const,
    setActiveView: vi.fn(),
    isMobileMenuOpen: false,
    setIsMobileMenuOpen: vi.fn(),
    isDesktopCollapsed: false,
    isDarkMode: true,
    toggleTheme: vi.fn(),
    authSession: {
      token: 'token-123',
      userId: '1002',
      username: 'user',
      displayName: 'CodingX User',
      userType: 'USER',
    },
    isAuthSubmitting: false,
    onOpenLogin: vi.fn(),
    onLogout: vi.fn(async () => undefined),
    conversations: [],
    activeConversationId: null,
    onSelectConversation: vi.fn(async () => undefined),
    onStartNewConversation: vi.fn(async () => undefined),
    onRenameConversation: vi.fn(async () => undefined),
    onDeleteConversation: vi.fn(async () => undefined),
    workspaceGroups:
      overrides?.workspaceGroups ??
      [
        {
          partitionKey: 'local::d:/code/codingx',
          workspacePath: 'D:/code/CodingX',
          workspaceLabel: 'CodingX',
          runtimeTarget: 'local',
          lastOpenedAt: Date.now(),
          activeConversationId: '2001',
          conversations: [
            {
              id: '2001',
              title: '查询内容',
              status: 'ACTIVE',
              lastMessageAt: '2026-05-19 09:00:00',
              lastRunId: '5001',
            },
          ],
        },
      ],
    activeWorkspacePartitionKey: 'local::d:/code/codingx',
    workspaceLabel: 'CodingX',
    onSelectWorkspacePath: vi.fn(async () => undefined),
    onPickRepositoryDirectory: vi.fn(async () => undefined),
  };
}

describe('Sidebar workspace tree', () => {
  it('应展示工作空间入口与目录按钮', () => {
    render(<Sidebar {...createSidebarProps()} />);

    expect(screen.getByText('工作空间')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '选择本地仓库目录' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增工作空间' })).toBeInTheDocument();
    expect(screen.getByText('当前工作空间')).toBeInTheDocument();
  });

  it('应渲染工作空间分组中的会话', () => {
    render(
      <Sidebar
        {...createSidebarProps({
          workspaceGroups: [
            {
              partitionKey: 'local::d:/code/codingx',
              workspacePath: 'D:/code/CodingX',
              workspaceLabel: 'CodingX',
              runtimeTarget: 'local',
              lastOpenedAt: Date.now(),
              activeConversationId: '2001',
              conversations: [
                {
                  id: '2001',
                  title: '查询内容',
                  status: 'ACTIVE',
                  lastMessageAt: '2026-05-19 09:00:00',
                  lastRunId: '5001',
                },
                {
                  id: '2002',
                  title: '文件分析',
                  status: 'ACTIVE',
                  lastMessageAt: '2026-05-19 08:30:00',
                  lastRunId: '5002',
                },
              ],
            },
          ],
        })}
      />,
    );

    expect(screen.getByText('查询内容')).toBeInTheDocument();
    expect(screen.getByText('文件分析')).toBeInTheDocument();
    expect(screen.getByText('查询内容')).toBeInTheDocument();
    expect(screen.getByText('文件分析')).toBeInTheDocument();
    expect(screen.getByText('当前工作空间')).toBeInTheDocument();
  });
});
