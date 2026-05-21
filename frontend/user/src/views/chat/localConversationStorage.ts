import {
  ArtifactItem,
  ChatMessageItem,
  ConversationItem,
  CurrentExpertItem,
  CurrentMcpItem,
  CurrentSkillItem,
  ExecutionStepItem,
  ReferenceItem,
} from './types';

/**
 * 本地会话快照存储键，按工作空间隔离保存聊天历史。
 */
const LOCAL_WORKSPACE_CONVERSATION_STORE_KEY = 'codingx.chat.workspace.conversations.v1';
const WORKSPACE_HISTORY_PARTITION_SUFFIX = '__history__';
const WORKSPACE_HISTORY_LABEL = '历史会话';

/**
 * 统一表示单个会话在本地缓存中的完整回放数据。
 */
export interface LocalConversationRecord {
  owned?: boolean;
  messages: ChatMessageItem[];
  executionSteps: ExecutionStepItem[];
  references: ReferenceItem[];
  artifacts: ArtifactItem[];
  currentExperts: CurrentExpertItem[];
  currentSkills: CurrentSkillItem[];
  currentMcps: CurrentMcpItem[];
}

/**
 * 统一表示某个工作空间对应的会话快照。
 */
export interface LocalWorkspaceConversationSnapshot {
  workspacePath: string | null;
  workspaceLabel: string;
  runtimeTarget: 'cloud' | 'local';
  lastOpenedAt: number;
  activeConversationId: string | null;
  conversations: ConversationItem[];
  conversationRecords: Record<string, LocalConversationRecord>;
}

/**
 * 统一表示工作空间分组项，供侧边栏按目录展示多工作空间会话。
 */
export interface WorkspaceConversationGroup extends LocalWorkspaceConversationSnapshot {
  partitionKey: string;
  groupType?: 'workspace' | 'history';
}

interface LocalWorkspaceConversationStore {
  version: 1;
  snapshots: Record<string, LocalWorkspaceConversationSnapshot>;
}

const EMPTY_SNAPSHOT: LocalWorkspaceConversationSnapshot = {
  workspacePath: null,
  workspaceLabel: '未命名工作空间',
  runtimeTarget: 'cloud',
  lastOpenedAt: 0,
  activeConversationId: null,
  conversations: [],
  conversationRecords: {},
};

/**
 * 生成本地工作空间分区键，确保不同目录下会话相互隔离。
 * @param runtimeTarget 运行环境。
 * @param workspacePath 当前绑定的工作空间路径。
 * @returns 本地快照分区键。
 */
export function buildWorkspacePartitionKey(
  runtimeTarget: 'cloud' | 'local',
  workspacePath: string | null,
) {
  const normalizedPath = (workspacePath ?? '').trim().toLowerCase();
  return `${runtimeTarget}::${normalizedPath || '__no_workspace__'}`;
}

/**
 * 生成“历史会话”分区键，用于承载未归属当前工作空间的历史会话。
 * @param runtimeTarget 运行环境。
 * @returns 历史分区键。
 */
export function buildWorkspaceHistoryPartitionKey(runtimeTarget: 'cloud' | 'local') {
  return `${runtimeTarget}::${WORKSPACE_HISTORY_PARTITION_SUFFIX}`;
}

/**
 * 判断分区键是否属于“历史会话”分组。
 * @param partitionKey 分区键。
 * @returns 是否为历史分组。
 */
export function isWorkspaceHistoryPartitionKey(partitionKey: string) {
  return partitionKey.endsWith(`::${WORKSPACE_HISTORY_PARTITION_SUFFIX}`);
}

/**
 * 将原始路径转换为适合展示的工作空间标题，避免左侧重复显示冗长完整路径。
 * @param workspacePath 原始路径。
 * @returns 可展示的工作空间标题。
 */
export function getWorkspaceLabel(workspacePath: string | null) {
  const normalizedPath = (workspacePath ?? '').replace(/\\/g, '/').replace(/\/+$/g, '');
  if (!normalizedPath) {
    return '云端工作空间';
  }
  const segments = normalizedPath.split('/').filter(Boolean);
  return segments.length ? segments[segments.length - 1] : normalizedPath;
}

/**
 * 读取指定分区的本地会话快照；读取失败时返回空快照。
 * @param partitionKey 分区键。
 * @returns 本地会话快照。
 */
export function readWorkspaceSnapshot(partitionKey: string): LocalWorkspaceConversationSnapshot {
  const store = readStore();
  const snapshot = store.snapshots[partitionKey];
  if (!snapshot) {
    return EMPTY_SNAPSHOT;
  }
  return {
    workspacePath: snapshot.workspacePath ?? null,
    workspaceLabel: snapshot.workspaceLabel ?? '未命名工作空间',
    runtimeTarget: snapshot.runtimeTarget ?? 'cloud',
    lastOpenedAt: snapshot.lastOpenedAt ?? 0,
    activeConversationId: snapshot.activeConversationId ?? null,
    conversations: Array.isArray(snapshot.conversations) ? snapshot.conversations : [],
    conversationRecords:
      snapshot.conversationRecords && typeof snapshot.conversationRecords === 'object'
        ? snapshot.conversationRecords
        : {},
  };
}

/**
 * 持久化写入指定分区快照，保证本地模式会话可跨刷新恢复。
 * @param partitionKey 分区键。
 * @param snapshot 快照数据。
 */
export function writeWorkspaceSnapshot(
  partitionKey: string,
  snapshot: LocalWorkspaceConversationSnapshot,
) {
  const store = readStore();
  store.snapshots[partitionKey] = snapshot;
  window.localStorage.setItem(LOCAL_WORKSPACE_CONVERSATION_STORE_KEY, JSON.stringify(store));
}

/**
 * 创建或更新指定工作空间快照，同时刷新最后访问时间，便于左侧按最近使用排序。
 * @param runtimeTarget 运行环境。
 * @param workspacePath 工作空间路径。
 * @param partialSnapshot 局部快照。
 * @returns 完整工作空间快照与分区键。
 */
export function upsertWorkspaceSnapshot(
  runtimeTarget: 'cloud' | 'local',
  workspacePath: string | null,
  partialSnapshot?: Partial<LocalWorkspaceConversationSnapshot>,
): { partitionKey: string; snapshot: LocalWorkspaceConversationSnapshot } {
  const partitionKey = buildWorkspacePartitionKey(runtimeTarget, workspacePath);
  const currentSnapshot = readWorkspaceSnapshot(partitionKey);
  const snapshot: LocalWorkspaceConversationSnapshot = {
    workspacePath: workspacePath ?? currentSnapshot.workspacePath ?? null,
    workspaceLabel:
      partialSnapshot?.workspaceLabel ??
      currentSnapshot.workspaceLabel ??
      getWorkspaceLabel(workspacePath),
    runtimeTarget,
    lastOpenedAt: Date.now(),
    activeConversationId:
      partialSnapshot?.activeConversationId ?? currentSnapshot.activeConversationId ?? null,
    conversations: partialSnapshot?.conversations ?? currentSnapshot.conversations ?? [],
    conversationRecords:
      partialSnapshot?.conversationRecords ?? currentSnapshot.conversationRecords ?? {},
  };
  writeWorkspaceSnapshot(partitionKey, snapshot);
  return { partitionKey, snapshot };
}

/**
 * 清理指定分区快照，供后续状态重置或故障恢复使用。
 * @param partitionKey 分区键。
 */
export function clearWorkspaceSnapshot(partitionKey: string) {
  const store = readStore();
  if (!(partitionKey in store.snapshots)) {
    return;
  }
  delete store.snapshots[partitionKey];
  window.localStorage.setItem(LOCAL_WORKSPACE_CONVERSATION_STORE_KEY, JSON.stringify(store));
}

/**
 * 枚举所有工作空间快照，供左侧工作空间树按最近打开时间排序渲染。
 * @returns 排序后的工作空间分组。
 */
export function listWorkspaceGroups(
  runtimeTarget?: 'cloud' | 'local',
): WorkspaceConversationGroup[] {
  const store = readStore();
  return Object.entries(store.snapshots)
    .map(([partitionKey, snapshot]) => ({
      partitionKey,
      workspacePath: snapshot.workspacePath ?? null,
      workspaceLabel: snapshot.workspaceLabel ?? getWorkspaceLabel(snapshot.workspacePath ?? null),
      runtimeTarget: snapshot.runtimeTarget ?? 'cloud',
      lastOpenedAt: snapshot.lastOpenedAt ?? 0,
      activeConversationId: snapshot.activeConversationId ?? null,
      conversations: Array.isArray(snapshot.conversations) ? snapshot.conversations : [],
      groupType: isWorkspaceHistoryPartitionKey(partitionKey) ? 'history' : 'workspace',
      conversationRecords:
        snapshot.conversationRecords && typeof snapshot.conversationRecords === 'object'
          ? snapshot.conversationRecords
          : {},
    }))
    // 业务约束：云端会话统一保留在默认云端分组，不再单独渲染云端历史分区。
    .filter((group) => !(group.runtimeTarget === 'cloud' && group.groupType === 'history'))
    .filter((group) => (runtimeTarget ? group.runtimeTarget === runtimeTarget : true))
    .sort((left, right) => {
      const leftIsHistory = left.groupType === 'history';
      const rightIsHistory = right.groupType === 'history';
      // 先固定“工作空间分组在前、历史分组在后”，避免访问时间波动导致分组位置抖动。
      if (leftIsHistory !== rightIsHistory) {
        return leftIsHistory ? 1 : -1;
      }
      // 同级分组按标签名稳定排序，确保点击/刷新后渲染顺序可预测。
      const labelCompare = left.workspaceLabel.localeCompare(right.workspaceLabel, 'zh-Hans-CN');
      if (labelCompare !== 0) {
        return labelCompare;
      }
      return left.partitionKey.localeCompare(right.partitionKey, 'zh-Hans-CN');
    });
}

/**
 * 根据会话标识查找其已归属的工作空间分区。
 * @param conversationId 会话标识。
 * @returns 分区键或 null。
 */
export function findWorkspacePartitionByConversationId(conversationId: string | null) {
  if (!conversationId) {
    return null;
  }
  const store = readStore();
  const entry = Object.entries(store.snapshots).find(([partitionKey, snapshot]) => {
    if (isWorkspaceHistoryPartitionKey(partitionKey)) {
      return false;
    }
    if (snapshot.activeConversationId === conversationId) {
      return true;
    }
    const record = snapshot.conversationRecords?.[conversationId];
    if (record && isOwnedConversationRecord(record)) {
      return true;
    }
    return false;
  });
  return entry?.[0] ?? null;
}

/**
 * 过滤出尚未被其他工作空间占用的会话，避免同一会话在左侧重复出现。
 * @param conversations 会话列表。
 * @returns 可分配的会话列表。
 */
export function filterUnassignedConversations(conversations: ConversationItem[]) {
  return conversations.filter((conversation) => findWorkspacePartitionByConversationId(conversation.id) == null);
}

/**
 * 更新指定运行环境下的历史会话分组，保证未归属会话不混入当前工作空间。
 * @param runtimeTarget 运行环境。
 * @param conversations 历史会话列表。
 */
export function upsertWorkspaceHistorySnapshot(
  runtimeTarget: 'cloud' | 'local',
  conversations: ConversationItem[],
) {
  const partitionKey = buildWorkspaceHistoryPartitionKey(runtimeTarget);
  const snapshot = readWorkspaceSnapshot(partitionKey);
  const conversationIds = new Set(conversations.map((conversation) => conversation.id));
  const nextConversationRecords = Object.fromEntries(
    Object.entries(snapshot.conversationRecords ?? {}).filter(([conversationId]) =>
      conversationIds.has(conversationId),
    ),
  );
  writeWorkspaceSnapshot(partitionKey, {
    workspacePath: null,
    workspaceLabel: WORKSPACE_HISTORY_LABEL,
    runtimeTarget,
    lastOpenedAt: Date.now(),
    activeConversationId: null,
    conversations,
    conversationRecords: nextConversationRecords,
  });
}

/**
 * 批量标记会话归属到指定工作空间，避免后续刷新时归属丢失。
 * @param runtimeTarget 运行环境。
 * @param workspacePath 工作空间路径。
 * @param conversationIds 会话标识列表。
 */
export function markWorkspaceConversationOwnership(
  runtimeTarget: 'cloud' | 'local',
  workspacePath: string | null,
  conversationIds: string[],
) {
  if (conversationIds.length === 0) {
    return;
  }
  const partitionKey = buildWorkspacePartitionKey(runtimeTarget, workspacePath);
  const snapshot = readWorkspaceSnapshot(partitionKey);
  const nextConversationRecords = { ...(snapshot.conversationRecords ?? {}) };
  for (const conversationId of conversationIds) {
    const currentRecord = nextConversationRecords[conversationId];
    nextConversationRecords[conversationId] = {
      owned: true,
      messages: currentRecord?.messages ?? [],
      executionSteps: currentRecord?.executionSteps ?? [],
      references: currentRecord?.references ?? [],
      artifacts: currentRecord?.artifacts ?? [],
      currentExperts: currentRecord?.currentExperts ?? [],
      currentSkills: currentRecord?.currentSkills ?? [],
      currentMcps: currentRecord?.currentMcps ?? [],
    };
  }
  writeWorkspaceSnapshot(partitionKey, {
    ...snapshot,
    runtimeTarget,
    workspacePath: workspacePath ?? snapshot.workspacePath ?? null,
    lastOpenedAt: Date.now(),
    conversationRecords: nextConversationRecords,
  });
}

/**
 * 读取本地存储中的完整快照仓库，不合法数据自动降级为空结构。
 * @returns 结构化快照仓库。
 */
function readStore(): LocalWorkspaceConversationStore {
  try {
    const rawStore = window.localStorage.getItem(LOCAL_WORKSPACE_CONVERSATION_STORE_KEY);
    if (!rawStore) {
      return {
        version: 1,
        snapshots: {},
      };
    }
    const parsedStore = JSON.parse(rawStore) as Partial<LocalWorkspaceConversationStore>;
    if (parsedStore.version !== 1 || parsedStore.snapshots == null) {
      return {
        version: 1,
        snapshots: {},
      };
    }
    return normalizeCloudWorkspaceSnapshots({
      version: 1,
      snapshots: parsedStore.snapshots,
    });
  } catch {
    return {
      version: 1,
      snapshots: {},
    };
  }
}

/**
 * 将云端历史分区自动并回默认云端分组，避免刷新后会话落到隐藏分区里。
 * @param store 原始快照仓库。
 * @returns 归一化后的快照仓库。
 */
function normalizeCloudWorkspaceSnapshots(
  store: LocalWorkspaceConversationStore,
): LocalWorkspaceConversationStore {
  const cloudWorkspacePartitionKey = buildWorkspacePartitionKey('cloud', null);
  const cloudHistoryPartitionKey = buildWorkspaceHistoryPartitionKey('cloud');
  const cloudHistorySnapshot = store.snapshots[cloudHistoryPartitionKey];
  if (!cloudHistorySnapshot) {
    return store;
  }

  const cloudWorkspaceSnapshot = store.snapshots[cloudWorkspacePartitionKey] ?? EMPTY_SNAPSHOT;
  const mergedConversations = mergeConversationLists(
    Array.isArray(cloudWorkspaceSnapshot.conversations) ? cloudWorkspaceSnapshot.conversations : [],
    Array.isArray(cloudHistorySnapshot.conversations) ? cloudHistorySnapshot.conversations : [],
  );
  const mergedConversationRecords = mergeConversationRecordMap(
    cloudWorkspaceSnapshot.conversationRecords ?? {},
    cloudHistorySnapshot.conversationRecords ?? {},
  );
  const normalizedStore: LocalWorkspaceConversationStore = {
    version: 1,
    snapshots: {
      ...store.snapshots,
      [cloudWorkspacePartitionKey]: {
        workspacePath: null,
        workspaceLabel:
          cloudWorkspaceSnapshot.workspaceLabel &&
          cloudWorkspaceSnapshot.workspaceLabel !== EMPTY_SNAPSHOT.workspaceLabel &&
          cloudWorkspaceSnapshot.workspaceLabel !== WORKSPACE_HISTORY_LABEL
            ? cloudWorkspaceSnapshot.workspaceLabel
            : getWorkspaceLabel(null),
        runtimeTarget: 'cloud',
        lastOpenedAt: Math.max(
          cloudWorkspaceSnapshot.lastOpenedAt ?? 0,
          cloudHistorySnapshot.lastOpenedAt ?? 0,
        ),
        activeConversationId:
          cloudWorkspaceSnapshot.activeConversationId ??
          cloudHistorySnapshot.activeConversationId ??
          mergedConversations[0]?.id ??
          null,
        conversations: mergedConversations,
        conversationRecords: mergedConversationRecords,
      },
    },
  };
  delete normalizedStore.snapshots[cloudHistoryPartitionKey];
  window.localStorage.setItem(
    LOCAL_WORKSPACE_CONVERSATION_STORE_KEY,
    JSON.stringify(normalizedStore),
  );
  return normalizedStore;
}

/**
 * 按会话 ID 去重合并会话列表，保持默认分组里的原始顺序优先。
 * @param primary 默认分组会话列表。
 * @param secondary 历史分组会话列表。
 * @returns 合并后的会话列表。
 */
function mergeConversationLists(primary: ConversationItem[], secondary: ConversationItem[]) {
  const mergedConversations = [...primary];
  const existingIds = new Set(primary.map((conversation) => conversation.id));
  for (const conversation of secondary) {
    if (existingIds.has(conversation.id)) {
      continue;
    }
    mergedConversations.push(conversation);
  }
  return mergedConversations;
}

/**
 * 合并单条会话记录时优先保留默认分组中已有的真实内容，避免迁移时丢失回放数据。
 * @param primary 默认分组记录。
 * @param secondary 历史分组记录。
 * @returns 合并后的记录。
 */
function mergeConversationRecord(
  primary?: LocalConversationRecord,
  secondary?: LocalConversationRecord,
): LocalConversationRecord {
  return {
    owned: primary?.owned ?? secondary?.owned,
    messages: primary?.messages?.length ? primary.messages : secondary?.messages ?? [],
    executionSteps:
      primary?.executionSteps?.length ? primary.executionSteps : secondary?.executionSteps ?? [],
    references: primary?.references?.length ? primary.references : secondary?.references ?? [],
    artifacts: primary?.artifacts?.length ? primary.artifacts : secondary?.artifacts ?? [],
    currentExperts:
      primary?.currentExperts?.length ? primary.currentExperts : secondary?.currentExperts ?? [],
    currentSkills:
      primary?.currentSkills?.length ? primary.currentSkills : secondary?.currentSkills ?? [],
    currentMcps: primary?.currentMcps?.length ? primary.currentMcps : secondary?.currentMcps ?? [],
  };
}

/**
 * 按会话主键合并记录映射，保留两侧已有的回放内容。
 * @param primary 默认分组记录映射。
 * @param secondary 历史分组记录映射。
 * @returns 合并后的记录映射。
 */
function mergeConversationRecordMap(
  primary: Record<string, LocalConversationRecord>,
  secondary: Record<string, LocalConversationRecord>,
) {
  const mergedConversationIds = new Set([...Object.keys(primary), ...Object.keys(secondary)]);
  const mergedRecords: Record<string, LocalConversationRecord> = {};
  for (const conversationId of mergedConversationIds) {
    mergedRecords[conversationId] = mergeConversationRecord(
      primary[conversationId],
      secondary[conversationId],
    );
  }
  return mergedRecords;
}

/**
 * 判断会话记录是否包含真实回放内容，用于区分“仅占位空记录”和“真实已归属会话”。
 * @param record 会话记录。
 * @returns 是否存在真实回放内容。
 */
function hasPersistedConversationData(record: LocalConversationRecord) {
  return (
    record.messages.length > 0 ||
    record.executionSteps.length > 0 ||
    record.references.length > 0 ||
    record.artifacts.length > 0 ||
    (record.currentExperts?.length ?? 0) > 0 ||
    (record.currentSkills?.length ?? 0) > 0 ||
    (record.currentMcps?.length ?? 0) > 0
  );
}

/**
 * 判断记录是否已明确归属到某个工作空间。
 * @param record 会话记录。
 * @returns 是否归属。
 */
function isOwnedConversationRecord(record: LocalConversationRecord) {
  return record.owned === true || hasPersistedConversationData(record);
}

/**
 * 将当前会话数据写回指定工作空间快照，保证刷新后仍能按工作空间恢复。
 * @param runtimeTarget 运行环境。
 * @param workspacePath 当前工作空间路径。
 * @param conversationId 当前会话标识。
 * @param conversationList 会话列表。
 * @param record 会话明细。
 */
export function saveConversationRecordToWorkspace(
  runtimeTarget: 'cloud' | 'local',
  workspacePath: string | null,
  conversationId: string | null,
  conversationList: ConversationItem[],
  record: LocalConversationRecord,
) {
  const partitionKey = buildWorkspacePartitionKey(runtimeTarget, workspacePath);
  const store = readStore();
  const nextSnapshot = store.snapshots[partitionKey] ?? EMPTY_SNAPSHOT;
  store.snapshots[partitionKey] = {
    ...nextSnapshot,
    workspacePath: workspacePath ?? nextSnapshot.workspacePath ?? null,
    workspaceLabel:
      nextSnapshot.workspaceLabel ?? getWorkspaceLabel(workspacePath ?? nextSnapshot.workspacePath),
    runtimeTarget,
    lastOpenedAt: Date.now(),
    activeConversationId: conversationId,
    conversations: conversationList,
    conversationRecords: {
      ...(nextSnapshot.conversationRecords ?? {}),
      [conversationId ?? 'pending-conversation']: {
        ...record,
        owned: true,
      },
    },
  };
  window.localStorage.setItem(LOCAL_WORKSPACE_CONVERSATION_STORE_KEY, JSON.stringify(store));
}
