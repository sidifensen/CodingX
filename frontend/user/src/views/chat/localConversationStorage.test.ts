import {
  buildWorkspaceHistoryPartitionKey,
  buildWorkspacePartitionKey,
  listWorkspaceGroups,
  readWorkspaceSnapshot,
  upsertWorkspaceHistorySnapshot,
  upsertWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from './localConversationStorage';

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
   * 云端会话应直接展示在默认云端分组，不再拆成隐藏的历史分区。
   */
  it('云端分组应合并历史会话并保留默认云端分组', () => {
    upsertWorkspaceSnapshot('cloud', null, {
      workspaceLabel: '历史记录',
      conversations: [{ id: '5001', title: '云端会话', status: 'ACTIVE', lastRunId: '9001' }],
      activeConversationId: '5001',
    });
    upsertWorkspaceHistorySnapshot('cloud', [
      { id: '5002', title: '云端会话B', status: 'ACTIVE', lastRunId: '9002' },
    ]);

    const cloudGroups = listWorkspaceGroups('cloud');
    expect(cloudGroups).toHaveLength(1);
    expect(cloudGroups[0].workspaceLabel).toBe('历史记录');
    expect(cloudGroups[0].groupType).toBe('workspace');
    expect(cloudGroups[0].conversations.map((conversation) => conversation.id)).toEqual(['5001', '5002']);
  });

  /**
   * 刷新后云端历史会话应合并回默认云端分组，避免侧栏只剩隐藏历史分区。
   */
  it('云端历史会话应合并到默认云端分组并清理历史分区', () => {
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

    expect(cloudGroups).toHaveLength(1);
    expect(cloudGroups[0].partitionKey).toBe(defaultPartitionKey);
    expect(cloudGroups[0].groupType).toBe('workspace');
    expect(cloudGroups[0].conversations.map((conversation) => conversation.id)).toEqual([
      '5001',
      '5002',
    ]);
    expect(readWorkspaceSnapshot(defaultPartitionKey).conversations.map((conversation) => conversation.id)).toEqual([
      '5001',
      '5002',
    ]);
    expect(readWorkspaceSnapshot(historyPartitionKey).conversations).toEqual([]);
    expect(window.localStorage.getItem('codingx.chat.workspace.conversations.v1')).not.toContain(
      historyPartitionKey,
    );
  });
});
