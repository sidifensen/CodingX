import {
  ArtifactItem,
  ChatMessageItem,
  ConversationItem,
  CurrentMcpItem,
  CurrentSkillItem,
  ExecutionStepItem,
  ReferenceItem,
} from './types';

/**
 * 本地会话快照存储键，按工作空间隔离保存聊天历史。
 */
const LOCAL_WORKSPACE_CONVERSATION_STORE_KEY = 'codingx.chat.workspace.conversations.v1';

/**
 * 统一表示单个会话在本地缓存中的完整回放数据。
 */
export interface LocalConversationRecord {
  messages: ChatMessageItem[];
  executionSteps: ExecutionStepItem[];
  references: ReferenceItem[];
  artifacts: ArtifactItem[];
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
export function listWorkspaceGroups(): WorkspaceConversationGroup[] {
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
      conversationRecords:
        snapshot.conversationRecords && typeof snapshot.conversationRecords === 'object'
          ? snapshot.conversationRecords
          : {},
    }))
    .sort((left, right) => right.lastOpenedAt - left.lastOpenedAt);
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
  const entry = Object.entries(store.snapshots).find(([, snapshot]) => {
    if (snapshot.activeConversationId === conversationId) {
      return true;
    }
    if (snapshot.conversationRecords?.[conversationId]) {
      return true;
    }
    return snapshot.conversations.some((conversation) => conversation.id === conversationId);
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
    return {
      version: 1,
      snapshots: parsedStore.snapshots,
    };
  } catch {
    return {
      version: 1,
      snapshots: {},
    };
  }
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
      [conversationId ?? 'pending-conversation']: record,
    },
  };
  window.localStorage.setItem(LOCAL_WORKSPACE_CONVERSATION_STORE_KEY, JSON.stringify(store));
}
