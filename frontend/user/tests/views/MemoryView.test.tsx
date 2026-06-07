import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import MemoryView from '@/views/MemoryView';
import { LongTermMemoryItem } from '@/views/chat/types';

const listLongTermMemoriesMock = vi.fn();
const updateLongTermMemoryStatusMock = vi.fn();
const updateLongTermMemoryContentMock = vi.fn();
const deleteLongTermMemoryMock = vi.fn();
const getSessionMock = vi.fn();

vi.mock('@/views/chat/chatApi', () => ({
  ChatApi: {
    listLongTermMemories: (...args: unknown[]) => listLongTermMemoriesMock(...args),
    updateLongTermMemoryStatus: (...args: unknown[]) => updateLongTermMemoryStatusMock(...args),
    updateLongTermMemoryContent: (...args: unknown[]) => updateLongTermMemoryContentMock(...args),
    deleteLongTermMemory: (...args: unknown[]) => deleteLongTermMemoryMock(...args),
  },
}));

vi.mock('@/utils/authStorage', () => ({
  AuthStorage: {
    getSession: () => getSessionMock(),
  },
}));

const memories: LongTermMemoryItem[] = [
  {
    id: '9001',
    memoryScope: 'USER',
    userId: '1002',
    workspaceId: null,
    content: '以后回答都先给结论',
    status: 'ACTIVE',
    keywordJson: '["结论"]',
    createdAt: '2026-06-07 10:00:00',
    updatedAt: '2026-06-07 10:00:00',
  },
  {
    id: '9002',
    memoryScope: 'PROJECT',
    userId: '1002',
    workspaceId: '3001',
    content: '项目 Java 文件必须补充业务注释',
    status: 'ACTIVE',
    keywordJson: '["业务注释"]',
    updatedAt: '2026-06-07 11:00:00',
  },
  {
    id: '9003',
    memoryScope: 'PROJECT',
    userId: '1002',
    workspaceId: '3001',
    content: '已废弃的旧项目约定',
    status: 'REJECTED',
    updatedAt: '2026-06-07 12:00:00',
  },
];

describe('MemoryView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getSessionMock.mockReturnValue({ token: 'token-123' });
    listLongTermMemoriesMock.mockResolvedValue(memories);
    updateLongTermMemoryStatusMock.mockImplementation(
      async (_token: string, memoryId: string, status: string) => ({
        ...memories.find((memory) => memory.id === memoryId)!,
        status,
      }),
    );
    updateLongTermMemoryContentMock.mockImplementation(
      async (_token: string, memoryId: string, content: string) => ({
        ...memories.find((memory) => memory.id === memoryId)!,
        content,
      }),
    );
    deleteLongTermMemoryMock.mockResolvedValue(undefined);
  });

  /**
   * 页面应按当前工作空间加载用户级和项目级记忆，并展示关键统计。
   */
  it('loads visible memories for current workspace', async () => {
    render(
      <MemoryView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspaceId="3001"
        workspaceLabel="CodingX"
      />,
    );

    expect(await screen.findByRole('heading', { name: '记忆管理' })).toBeInTheDocument();
    expect(listLongTermMemoriesMock).toHaveBeenCalledWith('token-123', '3001', 'ALL');
    expect(screen.getByText('以后回答都先给结论')).toBeInTheDocument();
    expect(screen.getByText('项目 Java 文件必须补充业务注释')).toBeInTheDocument();
    expect(screen.getByText('已废弃的旧项目约定')).toBeInTheDocument();
    expect(screen.getByText('CodingX')).toBeInTheDocument();
    expect(screen.getByText('生效 2')).toBeInTheDocument();
  });

  /**
   * 范围与状态筛选应在前端本地完成，避免用户切换筛选时反复请求。
   */
  it('filters memories by scope and status', async () => {
    render(
      <MemoryView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspaceId="3001"
        workspaceLabel="CodingX"
      />,
    );

    await screen.findByText('以后回答都先给结论');
    fireEvent.click(screen.getByRole('button', { name: '项目记忆' }));

    expect(screen.queryByText('以后回答都先给结论')).not.toBeInTheDocument();
    expect(screen.getByText('项目 Java 文件必须补充业务注释')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '已停用' }));
    expect(screen.queryByText('项目 Java 文件必须补充业务注释')).not.toBeInTheDocument();
    expect(screen.getByText('已废弃的旧项目约定')).toBeInTheDocument();
  });

  /**
   * 编辑弹窗必须是项目内自定义浮层，并在保存后同步列表正文。
   */
  it('edits memory content with custom dialog', async () => {
    render(
      <MemoryView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspaceId="3001"
        workspaceLabel="CodingX"
      />,
    );

    await screen.findByText('以后回答都先给结论');
    fireEvent.click(screen.getByRole('button', { name: '编辑记忆 以后回答都先给结论' }));
    const dialog = screen.getByRole('dialog', { name: '编辑长期记忆' });
    const textarea = within(dialog).getByLabelText('记忆内容');
    fireEvent.change(textarea, { target: { value: '以后回答都先给可执行步骤' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存' }));

    await waitFor(() => {
      expect(updateLongTermMemoryContentMock).toHaveBeenCalledWith(
        'token-123',
        '9001',
        '以后回答都先给可执行步骤',
      );
    });
    expect(await screen.findByText('以后回答都先给可执行步骤')).toBeInTheDocument();
  });

  /**
   * 空白记忆正文应在前端阻止提交，并显示中文错误。
   */
  it('rejects blank memory content before submit', async () => {
    render(
      <MemoryView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspaceId="3001"
        workspaceLabel="CodingX"
      />,
    );

    await screen.findByText('以后回答都先给结论');
    fireEvent.click(screen.getByRole('button', { name: '编辑记忆 以后回答都先给结论' }));
    const dialog = screen.getByRole('dialog', { name: '编辑长期记忆' });
    fireEvent.change(within(dialog).getByLabelText('记忆内容'), { target: { value: '   ' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存' }));

    expect(screen.getByText('记忆内容不能为空')).toBeInTheDocument();
    expect(updateLongTermMemoryContentMock).not.toHaveBeenCalled();
  });

  /**
   * 用户应能停用 ACTIVE 记忆，也能重新启用 REJECTED 记忆。
   */
  it('toggles memory status', async () => {
    render(
      <MemoryView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspaceId="3001"
        workspaceLabel="CodingX"
      />,
    );

    await screen.findByText('以后回答都先给结论');
    fireEvent.click(screen.getByRole('button', { name: '停用记忆 以后回答都先给结论' }));
    await waitFor(() => {
      expect(updateLongTermMemoryStatusMock).toHaveBeenCalledWith('token-123', '9001', 'REJECTED');
    });

    fireEvent.click(screen.getByRole('button', { name: '已停用' }));
    fireEvent.click(screen.getByRole('button', { name: '启用记忆 已废弃的旧项目约定' }));
    await waitFor(() => {
      expect(updateLongTermMemoryStatusMock).toHaveBeenCalledWith('token-123', '9003', 'ACTIVE');
    });
  });

  /**
   * 删除操作必须先展示自定义确认弹窗，确认后从列表移除。
   */
  it('deletes memory after custom confirmation', async () => {
    render(
      <MemoryView
        isAuthenticated={true}
        onRequireLogin={vi.fn()}
        workspaceId="3001"
        workspaceLabel="CodingX"
      />,
    );

    await screen.findByText('以后回答都先给结论');
    fireEvent.click(screen.getByRole('button', { name: '删除记忆 以后回答都先给结论' }));
    const dialog = screen.getByRole('dialog', { name: '删除长期记忆' });
    fireEvent.click(within(dialog).getByRole('button', { name: '删除' }));

    await waitFor(() => {
      expect(deleteLongTermMemoryMock).toHaveBeenCalledWith('token-123', '9001');
    });
    expect(screen.queryByText('以后回答都先给结论')).not.toBeInTheDocument();
  });

  /**
   * 未登录时不应加载记忆接口，页面只提供项目内登录入口。
   */
  it('shows login prompt without requesting memories when unauthenticated', () => {
    getSessionMock.mockReturnValue(null);
    const onRequireLogin = vi.fn();

    render(
      <MemoryView
        isAuthenticated={false}
        onRequireLogin={onRequireLogin}
        workspaceId={null}
        workspaceLabel="云端历史记录"
      />,
    );

    expect(screen.getByRole('heading', { name: '记忆管理' })).toBeInTheDocument();
    expect(screen.getByText('登录后查看和管理长期记忆')).toBeInTheDocument();
    expect(listLongTermMemoriesMock).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '登录后管理记忆' }));
    expect(onRequireLogin).toHaveBeenCalledTimes(1);
  });
});
