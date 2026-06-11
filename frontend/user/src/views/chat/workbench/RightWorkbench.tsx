import React from 'react';
import { FileText, FolderOpen, Globe2, PanelRightClose, Plus, X } from 'lucide-react';
import { ChatMessageItem } from '../types';
import { FileWorkbenchPanel } from './FileWorkbenchPanel';
import { BrowserWorkbenchPanel } from './BrowserWorkbenchPanel';
import { CodeReviewPanel } from './CodeReviewPanel';

/** 右侧工作台默认宽度（px），ChatView 用它初始化侧栏宽度 state。 */
export const RIGHT_WORKBENCH_DEFAULT_WIDTH = 420;
/** 拖拽调宽下限，保证面板内容不被压缩到不可用。 */
const RIGHT_WORKBENCH_MIN_WIDTH = 340;
/** 拖拽调宽上限，避免工作台吞掉聊天主区。 */
const RIGHT_WORKBENCH_MAX_WIDTH = 760;

/** 工作台支持的工具面板类型。 */
type RightWorkbenchTool = 'files' | 'browser' | 'review';

/** 工作台顶部选项卡：同一工具最多保留一个 Tab。 */
type RightWorkbenchTab = {
  id: string;
  tool: RightWorkbenchTool;
  title: string;
};

/** 工作台工具注册表：启动器卡片与添加菜单共用同一份定义。 */
const RIGHT_WORKBENCH_TOOLS: Array<{
  tool: RightWorkbenchTool;
  title: string;
  description: string;
  shortcut: string;
  icon: React.ComponentType<{ size?: number; className?: string }>;
}> = [
  { tool: 'files', title: '文件', description: '浏览项目文件', shortcut: 'Ctrl+P', icon: FolderOpen },
  { tool: 'browser', title: '浏览器', description: '打开网站', shortcut: 'Ctrl+T', icon: Globe2 },
  { tool: 'review', title: '审查', description: '查看代码更改', shortcut: 'Ctrl+Shift+G', icon: FileText },
];

/**
 * 右侧工作台承载文件、浏览器和代码审查多个面板；默认入口参考用户提供的桌面端截图。
 */
export function RightWorkbenchSidebar({
  messages,
  width,
  workspaceLabel,
  workspaceId,
  workspacePath,
  repositoryPath,
  onPickRepositoryDirectory,
  onClose,
  onResize,
}: {
  messages: ChatMessageItem[];
  width: number;
  workspaceLabel: string;
  workspaceId: string | null;
  workspacePath: string | null;
  repositoryPath: string | null;
  onPickRepositoryDirectory: () => Promise<void>;
  onClose: () => void;
  onResize: (nextWidth: number) => void;
}) {
  const [tabs, setTabs] = React.useState<RightWorkbenchTab[]>([]);
  const [activeTabId, setActiveTabId] = React.useState('');
  const [isAddMenuOpen, setIsAddMenuOpen] = React.useState(false);
  const addMenuRef = React.useRef<HTMLDivElement | null>(null);
  const activeTab = tabs.find((tab) => tab.id === activeTabId) ?? null;

  const openTool = React.useCallback((tool: RightWorkbenchTool) => {
    const toolDefinition = RIGHT_WORKBENCH_TOOLS.find((item) => item.tool === tool);
    if (!toolDefinition) {
      return;
    }
    const existingTab = tabs.find((tab) => tab.tool === tool);
    if (existingTab) {
      setActiveTabId(existingTab.id);
      setIsAddMenuOpen(false);
      return;
    }
    const nextTab = {
      id: `${tool}-${Date.now()}`,
      tool,
      title: toolDefinition.title,
    };
    setTabs((currentTabs) => (
      currentTabs.some((tab) => tab.id === nextTab.id) ? currentTabs : [...currentTabs, nextTab]
    ));
    setActiveTabId(nextTab.id);
    setIsAddMenuOpen(false);
  }, [tabs]);

  const closeTab = React.useCallback((tabId: string) => {
    setTabs((currentTabs) => {
      const closingIndex = currentTabs.findIndex((tab) => tab.id === tabId);
      const nextTabs = currentTabs.filter((tab) => tab.id !== tabId);
      if (activeTabId === tabId) {
        const nextActiveTab = nextTabs[Math.max(0, closingIndex - 1)] ?? nextTabs[0] ?? null;
        setActiveTabId(nextActiveTab?.id ?? '');
      }
      return nextTabs;
    });
  }, [activeTabId]);

  const clampSidebarWidth = React.useCallback((nextWidth: number) => {
    const viewportLimit =
      typeof window === 'undefined'
        ? RIGHT_WORKBENCH_MAX_WIDTH
        : Math.max(RIGHT_WORKBENCH_MIN_WIDTH, window.innerWidth - 520);
    return Math.min(
      Math.max(nextWidth, RIGHT_WORKBENCH_MIN_WIDTH),
      Math.min(RIGHT_WORKBENCH_MAX_WIDTH, viewportLimit),
    );
  }, []);

  const startResize = React.useCallback(
    (event: React.MouseEvent<HTMLButtonElement>) => {
      event.preventDefault();
      const startX = event.clientX;
      const startWidth = width;
      const handleMouseMove = (moveEvent: MouseEvent) => {
        // 右侧固定面板向左拖宽、向右拖窄；宽度仍受视口和最大值限制。
        onResize(clampSidebarWidth(startWidth - (moveEvent.clientX - startX)));
      };
      const stopResize = () => {
        window.removeEventListener('mousemove', handleMouseMove);
        window.removeEventListener('mouseup', stopResize);
      };
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', stopResize);
    },
    [clampSidebarWidth, onResize, width],
  );

  React.useEffect(() => {
    if (!isAddMenuOpen) {
      return undefined;
    }
    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target;
      if (target instanceof Node && addMenuRef.current?.contains(target)) {
        return;
      }
      setIsAddMenuOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsAddMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isAddMenuOpen]);

  return (
    <aside
      data-testid="right-workbench-sidebar"
      aria-label="右侧工作台"
      className="relative hidden h-full shrink-0 border-l border-border bg-surface text-foreground shadow-[-18px_0_46px_rgba(0,0,0,0.18)] md:flex md:flex-col"
      style={{ width }}
    >
      <button
        type="button"
        data-testid="code-review-sidebar-resize-handle"
        role="separator"
        aria-label="调整右侧工作台宽度"
        aria-orientation="vertical"
        onMouseDown={startResize}
        className="absolute -left-1 top-0 z-10 h-full w-2 cursor-col-resize border-l border-transparent transition-colors hover:border-foreground/40 focus-visible:border-foreground focus-visible:outline-none"
      />
      <div className="flex h-14 shrink-0 items-center gap-2 border-b border-border px-3">
        {tabs.length > 0 ? (
          <div className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto">
            {tabs.map((tab) => {
              const toolDefinition = RIGHT_WORKBENCH_TOOLS.find((item) => item.tool === tab.tool);
              const TabIcon = toolDefinition?.icon ?? FileText;
              return (
                <button
                  key={tab.id}
                  type="button"
                  aria-label={`切换到${tab.title}选项卡`}
                  aria-pressed={activeTabId === tab.id}
                  onClick={() => setActiveTabId(tab.id)}
                  className={`inline-flex h-9 max-w-[160px] shrink-0 items-center gap-2 rounded-lg px-3 text-sm transition-colors ${
                    activeTabId === tab.id
                      ? 'bg-surface-container text-foreground'
                      : 'text-muted hover:bg-surface-container hover:text-foreground'
                  }`}
                >
                  <TabIcon size={15} />
                  <span className="truncate">{tab.title}</span>
                  <span
                    role="button"
                    tabIndex={0}
                    aria-label={`关闭${tab.title}选项卡`}
                    onClick={(event) => {
                      event.stopPropagation();
                      closeTab(tab.id);
                    }}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        event.stopPropagation();
                        closeTab(tab.id);
                      }
                    }}
                    className="rounded p-0.5 text-muted hover:bg-background hover:text-foreground"
                  >
                    <X size={12} />
                  </span>
                </button>
              );
            })}
          </div>
        ) : (
          <div className="min-w-0 flex-1 text-sm font-semibold text-foreground">工作台</div>
        )}
        <div ref={addMenuRef} className="relative">
          <button
            type="button"
            aria-label="添加侧栏选项卡"
            aria-haspopup="menu"
            aria-expanded={isAddMenuOpen}
            onClick={() => setIsAddMenuOpen((current) => !current)}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-container hover:text-foreground"
          >
            <Plus size={17} />
          </button>
          {isAddMenuOpen ? (
            <div
              role="menu"
              aria-label="添加工作台功能"
              className="absolute right-0 top-10 z-30 w-64 rounded-xl border border-border bg-surface p-2 text-sm text-foreground shadow-[0_18px_44px_rgba(0,0,0,0.28)]"
            >
              {RIGHT_WORKBENCH_TOOLS.map((item) => {
                const ToolIcon = item.icon;
                return (
                  <button
                    key={item.tool}
                    type="button"
                    role="menuitem"
                    onClick={() => openTool(item.tool)}
                    className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors hover:bg-surface-container"
                  >
                    <ToolIcon size={17} className="text-muted" />
                    <span className="min-w-0 flex-1">{item.title}</span>
                    <span className="font-mono text-xs text-muted">{item.shortcut}</span>
                  </button>
                );
              })}
            </div>
          ) : null}
        </div>
        <button
          type="button"
          aria-label="关闭右侧工作台"
          onClick={onClose}
          className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-container hover:text-foreground"
        >
          <PanelRightClose size={17} />
        </button>
      </div>
      <div className="min-h-0 flex-1 overflow-hidden">
        {!activeTab ? (
          <RightWorkbenchLauncher onOpenTool={openTool} />
        ) : activeTab.tool === 'review' ? (
          <CodeReviewPanel
            messages={messages}
            workspaceId={workspaceId}
            repositoryPath={repositoryPath}
          />
        ) : activeTab.tool === 'files' ? (
          <FileWorkbenchPanel
            workspaceLabel={workspaceLabel}
            workspacePath={workspacePath}
            onPickRepositoryDirectory={onPickRepositoryDirectory}
          />
        ) : (
          <BrowserWorkbenchPanel workspaceLabel={workspaceLabel} />
        )}
      </div>
    </aside>
  );
}

/**
 * 工作台默认入口，按用户参考图提供文件、浏览器和审查三个主功能。
 */
function RightWorkbenchLauncher({
  onOpenTool,
}: {
  onOpenTool: (tool: RightWorkbenchTool) => void;
}) {
  return (
    <div
      data-testid="right-workbench-launcher"
      className="flex h-full flex-col justify-center gap-4 overflow-auto px-8 py-8"
    >
      {RIGHT_WORKBENCH_TOOLS.map((item) => {
        const ToolIcon = item.icon;
        return (
          <button
            key={item.tool}
            type="button"
            aria-label={`打开${item.title}面板`}
            onClick={() => onOpenTool(item.tool)}
            className="flex min-h-[128px] flex-col items-center justify-center gap-3 rounded-lg border border-transparent bg-background text-center transition-colors hover:border-border-active hover:bg-surface-container"
          >
            <ToolIcon size={25} className="text-muted" />
            <span className="text-base font-semibold text-foreground">{item.title}</span>
            <span className="text-sm text-muted">{item.description}</span>
            <span className="rounded-full bg-surface-container px-2 py-0.5 font-mono text-[11px] text-muted">
              {item.shortcut}
            </span>
          </button>
        );
      })}
    </div>
  );
}
