import React, { useEffect, useRef, useState } from 'react';
import {
  ChevronDown,
  Cloud,
  Folder,
  History,
  Plus,
  X,
} from 'lucide-react';
import {
  ConversationActionContext,
  ConversationExportFormat,
  ConversationItem,
  WorkspaceConversationCreateContext,
  WorkspaceConversationGroup,
  WorkspaceConversationSelectionContext,
} from '../../views/chat/types';
import ConversationRow, {
  CONVERSATION_MENU_ESTIMATED_HEIGHT,
  CONVERSATION_MENU_OFFSET,
  CONVERSATION_MENU_SAFE_PADDING,
  CONVERSATION_MENU_WIDTH,
} from './ConversationRow';

/**
 * 约定侧边栏会话列表每次展开的条目数，保证交互节奏稳定。
 */
const CONVERSATION_PAGE_SIZE = 5;
const BATCH_SELECTION_LIMIT = 100;
const HISTORY_PARTITION_SUFFIX = '::__history__';
const HISTORY_WORKSPACE_LABELS = new Set(['云端历史记录', '本地历史记录']);

/**
 * 识别历史记录分组，兼容旧快照仅写 label、未写 groupType 的默认云端/本地历史记录。
 * @param group 工作空间分组。
 */
function isHistoryConversationGroup(group: WorkspaceConversationGroup) {
  return (
    group.groupType === 'history' ||
    group.partitionKey.endsWith(HISTORY_PARTITION_SUFFIX) ||
    HISTORY_WORKSPACE_LABELS.has(group.workspaceLabel.trim())
  );
}

interface ConversationHistoryProps {
  workspaceGroups: WorkspaceConversationGroup[];
  activeWorkspacePartitionKey: string | null;
  activeConversationId: string | null;
  onSelectConversation: (
    conversationId: string,
    selectionContext: WorkspaceConversationSelectionContext,
  ) => Promise<void>;
  onStartNewConversation: (
    createContext?: WorkspaceConversationCreateContext,
  ) => Promise<void>;
  onRenameConversation: (
    conversationId: string,
    title: string,
    actionContext?: ConversationActionContext,
  ) => Promise<void>;
  onDeleteConversation: (
    conversationId: string,
    actionContext?: ConversationActionContext,
  ) => Promise<void>;
  onShareConversation: (
    conversationId: string,
    actionContext?: ConversationActionContext,
  ) => Promise<string>;
  onToggleConversationPin: (
    conversationId: string,
    actionContext: ConversationActionContext,
  ) => Promise<boolean>;
  onExportConversation: (
    conversationId: string,
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  onExportConversations: (
    conversationIds: string[],
    format: ConversationExportFormat,
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  onDeleteConversations: (
    conversationIds: string[],
    actionContext: ConversationActionContext,
  ) => Promise<void>;
  onLoadMoreConversations: (
    selectionContext: WorkspaceConversationSelectionContext,
  ) => Promise<void>;
  onSelectWorkspacePath: (workspacePath: string | null) => Promise<void>;
}

/**
 * 按工作空间分组渲染真实会话历史，并承接分组折叠、分页加载与批量管理。
 */
export default function ConversationHistory({
  workspaceGroups,
  activeWorkspacePartitionKey,
  activeConversationId,
  onSelectConversation,
  onStartNewConversation,
  onRenameConversation,
  onDeleteConversation,
  onShareConversation,
  onToggleConversationPin,
  onExportConversation,
  onExportConversations,
  onDeleteConversations,
  onLoadMoreConversations,
  onSelectWorkspacePath,
}: ConversationHistoryProps) {
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [activeExportConversationId, setActiveExportConversationId] = useState<string | null>(null);
  const [hoveredActionId, setHoveredActionId] = useState<string | null>(null);
  const [expandedCountMap, setExpandedCountMap] = useState<Record<string, number>>({});
  const [collapsedGroupMap, setCollapsedGroupMap] = useState<Record<string, boolean>>({});
  const [batchModePartitionKey, setBatchModePartitionKey] = useState<string | null>(null);
  const [selectedConversationIds, setSelectedConversationIds] = useState<string[]>([]);
  const [isBatchDeleteDialogOpen, setIsBatchDeleteDialogOpen] = useState(false);
  const menuLayerRef = useRef<HTMLDivElement | null>(null);
  const menuTriggerRefs = useRef<Record<string, HTMLButtonElement | null>>({});
  const touchTimerRef = useRef<number | null>(null);
  const [menuPosition, setMenuPosition] = useState<{ top: number; left: number } | null>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (menuLayerRef.current?.contains(target)) {
        return;
      }
      const activeTrigger = openMenuId ? menuTriggerRefs.current[openMenuId] : null;
      if (activeTrigger?.contains(target)) {
        return;
      }
      setOpenMenuId(null);
      setActiveExportConversationId(null);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [openMenuId]);

  /**
   * 将会话菜单锚定到触发按钮，并对视口边缘进行安全收敛，避免被裁剪或超出屏幕。
   */
  const syncConversationMenuPosition = React.useCallback(() => {
    if (!openMenuId) {
      setMenuPosition(null);
      return;
    }
    const trigger = menuTriggerRefs.current[openMenuId];
    if (!trigger) {
      setMenuPosition(null);
      return;
    }
    const triggerRect = trigger.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    const preferredLeft = triggerRect.right - CONVERSATION_MENU_WIDTH;
    const minLeft = CONVERSATION_MENU_SAFE_PADDING;
    const maxLeft = viewportWidth - CONVERSATION_MENU_SAFE_PADDING - CONVERSATION_MENU_WIDTH;
    const preferredTop = triggerRect.bottom + CONVERSATION_MENU_OFFSET;
    const minTop = CONVERSATION_MENU_SAFE_PADDING;
    const maxTop =
      viewportHeight - CONVERSATION_MENU_SAFE_PADDING - CONVERSATION_MENU_ESTIMATED_HEIGHT;
    setMenuPosition({
      left: Math.max(minLeft, Math.min(preferredLeft, maxLeft)),
      top: Math.max(minTop, Math.min(preferredTop, maxTop)),
    });
  }, [openMenuId]);

  useEffect(() => {
    if (!openMenuId) {
      return undefined;
    }
    syncConversationMenuPosition();
    // 关键约束：菜单使用 portal 挂载到 body 后，需要监听全局滚动/窗口缩放实时重算锚点。
    const updatePosition = () => syncConversationMenuPosition();
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);
    return () => {
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [openMenuId, syncConversationMenuPosition]);

  const startLongPress = (conversationId: string) => {
    clearLongPress();
    touchTimerRef.current = window.setTimeout(() => {
      setOpenMenuId(conversationId);
    }, 450);
  };

  const clearLongPress = () => {
    if (touchTimerRef.current != null) {
      window.clearTimeout(touchTimerRef.current);
      touchTimerRef.current = null;
    }
  };

  /**
   * 根据分组与会话拼出动作上下文，确保菜单动作在多工作空间场景下仍作用于正确分区。
   * @param group 会话所属分组。
   * @returns 可复用动作上下文。
   */
  const buildActionContext = (group: WorkspaceConversationGroup): ConversationActionContext => ({
    partitionKey: group.partitionKey,
    runtimeTarget: group.runtimeTarget,
    workspacePath: group.workspacePath,
    groupType: group.groupType,
  });

  /**
   * 进入某个分组的批量管理模式，并清空上一个分组的勾选结果。
   * @param partitionKey 目标分组键。
   */
  const enterBatchMode = (partitionKey: string) => {
    setBatchModePartitionKey(partitionKey);
    setSelectedConversationIds([]);
    setOpenMenuId(null);
    setActiveExportConversationId(null);
  };

  /**
   * 退出批量管理模式，清空勾选与确认弹窗状态。
   */
  const exitBatchMode = () => {
    setBatchModePartitionKey(null);
    setSelectedConversationIds([]);
    setIsBatchDeleteDialogOpen(false);
  };

  /**
   * 切换单个会话的批量勾选状态。
   * @param conversationId 会话标识。
   */
  const toggleBatchSelection = (conversationId: string) => {
    setSelectedConversationIds((previousIds) =>
      previousIds.includes(conversationId)
        ? previousIds.filter((item) => item !== conversationId)
        : previousIds.length >= BATCH_SELECTION_LIMIT
          ? previousIds
          : [...previousIds, conversationId],
    );
  };

  /**
   * 千问批量管理最多勾选 100 条，超过部分保留未选中状态。
   * @param conversations 当前抽屉可见会话。
   */
  const selectBatchConversations = (conversations: ConversationItem[]) => {
    setSelectedConversationIds(conversations.slice(0, BATCH_SELECTION_LIMIT).map((conversation) => conversation.id));
  };

  /**
   * 处理会话列表底部按钮：先展开已加载的本地条目，当前页用尽后再请求后端下一页。
   * @param group 工作空间分组。
   * @param visibleConversationCount 当前可见条数。
   * @param totalConversations 当前分区已加载总数。
   */
  const handleShowMoreConversations = (
    group: WorkspaceConversationGroup,
    visibleConversationCount: number,
    totalConversations: number,
  ) => {
    if (visibleConversationCount < totalConversations) {
      setExpandedCountMap((previousMap) => {
        const previousCount = previousMap[group.partitionKey] ?? CONVERSATION_PAGE_SIZE;
        const nextCount = Math.min(totalConversations, previousCount + CONVERSATION_PAGE_SIZE);
        return {
          ...previousMap,
          [group.partitionKey]: nextCount,
        };
      });
      return;
    }
    if (group.hasMore && !group.isLoadingMore) {
      void onLoadMoreConversations(buildActionContext(group));
    }
  };

  /**
   * 收起当前工作空间会话列表，回到默认展示 5 条。
   * @param partitionKey 工作空间分区键。
   */
  const handleCollapseConversations = (partitionKey: string) => {
    setExpandedCountMap((previousMap) => ({
      ...previousMap,
      [partitionKey]: CONVERSATION_PAGE_SIZE,
    }));
  };

  /**
   * 切换工作空间分组折叠状态，用于快速隐藏或展示该分组会话。
   * @param group 工作空间分组。
   */
  const handleToggleGroupCollapse = (group: WorkspaceConversationGroup) => {
    const isHistoryGroup = isHistoryConversationGroup(group);
    setCollapsedGroupMap((previousMap) => ({
      ...previousMap,
      [group.partitionKey]: !(
        previousMap[group.partitionKey] ??
        // 首屏默认只展开历史记录分组，普通工作空间先收起，降低侧栏纵向噪音。
        !isHistoryGroup
      ),
    }));
  };

  /**
   * 统一工作空间运行环境图标，避免在左侧列表重复出现“云端/本地”文字占位。
   * @param runtimeTarget 运行环境类型。
   * @returns 对应的图标组件。
   */
  const getWorkspaceRuntimeIcon = (
    runtimeTarget: WorkspaceConversationGroup['runtimeTarget'],
    groupType: WorkspaceConversationGroup['groupType'],
  ) => {
    if (groupType === 'history') {
      return History;
    }
    return runtimeTarget === 'local' ? Folder : Cloud;
  };

  return (
    <>
      {workspaceGroups.length ? (
        <div className="space-y-2 px-3">
          {workspaceGroups.map((group) => {
            const totalConversations = group.conversations.length;
            const visibleConversationCount = Math.min(
              totalConversations,
              expandedCountMap[group.partitionKey] ?? CONVERSATION_PAGE_SIZE,
            );
            const visibleConversations = group.conversations.slice(0, visibleConversationCount);
            const hasLocalHiddenConversations = visibleConversationCount < totalConversations;
            const hasRemoteMoreConversations = Boolean(group.hasMore);
            const canShowMoreConversations = hasLocalHiddenConversations || hasRemoteMoreConversations;
            const canCollapseConversations =
              totalConversations > CONVERSATION_PAGE_SIZE &&
              !hasLocalHiddenConversations &&
              !hasRemoteMoreConversations;
            const isHistoryGroup = isHistoryConversationGroup(group);
            const isGroupCollapsed =
              collapsedGroupMap[group.partitionKey] ??
              // 新进入的分组若还没有用户点击状态，则仅历史记录默认展开。
              !isHistoryGroup;
            const isBatchMode = batchModePartitionKey === group.partitionKey;
            const actionContext = buildActionContext(group);
            const RuntimeIcon = isHistoryGroup
              ? History
              : getWorkspaceRuntimeIcon(group.runtimeTarget, group.groupType);
            return (
              <section key={group.partitionKey}>
                <div className="group/workspace-header flex min-h-11 w-full items-center justify-between gap-3 rounded-lg bg-surface-container/55 px-1 py-1.5 transition-colors hover:bg-surface-container">
                  <button
                    type="button"
                    aria-label={`${isGroupCollapsed ? '展开' : '折叠'}工作空间 ${group.workspaceLabel} 会话`}
                    onClick={() => {
                      // 历史分组不绑定具体目录，点击分组头仅折叠/展开，避免触发无效路径切换。
                      if (!isHistoryGroup) {
                        void onSelectWorkspacePath(group.workspacePath);
                      }
                      handleToggleGroupCollapse(group);
                    }}
                    className="flex min-w-0 flex-1 cursor-pointer items-center gap-2 rounded-md px-0 py-0 text-left"
                  >
                    <RuntimeIcon
                      size={14}
                      className="ml-1 shrink-0 text-muted"
                      data-testid={
                        isHistoryGroup
                          ? 'workspace-runtime-icon-history'
                          : `workspace-runtime-icon-${group.runtimeTarget}`
                      }
                      aria-hidden="true"
                    />
                    <span className="truncate text-sm font-semibold text-foreground">
                      {group.workspaceLabel}
                    </span>
                    <span
                      className="rounded-md p-1 text-muted transition-colors hover:bg-surface-container hover:text-foreground"
                      aria-hidden="true"
                    >
                      <ChevronDown
                        size={16}
                        className={`transition-transform duration-200 ${
                          isGroupCollapsed ? '-rotate-90' : 'rotate-0'
                        }`}
                      />
                    </span>
                  </button>
                  <button
                    type="button"
                    aria-label={`在工作空间 ${group.workspaceLabel} 新建对话`}
                    onClick={() =>
                      void onStartNewConversation({
                        partitionKey: group.partitionKey,
                        runtimeTarget: group.runtimeTarget,
                        workspacePath: group.workspacePath,
                        groupType: group.groupType,
                      })
                    }
                    className="mr-1 inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-md text-muted transition-colors hover:bg-surface-container-high hover:text-foreground"
                  >
                    <Plus size={15} />
                  </button>
                </div>

                {/* 使用 grid-rows 过渡折叠高度，避免列表展开/收起时突兀跳变，并保持分组头与首条会话的纵向间距更紧凑。 */}
                <div
                  className={`grid transition-[grid-template-rows,opacity,margin-top] duration-250 ease-out ${
                    isGroupCollapsed
                      ? 'mt-0 grid-rows-[0fr] opacity-0 pointer-events-none'
                      : 'mt-1 grid-rows-[1fr] opacity-100'
                  }`}
                  aria-hidden={isGroupCollapsed}
                >
                  <div className="min-h-0 overflow-hidden">
                    <div className="space-y-1">
                      {visibleConversations.length ? (
                        visibleConversations.map((conversation) => {
                          // 关键约束：会话高亮必须同时命中“当前分区 + 当前会话”，避免同 ID 跨分组双高亮。
                          const isActive =
                            group.partitionKey === activeWorkspacePartitionKey &&
                            conversation.id === activeConversationId;
                          const selectConversation = () =>
                            onSelectConversation(conversation.id, {
                              partitionKey: group.partitionKey,
                              runtimeTarget: group.runtimeTarget,
                              workspacePath: group.workspacePath,
                              groupType: group.groupType,
                            });
                          return (
                            <ConversationRow
                              key={conversation.id}
                              group={group}
                              conversation={conversation}
                              actionContext={actionContext}
                              isActive={isActive}
                              isBatchMode={isBatchMode}
                              isSelected={selectedConversationIds.includes(conversation.id)}
                              isMenuOpen={openMenuId === conversation.id}
                              isExportOpen={activeExportConversationId === conversation.id}
                              isActionHovered={hoveredActionId === conversation.id}
                              menuPosition={menuPosition}
                              menuLayerRef={menuLayerRef}
                              onSelectConversation={selectConversation}
                              onToggleBatchSelection={toggleBatchSelection}
                              onSetHoveredActionId={setHoveredActionId}
                              onStartLongPress={startLongPress}
                              onClearLongPress={clearLongPress}
                              onSetOpenMenuId={setOpenMenuId}
                              onSetActiveExportConversationId={setActiveExportConversationId}
                              onSetMenuTriggerRef={(conversationId, element) => {
                                menuTriggerRefs.current[conversationId] = element;
                              }}
                              onEnterBatchMode={enterBatchMode}
                              onRenameConversation={onRenameConversation}
                              onDeleteConversation={onDeleteConversation}
                              onShareConversation={onShareConversation}
                              onToggleConversationPin={onToggleConversationPin}
                              onExportConversation={onExportConversation}
                            />
                          );
                        })
                      ) : (
                        <div className="rounded-xl bg-surface-container pl-[22px] pr-3 py-2 text-sm text-muted">
                          暂无会话
                        </div>
                      )}
                      {canShowMoreConversations ? (
                        <button
                          type="button"
                          aria-label={hasLocalHiddenConversations ? '展开显示' : '加载更多会话'}
                          disabled={!hasLocalHiddenConversations && group.isLoadingMore}
                          onClick={() =>
                            handleShowMoreConversations(
                              group,
                              visibleConversationCount,
                              totalConversations,
                            )
                          }
                          className="ml-5 inline-flex cursor-pointer items-center rounded-lg bg-surface-container px-2.5 py-1.5 text-xs font-medium text-muted transition-colors hover:bg-surface-container-high hover:text-foreground disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {hasLocalHiddenConversations
                            ? '展开显示'
                            : group.isLoadingMore
                              ? '加载中...'
                              : '加载更多'}
                        </button>
                      ) : null}
                      {canCollapseConversations ? (
                        <button
                          type="button"
                          aria-label="收起显示"
                          onClick={() => handleCollapseConversations(group.partitionKey)}
                          className="ml-5 inline-flex cursor-pointer items-center rounded-lg bg-surface-container px-2.5 py-1.5 text-xs font-medium text-muted transition-colors hover:bg-surface-container-high hover:text-foreground"
                        >
                          收起显示
                        </button>
                      ) : null}
                    </div>
                  </div>
                </div>
              </section>
            );
          })}
        </div>
      ) : (
        <div className="px-5 text-sm text-muted">暂无工作空间</div>
      )}
      {batchModePartitionKey ? (
        (() => {
          const batchGroup = workspaceGroups.find((group) => group.partitionKey === batchModePartitionKey);
          const batchConversations = (batchGroup?.conversations ?? []).slice(0, BATCH_SELECTION_LIMIT);
          const allSelected =
            batchConversations.length > 0 &&
            batchConversations.every((conversation) => selectedConversationIds.includes(conversation.id));
          return (
            <div className="fixed inset-0 z-[132] bg-background/35 backdrop-blur-sm">
              <section
                role="dialog"
                aria-modal="true"
                aria-label="对话批量管理"
                className="flex h-full w-full max-w-[360px] flex-col border-r border-border bg-surface text-foreground shadow-[24px_0_60px_rgba(0,0,0,0.18)]"
              >
                <div className="flex items-center justify-between border-b border-border px-5 py-4">
                  <h2 className="text-base font-semibold">对话批量管理</h2>
                  <button
                    type="button"
                    aria-label="关闭批量管理"
                    onClick={exitBatchMode}
                    className="rounded-full p-1.5 text-muted transition-colors hover:bg-surface-container hover:text-foreground"
                  >
                    <X size={18} />
                  </button>
                </div>
                <label className="flex cursor-pointer items-center justify-between border-b border-border px-5 py-3 text-sm text-foreground">
                  <span>全选</span>
                  <input
                    type="checkbox"
                    aria-label="全选"
                    checked={allSelected}
                    onChange={(event) => {
                      if (event.target.checked) {
                        selectBatchConversations(batchConversations);
                        return;
                      }
                      setSelectedConversationIds([]);
                    }}
                    className="h-4 w-4 accent-foreground"
                  />
                </label>
                <div className="flex-1 overflow-y-auto px-3 py-3">
                  {batchConversations.map((conversation) => (
                    <label
                      key={conversation.id}
                      className="flex cursor-pointer items-center justify-between gap-3 rounded-xl px-3 py-2.5 text-sm text-foreground transition-colors hover:bg-surface-container"
                    >
                      <span className="min-w-0 truncate">{conversation.title}</span>
                      <input
                        type="checkbox"
                        aria-label={`选择对话 ${conversation.title}`}
                        checked={selectedConversationIds.includes(conversation.id)}
                        onChange={() => toggleBatchSelection(conversation.id)}
                        className="h-4 w-4 shrink-0 accent-foreground"
                      />
                    </label>
                  ))}
                </div>
                <div className="flex items-center gap-3 border-t border-border px-5 py-4">
                  <button
                    type="button"
                    aria-label="取消"
                    onClick={exitBatchMode}
                    className="rounded-xl border border-border px-4 py-2 text-sm text-muted transition-colors hover:bg-surface-container hover:text-foreground"
                  >
                    取消
                  </button>
                  <span className="ml-auto text-sm text-muted">
                    已选{selectedConversationIds.length}/{BATCH_SELECTION_LIMIT}
                  </span>
                  <button
                    type="button"
                    aria-label="删除"
                    disabled={selectedConversationIds.length === 0}
                    onClick={() => setIsBatchDeleteDialogOpen(true)}
                    className="rounded-xl bg-[#ff5b57] px-4 py-2 text-sm text-white transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-45"
                  >
                    删除
                  </button>
                </div>
              </section>
            </div>
          );
        })()
      ) : null}
      {isBatchDeleteDialogOpen ? (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="批量删除对话"
          className="fixed inset-0 z-[135] flex items-center justify-center bg-background/35 backdrop-blur-sm"
        >
          <div className="w-full max-w-sm rounded-2xl border border-border bg-surface-container p-5 shadow-[0_24px_64px_rgba(0,0,0,0.26)]">
            <h3 className="text-base font-semibold text-foreground">批量删除对话</h3>
            <p className="mt-3 text-sm leading-6 text-muted">
              确认删除已选择的 {selectedConversationIds.length} 个对话吗？
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setIsBatchDeleteDialogOpen(false)}
                className="rounded-lg bg-surface px-4 py-2 text-sm text-muted transition-colors hover:bg-surface-high hover:text-foreground"
              >
                取消
              </button>
              <button
                type="button"
                aria-label="确认批量删除"
                onClick={() => {
                  const currentGroup = workspaceGroups.find(
                    (group) => group.partitionKey === batchModePartitionKey,
                  );
                  if (currentGroup && selectedConversationIds.length > 0) {
                    void onDeleteConversations(
                      selectedConversationIds,
                      buildActionContext(currentGroup),
                    );
                  }
                  exitBatchMode();
                }}
                className="rounded-lg bg-[#ff5b57] px-4 py-2 text-sm text-white transition-opacity hover:opacity-90"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
