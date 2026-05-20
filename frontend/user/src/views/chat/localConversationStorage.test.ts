import {
  listWorkspaceGroups,
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
   * 云端会话统一归入历史分组时，不应再渲染默认云端工作空间分组。
   */
  it('云端分组应隐藏默认工作空间，仅保留历史会话分组', () => {
    upsertWorkspaceSnapshot('cloud', null, {
      workspaceLabel: '云端工作空间',
      conversations: [{ id: '5001', title: '云端会话', status: 'ACTIVE', lastRunId: '9001' }],
      activeConversationId: '5001',
    });
    upsertWorkspaceHistorySnapshot('cloud', [
      { id: '5001', title: '云端会话', status: 'ACTIVE', lastRunId: '9001' },
    ]);

    const cloudGroups = listWorkspaceGroups('cloud');
    expect(cloudGroups).toHaveLength(1);
    expect(cloudGroups[0].workspaceLabel).toBe('历史会话');
    expect(cloudGroups[0].groupType).toBe('history');
  });
});
