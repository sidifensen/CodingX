import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import {
  BookMarked,
  CheckCircle2,
  CircleOff,
  PencilLine,
  RefreshCw,
  Trash2,
} from 'lucide-react';

import { AuthStorage } from '../utils/authStorage';
import { ChatApi } from './chat/chatApi';
import { LongTermMemoryItem, LongTermMemoryStatus, WorkspaceConversationGroup } from './chat/types';

type MemoryScopeFilter = 'ALL' | 'USER' | 'PROJECT';
type MemoryStatusFilter = 'ALL' | LongTermMemoryStatus;

interface MemoryViewProps {
  isAuthenticated: boolean;
  onRequireLogin: () => void;
  workspaceId: string | null;
  workspaceLabel: string;
  workspaceGroups?: WorkspaceConversationGroup[];
}

interface MemoryWorkspaceGroup {
  key: string;
  label: string;
  description: string;
  memories: LongTermMemoryItem[];
}

/**
 * 渲染用户端长期记忆管理页，支持查看、筛选、编辑、启停和删除当前用户可见记忆。
 */
export default function MemoryView({
  isAuthenticated,
  onRequireLogin,
  workspaceId,
  workspaceLabel,
  workspaceGroups = [],
}: MemoryViewProps) {
  const [memories, setMemories] = useState<LongTermMemoryItem[]>([]);
  const [scopeFilter, setScopeFilter] = useState<MemoryScopeFilter>('ALL');
  const [statusFilter, setStatusFilter] = useState<MemoryStatusFilter>('ALL');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [editingMemory, setEditingMemory] = useState<LongTermMemoryItem | null>(null);
  const [editContent, setEditContent] = useState('');
  const [editError, setEditError] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [deletingMemory, setDeletingMemory] = useState<LongTermMemoryItem | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    let disposed = false;

    const loadMemories = async () => {
      if (!isAuthenticated) {
        setMemories([]);
        return;
      }
      const token = AuthStorage.getSession()?.token;
      if (!token) {
        setMemories([]);
        return;
      }
      setIsLoading(true);
      setErrorMessage('');
      try {
        // 步骤 1：管理页需要跨工作空间治理记忆，状态和范围筛选在本地完成以减少来回请求。
        const response = await ChatApi.listLongTermMemories(token, null, 'ALL', {
          includeAllWorkspaces: true,
        });
        if (!disposed) {
          setMemories(response);
        }
      } catch (error) {
        if (!disposed) {
          setMemories([]);
          setErrorMessage(error instanceof Error ? error.message : '长期记忆加载失败');
        }
      } finally {
        if (!disposed) {
          setIsLoading(false);
        }
      }
    };

    void loadMemories();
    return () => {
      disposed = true;
    };
  }, [isAuthenticated]);

  const activeCount = useMemo(
    () => memories.filter((memory) => normalizeStatus(memory.status) === 'ACTIVE').length,
    [memories],
  );
  const projectCount = useMemo(
    () => memories.filter((memory) => normalizeScope(memory.memoryScope) === 'PROJECT').length,
    [memories],
  );
  const workspaceLabelById = useMemo(
    () => buildWorkspaceLabelById(workspaceGroups, workspaceId, workspaceLabel),
    [workspaceGroups, workspaceId, workspaceLabel],
  );
  const workspaceCoverageCount = useMemo(() => {
    return new Set(
      memories
        .map((memory) => normalizeWorkspaceId(memory.workspaceId))
        .filter((memoryWorkspaceId): memoryWorkspaceId is string => memoryWorkspaceId != null),
    ).size;
  }, [memories]);

  const visibleMemories = useMemo(() => {
    return memories.filter((memory) => {
      const matchesScope =
        scopeFilter === 'ALL' || normalizeScope(memory.memoryScope) === scopeFilter;
      const matchesStatus =
        statusFilter === 'ALL' || normalizeStatus(memory.status) === statusFilter;
      return matchesScope && matchesStatus;
    });
  }, [memories, scopeFilter, statusFilter]);
  const visibleMemoryGroups = useMemo(
    () => groupVisibleMemories(visibleMemories, workspaceLabelById),
    [visibleMemories, workspaceLabelById],
  );

  /**
   * 手动刷新记忆列表，用于用户在聊天页新增记忆后回到管理页拉取最新状态。
   */
  const refreshMemories = async () => {
    const token = AuthStorage.getSession()?.token;
    if (!token) {
      onRequireLogin();
      return;
    }
    setIsLoading(true);
    setErrorMessage('');
    try {
      const response = await ChatApi.listLongTermMemories(token, null, 'ALL', {
        includeAllWorkspaces: true,
      });
      setMemories(response);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '长期记忆加载失败');
    } finally {
      setIsLoading(false);
    }
  };

  /**
   * 打开编辑弹窗并复制当前正文，避免用户编辑过程中列表刷新影响输入框内容。
   * @param memory 待编辑记忆。
   */
  const openEditDialog = (memory: LongTermMemoryItem) => {
    setEditingMemory(memory);
    setEditContent(memory.content);
    setEditError('');
  };

  /**
   * 保存编辑后的记忆正文，并用后端返回结果替换本地列表项。
   */
  const saveMemoryContent = async () => {
    if (!editingMemory) {
      return;
    }
    const normalizedContent = editContent.trim().replace(/\s+/g, ' ');
    if (!normalizedContent) {
      setEditError('记忆内容不能为空');
      return;
    }
    const token = AuthStorage.getSession()?.token;
    if (!token) {
      onRequireLogin();
      return;
    }
    setIsSaving(true);
    setEditError('');
    try {
      const updatedMemory = await ChatApi.updateLongTermMemoryContent(
        token,
        editingMemory.id,
        normalizedContent,
      );
      setMemories((previousMemories) =>
        previousMemories.map((memory) =>
          memory.id === updatedMemory.id ? updatedMemory : memory,
        ),
      );
      setEditingMemory(null);
    } catch (error) {
      setEditError(error instanceof Error ? error.message : '长期记忆保存失败');
    } finally {
      setIsSaving(false);
    }
  };

  /**
   * 切换长期记忆启停状态；ACTIVE 会参与模型回注，REJECTED 不参与回注。
   * @param memory 目标记忆。
   */
  const toggleMemoryStatus = async (memory: LongTermMemoryItem) => {
    const token = AuthStorage.getSession()?.token;
    if (!token) {
      onRequireLogin();
      return;
    }
    const nextStatus: LongTermMemoryStatus =
      normalizeStatus(memory.status) === 'ACTIVE' ? 'REJECTED' : 'ACTIVE';
    try {
      const updatedMemory = await ChatApi.updateLongTermMemoryStatus(token, memory.id, nextStatus);
      setMemories((previousMemories) =>
        previousMemories.map((item) => (item.id === updatedMemory.id ? updatedMemory : item)),
      );
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '长期记忆状态更新失败');
    }
  };

  /**
   * 确认删除长期记忆；后端逻辑删除成功后从当前列表移除。
   */
  const confirmDeleteMemory = async () => {
    if (!deletingMemory) {
      return;
    }
    const token = AuthStorage.getSession()?.token;
    if (!token) {
      onRequireLogin();
      return;
    }
    setIsDeleting(true);
    try {
      await ChatApi.deleteLongTermMemory(token, deletingMemory.id);
      setMemories((previousMemories) =>
        previousMemories.filter((memory) => memory.id !== deletingMemory.id),
      );
      setDeletingMemory(null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '长期记忆删除失败');
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: 16 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -16 }}
      className="flex h-full flex-col overflow-y-auto bg-background px-5 py-7 text-foreground md:px-12"
    >
      <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col">
        <header className="border-b border-border pb-6">
          <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
            <div>
              <p className="mb-2 font-mono text-xs uppercase tracking-widest text-muted">
                Long-term memory
              </p>
              <h1 className="text-3xl font-bold tracking-tight text-foreground md:text-4xl">
                记忆管理
              </h1>
              <p className="mt-3 max-w-2xl text-sm leading-6 text-muted">
                管理会进入模型上下文的用户偏好和项目约定。
              </p>
            </div>
            <button
              type="button"
              aria-label="刷新长期记忆"
              onClick={refreshMemories}
              disabled={!isAuthenticated || isLoading}
              className="inline-flex w-fit items-center gap-2 rounded-full border border-border bg-surface px-4 py-2 text-sm font-medium text-foreground transition-colors hover:border-border-active hover:bg-surface-container disabled:cursor-not-allowed disabled:opacity-50"
            >
              <RefreshCw size={16} className={isLoading ? 'animate-spin' : ''} />
              刷新
            </button>
          </div>
          <div className="mt-6 grid grid-cols-1 gap-3 text-sm sm:grid-cols-3">
            <Metric label="覆盖工作空间" value={`覆盖 ${workspaceCoverageCount} 个工作空间`} />
            <Metric label="记忆总数" value={`${memories.length}`} />
            <Metric label="上下文生效" value={`生效 ${activeCount}`} />
          </div>
        </header>

        {!isAuthenticated ? (
          <section className="mt-8 flex flex-1 flex-col items-center justify-center border border-dashed border-border bg-surface py-16 text-center">
            <BookMarked size={34} className="mb-4 text-muted" />
            <h2 className="text-xl font-semibold text-foreground">登录后查看和管理长期记忆</h2>
            <p className="mt-3 max-w-md text-sm leading-6 text-muted">
              用户记忆和项目记忆只对当前账号可见，登录后才能读取和修改。
            </p>
            <button
              type="button"
              onClick={onRequireLogin}
              className="mt-6 rounded-full bg-foreground px-5 py-2 text-sm font-semibold text-background"
            >
              登录后管理记忆
            </button>
          </section>
        ) : (
          <>
            <section className="mt-6 flex flex-col gap-3 border-b border-border pb-5">
              <SegmentedFilter
                label="范围"
                options={[
                  { label: '全部', value: 'ALL' },
                  { label: '用户记忆', value: 'USER' },
                  { label: '项目记忆', value: 'PROJECT', count: projectCount },
                ]}
                value={scopeFilter}
                onChange={(value) => setScopeFilter(value as MemoryScopeFilter)}
              />
              <SegmentedFilter
                label="状态"
                options={[
                  { label: '全部', value: 'ALL' },
                  { label: '已生效', value: 'ACTIVE', count: activeCount },
                  {
                    label: '已停用',
                    value: 'REJECTED',
                    count: memories.length - activeCount,
                  },
                ]}
                value={statusFilter}
                onChange={(value) => setStatusFilter(value as MemoryStatusFilter)}
              />
            </section>

            {errorMessage ? (
              <div className="mt-5 border border-error/40 bg-error/10 px-4 py-3 text-sm text-error">
                {errorMessage}
              </div>
            ) : null}

            <section className="mt-5 flex-1">
              {isLoading ? (
                <div className="border border-border bg-surface px-4 py-8 text-sm text-muted">
                  长期记忆加载中...
                </div>
              ) : null}
              {!isLoading && visibleMemories.length === 0 ? (
                <div className="border border-dashed border-border bg-surface px-4 py-12 text-center text-sm text-muted">
                  当前筛选下暂无长期记忆
                </div>
              ) : null}
              {!isLoading && visibleMemories.length > 0 ? (
                <div className="space-y-6">
                  {visibleMemoryGroups.map((group) => (
                    <section key={group.key} className="border-y border-border">
                      <header className="flex flex-col gap-1 bg-surface-container px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <h2 className="text-sm font-semibold text-foreground">{group.label}</h2>
                          <p className="mt-1 text-xs text-muted">{group.description}</p>
                        </div>
                        <span className="text-xs font-medium text-muted">
                          {group.memories.length} 条记忆
                        </span>
                      </header>
                      <div className="divide-y divide-border">
                        {group.memories.map((memory) => (
                          <MemoryRow
                            key={memory.id}
                            memory={memory}
                            workspaceLabel={resolveMemoryWorkspaceLabel(memory, workspaceLabelById)}
                            onEdit={() => openEditDialog(memory)}
                            onToggleStatus={() => void toggleMemoryStatus(memory)}
                            onDelete={() => setDeletingMemory(memory)}
                          />
                        ))}
                      </div>
                    </section>
                  ))}
                </div>
              ) : null}
            </section>
          </>
        )}
      </div>

      {editingMemory ? (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="编辑长期记忆"
          className="fixed inset-0 z-[140] flex items-center justify-center bg-background/55 px-4 backdrop-blur-sm"
        >
          <div className="w-full max-w-lg border border-border bg-surface p-5 shadow-[0_24px_70px_rgba(0,0,0,0.28)]">
            <h2 className="text-lg font-semibold text-foreground">编辑长期记忆</h2>
            <label className="mt-4 block text-sm font-medium text-muted" htmlFor="memory-edit-content">
              记忆内容
            </label>
            <textarea
              id="memory-edit-content"
              value={editContent}
              onChange={(event) => setEditContent(event.target.value)}
              className="mt-2 min-h-32 w-full resize-y border border-border bg-background px-3 py-2 text-sm leading-6 text-foreground outline-none focus:border-border-active"
            />
            {editError ? <p className="mt-2 text-sm text-error">{editError}</p> : null}
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setEditingMemory(null)}
                className="border border-border bg-surface-container px-4 py-2 text-sm text-muted transition-colors hover:bg-surface-high hover:text-foreground"
              >
                取消
              </button>
              <button
                type="button"
                onClick={() => void saveMemoryContent()}
                disabled={isSaving}
                className="bg-foreground px-4 py-2 text-sm font-semibold text-background disabled:cursor-not-allowed disabled:opacity-50"
              >
                保存
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {deletingMemory ? (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="删除长期记忆"
          className="fixed inset-0 z-[140] flex items-center justify-center bg-background/55 px-4 backdrop-blur-sm"
        >
          <div className="w-full max-w-md border border-border bg-surface p-5 shadow-[0_24px_70px_rgba(0,0,0,0.28)]">
            <h2 className="text-lg font-semibold text-foreground">删除长期记忆</h2>
            <p className="mt-3 text-sm leading-6 text-muted">
              删除后这条记忆不会再进入模型上下文，历史来源链路仍会保留在后端。
            </p>
            <blockquote className="mt-4 border-l-2 border-border pl-3 text-sm leading-6 text-foreground">
              {deletingMemory.content}
            </blockquote>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setDeletingMemory(null)}
                className="border border-border bg-surface-container px-4 py-2 text-sm text-muted transition-colors hover:bg-surface-high hover:text-foreground"
              >
                取消
              </button>
              <button
                type="button"
                onClick={() => void confirmDeleteMemory()}
                disabled={isDeleting}
                className="bg-[#ff5b57] px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </motion.div>
  );
}

/**
 * 渲染顶部指标，使用纯文本值避免图表装饰占用工作台空间。
 */
function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="border border-border bg-surface px-4 py-3">
      <div className="text-xs text-muted">{label}</div>
      <div className="mt-1 truncate text-sm font-semibold text-foreground">{value}</div>
    </div>
  );
}

/**
 * 渲染筛选分段控件，用于范围和状态两个维度。
 */
function SegmentedFilter({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Array<{ label: string; value: string; count?: number }>;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="mr-1 text-xs font-medium text-muted">{label}</span>
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={`${label}-${option.value}`}
            type="button"
            aria-label={option.label}
            onClick={() => onChange(option.value)}
            className={`rounded-full border px-3 py-1.5 text-xs font-medium transition-colors ${
              active
                ? 'border-border-active bg-foreground text-background'
                : 'border-border bg-surface text-muted hover:border-border-active hover:text-foreground'
            }`}
          >
            {option.label}
            {option.count == null ? '' : ` ${option.count}`}
          </button>
        );
      })}
    </div>
  );
}

/**
 * 渲染单条长期记忆，操作按钮使用明确 aria-label 供键盘和测试稳定定位。
 */
function MemoryRow({
  memory,
  workspaceLabel,
  onEdit,
  onToggleStatus,
  onDelete,
}: {
  memory: LongTermMemoryItem;
  workspaceLabel: string | null;
  onEdit: () => void;
  onToggleStatus: () => void;
  onDelete: () => void;
}) {
  const scope = normalizeScope(memory.memoryScope);
  const status = normalizeStatus(memory.status);
  const active = status === 'ACTIVE';
  return (
    <article className="grid gap-4 bg-surface px-4 py-4 transition-colors hover:bg-surface-container md:grid-cols-[1fr_auto] md:items-start">
      <div className="min-w-0">
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <Badge>{scope === 'PROJECT' ? '项目记忆' : '用户记忆'}</Badge>
          <Badge tone={active ? 'active' : 'muted'}>{active ? '已生效' : '已停用'}</Badge>
          <span className="text-xs text-muted">{formatDate(memory.updatedAt)}</span>
        </div>
        <p className="break-words text-sm leading-7 text-foreground">{memory.content}</p>
        <div className="mt-3 flex flex-wrap gap-3 text-xs text-muted">
          <span>来源：{memory.sourceType || '聊天记忆'}</span>
          {workspaceLabel ? <span>工作空间：{workspaceLabel}</span> : <span>跨项目偏好</span>}
        </div>
      </div>
      <div className="flex flex-wrap gap-2 md:justify-end">
        <IconButton label={`编辑记忆 ${memory.content}`} onClick={onEdit}>
          <PencilLine size={15} />
          编辑
        </IconButton>
        <IconButton
          label={`${active ? '停用' : '启用'}记忆 ${memory.content}`}
          onClick={onToggleStatus}
        >
          {active ? <CircleOff size={15} /> : <CheckCircle2 size={15} />}
          {active ? '停用' : '启用'}
        </IconButton>
        <IconButton label={`删除记忆 ${memory.content}`} danger onClick={onDelete}>
          <Trash2 size={15} />
          删除
        </IconButton>
      </div>
    </article>
  );
}

/**
 * 渲染轻量标签，用于范围和状态信息。
 */
function Badge({
  children,
  tone = 'muted',
}: {
  children: React.ReactNode;
  tone?: 'active' | 'muted';
}) {
  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-1 text-xs ${
        tone === 'active'
          ? 'border-border-active bg-surface-container-high text-foreground'
          : 'border-border bg-surface-container text-muted'
      }`}
    >
      {children}
    </span>
  );
}

/**
 * 渲染列表操作按钮，保持所有操作按钮拥有一致命中区域和主题状态。
 */
function IconButton({
  label,
  danger,
  children,
  onClick,
}: {
  label: string;
  danger?: boolean;
  children: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 border px-3 py-1.5 text-xs font-medium transition-colors ${
        danger
          ? 'border-[#ff5b57]/35 text-[#ff5b57] hover:bg-[#ff5b57]/10'
          : 'border-border bg-surface-container text-muted hover:border-border-active hover:text-foreground'
      }`}
    >
      {children}
    </button>
  );
}

/**
 * 从左侧工作空间分组反推 workspaceId 与展示名关系，缺失时保留当前工作空间作为兜底。
 */
function buildWorkspaceLabelById(
  workspaceGroups: WorkspaceConversationGroup[],
  currentWorkspaceId: string | null,
  currentWorkspaceLabel: string,
) {
  const labelById = new Map<string, string>();
  workspaceGroups.forEach((group) => {
    group.conversations.forEach((conversation) => {
      const workspaceId = normalizeWorkspaceId(conversation.workspaceId);
      if (workspaceId && !labelById.has(workspaceId)) {
        labelById.set(workspaceId, group.workspaceLabel || `工作空间 ${workspaceId}`);
      }
    });
  });
  const normalizedCurrentWorkspaceId = normalizeWorkspaceId(currentWorkspaceId);
  if (normalizedCurrentWorkspaceId && currentWorkspaceLabel && !labelById.has(normalizedCurrentWorkspaceId)) {
    labelById.set(normalizedCurrentWorkspaceId, currentWorkspaceLabel);
  }
  return labelById;
}

/**
 * 将筛选后的记忆按跨项目用户记忆和具体工作空间分组，保证用户能一次性治理所有项目约定。
 */
function groupVisibleMemories(
  memories: LongTermMemoryItem[],
  workspaceLabelById: Map<string, string>,
): MemoryWorkspaceGroup[] {
  const userMemories: LongTermMemoryItem[] = [];
  const projectMemoriesByWorkspaceId = new Map<string, LongTermMemoryItem[]>();

  memories.forEach((memory) => {
    const memoryWorkspaceId = normalizeWorkspaceId(memory.workspaceId);
    if (normalizeScope(memory.memoryScope) !== 'PROJECT' || !memoryWorkspaceId) {
      userMemories.push(memory);
      return;
    }
    projectMemoriesByWorkspaceId.set(
      memoryWorkspaceId,
      [...(projectMemoriesByWorkspaceId.get(memoryWorkspaceId) ?? []), memory],
    );
  });

  const groups: MemoryWorkspaceGroup[] = [];
  if (userMemories.length > 0) {
    groups.push({
      key: 'user',
      label: '跨项目用户记忆',
      description: '这些偏好不绑定具体工作空间，会在用户相关对话中参与上下文。',
      memories: userMemories,
    });
  }

  Array.from(projectMemoriesByWorkspaceId.entries())
    .sort(([leftWorkspaceId], [rightWorkspaceId]) => {
      const leftLabel = workspaceLabelById.get(leftWorkspaceId) ?? `工作空间 ${leftWorkspaceId}`;
      const rightLabel = workspaceLabelById.get(rightWorkspaceId) ?? `工作空间 ${rightWorkspaceId}`;
      return leftLabel.localeCompare(rightLabel, 'zh-CN');
    })
    .forEach(([memoryWorkspaceId, workspaceMemories]) => {
      const label = workspaceLabelById.get(memoryWorkspaceId) ?? `工作空间 ${memoryWorkspaceId}`;
      groups.push({
        key: `workspace-${memoryWorkspaceId}`,
        label,
        description: `工作空间 ${memoryWorkspaceId} 的项目约定，只在该项目上下文中生效。`,
        memories: workspaceMemories,
      });
    });

  return groups;
}

/**
 * 返回单条记忆的工作空间展示名，无法匹配到侧栏分组时回退到 workspaceId。
 */
function resolveMemoryWorkspaceLabel(
  memory: LongTermMemoryItem,
  workspaceLabelById: Map<string, string>,
) {
  const memoryWorkspaceId = normalizeWorkspaceId(memory.workspaceId);
  if (!memoryWorkspaceId) {
    return null;
  }
  return workspaceLabelById.get(memoryWorkspaceId) ?? memoryWorkspaceId;
}

function normalizeScope(scope: string | null | undefined): 'USER' | 'PROJECT' {
  return String(scope ?? '').toUpperCase() === 'PROJECT' ? 'PROJECT' : 'USER';
}

function normalizeStatus(status: string | null | undefined): LongTermMemoryStatus {
  return String(status ?? '').toUpperCase() === 'REJECTED' ? 'REJECTED' : 'ACTIVE';
}

function normalizeWorkspaceId(workspaceId: string | number | null | undefined) {
  const normalizedWorkspaceId = String(workspaceId ?? '').trim();
  return normalizedWorkspaceId.length > 0 ? normalizedWorkspaceId : null;
}

function formatDate(value?: string | null) {
  if (!value) {
    return '未记录时间';
  }
  return String(value).replace('T', ' ').slice(0, 16);
}
