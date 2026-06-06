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
const WORKSPACE_HISTORY_LABEL = '历史记录';
const CLOUD_DEFAULT_WORKSPACE_LABEL = '云端历史记录';
const LOCAL_DEFAULT_WORKSPACE_LABEL = '本地历史记录';
const UNNAMED_WORKSPACE_LABEL = '未命名工作空间';

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
  /**
   * 当前分区的置顶会话顺序，仅在本地侧栏排序中生效。
   */
  pinnedConversationIds?: string[];
  conversations: ConversationItem[];
  conversationRecords: Record<string, LocalConversationRecord>;
  /**
   * 记录用户已经打开过的任务完成时间，用于刷新后恢复“新完成任务”圆点。
   */
  seenTaskFinishedAtByConversationId?: Record<string, string>;
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
  workspaceLabel: UNNAMED_WORKSPACE_LABEL,
  runtimeTarget: 'cloud',
  lastOpenedAt: 0,
  activeConversationId: null,
  pinnedConversationIds: [],
  conversations: [],
  conversationRecords: {},
  seenTaskFinishedAtByConversationId: {},
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
  if (runtimeTarget === 'cloud') {
    return `${runtimeTarget}::__no_workspace__`;
  }
  const normalizedPath = normalizeWorkspacePathForPartition(workspacePath);
  return `${runtimeTarget}::${normalizedPath || '__no_workspace__'}`;
}

/**
 * 归一化用于分区键的工作空间路径，避免 Windows 正反斜杠写法把同一目录拆成两个分组。
 * @param workspacePath 原始工作空间路径。
 * @returns 适合作为分区键的路径片段。
 */
function normalizeWorkspacePathForPartition(workspacePath: string | null) {
  const slashNormalizedPath = (workspacePath ?? '').trim().replace(/\\/g, '/');
  const withoutTrailingSlash = slashNormalizedPath.length > 1
    ? slashNormalizedPath.replace(/\/+$/g, '')
    : slashNormalizedPath;
  return withoutTrailingSlash.toLowerCase();
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
    return CLOUD_DEFAULT_WORKSPACE_LABEL;
  }
  const segments = normalizedPath.split('/').filter(Boolean);
  return segments.length ? segments[segments.length - 1] : normalizedPath;
}

/**
 * 判断快照标签是否只是内部空模板占位，避免把占位文案当成真实工作空间名称展示。
 * @param workspaceLabel 快照中的工作空间标签。
 * @returns 是否为空模板标签。
 */
function isPlaceholderWorkspaceLabel(workspaceLabel: string | null | undefined) {
  return String(workspaceLabel ?? '').trim() === UNNAMED_WORKSPACE_LABEL;
}

/**
 * 根据分区语义推导展示标签；默认分区使用固定中文名，有路径分区使用目录名。
 * @param runtimeTarget 运行环境。
 * @param workspacePath 工作空间路径。
 * @param currentWorkspaceLabel 已存在的快照标签。
 * @returns 可展示工作空间标签。
 */
function resolveWorkspaceLabel(
  runtimeTarget: 'cloud' | 'local',
  workspacePath: string | null,
  currentWorkspaceLabel: string | null | undefined,
) {
  const normalizedWorkspacePath = workspacePath ?? null;
  if (!normalizedWorkspacePath) {
    return getDefaultWorkspaceLabel(runtimeTarget);
  }
  if (
    currentWorkspaceLabel &&
    !isPlaceholderWorkspaceLabel(currentWorkspaceLabel)
  ) {
    return currentWorkspaceLabel;
  }
  return getWorkspaceLabel(normalizedWorkspacePath);
}

/**
 * 根据运行环境返回默认分区标题，避免默认分区标签来源不一致。
 * @param runtimeTarget 运行环境。
 * @returns 默认分区标题。
 */
function getDefaultWorkspaceLabel(runtimeTarget: 'cloud' | 'local') {
  return runtimeTarget === 'local' ? LOCAL_DEFAULT_WORKSPACE_LABEL : CLOUD_DEFAULT_WORKSPACE_LABEL;
}

/**
 * 判断当前分区是否为默认云端或默认本地分区。
 * 默认分区的展示名必须跟随运行环境固定归一，避免旧数据把云端/本地历史混成同一个标签。
 * @param partitionKey 分区键。
 * @param runtimeTarget 运行环境。
 * @returns 是否为默认分区。
 */
function isDefaultWorkspacePartitionKey(
  partitionKey: string,
  runtimeTarget: 'cloud' | 'local',
) {
  return partitionKey === buildWorkspacePartitionKey(runtimeTarget, null);
}

/**
 * 将默认分区快照归一为运行环境对应的固定标签。
 * 历史记录键只在兼容迁移里使用，不应被改名，否则会丢失“默认云端/本地”语义。
 * @param partitionKey 分区键。
 * @param snapshot 原始快照。
 * @returns 归一化后的快照。
 */
function normalizeWorkspaceSnapshot(
  partitionKey: string,
  snapshot: LocalWorkspaceConversationSnapshot,
): LocalWorkspaceConversationSnapshot {
  const normalizedRuntimeTarget = snapshot.runtimeTarget ?? 'cloud';
  if (!isDefaultWorkspacePartitionKey(partitionKey, normalizedRuntimeTarget)) {
    return snapshot;
  }
  return {
    ...snapshot,
    workspacePath: null,
    workspaceLabel: getDefaultWorkspaceLabel(normalizedRuntimeTarget),
    runtimeTarget: normalizedRuntimeTarget,
  };
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
  const normalizedSnapshot = normalizeWorkspaceSnapshot(partitionKey, snapshot);
  return {
    workspacePath: normalizedSnapshot.workspacePath ?? null,
    workspaceLabel: resolveWorkspaceLabel(
      normalizedSnapshot.runtimeTarget ?? 'cloud',
      normalizedSnapshot.workspacePath ?? null,
      normalizedSnapshot.workspaceLabel,
    ),
    runtimeTarget: normalizedSnapshot.runtimeTarget ?? 'cloud',
    lastOpenedAt: normalizedSnapshot.lastOpenedAt ?? 0,
    activeConversationId: normalizedSnapshot.activeConversationId ?? null,
    pinnedConversationIds: normalizePinnedConversationIds(snapshot.pinnedConversationIds),
    conversations: decoratePinnedConversations(
      Array.isArray(normalizedSnapshot.conversations) ? normalizedSnapshot.conversations : [],
      normalizePinnedConversationIds(snapshot.pinnedConversationIds),
    ),
    conversationRecords:
      normalizedSnapshot.conversationRecords && typeof normalizedSnapshot.conversationRecords === 'object'
        ? normalizedSnapshot.conversationRecords
        : {},
    seenTaskFinishedAtByConversationId:
      normalizedSnapshot.seenTaskFinishedAtByConversationId &&
      typeof normalizedSnapshot.seenTaskFinishedAtByConversationId === 'object'
        ? normalizedSnapshot.seenTaskFinishedAtByConversationId
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
  const normalizedSnapshot = normalizeWorkspaceSnapshot(partitionKey, snapshot);
  const store = readStore();
  store.snapshots[partitionKey] = normalizedSnapshot;
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
  const isDefaultPartition = isDefaultWorkspacePartitionKey(partitionKey, runtimeTarget);
  const currentSnapshot = readWorkspaceSnapshot(partitionKey);
  const hasActiveConversationOverride =
    partialSnapshot != null &&
    Object.prototype.hasOwnProperty.call(partialSnapshot, 'activeConversationId');
  const snapshot: LocalWorkspaceConversationSnapshot = {
    workspacePath: isDefaultPartition
      ? null
      : (workspacePath ?? currentSnapshot.workspacePath ?? null),
    workspaceLabel:
      partialSnapshot?.workspaceLabel ??
      resolveWorkspaceLabel(
        runtimeTarget,
        isDefaultPartition
          ? null
          : (workspacePath ?? currentSnapshot.workspacePath ?? null),
        currentSnapshot.workspaceLabel,
      ),
    runtimeTarget,
    lastOpenedAt: Date.now(),
    // 允许调用方显式写入 null（例如“新建会话”场景清空激活会话），避免被 ?? 回退到旧值。
    activeConversationId: hasActiveConversationOverride
      ? (partialSnapshot?.activeConversationId ?? null)
      : (currentSnapshot.activeConversationId ?? null),
    pinnedConversationIds:
      partialSnapshot?.pinnedConversationIds ?? currentSnapshot.pinnedConversationIds ?? [],
    conversations: partialSnapshot?.conversations ?? currentSnapshot.conversations ?? [],
    conversationRecords:
      partialSnapshot?.conversationRecords ?? currentSnapshot.conversationRecords ?? {},
    seenTaskFinishedAtByConversationId:
      partialSnapshot?.seenTaskFinishedAtByConversationId ??
      currentSnapshot.seenTaskFinishedAtByConversationId ??
      {},
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
      workspaceLabel: resolveWorkspaceLabel(
        snapshot.runtimeTarget ?? 'cloud',
        snapshot.workspacePath ?? null,
        snapshot.workspaceLabel,
      ),
      runtimeTarget: snapshot.runtimeTarget ?? 'cloud',
      lastOpenedAt: snapshot.lastOpenedAt ?? 0,
      activeConversationId: snapshot.activeConversationId ?? null,
      pinnedConversationIds: normalizePinnedConversationIds(snapshot.pinnedConversationIds),
      conversations: decoratePinnedConversations(
        Array.isArray(snapshot.conversations) ? snapshot.conversations : [],
        normalizePinnedConversationIds(snapshot.pinnedConversationIds),
      ),
      groupType: isWorkspaceHistoryPartitionKey(partitionKey) ? 'history' : 'workspace',
      conversationRecords:
        snapshot.conversationRecords && typeof snapshot.conversationRecords === 'object'
          ? snapshot.conversationRecords
          : {},
      seenTaskFinishedAtByConversationId:
        snapshot.seenTaskFinishedAtByConversationId &&
        typeof snapshot.seenTaskFinishedAtByConversationId === 'object'
          ? snapshot.seenTaskFinishedAtByConversationId
          : {},
    }))
    // 历史分组仅用于兼容旧数据迁移，不在左侧渲染，避免重复出现“历史记录”。
    .filter((group) => group.groupType !== 'history')
    // 仅绑定目录但没有任何会话的工作空间属于空缓存，不应在侧栏形成“local”等空分组。
    .filter((group) => isRenderableWorkspaceGroup(group))
    .filter((group) => (runtimeTarget ? group.runtimeTarget === runtimeTarget : true))
    .sort((left, right) => {
      // 同级分组按标签名稳定排序，确保点击/刷新后渲染顺序可预测。
      const labelCompare = left.workspaceLabel.localeCompare(right.workspaceLabel, 'zh-Hans-CN');
      if (labelCompare !== 0) {
        return labelCompare;
      }
      return left.partitionKey.localeCompare(right.partitionKey, 'zh-Hans-CN');
    });
}

/**
 * 判断工作空间分组是否有必要展示在侧栏。
 * 默认历史入口即使没有会话也保留；普通路径工作空间必须至少有可展示会话或激活会话。
 * @param group 工作空间分组。
 * @returns 是否应渲染。
 */
function isRenderableWorkspaceGroup(group: WorkspaceConversationGroup) {
  if (isDefaultWorkspacePartitionKey(group.partitionKey, group.runtimeTarget)) {
    return true;
  }
  if (group.conversations.length > 0) {
    return true;
  }
  return String(group.activeConversationId ?? '').trim().length > 0;
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
    pinnedConversationIds: snapshot.pinnedConversationIds ?? [],
    conversations,
    conversationRecords: nextConversationRecords,
    seenTaskFinishedAtByConversationId: snapshot.seenTaskFinishedAtByConversationId ?? {},
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
    pinnedConversationIds: snapshot.pinnedConversationIds ?? [],
    conversationRecords: nextConversationRecords,
    seenTaskFinishedAtByConversationId: snapshot.seenTaskFinishedAtByConversationId ?? {},
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
    return normalizeWorkspaceSnapshots({
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
 * 归一化快照仓库，兼容旧版独立历史分区并将其并回默认分区。
 * @param store 原始快照仓库。
 * @returns 归一化后的快照仓库。
 */
function normalizeWorkspaceSnapshots(store: LocalWorkspaceConversationStore): LocalWorkspaceConversationStore {
  const normalizedSnapshots: Record<string, LocalWorkspaceConversationSnapshot> = {};
  for (const [partitionKey, snapshot] of Object.entries(store.snapshots)) {
    const [canonicalPartitionKey, normalizedSnapshot] = normalizeWorkspaceSnapshotEntry(
      partitionKey,
      snapshot,
    );
    const existingSnapshot = normalizedSnapshots[canonicalPartitionKey];
    normalizedSnapshots[canonicalPartitionKey] = existingSnapshot
      ? mergeWorkspaceSnapshots(existingSnapshot, normalizedSnapshot)
      : normalizedSnapshot;
  }
  mergeHistoryPartitionIntoDefaultGroup(normalizedSnapshots, 'local');
  mergeHistoryPartitionIntoDefaultGroup(normalizedSnapshots, 'cloud');
  const normalizedStore: LocalWorkspaceConversationStore = {
    version: 1,
    snapshots: normalizedSnapshots,
  };
  const normalizedSerialized = JSON.stringify(normalizedStore);
  const rawSerialized = JSON.stringify(store);
  if (normalizedSerialized !== rawSerialized) {
    window.localStorage.setItem(LOCAL_WORKSPACE_CONVERSATION_STORE_KEY, normalizedSerialized);
  }
  return normalizedStore;
}

/**
 * 将单个快照条目归一到规范分区键，并补齐旧缓存可能缺失的路径语义。
 * @param partitionKey 原始分区键。
 * @param snapshot 原始快照。
 * @returns 规范分区键与归一化快照。
 */
function normalizeWorkspaceSnapshotEntry(
  partitionKey: string,
  snapshot: LocalWorkspaceConversationSnapshot,
): [string, LocalWorkspaceConversationSnapshot] {
  const runtimeTarget = getRuntimeTargetFromPartitionKey(
    partitionKey,
    snapshot.runtimeTarget ?? 'cloud',
  );
  if (isWorkspaceHistoryPartitionKey(partitionKey)) {
    return [
      buildWorkspaceHistoryPartitionKey(runtimeTarget),
      {
        ...snapshot,
        runtimeTarget,
      },
    ];
  }

  // 业务约束：云端会话不绑定本地目录，旧缓存里的 cloud::<path> 必须并回云端默认分区。
  const workspacePath = runtimeTarget === 'cloud'
    ? null
    : resolveWorkspacePathFromPartition(partitionKey, snapshot, runtimeTarget);
  const canonicalPartitionKey = buildWorkspacePartitionKey(runtimeTarget, workspacePath);
  const normalizedSnapshot = normalizeWorkspaceSnapshot(canonicalPartitionKey, {
    ...snapshot,
    workspacePath,
    workspaceLabel: resolveWorkspaceLabel(runtimeTarget, workspacePath, snapshot.workspaceLabel),
    runtimeTarget,
    pinnedConversationIds: normalizePinnedConversationIds(snapshot.pinnedConversationIds),
    conversations: Array.isArray(snapshot.conversations) ? snapshot.conversations : [],
    conversationRecords: snapshot.conversationRecords ?? {},
    seenTaskFinishedAtByConversationId: snapshot.seenTaskFinishedAtByConversationId ?? {},
  });

  return [canonicalPartitionKey, normalizedSnapshot];
}

/**
 * 从分区键前缀还原运行环境，兼容旧快照 runtimeTarget 缺失或写错的情况。
 * @param partitionKey 分区键。
 * @param fallbackRuntimeTarget 快照中的兜底运行环境。
 * @returns 归一后的运行环境。
 */
function getRuntimeTargetFromPartitionKey(
  partitionKey: string,
  fallbackRuntimeTarget: 'cloud' | 'local',
) {
  if (partitionKey.startsWith('local::')) {
    return 'local';
  }
  if (partitionKey.startsWith('cloud::')) {
    return 'cloud';
  }
  return fallbackRuntimeTarget;
}

/**
 * 优先使用快照路径；旧缓存没有 workspacePath 时，从分区键中恢复目录路径。
 * @param partitionKey 分区键。
 * @param snapshot 快照。
 * @param runtimeTarget 运行环境。
 * @returns 工作空间路径。
 */
function resolveWorkspacePathFromPartition(
  partitionKey: string,
  snapshot: LocalWorkspaceConversationSnapshot,
  runtimeTarget: 'cloud' | 'local',
) {
  const explicitWorkspacePath = String(snapshot.workspacePath ?? '').trim();
  if (explicitWorkspacePath) {
    return explicitWorkspacePath;
  }
  const partitionPrefix = `${runtimeTarget}::`;
  if (!partitionKey.startsWith(partitionPrefix)) {
    return null;
  }
  const partitionWorkspacePath = partitionKey.slice(partitionPrefix.length);
  if (
    !partitionWorkspacePath ||
    partitionWorkspacePath === '__no_workspace__' ||
    partitionWorkspacePath === WORKSPACE_HISTORY_PARTITION_SUFFIX
  ) {
    return null;
  }
  return partitionWorkspacePath;
}

/**
 * 合并归一化后落到同一分区的快照，保留会话、置顶和已读状态，清理旧重复分组。
 * @param primary 已归一到目标分区的主快照。
 * @param secondary 需要并入的重复快照。
 * @returns 合并后的快照。
 */
function mergeWorkspaceSnapshots(
  primary: LocalWorkspaceConversationSnapshot,
  secondary: LocalWorkspaceConversationSnapshot,
) {
  const runtimeTarget = primary.runtimeTarget ?? secondary.runtimeTarget ?? 'cloud';
  const workspacePath = primary.workspacePath ?? secondary.workspacePath ?? null;
  const mergedConversations = mergeConversationLists(
    Array.isArray(primary.conversations) ? primary.conversations : [],
    Array.isArray(secondary.conversations) ? secondary.conversations : [],
  );
  const primaryLastOpenedAt = primary.lastOpenedAt ?? 0;
  const secondaryLastOpenedAt = secondary.lastOpenedAt ?? 0;
  const recentActiveConversationId = secondaryLastOpenedAt > primaryLastOpenedAt
    ? secondary.activeConversationId ?? primary.activeConversationId
    : primary.activeConversationId ?? secondary.activeConversationId;
  return {
    ...primary,
    workspacePath,
    workspaceLabel: resolveWorkspaceLabel(
      runtimeTarget,
      workspacePath,
      primary.workspaceLabel ?? secondary.workspaceLabel,
    ),
    runtimeTarget,
    lastOpenedAt: Math.max(primaryLastOpenedAt, secondaryLastOpenedAt),
    activeConversationId:
      recentActiveConversationId ??
      mergedConversations[0]?.id ??
      null,
    pinnedConversationIds: mergePinnedConversationIds(
      primary.pinnedConversationIds ?? [],
      secondary.pinnedConversationIds ?? [],
    ),
    conversations: mergedConversations,
    conversationRecords: mergeConversationRecordMap(
      primary.conversationRecords ?? {},
      secondary.conversationRecords ?? {},
    ),
    seenTaskFinishedAtByConversationId: {
      ...(secondary.seenTaskFinishedAtByConversationId ?? {}),
      ...(primary.seenTaskFinishedAtByConversationId ?? {}),
    },
  };
}

/**
 * 将历史分区并入默认分区并清理历史键，保证左侧不会重复渲染历史记录入口。
 * @param snapshots 快照集合。
 * @param runtimeTarget 运行环境。
 */
function mergeHistoryPartitionIntoDefaultGroup(
  snapshots: Record<string, LocalWorkspaceConversationSnapshot>,
  runtimeTarget: 'cloud' | 'local',
) {
  const defaultPartitionKey = buildWorkspacePartitionKey(runtimeTarget, null);
  const historyPartitionKey = buildWorkspaceHistoryPartitionKey(runtimeTarget);
  const defaultWorkspaceLabel = getDefaultWorkspaceLabel(runtimeTarget);
  const defaultSnapshot = snapshots[defaultPartitionKey];
  const historySnapshot = snapshots[historyPartitionKey];
  const effectiveDefaultSnapshot: LocalWorkspaceConversationSnapshot = defaultSnapshot
    ? {
        ...defaultSnapshot,
        workspacePath: null,
        workspaceLabel: defaultWorkspaceLabel,
        runtimeTarget,
        pinnedConversationIds: normalizePinnedConversationIds(defaultSnapshot.pinnedConversationIds),
      }
    : {
        ...EMPTY_SNAPSHOT,
        workspacePath: null,
        workspaceLabel: defaultWorkspaceLabel,
        runtimeTarget,
        pinnedConversationIds: [],
      };

  if (!historySnapshot) {
    snapshots[defaultPartitionKey] = effectiveDefaultSnapshot;
    return;
  }

  const mergedConversations = mergeConversationLists(
    Array.isArray(effectiveDefaultSnapshot.conversations) ? effectiveDefaultSnapshot.conversations : [],
    Array.isArray(historySnapshot.conversations) ? historySnapshot.conversations : [],
  );
  const mergedConversationRecords = mergeConversationRecordMap(
    effectiveDefaultSnapshot.conversationRecords ?? {},
    historySnapshot.conversationRecords ?? {},
  );

  snapshots[defaultPartitionKey] = {
    ...effectiveDefaultSnapshot,
    lastOpenedAt: Math.max(effectiveDefaultSnapshot.lastOpenedAt ?? 0, historySnapshot.lastOpenedAt ?? 0),
    activeConversationId:
      effectiveDefaultSnapshot.activeConversationId ??
      historySnapshot.activeConversationId ??
      mergedConversations[0]?.id ??
      null,
    pinnedConversationIds: mergePinnedConversationIds(
      effectiveDefaultSnapshot.pinnedConversationIds ?? [],
      historySnapshot.pinnedConversationIds ?? [],
    ),
    conversations: mergedConversations,
    conversationRecords: mergedConversationRecords,
    seenTaskFinishedAtByConversationId: {
      ...(historySnapshot.seenTaskFinishedAtByConversationId ?? {}),
      ...(effectiveDefaultSnapshot.seenTaskFinishedAtByConversationId ?? {}),
    },
  };
  delete snapshots[historyPartitionKey];
}

/**
 * 合并会话列表并按会话 ID 去重，优先保留主列表已存在项。
 * @param primary 主列表。
 * @param secondary 次列表。
 * @returns 合并后列表。
 */
function mergeConversationLists(primary: ConversationItem[], secondary: ConversationItem[]) {
  const mergedConversations = [...primary];
  const existingConversationIds = new Set(primary.map((conversation) => conversation.id));
  for (const conversation of secondary) {
    if (existingConversationIds.has(conversation.id)) {
      continue;
    }
    existingConversationIds.add(conversation.id);
    mergedConversations.push(conversation);
  }
  return mergedConversations;
}

/**
 * 合并会话记录映射，优先保留主分区已有记录。
 * @param primary 主分区记录。
 * @param secondary 次分区记录。
 * @returns 合并后记录映射。
 */
function mergeConversationRecordMap(
  primary: Record<string, LocalConversationRecord>,
  secondary: Record<string, LocalConversationRecord>,
) {
  const mergedConversationRecords = { ...primary };
  for (const [conversationId, record] of Object.entries(secondary)) {
    if (mergedConversationRecords[conversationId]) {
      continue;
    }
    mergedConversationRecords[conversationId] = record;
  }
  return mergedConversationRecords;
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
      resolveWorkspaceLabel(
        runtimeTarget,
        workspacePath ?? nextSnapshot.workspacePath ?? null,
        nextSnapshot.workspaceLabel,
      ),
    runtimeTarget,
    lastOpenedAt: Date.now(),
    activeConversationId: conversationId,
    pinnedConversationIds: nextSnapshot.pinnedConversationIds ?? [],
    conversations: conversationList,
    conversationRecords: {
      ...(nextSnapshot.conversationRecords ?? {}),
      [conversationId ?? 'pending-conversation']: {
        ...record,
        owned: true,
      },
    },
    seenTaskFinishedAtByConversationId: nextSnapshot.seenTaskFinishedAtByConversationId ?? {},
  };
  window.localStorage.setItem(LOCAL_WORKSPACE_CONVERSATION_STORE_KEY, JSON.stringify(store));
}

/**
 * 标记会话最近任务完成时间已被用户看过，刷新后不再显示完成提醒圆点。
 * @param runtimeTarget 运行环境。
 * @param workspacePath 当前工作空间路径。
 * @param conversationId 会话标识。
 * @param taskFinishedAt 后端返回的任务完成时间。
 */
export function markConversationTaskCompletionSeen(
  runtimeTarget: 'cloud' | 'local',
  workspacePath: string | null,
  conversationId: string,
  taskFinishedAt?: string,
) {
  if (!taskFinishedAt) {
    return;
  }
  const partitionKey = buildWorkspacePartitionKey(runtimeTarget, workspacePath);
  const snapshot = readWorkspaceSnapshot(partitionKey);
  writeWorkspaceSnapshot(partitionKey, {
    ...snapshot,
    pinnedConversationIds: snapshot.pinnedConversationIds ?? [],
    seenTaskFinishedAtByConversationId: {
      ...(snapshot.seenTaskFinishedAtByConversationId ?? {}),
      [conversationId]: taskFinishedAt,
    },
  });
}

/**
 * 归一化置顶会话标识列表，过滤空值与重复项，避免本地快照因脏数据导致排序异常。
 * @param pinnedConversationIds 原始置顶列表。
 * @returns 去重后的置顶列表。
 */
function normalizePinnedConversationIds(pinnedConversationIds: unknown): string[] {
  if (!Array.isArray(pinnedConversationIds)) {
    return [];
  }
  const uniqueConversationIds = new Set<string>();
  pinnedConversationIds.forEach((conversationId) => {
    const normalizedConversationId = String(conversationId ?? '').trim();
    if (!normalizedConversationId) {
      return;
    }
    uniqueConversationIds.add(normalizedConversationId);
  });
  return Array.from(uniqueConversationIds);
}

/**
 * 基于置顶标识列表装饰会话顺序，并为置顶项打上 `isPinned` 标记。
 * @param conversations 原始会话列表。
 * @param pinnedConversationIds 置顶会话标识列表。
 * @returns 已按置顶规则排序的会话列表。
 */
function decoratePinnedConversations(
  conversations: ConversationItem[],
  pinnedConversationIds: string[],
) {
  const pinnedIdOrder = normalizePinnedConversationIds(pinnedConversationIds);
  const pinnedIdSet = new Set(pinnedIdOrder);
  const conversationMap = new Map(
    conversations.map((conversation) => [
      conversation.id,
      {
        ...conversation,
        isPinned: pinnedIdSet.has(conversation.id),
      },
    ]),
  );
  const pinnedConversations = pinnedIdOrder
    .map((conversationId) => conversationMap.get(conversationId))
    .filter((conversation): conversation is ConversationItem => conversation != null);
  const unpinnedConversations = conversations
    .filter((conversation) => !pinnedIdSet.has(conversation.id))
    .map((conversation) => ({
      ...conversation,
      isPinned: false,
    }));
  return [...pinnedConversations, ...unpinnedConversations];
}

/**
 * 合并默认分区与历史分区的置顶列表，优先保留主分区顺序，再补齐历史分区中遗漏的标识。
 * @param primary 主分区置顶列表。
 * @param secondary 次分区置顶列表。
 * @returns 合并后的置顶列表。
 */
function mergePinnedConversationIds(primary: string[], secondary: string[]) {
  const mergedConversationIds = normalizePinnedConversationIds(primary);
  normalizePinnedConversationIds(secondary).forEach((conversationId) => {
    if (!mergedConversationIds.includes(conversationId)) {
      mergedConversationIds.push(conversationId);
    }
  });
  return mergedConversationIds;
}
