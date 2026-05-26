import {
  buildWorkspaceHistoryPartitionKey,
  buildWorkspacePartitionKey,
  listWorkspaceGroups,
  readWorkspaceSnapshot,
  upsertWorkspaceHistorySnapshot,
  upsertWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from '@/views/chat/localConversationStorage';

describe('localConversationStorage', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('应保持工作空间分组顺序稳定，不随访问时间变化抖动', () => {
    const workspaceA = upsertWorkspaceSnapshot('local', 'D:/code/SpaceA', {
      workspaceLabel: 'SpaceA',
      conversations: [],
      activeConversationId: null,
    });
    const workspaceB = upsertWorkspaceSnapshot('local', 'D:/code/SpaceB', {
      workspaceLabel: 'SpaceB',
      conversations: [],
      activeConversationId: null,
    });
    upsertWorkspaceHistorySnapshot('local', []);

    const initialOrder = listWorkspaceGroups('local').map((group) => group.partitionKey);

    writeWorkspaceSnapshot(workspaceA.partitionKey, {
      ...workspaceA.snapshot,
      lastOpenedAt: Date.now() + 100000,
    });
    writeWorkspaceSnapshot(workspaceB.partitionKey, {
      ...workspaceB.snapshot,
      lastOpenedAt: Date.now() - 100000,
    });

    const nextOrder = listWorkspaceGroups('local').map((group) => group.partitionKey);
    expect(nextOrder).toEqual(initialOrder);
  });

  /**
   * 历史分区应并回默认分组，仅保留默认入口与真实工作空间分组。
   */
  it('应将历史分区并入默认分组并保留本地工作空间入口', () => {
    upsertWorkspaceSnapshot('cloud', null, {
      workspaceLabel: '云端历史记录',
      conversations: [{ id: '5001', title: '云端会话', status: 'ACTIVE', lastRunId: '9001' }],
      activeConversationId: '5001',
    });
    upsertWorkspaceHistorySnapshot('cloud', [
      { id: '5002', title: '云端会话B', status: 'ACTIVE', lastRunId: '9002' },
    ]);
    upsertWorkspaceSnapshot('local', 'D:/code/CodingX', {
      workspaceLabel: 'CodingX',
      conversations: [{ id: '6001', title: '本地会话', status: 'ACTIVE', lastRunId: '9101' }],
      activeConversationId: '6001',
    });
    upsertWorkspaceHistorySnapshot('local', [
      { id: '6002', title: '本地历史会话', status: 'ACTIVE', lastRunId: '9102' },
    ]);

    const allGroups = listWorkspaceGroups();
    expect(allGroups.some((group) => group.partitionKey === 'cloud::__history__')).toBe(false);
    expect(allGroups.some((group) => group.partitionKey === 'local::__history__')).toBe(false);
    const cloudDefaultGroup = allGroups.find((group) => group.partitionKey === 'cloud::__no_workspace__');
    expect(cloudDefaultGroup?.workspaceLabel).toBe('云端历史记录');
    expect(cloudDefaultGroup?.groupType).toBe('workspace');
    expect(cloudDefaultGroup?.conversations.map((conversation) => conversation.id)).toEqual(['5001', '5002']);
    const localDefaultGroup = allGroups.find((group) => group.partitionKey === 'local::__no_workspace__');
    expect(localDefaultGroup?.workspaceLabel).toBe('本地历史记录');
    expect(localDefaultGroup?.groupType).toBe('workspace');
    expect(localDefaultGroup?.conversations.map((conversation) => conversation.id)).toEqual(['6002']);
    expect(
      allGroups.some(
        (group) =>
          group.partitionKey === 'local::d:/code/codingx' &&
          group.groupType === 'workspace' &&
          group.workspaceLabel === 'CodingX',
      ),
    ).toBe(true);
  });

  /**
   * 刷新后云端历史分区应自动并入默认云端分组，避免历史键残留导致重复入口。
   */
  it('刷新后应合并云端历史分区并清理历史键', () => {
    const defaultPartitionKey = buildWorkspacePartitionKey('cloud', null);
    const historyPartitionKey = buildWorkspaceHistoryPartitionKey('cloud');
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          [defaultPartitionKey]: {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: 100,
            activeConversationId: null,
            conversations: [
              {
                id: '5001',
                title: '云端会话A',
                status: 'ACTIVE',
                lastRunId: '9001',
              },
            ],
            conversationRecords: {},
          },
          [historyPartitionKey]: {
            workspacePath: null,
            workspaceLabel: '历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: 200,
            activeConversationId: '5002',
            conversations: [
              {
                id: '5002',
                title: '云端会话B',
                status: 'ACTIVE',
                lastRunId: '9002',
              },
            ],
            conversationRecords: {
              '5002': {
                owned: true,
                messages: [],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
          },
        },
      }),
    );

    const cloudGroups = listWorkspaceGroups('cloud');
    expect(cloudGroups.map((group) => group.partitionKey)).toEqual([defaultPartitionKey]);
    expect(cloudGroups[0].conversations.map((conversation) => conversation.id)).toEqual(['5001', '5002']);
    expect(readWorkspaceSnapshot(historyPartitionKey).conversations).toEqual([]);
    expect(window.localStorage.getItem('codingx.chat.workspace.conversations.v1')).not.toContain(
      historyPartitionKey,
    );
  });
});
