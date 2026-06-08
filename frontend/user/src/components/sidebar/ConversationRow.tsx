import React from 'react';
import { createPortal } from 'react-dom';
import {
  Check,
  ChevronDown,
  Download,
  File,
  Layers3,
  LoaderCircle,
  MoreHorizontal,
  PencilLine,
  Pin,
  PinOff,
  Share2,
  Trash2,
} from 'lucide-react';
import {
  ConversationActionContext,
  ConversationExportFormat,
  ConversationItem,
  WorkspaceConversationGroup,
} from '../../views/chat/types';

const CONVERSATION_MENU_WIDTH = 176;
export const CONVERSATION_MENU_SAFE_PADDING = 12;
export const CONVERSATION_MENU_ESTIMATED_HEIGHT = 332;
export const CONVERSATION_MENU_OFFSET = 8;

const QIANWEN_EXPORT_FORMATS: Array<{ label: string; value: ConversationExportFormat }> = [
  { label: 'Word', value: 'word' },
  { label: 'PDF', value: 'pdf' },
  { label: 'TXT', value: 'txt' },
  { label: 'Json', value: 'json' },
];

interface ConversationRowProps {
  group: WorkspaceConversationGroup;
  conversation: ConversationItem;
  actionContext: ConversationActionContext;
  isActive: boolean;
  isBatchMode: boolean;
  isSelected: boolean;
  isMenuOpen: boolean;
  isExportOpen: boolean;
  isActionHovered: boolean;
  menuPosition: { top: number; left: number } | null;
  menuLayerRef: React.RefObject<HTMLDivElement | null>;
  onSelectConversation: () => Promise<void>;
  onToggleBatchSelection: (conversationId: string) => void;
  onSetHoveredActionId: (conversationId: string | null) => void;
  onStartLongPress: (conversationId: string) => void;
  onClearLongPress: () => void;
  onSetOpenMenuId: (conversationId: string | null) => void;
  onSetActiveExportConversationId: (
    nextValue: string | null | ((currentId: string | null) => string | null),
  ) => void;
  onSetMenuTriggerRef: (conversationId: string, element: HTMLButtonElement | null) => void;
  onEnterBatchMode: (partitionKey: string) => void;
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
}

/**
 * 渲染侧栏单条会话，集中承接行点击、状态槽与菜单浮层，避免历史列表组件继续堆叠行级细节。
 */
export default function ConversationRow({
  group,
  conversation,
  actionContext,
  isActive,
  isBatchMode,
  isSelected,
  isMenuOpen,
  isExportOpen,
  isActionHovered,
  menuPosition,
  menuLayerRef,
  onSelectConversation,
  onToggleBatchSelection,
  onSetHoveredActionId,
  onStartLongPress,
  onClearLongPress,
  onSetOpenMenuId,
  onSetActiveExportConversationId,
  onSetMenuTriggerRef,
  onEnterBatchMode,
  onRenameConversation,
  onDeleteConversation,
  onShareConversation,
  onToggleConversationPin,
  onExportConversation,
}: ConversationRowProps) {
  const isTaskRunning =
    String(conversation.activeTaskStatus ?? '').trim().toUpperCase() === 'RUNNING';
  const shouldShowActionMenu =
    (isActionHovered || isMenuOpen) && !(conversation.hasUnreadTaskCompletion && !isMenuOpen);
  const statusSlotClass = shouldShowActionMenu
    ? 'opacity-0 pointer-events-none'
    : 'opacity-100 pointer-events-auto';
  const actionButtonClass = shouldShowActionMenu
    ? 'opacity-100 pointer-events-auto'
    : 'opacity-0 pointer-events-none';

  /**
   * 关闭当前会话菜单和导出子菜单，保证执行菜单动作后浮层不会残留。
   */
  const closeMenu = () => {
    onSetOpenMenuId(null);
    onSetActiveExportConversationId(null);
  };

  return (
    <div
      className="relative"
      onMouseEnter={() => onSetHoveredActionId(conversation.id)}
      onMouseLeave={() => onSetHoveredActionId(null)}
    >
      <div
        data-testid={`sidebar-conversation-row-${conversation.id}`}
        onClick={() => {
          if (!isBatchMode) {
            void onSelectConversation();
          }
        }}
        className={`group flex cursor-pointer items-center justify-between gap-3 rounded-xl border px-3 py-1.5 transition-[border-color,background-color,box-shadow] duration-200 ${
          isActive
            ? 'border-border-selected bg-surface-selected shadow-sm'
            : 'border-transparent hover:border-border-active hover:bg-surface-selected active:border-border-active active:bg-surface-selected'
        }`}
      >
        {/* 让会话标题与工作空间标题文字起点对齐，并增强选中态可辨识度。 */}
        {isBatchMode ? (
          <label
            className="flex h-6 w-6 shrink-0 cursor-pointer items-center justify-center"
            aria-label={`选择侧栏对话 ${conversation.title}`}
          >
            <input
              type="checkbox"
              checked={isSelected}
              onChange={() => onToggleBatchSelection(conversation.id)}
              className="sr-only"
            />
            <span
              className={`flex h-4.5 w-4.5 items-center justify-center rounded border transition-colors ${
                isSelected
                  ? 'border-border-selected bg-foreground text-background'
                  : 'border-border bg-surface text-transparent'
              }`}
            >
              <Check size={12} />
            </span>
          </label>
        ) : null}
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            void onSelectConversation();
          }}
          disabled={isBatchMode}
          className={`min-w-0 flex-1 cursor-pointer pl-3 text-left ${
            isActive ? 'text-foreground' : 'text-muted hover:text-foreground'
          }`}
        >
          <div className="flex min-w-0 items-center justify-between gap-3">
            <div className={`flex min-w-0 items-center gap-2 ${isActive ? 'font-medium' : ''}`}>
              {conversation.isPinned ? (
                <Pin size={12} className="shrink-0 text-accent-breeze" aria-hidden="true" />
              ) : null}
              <span className="truncate text-[14px]">{conversation.title}</span>
            </div>
          </div>
        </button>
        {isBatchMode ? (
          <span className="text-[11px] text-muted">
            {isSelected ? '已选择' : ''}
          </span>
        ) : (
          <div
            className="relative flex h-5 min-w-[56px] shrink-0 items-center justify-end whitespace-nowrap"
            onTouchStart={() => onStartLongPress(conversation.id)}
            onTouchEnd={onClearLongPress}
            onTouchCancel={onClearLongPress}
          >
            {isTaskRunning ? (
              <span
                role="status"
                aria-label={`会话 ${conversation.title} 正在后台执行`}
                className={`absolute right-0 inline-flex h-5 w-5 items-center justify-center text-muted transition-opacity ${statusSlotClass}`}
              >
                <LoaderCircle size={15} className="animate-spin" />
              </span>
            ) : (
              <span
                className={`absolute right-0 inline-flex items-center gap-1.5 whitespace-nowrap text-[12px] text-muted transition-opacity ${statusSlotClass}`}
              >
                <span>{formatRelativeTime(conversation.lastMessageAt)}</span>
                {conversation.hasUnreadTaskCompletion ? (
                  <span
                    role="status"
                    aria-label={`会话 ${conversation.title} 有后台任务完成提醒`}
                    className="h-1.5 w-1.5 rounded-full bg-accent-breeze shadow-[0_0_0_3px_color-mix(in_srgb,var(--accent-breeze)_18%,transparent)]"
                  />
                ) : null}
              </span>
            )}
            <button
              type="button"
              aria-label={`打开会话菜单 ${conversation.title}`}
              onClick={(event) => {
                // 菜单按钮和状态槽重叠；阻止冒泡，避免打开菜单时误切换会话。
                event.stopPropagation();
                const nextIsOpen = !isMenuOpen;
                onSetOpenMenuId(nextIsOpen ? conversation.id : null);
                onSetActiveExportConversationId(null);
              }}
              ref={(element) => onSetMenuTriggerRef(conversation.id, element)}
              className={`absolute right-0 cursor-pointer rounded-md p-1 text-muted transition-opacity hover:text-foreground ${actionButtonClass}`}
            >
              <MoreHorizontal size={16} />
            </button>
          </div>
        )}
      </div>

      {isMenuOpen && menuPosition
        ? createPortal(
            <div
              ref={menuLayerRef}
              className="fixed z-[130] w-44 rounded-xl border border-border bg-surface-container p-2 shadow-[0_16px_40px_rgba(0,0,0,0.24)]"
              style={{ top: `${menuPosition.top}px`, left: `${menuPosition.left}px` }}
            >
              <button
                type="button"
                aria-label="重命名"
                data-testid="conversation-action-menu-item"
                onClick={() => {
                  void onRenameConversation(conversation.id, conversation.title, actionContext);
                  closeMenu();
                }}
                className="flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
              >
                <PencilLine size={16} />
                重命名
              </button>
              <button
                type="button"
                aria-label={conversation.isPinned ? '取消置顶' : '置顶此对话'}
                data-testid="conversation-action-menu-item"
                onClick={() => {
                  void onToggleConversationPin(conversation.id, actionContext);
                  closeMenu();
                }}
                className="mt-1 flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
              >
                {conversation.isPinned ? <PinOff size={16} /> : <Pin size={16} />}
                {conversation.isPinned ? '取消置顶' : '置顶此对话'}
              </button>
              <button
                type="button"
                aria-label="分享此对话"
                data-testid="conversation-action-menu-item"
                onClick={() => {
                  void onShareConversation(conversation.id, actionContext);
                  closeMenu();
                }}
                className="mt-1 flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
              >
                <Share2 size={16} />
                分享此对话
              </button>
              <button
                type="button"
                aria-label="批量管理"
                data-testid="conversation-action-menu-item"
                onClick={() => onEnterBatchMode(group.partitionKey)}
                className="mt-1 flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
              >
                <Layers3 size={16} />
                批量管理
              </button>
              <button
                type="button"
                aria-label="导出对话"
                data-testid="conversation-action-menu-item"
                onClick={() =>
                  onSetActiveExportConversationId((currentId) =>
                    currentId === conversation.id ? null : conversation.id,
                  )
                }
                className="mt-1 flex w-full cursor-pointer items-center justify-between rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-high"
              >
                <span className="inline-flex items-center gap-3">
                  <Download size={16} />
                  导出对话
                </span>
                <ChevronDown size={14} className="-rotate-90 text-muted" />
              </button>
              {isExportOpen ? (
                <div
                  role="menu"
                  aria-label="导出格式"
                  className="absolute left-[calc(100%-4px)] top-[206px] w-28 space-y-1 rounded-xl border border-border bg-surface-container px-2 py-2 shadow-[0_16px_40px_rgba(0,0,0,0.24)]"
                >
                  {QIANWEN_EXPORT_FORMATS.map((format) => (
                    <button
                      key={format.value}
                      type="button"
                      aria-label={format.label}
                      onClick={() => {
                        void onExportConversation(conversation.id, format.value, actionContext);
                        closeMenu();
                      }}
                      className="flex w-full cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-left text-xs text-foreground transition-colors hover:bg-surface-high"
                    >
                      <File size={14} />
                      {format.label}
                    </button>
                  ))}
                </div>
              ) : null}
              <button
                type="button"
                aria-label="删除此对话"
                data-testid="conversation-action-menu-item"
                onClick={() => {
                  void onDeleteConversation(conversation.id, actionContext);
                  closeMenu();
                }}
                className="mt-1 flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-[#ff5b57] transition-colors hover:bg-[#ff5b57]/10"
              >
                <Trash2 size={16} />
                删除此对话
              </button>
            </div>,
            document.body,
          )
        : null}
    </div>
  );
}

/**
 * 解析后端返回的会话时间字符串，失败时返回 null 以便安全降级。
 * @param value 后端返回的最近消息时间。
 * @returns 可比较的 Date 或 null。
 */
function parseConversationDate(value?: string) {
  if (!value) {
    return null;
  }
  const normalizedValue = value.includes('T') ? value : value.replace(' ', 'T');
  const nextDate = new Date(normalizedValue);
  if (Number.isNaN(nextDate.getTime())) {
    return null;
  }
  return nextDate;
}

/**
 * 将最后消息时间转换为“几小时前/几天前”的相对时间文案。
 * @param value 后端返回的最近消息时间。
 * @returns 适合展示在会话标题下方的相对时间。
 */
function formatRelativeTime(value?: string) {
  const conversationDate = parseConversationDate(value);
  if (!conversationDate) {
    return '刚刚';
  }
  const diffMs = Date.now() - conversationDate.getTime();
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  if (diffHours < 1) {
    const diffMinutes = Math.max(1, Math.floor(diffMs / (1000 * 60)));
    return `${diffMinutes} 分钟前`;
  }
  if (diffHours < 24) {
    return `${diffHours} 小时前`;
  }
  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays} 天前`;
}

export { CONVERSATION_MENU_WIDTH };
