import React from 'react';
import { ChevronRight, File, Folder, FolderOpen, Search } from 'lucide-react';
import { resolveHostBridge } from '../../../host/bridge';
import { LocalDirectoryEntry } from '../../../host/types';

/**
 * 资源管理器式文件面板：目录来自桌面宿主桥接，文件正文通过只读文本预览加载。
 * 顶部直接展示当前选中文件的完整路径，内容区不再包裹卡片边框，正文铺满面板。
 */
export function FileWorkbenchPanel({
  workspacePath,
  onPickRepositoryDirectory,
}: {
  workspacePath: string | null;
  onPickRepositoryDirectory: () => Promise<void>;
}) {
  const hostBridge = React.useMemo(() => resolveHostBridge(), []);
  const [currentDirectory, setCurrentDirectory] = React.useState(workspacePath ?? '');
  const [directoryEntries, setDirectoryEntries] = React.useState<LocalDirectoryEntry[]>([]);
  const [selectedFilePath, setSelectedFilePath] = React.useState('');
  const [selectedFileContent, setSelectedFileContent] = React.useState('');
  const [isDirectoryVisible, setIsDirectoryVisible] = React.useState(true);
  const [fileSearchKeyword, setFileSearchKeyword] = React.useState('');
  const [openWith, setOpenWith] = React.useState('codingx');
  const [isLoadingDirectory, setIsLoadingDirectory] = React.useState(false);
  const [isLoadingFile, setIsLoadingFile] = React.useState(false);
  const [fileError, setFileError] = React.useState('');

  React.useEffect(() => {
    setCurrentDirectory(workspacePath ?? '');
    setSelectedFilePath('');
    setSelectedFileContent('');
  }, [workspacePath]);

  React.useEffect(() => {
    if (!currentDirectory) {
      setDirectoryEntries([]);
      return;
    }
    let cancelled = false;
    const loadDirectory = async () => {
      setIsLoadingDirectory(true);
      setFileError('');
      try {
        const entries = await hostBridge.listDirectory(currentDirectory);
        if (cancelled) {
          return;
        }
        setDirectoryEntries(entries.slice().sort(sortDirectoryEntries));
      } catch (error) {
        if (!cancelled) {
          setDirectoryEntries([]);
          setFileError(error instanceof Error ? error.message : '读取目录失败');
        }
      } finally {
        if (!cancelled) {
          setIsLoadingDirectory(false);
        }
      }
    };
    void loadDirectory();
    return () => {
      cancelled = true;
    };
  }, [currentDirectory, hostBridge]);

  const filteredEntries = React.useMemo(() => {
    const keyword = fileSearchKeyword.trim().toLowerCase();
    if (!keyword) {
      return directoryEntries;
    }
    return directoryEntries.filter((entry) => entry.name.toLowerCase().includes(keyword));
  }, [directoryEntries, fileSearchKeyword]);

  const openDirectoryEntry = React.useCallback(async (entry: LocalDirectoryEntry) => {
    if (entry.entryType === 'directory') {
      setCurrentDirectory(entry.path);
      return;
    }
    setSelectedFilePath(entry.path);
    setIsLoadingFile(true);
    setFileError('');
    try {
      const fileContent = await hostBridge.readTextFile(entry.path);
      setSelectedFileContent(fileContent.content);
    } catch (error) {
      setSelectedFileContent('');
      setFileError(normalizeFileWorkbenchError(error, '读取文件失败'));
    } finally {
      setIsLoadingFile(false);
    }
  }, [hostBridge]);

  return (
    <div
      data-testid="file-workbench-panel"
      className="grid h-full min-h-0 grid-rows-[auto_1fr] bg-surface text-foreground"
    >
      <div className="border-b border-border px-4 py-3">
        <div className="flex min-w-0 items-center justify-between gap-3">
          {/* 顶部直接展示选中文件完整路径，未选择文件时给出占位提示，替代原工作区面包屑。 */}
          <div
            className={`min-w-0 flex-1 truncate font-mono text-sm ${
              selectedFilePath ? 'font-semibold text-foreground' : 'text-muted'
            }`}
            title={selectedFilePath || undefined}
          >
            {selectedFilePath || '未选择文件'}
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <select
              aria-label="选择打开方式"
              value={openWith}
              onChange={(event) => setOpenWith(event.target.value)}
              className="h-9 rounded-lg border border-border bg-background px-2 text-sm text-foreground outline-none"
            >
              <option value="codingx">CodingX 打开</option>
              <option value="system">系统默认软件</option>
              <option value="editor">代码编辑器</option>
            </select>
            <button
              type="button"
              aria-label={isDirectoryVisible ? '折叠文件目录' : '展开文件目录'}
              aria-pressed={isDirectoryVisible}
              onClick={() => setIsDirectoryVisible((current) => !current)}
              className="inline-flex h-9 w-9 items-center justify-center rounded-lg bg-background text-muted transition-colors hover:bg-surface-container hover:text-foreground"
            >
              <Folder size={16} />
            </button>
          </div>
        </div>
      </div>
      {!workspacePath ? (
        <div className="flex h-full flex-col items-center justify-center gap-3 px-8 text-center">
          <FolderOpen size={28} className="text-muted" />
          <div className="text-base font-semibold">还没有绑定本地工作区</div>
          <button
            type="button"
            onClick={() => void onPickRepositoryDirectory()}
            className="rounded-lg bg-foreground px-4 py-2 text-sm font-medium text-background"
          >
            选择本地目录
          </button>
        </div>
      ) : (
        <div
          className={`grid min-h-0 ${
            isDirectoryVisible ? 'grid-cols-[minmax(0,1fr)_240px]' : 'grid-cols-1'
          }`}
        >
          {/* 内容区不再包裹卡片边框：正文铺满面板，错误与加载提示按需展示。 */}
          <div className="grid min-h-0 grid-rows-[auto_1fr]">
            {fileError ? (
              <div className="mx-5 mt-4 rounded-lg border border-error/30 bg-error/10 px-3 py-2 text-sm text-error">
                {fileError}
              </div>
            ) : (
              <div />
            )}
            {selectedFilePath ? (
              isLoadingFile ? (
                <div className="px-5 py-4 text-sm text-muted">正在加载文件内容...</div>
              ) : (
                <pre className="min-h-0 overflow-auto whitespace-pre-wrap [overflow-wrap:anywhere] px-5 py-4 font-mono text-xs leading-5 text-foreground">
                  {selectedFileContent || '选择右侧目录中的文件后显示内容。'}
                </pre>
              )
            ) : (
              <div className="px-5 py-4 text-sm leading-6 text-muted">
                从右侧文件目录选择一个文件后，内容会在这里显示。目录可通过右上角文件夹按钮折叠。
              </div>
            )}
          </div>
          {isDirectoryVisible ? (
            <div
              data-testid="file-workbench-directory"
              className="min-h-0 border-l border-border bg-background px-3 py-3"
            >
              <label className="relative block">
                <Search size={14} className="pointer-events-none absolute left-3 top-2.5 text-muted" />
                <input
                  aria-label="筛选文件"
                  value={fileSearchKeyword}
                  onChange={(event) => setFileSearchKeyword(event.target.value)}
                  placeholder="筛选文件..."
                  className="h-9 w-full rounded-lg border border-border bg-surface pl-9 pr-3 text-sm text-foreground outline-none placeholder:text-muted"
                />
              </label>
              <div className="mt-3 max-h-full space-y-1 overflow-auto">
                {isLoadingDirectory ? (
                  <div className="px-2 py-2 text-sm text-muted">正在加载目录...</div>
                ) : filteredEntries.length === 0 ? (
                  <div className="px-2 py-2 text-sm text-muted">没有匹配的文件</div>
                ) : (
                  filteredEntries.map((entry) => (
                    <button
                      key={entry.path}
                      type="button"
                      aria-label={`${entry.entryType === 'directory' ? '打开目录' : '打开文件'} ${entry.name}`}
                      onClick={() => void openDirectoryEntry(entry)}
                      className={`flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left text-sm transition-colors ${
                        selectedFilePath === entry.path
                          ? 'bg-surface-container text-foreground'
                          : 'text-muted hover:bg-surface-container hover:text-foreground'
                      }`}
                    >
                      {entry.entryType === 'directory' ? <ChevronRight size={14} /> : <File size={14} />}
                      <span className="truncate">{entry.name}</span>
                    </button>
                  ))
                )}
              </div>
            </div>
          ) : null}
        </div>
      )}
    </div>
  );
}

/**
 * 将桌面端 IPC 原始错误转成用户可理解的文件面板提示，尤其覆盖旧桌面进程未加载新 handler 的场景。
 */
function normalizeFileWorkbenchError(error: unknown, fallbackMessage: string) {
  const rawMessage = error instanceof Error ? error.message : '';
  if (rawMessage.includes('No handler registered') && rawMessage.includes('host:read-text-file')) {
    return '桌面端文件预览能力未加载，请重启 CodingX 后重试。';
  }
  return rawMessage || fallbackMessage;
}

/**
 * 目录项按“目录优先、名称升序”展示，贴近桌面资源管理器扫描习惯。
 */
function sortDirectoryEntries(left: LocalDirectoryEntry, right: LocalDirectoryEntry) {
  if (left.entryType !== right.entryType) {
    return left.entryType === 'directory' ? -1 : 1;
  }
  return left.name.localeCompare(right.name, 'zh-Hans-CN');
}
