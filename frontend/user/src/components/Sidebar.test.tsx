import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Sidebar from './Sidebar';
import { WorkspaceConversationGroup } from '../views/chat/types';

/**
 * 生成测试所需的会话项，便于验证“每次展开 5 条”的分页行为。
 */
function createConversation(index: number) {
  return {
    id: `conversation-${index}`,
    title: `会话 ${index}`,
    status: 'ACTIVE',
    lastMessageAt: '2026-05-19 09:00:00',
    lastRunId: `run-${index}`,
  };
}

/**
 * 构建 Sidebar 测试所需的最小属性集。
 */
function createSidebarProps(overrides?: {
  workspaceGroups?: WorkspaceConversationGroup[];
}) {
  const defaultConversations = Array.from({ length: 11 }, (_, i) => createConversation(i + 1));
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
    conversations: defaultConversations,
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
          activeConversationId: 'conversation-1',
          conversations: defaultConversations,
        },
      ],
    activeWorkspacePartitionKey: 'local::d:/code/codingx',
    onSelectWorkspacePath: vi.fn(async () => undefined),
  };
}

describe('Sidebar conversation collapse behavior', () => {
  it('默认仅显示 5 条并展示展开按钮', () => {
    render(<Sidebar {...createSidebarProps()} />);

    expect(screen.getByText('会话 1')).toBeInTheDocument();
    expect(screen.getByText('会话 5')).toBeInTheDocument();
    expect(screen.queryByText('会话 6')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '展开显示' })).toBeInTheDocument();
  });

  it('点击展开后每次追加 5 条，并可收起回 5 条', () => {
    render(<Sidebar {...createSidebarProps()} />);

    fireEvent.click(screen.getByRole('button', { name: '展开显示' }));
    expect(screen.getByText('会话 10')).toBeInTheDocument();
    expect(screen.queryByText('会话 11')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '展开显示' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '展开显示' }));
    expect(screen.getByText('会话 11')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '收起显示' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '收起显示' }));
    expect(screen.queryByText('会话 6')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '展开显示' })).toBeInTheDocument();
  });
});
