import {
  buildWorkspaceHistoryPartitionKey,
  buildWorkspacePartitionKey,
  listWorkspaceGroups,
  readWorkspaceSnapshot,
  saveConversationRecordToWorkspace,
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

  /**
   * 首次保存有路径的本地会话时，应从目录路径推导工作空间名称，不能把空模板标签持久化到侧栏。
   */
  it('首次保存本地工作空间会话时应使用目录名作为分组名称', () => {
    saveConversationRecordToWorkspace(
      'local',
      'D:/code/CodingX',
      'local-1001',
      [{ id: 'local-1001', title: '你好', status: 'ACTIVE', workspaceType: 'LOCAL' }],
      {
        owned: true,
        messages: [],
        executionSteps: [],
        references: [],
        artifacts: [],
        currentExperts: [],
        currentSkills: [],
        currentMcps: [],
      },
    );

    const localGroup = listWorkspaceGroups('local').find(
      (group) => group.partitionKey === 'local::d:/code/codingx',
    );

    expect(localGroup?.workspaceLabel).toBe('CodingX');
  });

  /**
   * 首次创建有路径且包含会话的本地快照时，也必须从路径推导名称。
   */
  it('首次创建本地工作空间快照时应避免继承默认分区标签', () => {
    const { snapshot } = upsertWorkspaceSnapshot('local', 'D:/code/DesignSystem', {
      conversations: [
        {
          id: 'local-design-system',
          title: '设计系统会话',
          status: 'ACTIVE',
          workspaceType: 'LOCAL',
        },
      ],
      activeConversationId: 'local-design-system',
    });

    const localGroup = listWorkspaceGroups('local').find(
      (group) => group.partitionKey === 'local::d:/code/designsystem',
    );

    expect(snapshot.workspaceLabel).toBe('DesignSystem');
    expect(localGroup?.workspaceLabel).toBe('DesignSystem');
  });

  /**
   * 仅绑定过目录但没有任何会话的本地工作空间属于空缓存，侧栏不应展示成一个可展开分组。
   */
  it('应隐藏没有会话内容的本地工作空间分组', () => {
    upsertWorkspaceSnapshot('local', 'D:/code/local', {
      workspaceLabel: 'local',
      conversations: [],
      activeConversationId: null,
      conversationRecords: {},
    });

    const localGroups = listWorkspaceGroups('local');

    expect(localGroups.some((group) => group.partitionKey === 'local::d:/code/local')).toBe(false);
    expect(localGroups.some((group) => group.partitionKey === 'local::__no_workspace__')).toBe(true);
  });

  /**
   * Windows 目录可能从 Electron 返回反斜杠，也可能从缓存/前端逻辑返回正斜杠；
   * 同一物理目录必须归入同一个分区，避免侧栏出现两个同名项目。
   */
  it('应将同一路径的正反斜杠写法归一到同一个本地工作空间分区', () => {
    const forwardSlashKey = buildWorkspacePartitionKey('local', 'D:/code/test');
    const backslashKey = buildWorkspacePartitionKey('local', 'D:\\code\\test');

    upsertWorkspaceSnapshot('local', 'D:/code/test', {
      conversations: [{ id: 'local-a', title: '正斜杠会话', status: 'ACTIVE', workspaceType: 'LOCAL' }],
      activeConversationId: 'local-a',
    });
    upsertWorkspaceSnapshot('local', 'D:\\code\\test', {
      conversations: [{ id: 'local-b', title: '反斜杠会话', status: 'ACTIVE', workspaceType: 'LOCAL' }],
      activeConversationId: 'local-b',
    });

    const testGroups = listWorkspaceGroups('local').filter((group) => group.workspaceLabel === 'test');

    expect(backslashKey).toBe(forwardSlashKey);
    expect(testGroups).toHaveLength(1);
    expect(testGroups[0].partitionKey).toBe('local::d:/code/test');
  });

  /**
   * 已写入本地缓存的旧分区键也要迁移，否则用户刷新后仍会看到两个同名本地项目。
   */
  it('应在读取旧缓存时合并正反斜杠重复的本地工作空间分区', () => {
    const canonicalPartitionKey = buildWorkspacePartitionKey('local', 'D:/code/test');
    const legacyBackslashPartitionKey = 'local::d:\\code\\test';

    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          [canonicalPartitionKey]: {
            workspacePath: 'D:/code/test',
            workspaceLabel: 'test',
            runtimeTarget: 'local',
            lastOpenedAt: 100,
            activeConversationId: 'local-a',
            conversations: [{ id: 'local-a', title: '正斜杠会话', status: 'ACTIVE', workspaceType: 'LOCAL' }],
            conversationRecords: {},
          },
          [legacyBackslashPartitionKey]: {
            workspacePath: 'D:\\code\\test',
            workspaceLabel: 'test',
            runtimeTarget: 'local',
            lastOpenedAt: 200,
            activeConversationId: 'local-b',
            conversations: [{ id: 'local-b', title: '反斜杠会话', status: 'ACTIVE', workspaceType: 'LOCAL' }],
            conversationRecords: {},
          },
        },
      }),
    );

    const testGroups = listWorkspaceGroups('local').filter((group) => group.workspaceLabel === 'test');
    const persistedStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{"snapshots":{}}',
    );

    expect(testGroups).toHaveLength(1);
    expect(testGroups[0].partitionKey).toBe(canonicalPartitionKey);
    expect(testGroups[0].conversations.map((conversation) => conversation.id)).toEqual([
      'local-a',
      'local-b',
    ]);
    expect(testGroups[0].activeConversationId).toBe('local-b');
    expect(Object.keys(persistedStore.snapshots)).toContain(canonicalPartitionKey);
    expect(Object.keys(persistedStore.snapshots)).not.toContain(legacyBackslashPartitionKey);
  });

  /**
   * 云端历史不应绑定本地目录；旧缓存中带路径的云端分区要并回云端默认分区，避免侧栏出现云端 test。
   */
  it('应将带本地路径的云端工作空间分区归并到云端默认分区', () => {
    const defaultCloudPartitionKey = buildWorkspacePartitionKey('cloud', null);
    const cloudPathPartitionKey = 'cloud::d:/code/test';

    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          [defaultCloudPartitionKey]: {
            workspacePath: null,
            workspaceLabel: '云端历史记录',
            runtimeTarget: 'cloud',
            lastOpenedAt: 100,
            activeConversationId: 'cloud-a',
            conversations: [{ id: 'cloud-a', title: '云端默认会话', status: 'ACTIVE' }],
            conversationRecords: {},
          },
          [cloudPathPartitionKey]: {
            workspacePath: 'D:/code/test',
            workspaceLabel: 'test',
            runtimeTarget: 'cloud',
            lastOpenedAt: 200,
            activeConversationId: 'cloud-b',
            conversations: [{ id: 'cloud-b', title: '误归属本地目录的云端会话', status: 'ACTIVE' }],
            conversationRecords: {},
          },
        },
      }),
    );

    const cloudGroups = listWorkspaceGroups('cloud');
    const persistedStore = JSON.parse(
      window.localStorage.getItem('codingx.chat.workspace.conversations.v1') ?? '{"snapshots":{}}',
    );

    expect(buildWorkspacePartitionKey('cloud', 'D:/code/test')).toBe(defaultCloudPartitionKey);
    expect(cloudGroups).toHaveLength(1);
    expect(cloudGroups[0].partitionKey).toBe(defaultCloudPartitionKey);
    expect(cloudGroups[0].workspaceLabel).toBe('云端历史记录');
    expect(cloudGroups[0].workspacePath).toBeNull();
    expect(cloudGroups[0].activeConversationId).toBe('cloud-b');
    expect(cloudGroups[0].conversations.map((conversation) => conversation.id)).toEqual([
      'cloud-a',
      'cloud-b',
    ]);
    expect(Object.keys(persistedStore.snapshots)).toContain(defaultCloudPartitionKey);
    expect(Object.keys(persistedStore.snapshots)).not.toContain(cloudPathPartitionKey);
  });
});
