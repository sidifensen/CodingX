import React from 'react';
import { X } from 'lucide-react';
import {
  ChatMessageItem,
  DiffSummary,
  FileDiffItem,
  ProcessCardItem,
} from './types';
import { summarizeFileDiffs } from './fileDiffs';

/**
 * 聊天文件差异展示共享模块：消息流内嵌差异面板与右侧代码审查栏共用同一套
 * diff 渲染组件和按轮次收集差异的纯函数，避免两处各自维护一份高亮与去重逻辑。
 */

/** 按助手回复轮次聚合的文件差异快照，右侧栏据此切换“本轮编辑/上轮对话”。 */
export type ConversationDiffRound = {
  /** 来源助手消息 ID，用于定位差异所属轮次。 */
  messageId: string;
  /** 该轮去重后的文件差异列表，同一路径只保留最后一次编辑。 */
  fileDiffs: FileDiffItem[];
  /** 该轮差异统计（文件数/新增/删除行数）。 */
  diffSummary: DiffSummary;
};

/**
 * 文件行下方的内嵌差异面板，避免遮罩聊天区并保持文件列表上下文。
 */
export function InlineFileDiffPanel({
  panelId,
  fileDiff,
  isPending,
  onClose,
}: {
  panelId: string;
  fileDiff: FileDiffItem;
  isPending: boolean;
  onClose: () => void;
}) {
  return (
    <div
      id={panelId}
      role="dialog"
      aria-label={`文件差异内容：${fileDiff.path}`}
      className="ml-5 overflow-hidden rounded-lg border border-border bg-background text-foreground shadow-[0_16px_40px_rgba(0,0,0,0.22)]"
    >
      <div className="flex min-w-0 items-center justify-between gap-3 border-b border-border bg-surface px-3 py-2">
        <div className="min-w-0">
          <div className="text-[11px] text-muted">文件差异</div>
          <div className="truncate font-mono text-xs font-semibold text-foreground">
            {fileDiff.path}
          </div>
        </div>
        <button
          type="button"
          aria-label="收起文件差异内容"
          onClick={onClose}
          className="rounded-md p-1.5 text-muted transition-colors hover:bg-surface-container hover:text-foreground"
        >
          <X size={14} />
        </button>
      </div>
      <AutoScrollingDiffBlock
        diffText={fileDiff.diff || '等待写入内容...'}
        isStreamingPreview={isPending}
        className="max-h-[46vh] overflow-auto"
        testId="inline-file-diff-scroll"
      />
    </div>
  );
}

/**
 * 流式写文件时 diff 文本会不断增长，容器需要跟随到底部让用户看到最新新增内容。
 */
export function AutoScrollingDiffBlock({
  diffText,
  isStreamingPreview = false,
  className,
  testId,
}: {
  diffText: string;
  isStreamingPreview?: boolean;
  className: string;
  testId: string;
}) {
  const scrollContainerRef = React.useRef<HTMLDivElement | null>(null);

  React.useEffect(() => {
    const scrollContainer = scrollContainerRef.current;
    if (!scrollContainer) {
      return;
    }
    // 流式 diff 会高频追加内容，滚动放到下一帧执行，避免同步布局测量阻塞正文 token 渲染。
    const frameId = window.requestAnimationFrame(() => {
      const targetTop = scrollContainer.scrollHeight;
      if (typeof scrollContainer.scrollTo === 'function') {
        scrollContainer.scrollTo({ top: targetTop, behavior: 'auto' });
        return;
      }
      scrollContainer.scrollTop = targetTop;
    });
    return () => window.cancelAnimationFrame(frameId);
  }, [diffText]);

  return (
    <div ref={scrollContainerRef} data-testid={testId} className={className}>
      <DiffTextBlock diffText={diffText} isStreamingPreview={isStreamingPreview} />
    </div>
  );
}

/**
 * 按 unified diff 行前缀做轻量高亮，保持代码内容可复制且不依赖第三方 diff 组件。
 */
function DiffTextBlock({
  diffText,
}: {
  diffText: string;
  isStreamingPreview?: boolean;
}) {
  const lines = React.useMemo(() => diffText.split(/\r?\n/), [diffText]);
  return (
    <pre className="min-w-full whitespace-pre-wrap [overflow-wrap:anywhere] bg-background px-4 py-3 font-mono text-[12px] leading-5 text-foreground">
      {lines.map((line, index) => (
        <span
          key={index}
          data-testid="diff-line"
          className={`block border-l-2 px-2 ${
            line.startsWith('+') && !line.startsWith('+++')
              ? 'border-success/70 bg-success/15 font-medium text-success'
              : line.startsWith('-') && !line.startsWith('---')
                ? 'border-error/70 bg-error/15 font-medium text-error'
                : line.startsWith('@@')
                  ? 'border-accent-breeze/55 bg-accent-breeze/10 text-accent-breeze'
                  : 'border-transparent text-muted'
          }`}
        >
          {line || ' '}
        </span>
      ))}
    </pre>
  );
}

/**
 * 从会话消息中按助手回复轮次收集文件差异，右侧栏据此切换“本轮编辑/上轮对话”。
 */
export function collectConversationDiffRounds(messages: ChatMessageItem[]): ConversationDiffRound[] {
  return messages
    .filter((message) => message.role === 'ASSISTANT')
    .map((message) => {
      const fileDiffs = collectMessageFileDiffs(message);
      return fileDiffs.length > 0
        ? {
            messageId: message.id,
            fileDiffs,
            diffSummary: summarizeFileDiffs(fileDiffs),
          }
        : null;
    })
    .filter((round): round is ConversationDiffRound => round != null);
}

/**
 * 同一条消息可能同时保存 processCards 与 timelineItems；按过程卡片 ID 去重后收集文件差异。
 */
export function collectMessageFileDiffs(message: ChatMessageItem): FileDiffItem[] {
  const cards: ProcessCardItem[] = [];
  const seenCardIds = new Set<string>();
  const pushCard = (card: ProcessCardItem) => {
    if (seenCardIds.has(card.id)) {
      return;
    }
    seenCardIds.add(card.id);
    cards.push(card);
  };
  (message.processCards ?? []).forEach(pushCard);
  (message.timelineItems ?? []).forEach((item) => {
    if (item.type === 'process') {
      pushCard(item.card);
    }
  });
  return dedupeFileDiffs(cards.flatMap((card) => card.fileDiffs ?? []));
}

/**
 * 文件多次编辑时只保留同一路径最后一次 diff，侧栏关注当前可审查状态。
 */
export function dedupeFileDiffs(fileDiffs: FileDiffItem[]): FileDiffItem[] {
  const orderedPathKeys: string[] = [];
  const diffByPath = new Map<string, FileDiffItem>();
  for (const fileDiff of fileDiffs) {
    const pathKey = fileDiff.path || fileDiff.newPath || fileDiff.oldPath || 'unknown';
    if (!diffByPath.has(pathKey)) {
      orderedPathKeys.push(pathKey);
    }
    diffByPath.set(pathKey, fileDiff);
  }
  return orderedPathKeys
    .map((pathKey) => diffByPath.get(pathKey))
    .filter((fileDiff): fileDiff is FileDiffItem => fileDiff != null);
}

/**
 * 将文件路径转换成稳定测试标识片段，避免斜杠、点号影响 data-testid。
 */
export function getFileDiffSlug(path: string): string {
  return path
    .trim()
    .replace(/[^A-Za-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'file';
}

/**
 * 构造文件差异的 React 稳定键；流式写入时 diff 会频繁增长，组件身份只能绑定文件路径。
 */
export function getFileDiffStableKey(fileDiff: FileDiffItem): string {
  return fileDiff.path || fileDiff.newPath || fileDiff.oldPath || 'unknown';
}
