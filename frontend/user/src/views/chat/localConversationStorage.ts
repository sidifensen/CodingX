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
  activeConversationId: string | null;
  conversations: ConversationItem[];
  conversationRecords: Record<string, LocalConversationRecord>;
}

interface LocalWorkspaceConversationStore {
  version: 1;
  snapshots: Record<string, LocalWorkspaceConversationSnapshot>;
}

const EMPTY_SNAPSHOT: LocalWorkspaceConversationSnapshot = {
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
