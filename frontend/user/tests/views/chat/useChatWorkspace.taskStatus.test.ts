import { act, renderHook, waitFor } from '@testing-library/react';
import { useChatWorkspace } from '@/views/chat/useChatWorkspace';

/**
 * 写入测试登录态，避免每个任务状态用例重复构造认证上下文。
 */
function setAuthenticatedSession() {
  window.localStorage.setItem(
    'codingx.auth.session',
    JSON.stringify({
      token: 'token-123',
      userId: '1002',
      username: 'user',
      displayName: 'CodingX User',
      userType: 'USER',
    }),
  );
}

/**
 * 构造本地快照中的空会话回放记录。
 */
function createEmptyConversationRecord() {
  return {
    owned: true,
    messages: [],
    executionSteps: [],
    references: [],
    artifacts: [],
    currentExperts: [],
    currentSkills: [],
    currentMcps: [],
  };
}

/**
 * 按接口路径返回空数组，覆盖聊天工作区启动与会话回放所需的只读接口。
 */
function createEmptyListResponse() {
  return new Response(
    JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
    { status: 200 },
  );
}

describe('useChatWorkspace task status state', () => {
  beforeEach(() => {
    window.localStorage.clear();
    // 关键约束：会话恢复逻辑会读取 URL 参数，测试间必须清理地址栏。
    window.history.replaceState(window.history.state, '', '/');
    vi.restoreAllMocks();
  });

  /**
   * 从其他分区打开带完成提醒的会话时，应在目标分区标记已读，避免圆点在侧栏分组中残留。
   */
  it('应在跨分区打开会话后清除目标分区任务完成提醒', async () => {
    setAuthenticatedSession();
    const finishedAt = '2026-05-25 10:00:00';
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'local::d:/code/codingx': {
            workspacePath: 'D:/code/CodingX',
            workspaceLabel: 'CodingX',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now(),
            activeConversationId: null,
            conversations: [],
            conversationRecords: {},
            seenTaskFinishedAtByConversationId: {},
          },
          'local::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now() - 1000,
            activeConversationId: null,
            conversations: [
              {
                id: 'task-conversation-history',
                title: '历史完成会话',
                status: 'ACTIVE',
                lastTaskId: 'task-history',
                lastTaskStatus: 'SUCCEEDED',
                lastTaskFinishedAt: finishedAt,
                hasUnreadTaskCompletion: true,
              },
            ],
            conversationRecords: {
              'task-conversation-history': createEmptyConversationRecord(),
            },
            seenTaskFinishedAtByConversationId: {},
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return createEmptyListResponse();
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/task-conversation-history/messages' ||
        url === '/api/chat/conversations/task-conversation-history/steps' ||
        url === '/api/chat/conversations/task-conversation-history/references' ||
        url === '/api/chat/conversations/task-conversation-history/artifacts' ||
        url === '/api/chat/conversations/task-conversation-history/current-experts' ||
        url === '/api/chat/conversations/task-conversation-history/current-skills' ||
        url === '/api/chat/conversations/task-conversation-history/current-mcps'
      ) {
        return createEmptyListResponse();
      }
      throw new Error(`Unhandled fetch in cross partition task reminder clear test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/CodingX',
        permissionGranted: true,
      },
    };
    const { result } = renderHook(() => useChatWorkspace(true, { hostContext }));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    await act(async () => {
      await result.current.selectConversationInWorkspace('task-conversation-history', {
        partitionKey: 'local::__no_workspace__',
        runtimeTarget: 'local',
        workspacePath: null,
      });
    });

    const snapshotStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    expect(
      snapshotStore.snapshots['local::__no_workspace__'].seenTaskFinishedAtByConversationId[
        'task-conversation-history'
      ],
    ).toBe(finishedAt);
    expect(
      snapshotStore.snapshots['local::__no_workspace__'].conversations[0]
        .hasUnreadTaskCompletion,
    ).toBe(false);
  });

  /**
   * 默认本地分区只以本机快照为准，不再用云端会话列表覆盖本地历史状态。
   */
  it('本地默认分区不应被远端终态会话覆盖', async () => {
    setAuthenticatedSession();
    const finishedAt = '2026-05-25 10:00:00';
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'local::d:/code/codingx': {
            workspacePath: 'D:/code/CodingX',
            workspaceLabel: 'CodingX',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now(),
            activeConversationId: 'workspace-owned',
            conversations: [
              {
                id: 'workspace-owned',
                title: '工作区会话',
                status: 'ACTIVE',
              },
            ],
            conversationRecords: {
              'workspace-owned': createEmptyConversationRecord(),
            },
            seenTaskFinishedAtByConversationId: {},
          },
          'local::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now() - 1000,
            activeConversationId: null,
            conversations: [
              {
                id: 'running-stale-conversation',
                title: '旧运行态会话',
                status: 'ACTIVE',
                activeTaskId: 'task-1',
                activeTaskStatus: 'RUNNING',
              },
            ],
            conversationRecords: {},
            seenTaskFinishedAtByConversationId: {},
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/workspace-owned/messages' ||
        url === '/api/chat/conversations/workspace-owned/steps' ||
        url === '/api/chat/conversations/workspace-owned/references' ||
        url === '/api/chat/conversations/workspace-owned/artifacts' ||
        url === '/api/chat/conversations/workspace-owned/current-experts' ||
        url === '/api/chat/conversations/workspace-owned/current-skills' ||
        url === '/api/chat/conversations/workspace-owned/current-mcps'
      ) {
        return createEmptyListResponse();
      }
      throw new Error(`Unhandled fetch in stale running snapshot test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/CodingX',
        permissionGranted: true,
      },
    };
    const { result } = renderHook(() => useChatWorkspace(true, { hostContext }));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    const snapshotStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    const defaultConversation = snapshotStore.snapshots[
      'local::__no_workspace__'
    ].conversations.find((item: { id: string }) => item.id === 'running-stale-conversation');
    expect(defaultConversation).toEqual(
      expect.objectContaining({
        id: 'running-stale-conversation',
        activeTaskStatus: 'RUNNING',
      }),
    );
    expect(defaultConversation?.lastTaskStatus).toBeUndefined();
    expect(defaultConversation?.hasUnreadTaskCompletion).toBeUndefined();
    const defaultWorkspaceGroup = result.current.workspaceGroups.find(
      (group) => group.partitionKey === 'local::__no_workspace__',
    );
    expect(defaultWorkspaceGroup?.conversations[0]?.activeTaskStatus).toBe('RUNNING');
    expect(defaultWorkspaceGroup?.conversations[0]?.lastTaskStatus).toBeUndefined();
    expect(defaultWorkspaceGroup?.conversations[0]?.hasUnreadTaskCompletion).not.toBe(true);
  });

  /**
   * 桌面端同时支持云端和本地运行时，刷新后应回到最近打开过的本地工作空间分区。
   * 这样已读映射仍然能从同一个本地快照中恢复，任务完成提醒不会在刷新后重新点亮。
   */
  it('桌面双运行目标刷新后应恢复本地工作空间任务完成已读状态', async () => {
    setAuthenticatedSession();
    const finishedAt = '2026-05-25 10:00:00';
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'cloud::__no_workspace__': {
            workspacePath: null,
            workspaceLabel: '云端历史',
            runtimeTarget: 'cloud',
            lastOpenedAt: Date.now() - 1000,
            activeConversationId: 'cloud-conversation',
            conversations: [
              {
                id: 'cloud-conversation',
                title: '云端会话',
                status: 'ACTIVE',
                lastTaskId: 'task-cloud',
                lastTaskStatus: 'SUCCEEDED',
                lastTaskFinishedAt: '2026-05-24 09:00:00',
                hasUnreadTaskCompletion: true,
              },
            ],
            conversationRecords: {
              'cloud-conversation': createEmptyConversationRecord(),
            },
            seenTaskFinishedAtByConversationId: {},
          },
          'local::d:/code/codingx': {
            workspacePath: 'D:/code/CodingX',
            workspaceLabel: 'CodingX',
            runtimeTarget: 'local',
            lastOpenedAt: Date.now(),
            activeConversationId: 'local-conversation',
            conversations: [
              {
                id: 'local-conversation',
                title: '本地会话',
                status: 'ACTIVE',
                lastTaskId: 'task-local',
                lastTaskStatus: 'SUCCEEDED',
                lastTaskFinishedAt: finishedAt,
                hasUnreadTaskCompletion: false,
              },
            ],
            conversationRecords: {
              'local-conversation': createEmptyConversationRecord(),
            },
            seenTaskFinishedAtByConversationId: {
              'local-conversation': finishedAt,
            },
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/conversations') {
        return new Response(
          JSON.stringify({
            success: true,
            code: 'OK',
            message: 'success',
            data: [
              {
                id: 'cloud-conversation',
                title: '云端会话',
                status: 'ACTIVE',
                lastTaskId: 'task-cloud',
                lastTaskStatus: 'SUCCEEDED',
                lastTaskFinishedAt: '2026-05-24 09:00:00',
              },
              {
                id: 'local-conversation',
                title: '本地会话',
                status: 'ACTIVE',
                lastTaskId: 'task-local',
                lastTaskStatus: 'SUCCEEDED',
                lastTaskFinishedAt: finishedAt,
                workspaceType: 'LOCAL',
              },
            ],
          }),
          { status: 200 },
        );
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps' ||
        url === '/api/chat/conversations/cloud-conversation/messages' ||
        url === '/api/chat/conversations/cloud-conversation/steps' ||
        url === '/api/chat/conversations/cloud-conversation/references' ||
        url === '/api/chat/conversations/cloud-conversation/artifacts' ||
        url === '/api/chat/conversations/cloud-conversation/current-experts' ||
        url === '/api/chat/conversations/cloud-conversation/current-skills' ||
        url === '/api/chat/conversations/cloud-conversation/current-mcps' ||
        url === '/api/chat/conversations/local-conversation/messages' ||
        url === '/api/chat/conversations/local-conversation/steps' ||
        url === '/api/chat/conversations/local-conversation/references' ||
        url === '/api/chat/conversations/local-conversation/artifacts' ||
        url === '/api/chat/conversations/local-conversation/current-experts' ||
        url === '/api/chat/conversations/local-conversation/current-skills' ||
        url === '/api/chat/conversations/local-conversation/current-mcps'
      ) {
        return createEmptyListResponse();
      }
      throw new Error(`Unhandled fetch in desktop refresh partition restore test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['cloud', 'local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/CodingX',
        permissionGranted: true,
      },
    };

    const { result } = renderHook(() => useChatWorkspace(true, { hostContext }));

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });

    expect(result.current.activeRuntimeTarget).toBe('local');
    expect(result.current.activeWorkspacePartitionKey).toBe('local::d:/code/codingx');
    expect(result.current.workspacePath).toBe('D:/code/CodingX');
    expect(result.current.conversations[0]).toEqual(
      expect.objectContaining({
        id: 'local-conversation',
        hasUnreadTaskCompletion: false,
      }),
    );
    const snapshotStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{}',
    );
    expect(
      snapshotStore.snapshots['local::d:/code/codingx'].seenTaskFinishedAtByConversationId[
        'local-conversation'
      ],
    ).toBe(finishedAt);
  });
});
