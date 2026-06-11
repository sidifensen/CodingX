import React from 'react';
import { CheckCircle2, ChevronDown } from 'lucide-react';
import { AuthStorage } from '../../../utils/authStorage';
import { ChatApi } from '../chatApi';
import { ChatMessageItem, DiffSummary, FileDiffItem } from '../types';
import {
  normalizeDiffSummaryFromMetadata,
  normalizeFileDiffsFromMetadata,
  summarizeFileDiffs,
} from '../fileDiffs';
import {
  AutoScrollingDiffBlock,
  collectConversationDiffRounds,
  getFileDiffStableKey,
} from '../diffPresentation';

/** 代码审查栏可选的差异模式：本轮/上轮来自会话消息，其余四种走 git diff 工具。 */
type CodeReviewMode = 'current' | 'previous' | 'unstaged' | 'staged' | 'commit' | 'branch';

/** 差异模式下拉菜单的展示顺序与中文标签。 */
const CODE_REVIEW_MODES: Array<{ mode: CodeReviewMode; label: string }> = [
  { mode: 'current', label: '本轮编辑' },
  { mode: 'previous', label: '上轮对话' },
  { mode: 'unstaged', label: '未暂存' },
  { mode: 'staged', label: '已暂存' },
  { mode: 'commit', label: '提交' },
  { mode: 'branch', label: '分支' },
];

/** 需要调用后端 git_diff 工具按工作区读取差异的模式集合。 */
const GIT_DIFF_REVIEW_MODES = new Set<CodeReviewMode>(['unstaged', 'staged', 'commit', 'branch']);

/**
 * 右侧代码审查栏聚合本轮/上轮工具差异，并可按模式读取当前工作区 git diff。
 */
export function CodeReviewPanel({
  messages,
  workspaceId,
  repositoryPath,
}: {
  messages: ChatMessageItem[];
  workspaceId: string | null;
  repositoryPath: string | null;
}) {
  const [activeMode, setActiveMode] = React.useState<CodeReviewMode>('current');
  const [workspaceDiffs, setWorkspaceDiffs] = React.useState<FileDiffItem[]>([]);
  const [workspaceSummary, setWorkspaceSummary] = React.useState<DiffSummary | undefined>();
  const [isLoadingWorkspaceDiff, setIsLoadingWorkspaceDiff] = React.useState(false);
  const [workspaceDiffError, setWorkspaceDiffError] = React.useState('');
  const [isModeMenuOpen, setIsModeMenuOpen] = React.useState(false);
  const modeMenuRef = React.useRef<HTMLDivElement | null>(null);
  const conversationDiffRounds = React.useMemo(() => collectConversationDiffRounds(messages), [messages]);
  const currentRound = conversationDiffRounds[conversationDiffRounds.length - 1];
  const previousRound = conversationDiffRounds[conversationDiffRounds.length - 2];
  const isWorkspaceMode = GIT_DIFF_REVIEW_MODES.has(activeMode);
  const activeModeItem = CODE_REVIEW_MODES.find((item) => item.mode === activeMode) ?? CODE_REVIEW_MODES[0];
  const localDiffs = activeMode === 'previous'
    ? previousRound?.fileDiffs ?? []
    : currentRound?.fileDiffs ?? [];
  const localSummary =
    activeMode === 'previous'
      ? previousRound?.diffSummary
      : currentRound?.diffSummary;
  const visibleFileDiffs = isWorkspaceMode ? workspaceDiffs : localDiffs;
  const visibleSummary =
    (isWorkspaceMode ? workspaceSummary : localSummary) ??
    (visibleFileDiffs.length > 0 ? summarizeFileDiffs(visibleFileDiffs) : { filesChanged: 0, additions: 0, deletions: 0 });
  const [activePath, setActivePath] = React.useState<string>('');
  const activeFileDiff =
    visibleFileDiffs.find((fileDiff) => fileDiff.path === activePath) ??
    visibleFileDiffs[0] ??
    null;
  const selectCodeReviewMode = React.useCallback((nextMode: CodeReviewMode) => {
    setActiveMode(nextMode);
    setIsModeMenuOpen(false);
  }, []);
  React.useEffect(() => {
    if (!visibleFileDiffs.some((fileDiff) => fileDiff.path === activePath)) {
      setActivePath(visibleFileDiffs[0]?.path ?? '');
    }
  }, [activePath, visibleFileDiffs]);

  React.useEffect(() => {
    if (!isModeMenuOpen) {
      return undefined;
    }
    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target;
      if (target instanceof Node && modeMenuRef.current?.contains(target)) {
        return;
      }
      setIsModeMenuOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsModeMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isModeMenuOpen]);

  React.useEffect(() => {
    if (!isWorkspaceMode) {
      return;
    }
    const session = AuthStorage.getSession();
    if (!session?.token) {
      setWorkspaceDiffError('登录状态失效，无法读取工作区差异');
      setWorkspaceDiffs([]);
      setWorkspaceSummary(undefined);
      return;
    }
    let cancelled = false;
    const loadWorkspaceDiff = async () => {
      setIsLoadingWorkspaceDiff(true);
      setWorkspaceDiffError('');
      try {
        const result = await ChatApi.invokeTool(session.token, 'git_diff', {
          mode: activeMode,
        }, {
          workspaceId,
          repositoryPath,
        });
        if (cancelled) {
          return;
        }
        const nextFileDiffs = normalizeFileDiffsFromMetadata(result.metadata);
        setWorkspaceDiffs(nextFileDiffs);
        setWorkspaceSummary(normalizeDiffSummaryFromMetadata(result.metadata, nextFileDiffs));
      } catch (error) {
        if (cancelled) {
          return;
        }
        setWorkspaceDiffs([]);
        setWorkspaceSummary(undefined);
        setWorkspaceDiffError(error instanceof Error ? error.message : '读取工作区差异失败');
      } finally {
        if (!cancelled) {
          setIsLoadingWorkspaceDiff(false);
        }
      }
    };
    void loadWorkspaceDiff();
    return () => {
      cancelled = true;
    };
  }, [activeMode, isWorkspaceMode, repositoryPath, workspaceId]);

  return (
    <div
      data-testid="code-review-sidebar"
      aria-label="代码差异审查栏"
      className="flex h-full min-h-0 flex-col bg-surface text-foreground"
    >
      <div className="border-b border-border px-4 py-3">
        <div className="flex min-w-0 items-center justify-between gap-3">
          <div ref={modeMenuRef} className="relative min-w-0">
            <button
              type="button"
              data-testid="code-review-mode-menu-button"
              aria-haspopup="menu"
              aria-expanded={isModeMenuOpen}
              aria-label={`选择差异类型：${activeModeItem.label}`}
              onClick={() => setIsModeMenuOpen((current) => !current)}
              className="inline-flex h-9 max-w-full items-center gap-2 rounded-lg bg-background px-3 text-sm font-medium text-foreground transition-colors hover:bg-surface-container focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border"
            >
              <span className="truncate">{activeModeItem.label}</span>
              <ChevronDown
                size={14}
                className={`shrink-0 text-muted transition-transform ${isModeMenuOpen ? 'rotate-180' : ''}`}
              />
            </button>
            {isModeMenuOpen ? (
              <div
                role="menu"
                aria-label="差异类型"
                className="absolute left-0 top-10 z-20 w-52 rounded-lg border border-border bg-surface p-1.5 text-xs text-foreground shadow-[0_18px_44px_rgba(0,0,0,0.28)]"
              >
                {CODE_REVIEW_MODES.map((item) => (
                  <button
                    key={item.mode}
                    type="button"
                    role="menuitemradio"
                    aria-checked={activeMode === item.mode}
                    onClick={() => selectCodeReviewMode(item.mode)}
                    className={`flex w-full items-center justify-between gap-3 rounded-md px-2.5 py-2 text-left transition-colors ${
                      activeMode === item.mode
                        ? 'bg-surface-container text-foreground'
                        : 'text-muted hover:bg-surface-container hover:text-foreground'
                    }`}
                  >
                    <span>{item.label}</span>
                    {activeMode === item.mode ? <CheckCircle2 size={13} aria-hidden="true" /> : null}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          <div className="flex shrink-0 items-center gap-2 text-sm">
            <span className="font-mono text-success">+{visibleSummary.additions}</span>
            <span className="font-mono text-error">-{visibleSummary.deletions}</span>
          </div>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-muted">
          <span>{visibleSummary.filesChanged} 个文件</span>
          <span>{isWorkspaceMode ? 'Git 工作区差异' : '会话文件差异'}</span>
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-hidden">
        {isLoadingWorkspaceDiff ? (
          <div className="px-4 py-4 text-sm text-muted">正在读取 git diff...</div>
        ) : workspaceDiffError ? (
          <div className="mx-4 mt-4 rounded-md border border-error/30 bg-error/10 px-3 py-2 text-sm text-error">
            {workspaceDiffError}
          </div>
        ) : visibleFileDiffs.length === 0 ? (
          <div className="px-4 py-4 text-sm text-muted">当前模式暂无文件差异</div>
        ) : (
          <div className="grid h-full grid-rows-[auto_1fr]">
            <div className="space-y-1 border-b border-border px-3 py-3">
              {visibleFileDiffs.map((fileDiff) => (
                <button
                  key={getFileDiffStableKey(fileDiff)}
                  type="button"
                  onClick={() => setActivePath(fileDiff.path)}
                  className={`flex w-full min-w-0 items-center justify-between gap-2 rounded-md px-2 py-1.5 text-left text-xs transition-colors ${
                    activeFileDiff?.path === fileDiff.path
                      ? 'bg-surface-container text-foreground'
                      : 'text-muted hover:bg-surface-container hover:text-foreground'
                  }`}
                >
                  <span className="truncate font-mono" title={fileDiff.path}>{fileDiff.path}</span>
                  <span className="shrink-0 font-mono">
                    <span className="text-success">+{fileDiff.additions}</span>
                    <span className="ml-2 text-error">-{fileDiff.deletions}</span>
                  </span>
                </button>
              ))}
            </div>
            {activeFileDiff ? (
              <AutoScrollingDiffBlock
                diffText={activeFileDiff.diff || '等待写入内容...'}
                isStreamingPreview={activeFileDiff.status === 'pending'}
                className="min-h-0 overflow-auto"
                testId="code-review-sidebar-diff-scroll"
              />
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
}
