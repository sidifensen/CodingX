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
  it('点击分组箭头可折叠与展开会话列表', () => {
    render(<Sidebar {...createSidebarProps()} />);

    const toggleButton = screen.getByRole('button', { name: '折叠工作空间 CodingX 会话' });
    fireEvent.click(toggleButton);
    const collapsedToggleButton = screen.getByRole('button', { name: '展开工作空间 CodingX 会话' });
    expect(collapsedToggleButton).toBeInTheDocument();
    const collapseContainer = collapsedToggleButton.closest('section')?.querySelector('div[aria-hidden=\"true\"]');
    expect(collapseContainer).toBeInTheDocument();
    // 折叠区采用高度过渡动画，断言收起态的网格行与透明度状态。
    expect(collapseContainer?.className).toContain('grid-rows-[0fr]');
    expect(collapseContainer?.className).toContain('opacity-0');

    fireEvent.click(collapsedToggleButton);
    expect(screen.getByText('会话 1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '展开显示' })).toBeInTheDocument();
  });

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

  it('应以图标区分云端与本地分组并移除次级运行环境文案', () => {
    // 业务意图：侧栏通过图标表达运行环境，避免重复“云端/本地”文字占据纵向空间。
    const props = createSidebarProps({
      workspaceGroups: [
        {
          partitionKey: 'cloud::__no_workspace__',
          workspacePath: null,
          workspaceLabel: '历史记录',
          runtimeTarget: 'cloud',
          lastOpenedAt: Date.now(),
          activeConversationId: 'conversation-1',
          conversations: [createConversation(1)],
        },
        {
          partitionKey: 'local::d:/code/codingx',
          workspacePath: 'D:/code/CodingX',
          workspaceLabel: 'CodingX',
          runtimeTarget: 'local',
          lastOpenedAt: Date.now(),
          activeConversationId: 'conversation-2',
          conversations: [createConversation(2)],
        },
      ],
    });

    render(<Sidebar {...props} />);

    expect(screen.getByTestId('workspace-runtime-icon-cloud')).toBeInTheDocument();
    expect(screen.getByTestId('workspace-runtime-icon-local')).toBeInTheDocument();
    expect(screen.queryByText('云端')).not.toBeInTheDocument();
    expect(screen.queryByText('D:/code/CodingX')).not.toBeInTheDocument();
  });

  it('历史分组应固定使用历史图标，不应复用本地工作空间图标', () => {
    const props = createSidebarProps({
      workspaceGroups: [
        {
          partitionKey: 'local::d:/code/codingx',
          workspacePath: 'D:/code/CodingX',
          workspaceLabel: 'CodingX',
          runtimeTarget: 'local',
          lastOpenedAt: Date.now(),
          activeConversationId: 'conversation-1',
          conversations: [createConversation(1)],
        },
        {
          partitionKey: 'local::__history__',
          workspacePath: null,
          workspaceLabel: '历史记录',
          runtimeTarget: 'local',
          groupType: 'history',
          lastOpenedAt: Date.now() - 1000,
          activeConversationId: null,
          conversations: [createConversation(2)],
        },
      ],
    });

    render(<Sidebar {...props} />);

    expect(screen.getAllByTestId('workspace-runtime-icon-local').length).toBeGreaterThan(0);
    expect(screen.getByTestId('workspace-runtime-icon-history')).toBeInTheDocument();
  });

  it('会话项 hover 态应具备高亮边框背景，展开按钮应为无边框紧凑样式', () => {
    render(<Sidebar {...createSidebarProps()} />);

    const conversationButton = screen.getByRole('button', { name: '会话 1' });
    const conversationRow = conversationButton.closest('div[class*=\"rounded-xl border\"]');
    expect(conversationRow?.className).toContain('border-transparent');
    expect(conversationRow?.className).toContain('hover:border-border-active');
    expect(conversationRow?.className).toContain('hover:bg-surface-container-high');

    const expandButton = screen.getByRole('button', { name: '展开显示' });
    expect(expandButton.className).toContain('inline-flex');
    expect(expandButton.className).toContain('rounded-lg');
    expect(expandButton.className).toContain('px-2.5');
    expect(expandButton.className).toContain('py-1.5');
    expect(expandButton.className).toContain('text-xs');
    expect(expandButton.className).not.toContain('w-full');
    expect(expandButton.className).not.toContain('border');
  });

  it('点击会话时应回传会话所属分组上下文，避免跨空间串线', () => {
    const props = createSidebarProps();
    render(<Sidebar {...props} />);

    fireEvent.click(screen.getByRole('button', { name: '会话 1' }));

    expect(props.onSelectConversation).toHaveBeenCalledWith(
      'conversation-1',
      expect.objectContaining({
        partitionKey: 'local::d:/code/codingx',
        runtimeTarget: 'local',
        workspacePath: 'D:/code/CodingX',
      }),
    );
  });

  it('点击历史分组头只折叠展开，不应触发空间路径切换', () => {
    const historyOnlyConversations = Array.from({ length: 2 }, (_, i) => createConversation(i + 1));
    const props = createSidebarProps({
      workspaceGroups: [
        {
          partitionKey: 'local::d:/code/codingx',
          workspacePath: 'D:/code/CodingX',
          workspaceLabel: 'CodingX',
          runtimeTarget: 'local',
          lastOpenedAt: Date.now(),
          activeConversationId: 'conversation-1',
          conversations: historyOnlyConversations,
        },
        {
          partitionKey: 'local::__history__',
          workspacePath: null,
          workspaceLabel: '历史记录',
          runtimeTarget: 'local',
          groupType: 'history',
          lastOpenedAt: Date.now() - 1000,
          activeConversationId: null,
          conversations: historyOnlyConversations,
        },
      ],
    });

    render(<Sidebar {...props} />);
    fireEvent.click(screen.getByRole('button', { name: '折叠工作空间 历史记录 会话' }));

    expect(props.onSelectWorkspacePath).not.toHaveBeenCalledWith(null);
  });

  it('分组标题右侧应提供新建按钮并携带分组上下文', () => {
    const props = createSidebarProps();
    render(<Sidebar {...props} />);

    const createButton = screen.getByRole('button', { name: '在工作空间 CodingX 新建对话' });
    fireEvent.click(createButton);

    expect(props.onStartNewConversation).toHaveBeenCalledWith({
      partitionKey: 'local::d:/code/codingx',
      runtimeTarget: 'local',
      workspacePath: 'D:/code/CodingX',
      groupType: undefined,
    });
    expect(props.onSelectWorkspacePath).not.toHaveBeenCalled();
  });

  it('会话菜单应使用fixed高层级浮层，避免被侧栏滚动容器裁剪', () => {
    // 业务意图：通过 Portal + fixed 固定在视口层，避免菜单被侧栏 overflow 裁剪成“半截”。
    const props = createSidebarProps();
    render(<Sidebar {...props} />);

    fireEvent.click(screen.getByRole('button', { name: '打开会话菜单 会话 1' }));

    const renameButton = screen.getByRole('button', { name: '重命名对话' });
    const menuPanel = renameButton.closest('div');
    expect(menuPanel).toHaveClass('fixed');
    expect(menuPanel).toHaveClass('z-[130]');
  });
});
