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
});
