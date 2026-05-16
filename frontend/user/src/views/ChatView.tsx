import React from 'react';
import { motion } from 'motion/react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  ArrowUp,
  Check,
  ChevronDown,
  CheckCircle2,
  CircleStop,
  Copy,
  Database,
  FileText,
  Globe2,
  PanelRightClose,
  PanelRightOpen,
  Paperclip,
  FolderOpen,
  Search,
  ThumbsDown,
  ThumbsUp,
  WandSparkles,
  X,
} from 'lucide-react';
import { ChatWorkspaceController, McpCallItem } from './chat/types';

/**
 * 定义聊天视图的输入属性。
 */
interface ChatViewProps {
  isAuthenticated: boolean;
  isDesktopSidebarCollapsed?: boolean;
  onRequireLogin: () => void;
  workspace: ChatWorkspaceController;
}

/**
 * 渲染接入真实后端数据的聊天三栏工作台。
 */
export default function ChatView({
  isAuthenticated,
  isDesktopSidebarCollapsed = false,
  onRequireLogin,
  workspace,
}: ChatViewProps) {
  // 步骤：统一抽出底部输入区预留高度，避免滚动容器与绝对定位输入区叠加时出现尾部滚动错位。
  const CHAT_INPUT_SAFE_BOTTOM_CLASS = 'pb-36 md:pb-40';
  const {
    activeConversationId,
    messages,
    executionSteps,
    references,
    artifacts,
    sampleQuestions,
    availableMcps,
    selectedMcpCodes,
    mcpConnected,
    isStreaming,
    isCancelling,
    deepThinkingEnabled,
    streamError,
    inputValue,
    isBootstrapping,
    setInputValue,
    setDeepThinkingEnabled,
    setSelectedMcpCodes,
    setMcpConnected,
    submitMessage,
    cancelCurrentStream,
    renameDialog,
    deleteDialog,
    renameConversation,
    deleteConversation,
  } = workspace;
  const latestMessageAnchorRef = React.useRef<HTMLDivElement | null>(null);
  // 步骤：右侧工作区默认折叠，仅在存在真实回放内容时自动展开一次，后续允许用户手动控制。
  const [isWorkspacePanelCollapsed, setIsWorkspacePanelCollapsed] = React.useState(true);
  const [highlightedMcpIndex, setHighlightedMcpIndex] = React.useState(0);

  /**
   * 解析输入中的斜杠命令查询串；非斜杠模式时返回 null。
   * @param value 输入框原始值。
   * @returns 斜杠查询串。
   */
  const resolveSlashQuery = React.useCallback((value: string) => {
    const trimmedStart = value.trimStart();
    if (!trimmedStart.startsWith('/')) {
      return null;
    }
    return trimmedStart.slice(1);
  }, []);

  const slashQuery = resolveSlashQuery(inputValue);
  const isMcpPanelOpen = slashQuery !== null;
  const normalizedSlashQuery = (slashQuery ?? '').trim().toLowerCase();
  const filteredMcps = React.useMemo(
    () => availableMcps.filter((mcp) => {
      if (!normalizedSlashQuery) {
        return true;
      }
      return (
        mcp.displayName.toLowerCase().includes(normalizedSlashQuery)
        || mcp.mcpCode.toLowerCase().includes(normalizedSlashQuery)
      );
    }),
    [availableMcps, normalizedSlashQuery],
  );

  React.useEffect(() => {
    if (!isMcpPanelOpen || filteredMcps.length === 0) {
      setHighlightedMcpIndex(0);
      return;
    }
    setHighlightedMcpIndex((current) => {
      if (current >= filteredMcps.length) {
        return filteredMcps.length - 1;
      }
      return current;
    });
  }, [filteredMcps.length, isMcpPanelOpen]);

  /**
   * 将当前高亮 MCP 加入已选列表并关闭斜杠面板。
   * @param mcpCode 被选择 MCP 编码。
   */
  const selectMcp = React.useCallback((mcpCode: string) => {
    setSelectedMcpCodes((previous) => {
      if (previous.includes(mcpCode)) {
        return previous;
      }
      return [...previous, mcpCode];
    });
    setMcpConnected(true);
    setInputValue('');
    setHighlightedMcpIndex(0);
  }, [setInputValue, setSelectedMcpCodes, setMcpConnected]);

  /**
   * 删除一个已选 MCP 气泡。
   * @param mcpCode MCP 编码。
   */
  const removeSelectedMcp = React.useCallback((mcpCode: string) => {
    setSelectedMcpCodes((previous) => previous.filter((item) => item !== mcpCode));
  }, [setSelectedMcpCodes]);

  const selectedMcps = React.useMemo(
    () => selectedMcpCodes
      .map((code) => availableMcps.find((item) => item.mcpCode === code))
      .filter((mcp): mcp is NonNullable<typeof mcp> => mcp != null),
    [availableMcps, selectedMcpCodes],
  );

  React.useEffect(() => {
    if (!messages.length) {
      return;
    }
    if (typeof latestMessageAnchorRef.current?.scrollIntoView === 'function') {
      latestMessageAnchorRef.current.scrollIntoView({ block: 'end' });
    }
  }, [messages]);

  /**
   * 统一处理底部输入提交。
   * @param event 表单事件。
   */
  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (isMcpPanelOpen) {
      if (filteredMcps.length > 0) {
        const targetMcp = filteredMcps[Math.min(highlightedMcpIndex, filteredMcps.length - 1)];
        selectMcp(targetMcp.mcpCode);
      }
      return;
    }
    if (!inputValue.trim()) {
      return;
    }
    if (!isAuthenticated) {
      onRequireLogin();
      return;
    }
    await submitMessage();
  };

  // 步骤：仅当没有选中任何真实会话时才展示“新建对话”首页；避免空历史会话被误判为未跳转。
  const showLandingState = activeConversationId == null && !messages.length && !isBootstrapping;
  // 步骤：选中了历史会话但暂无消息时，显示会话级空态而不是回到首页。
  const showConversationEmptyState = activeConversationId != null && !messages.length && !isBootstrapping;
  // 步骤：首页只保留欢迎内容和输入框，右侧执行回放仅在真实会话上下文中展示。
  const showWorkspacePanel = !showLandingState;
  // 步骤：仅当步骤、来源或产物任一存在时，才认为右侧栏具备真实回放内容。
  const hasWorkspaceContent = executionSteps.length > 0 || references.length > 0 || artifacts.length > 0;
  // 步骤：右侧栏保留挂载以支持宽度过渡动画，面板内容在收起后不再渲染。
  const isWorkspacePanelVisible = showWorkspacePanel && !isWorkspacePanelCollapsed;

  React.useEffect(() => {
    if (hasWorkspaceContent) {
      setIsWorkspacePanelCollapsed(false);
    }
  }, [hasWorkspaceContent]);

  /**
   * 处理输入框中的斜杠 MCP 快捷键交互。
   * @param event 键盘事件。
   */
  const handleInputKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (!isMcpPanelOpen) {
      return;
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (!filteredMcps.length) {
        return;
      }
      setHighlightedMcpIndex((current) => (current + 1) % filteredMcps.length);
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (!filteredMcps.length) {
        return;
      }
      setHighlightedMcpIndex((current) => (current - 1 + filteredMcps.length) % filteredMcps.length);
      return;
    }
    if (event.key === 'Escape') {
      event.preventDefault();
      setInputValue('');
      return;
    }
    if (event.key === 'Enter' && filteredMcps.length > 0) {
      event.preventDefault();
      const targetMcp = filteredMcps[Math.min(highlightedMcpIndex, filteredMcps.length - 1)];
      selectMcp(targetMcp.mcpCode);
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="relative flex h-full overflow-hidden bg-background"
    >
      <section className="relative flex min-w-0 flex-1 flex-col overflow-hidden">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(71,96,122,0.16),transparent_38%),radial-gradient(circle_at_80%_18%,rgba(188,75,0,0.12),transparent_26%)]" />
        <div className="absolute left-4 right-4 top-4 z-20 hidden items-center justify-between md:flex">
          <div className={`${isDesktopSidebarCollapsed ? '' : 'w-10'}`}>
            {/* 步骤：左上角预留壳层折叠按钮占位，避免与主内容视觉挤压；真实交互由 App 壳层负责。 */}
          </div>
          {showWorkspacePanel ? (
            <button
              type="button"
              aria-label={isWorkspacePanelCollapsed ? '展开右侧工作区' : '折叠右侧工作区'}
              onClick={() => setIsWorkspacePanelCollapsed((current) => !current)}
              className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-xl border border-border bg-surface/92 text-foreground shadow-[0_14px_30px_rgba(0,0,0,0.18)] backdrop-blur"
            >
              {isWorkspacePanelCollapsed ? <PanelRightOpen size={18} /> : <PanelRightClose size={18} />}
            </button>
          ) : (
            <div className="w-10" />
          )}
        </div>
        <div
          data-testid="chat-scroll-region"
          className={`chat-scroll-region relative flex-1 overflow-y-auto px-4 pt-6 md:px-8 md:pt-20 ${CHAT_INPUT_SAFE_BOTTOM_CLASS}`}
          style={{ scrollPaddingTop: '96px', scrollPaddingBottom: '160px' }}
        >
          {showLandingState ? (
            <div className="mx-auto flex max-w-4xl flex-col items-center justify-center px-6 py-16 text-center">
              <h1 className="max-w-3xl text-4xl font-semibold tracking-tight text-foreground md:text-6xl">
                你好，我是 CodingX
              </h1>
              <div className="mt-10 grid w-full max-w-3xl gap-4 md:grid-cols-2">
                {(sampleQuestions.length
                  ? sampleQuestions.map((item, index) => ({
                      icon: [Globe2, Search, Database, FolderOpen][index % 4],
                      title: item.category || '示例问题',
                      desc: item.questionText,
                      iconClassName: ['text-[#8fb3da]', 'text-[#ff8a24]', 'text-[#a78bfa]', 'text-[#f4f4f5]'][index % 4],
                      dataSource: 'api',
                    }))
                  : [
                      { icon: Globe2, title: '网页读取', desc: '解析并总结外部网页内容', iconClassName: 'text-[#8fb3da]', dataSource: 'fallback' },
                      { icon: Search, title: '调研分析', desc: '深度搜索并生成研究报告', iconClassName: 'text-[#ff8a24]', dataSource: 'fallback' },
                      { icon: Database, title: '数据挖掘', desc: '结构化数据提取与清洗', iconClassName: 'text-[#a78bfa]', dataSource: 'fallback' },
                      { icon: FolderOpen, title: '文件管理', desc: '上传并与您的文档进行对话', iconClassName: 'text-[#f4f4f5]', dataSource: 'fallback' },
                    ]).map((item) => (
                  <button
                    key={`${item.title}-${item.desc}`}
                    type="button"
                    data-source={item.dataSource}
                    onClick={() => setInputValue(item.desc)}
                    className="rounded-[22px] border border-border bg-surface px-6 py-5 text-left shadow-sm transition-colors hover:border-border-active hover:bg-surface-container"
                  >
                    <div className="flex items-center gap-4">
                      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#2b2b31]">
                        <item.icon size={22} className={item.iconClassName} />
                      </div>
                      <div>
                        <div className="text-2xl font-semibold text-foreground">{item.title}</div>
                        <div className="mt-1 text-sm leading-6 text-muted">{item.desc}</div>
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          ) : showConversationEmptyState ? (
            <div className="mx-auto flex max-w-3xl flex-col items-center justify-center px-6 py-24 text-center">
              <div className="rounded-full border border-border bg-surface-container px-4 py-2 font-mono text-[11px] uppercase tracking-[0.32em] text-muted">
                Conversation
              </div>
              <h2 className="mt-6 text-3xl font-semibold tracking-tight text-foreground">
                当前会话暂无消息
              </h2>
              <p className="mt-4 max-w-xl text-sm leading-7 text-muted">
                这个历史会话还没有可回放内容。你可以直接在下方输入，继续往当前会话追加新的对话。
              </p>
            </div>
          ) : (
            <div className="mx-auto flex max-w-4xl flex-col gap-6">
              {messages.map((message) => {
                const isAssistant = message.role === 'ASSISTANT';
                const isLatestMessage = message.id === messages[messages.length - 1]?.id;
                const messageContent = message.content || (message.status === 'streaming' ? '正在生成回答...' : '');
                return (
                  <div
                    key={message.id}
                    className={`flex ${isAssistant ? 'justify-start' : 'justify-end'}`}
                  >
                    <div
                      className={`min-w-0 max-w-3xl px-1 py-1 ${
                        isAssistant
                          ? 'text-foreground'
                          : 'rounded-[20px] bg-foreground px-4 py-2 text-background'
                      }`}
                    >
                      {isAssistant ? (
                        <>
                          {message.thinkingContent ? (
                            <ThinkingPanel messageId={message.id} content={message.thinkingContent} />
                          ) : null}
                          {message.mcpCalls && message.mcpCalls.length > 0 ? (
                            <McpCallPanel messageId={message.id} calls={message.mcpCalls} />
                          ) : null}
                          <MarkdownMessage content={messageContent} />
                          <AssistantMessageActions messageId={message.id} content={messageContent} />
                        </>
                      ) : (
                        <div className="whitespace-pre-wrap text-sm leading-6">
                          {messageContent}
                        </div>
                      )}
                      {message.errorMessage ? (
                        <div className="mt-3 rounded-2xl border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs text-red-300">
                          {message.errorMessage}
                        </div>
                      ) : null}
                      {isLatestMessage ? (
                        // 步骤：尾部锚点固定高度为 0，确保滚动只对齐到真实内容末端，避免推高滚动高度。
                        <div
                          ref={latestMessageAnchorRef}
                          data-testid="latest-message-anchor"
                          className="h-0 scroll-mb-[160px] md:scroll-mb-[180px]"
                        />
                      ) : null}
                    </div>
                  </div>
                );
              })}
              {streamError ? (
                <div className="rounded-2xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-300">
                  {streamError}
                </div>
              ) : null}
            </div>
          )}
        </div>

        <div className="absolute bottom-0 left-0 right-0 bg-background/88 px-4 pb-6 pt-4 backdrop-blur-xl md:px-8">
          <div className="mx-auto max-w-4xl">
            <form
              onSubmit={(event) => void handleSubmit(event)}
              className="rounded-[24px] border border-border bg-surface shadow-[0_20px_64px_rgba(0,0,0,0.12)]"
            >
              <div className="px-4 pb-2 pt-3">
                <div className="mb-2 flex items-center justify-between gap-3">
                  <div className="text-[12px] text-muted">连接 MCP</div>
                  <button
                    type="button"
                    aria-label="切换MCP连接"
                    aria-pressed={mcpConnected}
                    onClick={() => setMcpConnected(!mcpConnected)}
                    className={`inline-flex h-6 w-11 items-center rounded-full border transition-colors ${
                      mcpConnected ? 'border-border-active bg-surface-container-high' : 'border-border bg-surface-container'
                    }`}
                  >
                    <span
                      className={`mx-0.5 h-4 w-4 rounded-full bg-foreground transition-transform ${
                        mcpConnected ? 'translate-x-5' : 'translate-x-0'
                      }`}
                    />
                  </button>
                </div>
                {selectedMcps.length > 0 ? (
                  <div className="mb-2 flex flex-wrap gap-2">
                    {selectedMcps.map((mcp) => (
                      <button
                        key={mcp.mcpCode}
                        type="button"
                        aria-label={`移除MCP ${mcp.displayName}`}
                        data-testid={`selected-mcp-${mcp.mcpCode}`}
                        onClick={() => removeSelectedMcp(mcp.mcpCode)}
                        className="inline-flex items-center gap-1 rounded-full border border-border bg-surface-container px-2.5 py-1 text-xs text-foreground transition-colors hover:border-border-active"
                      >
                        <span>{mcp.displayName}</span>
                        <X size={12} />
                      </button>
                    ))}
                  </div>
                ) : null}
                <div className="flex items-center gap-3">
                  <button type="button" className="rounded-full border border-border bg-surface-container p-1.5 text-muted">
                    <Paperclip size={17} />
                  </button>
                  <div className="relative flex-1">
                    <input
                      type="text"
                      value={inputValue}
                      onChange={(event) => setInputValue(event.target.value)}
                      onKeyDown={handleInputKeyDown}
                      placeholder="输入 / 选择MCP，或直接提问..."
                      className="h-8 w-full bg-transparent text-[14px] text-foreground outline-none placeholder:text-muted"
                    />
                    {isMcpPanelOpen ? (
                      <div
                        data-testid="mcp-command-panel"
                        className="absolute bottom-[calc(100%+10px)] left-0 right-0 z-20 rounded-2xl border border-border bg-surface p-2 shadow-[0_18px_44px_rgba(0,0,0,0.25)]"
                      >
                        {filteredMcps.length > 0 ? (
                          <div className="max-h-60 overflow-y-auto">
                            {filteredMcps.map((mcp, index) => {
                              const isSelected = index === highlightedMcpIndex;
                              return (
                                <button
                                  key={mcp.mcpCode}
                                  type="button"
                                  data-testid={`mcp-option-${mcp.mcpCode}`}
                                  onMouseEnter={() => setHighlightedMcpIndex(index)}
                                  onClick={() => selectMcp(mcp.mcpCode)}
                                  className={`mb-1 flex w-full items-center justify-between rounded-xl px-3 py-2 text-left text-sm transition-colors last:mb-0 ${
                                    isSelected
                                      ? 'bg-surface-container text-foreground'
                                      : 'text-muted hover:bg-surface-container hover:text-foreground'
                                  }`}
                                >
                                  <span className="min-w-0">
                                    <span className="block truncate text-foreground">{mcp.displayName}</span>
                                    <span className="mt-1 block truncate font-mono text-[11px] text-muted">/{mcp.mcpCode}</span>
                                  </span>
                                  {isSelected ? <Check size={14} className="text-foreground" /> : null}
                                </button>
                              );
                            })}
                          </div>
                        ) : (
                          <div className="rounded-xl bg-surface-container px-3 py-2 text-sm text-muted">未匹配到 MCP</div>
                        )}
                      </div>
                    ) : null}
                  </div>
                  <button
                    type="button"
                    aria-label="切换深度思考"
                    onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
                    className={`rounded-full border px-3 py-1.5 text-xs font-medium transition-colors ${
                      deepThinkingEnabled
                        ? 'border-foreground bg-foreground text-background'
                        : 'border-border bg-surface-container text-muted'
                    }`}
                  >
                    <span className="flex items-center gap-1.5">
                      <WandSparkles size={14} />
                      深度思考
                    </span>
                  </button>
                  {isStreaming ? (
                    <button
                      type="button"
                      aria-label="停止生成"
                      onClick={() => void cancelCurrentStream()}
                      disabled={isCancelling}
                      className="rounded-full bg-red-500 px-3 py-1.5 text-sm font-medium text-white transition-opacity disabled:opacity-60"
                    >
                      <span className="flex items-center gap-2">
                        <CircleStop size={16} />
                        {isCancelling ? '停止中' : '停止'}
                      </span>
                    </button>
                  ) : (
                    <button
                      type="submit"
                      aria-label="发送消息"
                      className="rounded-full bg-foreground p-2 text-background transition-opacity hover:opacity-90 disabled:opacity-60"
                      disabled={!inputValue.trim() && !isMcpPanelOpen}
                    >
                      <ArrowUp size={17} />
                    </button>
                  )}
                </div>
              </div>
            </form>
          </div>
        </div>
      </section>

      {showWorkspacePanel ? (
        <aside
          className={`hidden overflow-hidden border-l bg-surface/96 [contain:layout_paint] will-change-[width,opacity] transition-[width,opacity,border-color] duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] md:flex md:flex-col ${
            isWorkspacePanelVisible
              ? 'w-[340px] border-border opacity-100'
              : 'w-0 border-transparent opacity-0 pointer-events-none'
          }`}
          aria-hidden={!isWorkspacePanelVisible}
        >
          <div
            className={`flex h-full flex-col transition-[opacity,transform] duration-200 ${
              isWorkspacePanelVisible ? 'translate-x-0 opacity-100' : 'translate-x-4 opacity-0'
            }`}
          >
              <div className="border-b border-border px-5 py-5">
                <p className="font-mono text-[11px] uppercase tracking-[0.3em] text-muted">Workspace</p>
                <h3 className="mt-2 text-lg font-semibold text-foreground">执行回放</h3>
              </div>
              <div className="flex-1 overflow-y-auto px-4 py-4">
                <Panel title="执行步骤" icon={CheckCircle2}>
                  {executionSteps.length ? (
                    executionSteps.map((step) => (
                      <div key={step.id} className="rounded-2xl border border-border bg-surface-container px-4 py-3">
                        <div className="text-sm font-medium text-foreground">{step.stepTitle}</div>
                        <div className="mt-2 text-[12px] uppercase tracking-[0.2em] text-muted">{step.stepStatus}</div>
                        {step.content ? <div className="mt-3 text-sm leading-6 text-muted">{step.content}</div> : null}
                      </div>
                    ))
                  ) : (
                    <EmptyBlock text="当前会话暂无步骤回放" />
                  )}
                </Panel>

                <Panel title="参考来源" icon={Globe2}>
                  {references.length ? (
                    references.map((reference) => (
                      <a
                        key={reference.id}
                        href={reference.url}
                        target="_blank"
                        rel="noreferrer"
                        className="block rounded-2xl border border-border bg-surface-container px-4 py-3 transition-colors hover:border-border-active"
                      >
                        <div className="text-sm font-medium text-foreground">{reference.title}</div>
                        {reference.siteName ? <div className="mt-2 text-[12px] text-muted">{reference.siteName}</div> : null}
                        {reference.snippet ? <div className="mt-3 text-sm leading-6 text-muted">{reference.snippet}</div> : null}
                      </a>
                    ))
                  ) : (
                    <EmptyBlock text="当前会话暂无来源回放" />
                  )}
                </Panel>

                <Panel title="生成产物" icon={FileText}>
                  {artifacts.length ? (
                    artifacts.map((artifact) => (
                      <div key={artifact.id} className="rounded-2xl border border-border bg-surface-container px-4 py-3">
                        <div className="text-sm font-medium text-foreground">{artifact.name}</div>
                        <div className="mt-2 text-[12px] uppercase tracking-[0.2em] text-muted">{artifact.artifactType}</div>
                        {artifact.contentPreview ? <div className="mt-3 text-sm leading-6 text-muted">{artifact.contentPreview}</div> : null}
                      </div>
                    ))
                  ) : (
                    <EmptyBlock text="当前会话暂无产物回放" />
                  )}
                </Panel>
              </div>
          </div>
        </aside>
      ) : null}

      {renameDialog.isOpen ? (
        <InlineDialog
          title="重命名对话"
          defaultValue={renameDialog.initialTitle}
          confirmLabel="确认"
          cancelLabel="取消"
          onCancel={renameDialog.close}
          onConfirm={async (nextTitle) => {
            if (renameDialog.conversationId) {
              await renameConversation(renameDialog.conversationId, nextTitle);
            }
            renameDialog.close();
          }}
        />
      ) : null}

      {deleteDialog.isOpen ? (
        <ConfirmDialog
          title="删除对话"
          description={`确认删除对话“${deleteDialog.title}”吗？`}
          confirmLabel="删除"
          cancelLabel="取消"
          onCancel={deleteDialog.close}
          onConfirm={async () => {
            if (deleteDialog.conversationId) {
              await deleteConversation(deleteDialog.conversationId);
            }
            deleteDialog.close();
          }}
        />
      ) : null}
    </motion.div>
  );
}

/**
 * 渲染助手“思考过程”折叠面板，默认展开并通过过渡类提供展开/收起动画。
 */
function ThinkingPanel({ messageId, content }: { messageId: string; content: string }) {
  // 步骤：思考面板在首次渲染时默认展开，减少用户额外点击成本。
  const [isExpanded, setIsExpanded] = React.useState(true);
  const contentId = `thinking-content-panel-${messageId}`;

  return (
    <section
      data-testid={`thinking-panel-${messageId}`}
      // 步骤：折叠态改为自适应宽度胶囊，避免面板在收起后仍占据整行宽度。
      className={`mb-3 rounded-2xl border border-border bg-surface-container text-sm text-muted transition-[width,padding] duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] ${
        isExpanded ? 'w-full px-4 py-3' : 'w-fit px-3 py-2'
      }`}
    >
      <div
        data-testid={`thinking-summary-${messageId}`}
        className="flex items-center gap-3 font-medium text-foreground"
      >
        <span>思考过程</span>
        <button
          type="button"
          data-testid={`thinking-toggle-button-${messageId}`}
          aria-expanded={isExpanded}
          aria-controls={contentId}
          aria-label={isExpanded ? '折叠思考过程' : '展开思考过程'}
          onClick={() => setIsExpanded((current) => !current)}
          className="flex h-7 w-7 items-center justify-center rounded-full border border-border bg-surface text-muted transition-colors hover:text-foreground"
        >
          <span
            data-testid={`thinking-toggle-icon-${messageId}`}
            aria-hidden="true"
            className="flex h-7 w-7 items-center justify-center"
          >
            <ChevronDown size={15} className={`transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`} />
          </span>
        </button>
      </div>
      <div
        id={contentId}
        data-testid={`thinking-content-${messageId}`}
        // 步骤：内容区保持挂载，通过高度/透明度过渡实现展开与折叠动画，避免闪烁和重排跳变。
        className={`overflow-hidden transition-all duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] ${
          isExpanded
            ? 'mt-3 max-h-[640px] max-w-full translate-y-0 opacity-100'
            : 'mt-0 max-h-0 max-w-0 -translate-y-1 opacity-0 pointer-events-none'
        }`}
      >
        <div className="whitespace-pre-wrap leading-6">{content}</div>
      </div>
    </section>
  );
}

/**
 * 渲染助手消息中的 MCP 调用折叠面板，展示工具、入参与返回片段。
 */
function McpCallPanel({ messageId, calls }: { messageId: string; calls: McpCallItem[] }) {
  const [isExpanded, setIsExpanded] = React.useState(true);
  const contentId = `mcp-call-content-panel-${messageId}`;

  return (
    <section
      data-testid={`mcp-call-panel-${messageId}`}
      className={`mb-3 rounded-2xl border border-border bg-surface-container text-sm text-muted transition-[width,padding] duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] ${
        isExpanded ? 'w-full px-4 py-3' : 'w-fit px-3 py-2'
      }`}
    >
      <div className="flex items-center gap-3 font-medium text-foreground">
        <span>MCP 调用</span>
        <span className="rounded-full border border-border bg-surface px-2 py-0.5 text-[11px] text-muted">
          {calls.length}
        </span>
        <button
          type="button"
          data-testid={`mcp-call-toggle-button-${messageId}`}
          aria-expanded={isExpanded}
          aria-controls={contentId}
          aria-label={isExpanded ? '折叠MCP调用详情' : '展开MCP调用详情'}
          onClick={() => setIsExpanded((current) => !current)}
          className="flex h-7 w-7 items-center justify-center rounded-full border border-border bg-surface text-muted transition-colors hover:text-foreground"
        >
          <ChevronDown size={15} className={`transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`} />
        </button>
      </div>
      <div
        id={contentId}
        className={`overflow-hidden transition-all duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] ${
          isExpanded
            ? 'mt-3 max-h-[640px] max-w-full translate-y-0 opacity-100'
            : 'mt-0 max-h-0 max-w-0 -translate-y-1 opacity-0 pointer-events-none'
        }`}
      >
        <div className="space-y-3">
          {calls.map((call, index) => (
            <article
              key={`${call.toolId}-${index}`}
              data-testid={`mcp-call-item-${messageId}-${index}`}
              className="rounded-xl border border-border bg-surface px-3 py-2.5"
            >
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <div className="truncate text-sm font-medium text-foreground">{call.displayName || call.toolId}</div>
                  <div className="mt-1 font-mono text-[11px] text-muted">/{call.toolId}</div>
                </div>
              </div>
              {call.input ? (
                <div className="mt-2 rounded-lg bg-surface-container px-2.5 py-2">
                  <div className="text-[11px] uppercase tracking-[0.16em] text-muted">输入</div>
                  <div className="mt-1 whitespace-pre-wrap text-xs leading-5 text-foreground">{call.input}</div>
                </div>
              ) : null}
              {call.content ? (
                <div className="mt-2 rounded-lg bg-surface-container px-2.5 py-2">
                  <div className="text-[11px] uppercase tracking-[0.16em] text-muted">返回</div>
                  <div className="mt-1 whitespace-pre-wrap text-xs leading-5 text-foreground">{call.content}</div>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

/**
 * 使用 Markdown 渲染助手消息，保证标题、列表、代码块等富文本结构按预期展示。
 */
function MarkdownMessage({ content }: { content: string }) {
  return (
    <div className="chat-markdown min-w-0 [overflow-wrap:anywhere] text-sm leading-7 text-foreground">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ node: _node, ...props }) => <h1 className="mb-4 text-3xl font-semibold tracking-tight" {...props} />,
          h2: ({ node: _node, ...props }) => <h2 className="mb-3 mt-6 text-2xl font-semibold tracking-tight" {...props} />,
          h3: ({ node: _node, ...props }) => <h3 className="mb-3 mt-6 text-xl font-semibold tracking-tight" {...props} />,
          p: ({ node: _node, ...props }) => <p className="mb-4 last:mb-0" {...props} />,
          ul: ({ node: _node, ...props }) => <ul className="mb-4 list-disc space-y-2 pl-6" {...props} />,
          ol: ({ node: _node, ...props }) => <ol className="mb-4 list-decimal space-y-2 pl-6" {...props} />,
          li: ({ node: _node, ...props }) => <li className="pl-1" {...props} />,
          hr: ({ node: _node, ...props }) => <hr className="my-6 border-border" {...props} />,
          code: ({ node: _node, className, children, ...props }) => {
            const isBlockCode = className?.includes('language-');
            if (isBlockCode) {
              return (
                <code
                  className={`block overflow-x-auto rounded-2xl border border-border bg-surface-container px-4 py-3 font-mono text-[13px] leading-6 ${className}`}
                  {...props}
                >
                  {children}
                </code>
              );
            }
            return (
              <code className="rounded bg-surface-container px-1.5 py-0.5 font-mono text-[13px]" {...props}>
                {children}
              </code>
            );
          },
          pre: ({ node: _node, ...props }) => <pre className="mb-4 overflow-x-auto whitespace-pre-wrap" {...props} />,
          table: ({ node: _node, ...props }) => (
            <div className="mb-4 overflow-x-auto rounded-2xl border border-border">
              <table className="min-w-full border-collapse text-left text-sm" {...props} />
            </div>
          ),
          thead: ({ node: _node, ...props }) => <thead className="bg-surface-container" {...props} />,
          th: ({ node: _node, ...props }) => <th className="border-b border-border px-3 py-2 font-semibold" {...props} />,
          td: ({ node: _node, ...props }) => <td className="border-b border-border px-3 py-2 align-top last:border-b-0" {...props} />,
          blockquote: ({ node: _node, ...props }) => (
            <blockquote className="mb-4 border-l-2 border-border-active pl-4 text-muted" {...props} />
          ),
          strong: ({ node: _node, ...props }) => <strong className="font-semibold text-foreground" {...props} />,
          img: ({ node: _node, ...props }) => (
            <img
              className="chat-message-image mb-4 max-h-[460px] w-full rounded-2xl border border-border object-contain shadow-sm"
              loading="lazy"
              {...props}
            />
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}

type CopyMode = 'plain' | 'markdown';
type MessageReaction = 'up' | 'down' | null;

/**
 * 渲染助手消息底部操作栏，统一提供复制、复制 Markdown 与点赞反馈入口。
 */
function AssistantMessageActions({ messageId, content }: { messageId: string; content: string }) {
  const [isMenuOpen, setIsMenuOpen] = React.useState(false);
  const [copiedMode, setCopiedMode] = React.useState<CopyMode | null>(null);
  const [reaction, setReaction] = React.useState<MessageReaction>(null);
  const menuContainerRef = React.useRef<HTMLDivElement | null>(null);

  React.useEffect(() => {
    if (!isMenuOpen) {
      return;
    }
    const closeMenuWhenClickOutside = (event: MouseEvent) => {
      if (menuContainerRef.current?.contains(event.target as Node)) {
        return;
      }
      setIsMenuOpen(false);
    };
    document.addEventListener('mousedown', closeMenuWhenClickOutside);
    return () => {
      document.removeEventListener('mousedown', closeMenuWhenClickOutside);
    };
  }, [isMenuOpen]);

  /**
   * 复制消息内容并短暂给出状态反馈。
   * @param mode 复制模式，区分纯文本和 Markdown。
   */
  const copyContent = async (mode: CopyMode) => {
    const value = mode === 'markdown' ? content : normalizeMessageForPlainCopy(content);
    try {
      await navigator.clipboard.writeText(value);
      setCopiedMode(mode);
      window.setTimeout(() => {
        setCopiedMode((previousMode) => (previousMode === mode ? null : previousMode));
      }, 1200);
    } catch {
      setCopiedMode(null);
    } finally {
      if (mode === 'markdown') {
        setIsMenuOpen(false);
      }
    }
  };

  /**
   * 同步本地反馈状态，确保点赞与倒赞互斥。
   * @param nextReaction 下一次反馈值。
   */
  const toggleReaction = (nextReaction: Exclude<MessageReaction, null>) => {
    setReaction((previousReaction) => (previousReaction === nextReaction ? null : nextReaction));
  };

  return (
    <div className="chat-message-actions mt-3 flex items-center gap-1 text-muted">
      <button
        type="button"
        data-testid={`copy-message-${messageId}`}
        aria-label="复制消息"
        onClick={() => void copyContent('plain')}
        className="chat-message-action-button"
      >
        <Copy size={15} />
      </button>
      <div ref={menuContainerRef} className="relative">
        <button
          type="button"
          data-testid={`copy-menu-toggle-${messageId}`}
          aria-label="复制更多"
          aria-expanded={isMenuOpen}
          onClick={() => setIsMenuOpen((open) => !open)}
          className="chat-message-action-button"
        >
          <ChevronDown size={15} className={`transition-transform ${isMenuOpen ? 'rotate-180' : ''}`} />
        </button>
        {isMenuOpen ? (
          <div className="chat-copy-menu absolute bottom-11 left-0 min-w-[190px] rounded-2xl border border-border bg-surface px-2 py-2 shadow-[0_16px_40px_rgba(0,0,0,0.24)]">
            <button
              type="button"
              data-testid={`copy-markdown-${messageId}`}
              onClick={() => void copyContent('markdown')}
              className="chat-copy-menu-item"
            >
              复制为Markdown
            </button>
            <button
              type="button"
              data-testid={`copy-plain-${messageId}`}
              onClick={() => void copyContent('plain')}
              className="chat-copy-menu-item"
            >
              复制
            </button>
          </div>
        ) : null}
      </div>
      <button
        type="button"
        data-testid={`thumbs-up-${messageId}`}
        aria-label="点赞"
        aria-pressed={reaction === 'up'}
        onClick={() => toggleReaction('up')}
        className={`chat-message-action-button ${reaction === 'up' ? 'chat-message-action-button-active' : ''}`}
      >
        <ThumbsUp size={15} />
      </button>
      <button
        type="button"
        data-testid={`thumbs-down-${messageId}`}
        aria-label="倒赞"
        aria-pressed={reaction === 'down'}
        onClick={() => toggleReaction('down')}
        className={`chat-message-action-button ${reaction === 'down' ? 'chat-message-action-button-active' : ''}`}
      >
        <ThumbsDown size={15} />
      </button>
      {copiedMode ? (
        <span className="ml-2 text-[11px] text-muted">{copiedMode === 'markdown' ? '已复制 Markdown' : '已复制'}</span>
      ) : null}
    </div>
  );
}

/**
 * 将 Markdown 消息转换为复制用纯文本，避免把语法标记原样拷贝给用户。
 * @param content Markdown 原文。
 * @returns 可读纯文本。
 */
function normalizeMessageForPlainCopy(content: string) {
  return content
    .replace(/\r\n/g, '\n')
    .replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '$1')
    .replace(/`{1,3}/g, '')
    .replace(/^[>\-#*]+\s*/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/**
 * 统一渲染右栏分组面板。
 */
function Panel({
  title,
  icon: Icon,
  children,
}: {
  title: string;
  icon: React.ComponentType<{ size?: number; className?: string }>;
  children: React.ReactNode;
}) {
  return (
    <section className="mb-6">
      <div className="mb-3 flex items-center gap-2 text-foreground">
        <Icon size={16} className="text-accent-breeze" />
        <h4 className="text-sm font-semibold">{title}</h4>
      </div>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

/**
 * 统一渲染空态块。
 */
function EmptyBlock({ text }: { text: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-border bg-surface-container px-4 py-4 text-sm text-muted">
      {text}
    </div>
  );
}

/**
 * 渲染页面内重命名弹窗，替代浏览器原生 prompt。
 */
function InlineDialog({
  title,
  defaultValue,
  confirmLabel,
  cancelLabel,
  onCancel,
  onConfirm,
}: {
  title: string;
  defaultValue: string;
  confirmLabel: string;
  cancelLabel: string;
  onCancel: () => void;
  onConfirm: (value: string) => Promise<void>;
}) {
  const [value, setValue] = React.useState(defaultValue);

  return (
    <div className="absolute inset-0 z-40 flex items-center justify-center bg-background/35 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-2xl border border-border bg-surface-container p-5 shadow-[0_24px_64px_rgba(0,0,0,0.26)]">
        <h3 className="text-base font-semibold text-foreground">{title}</h3>
        <input
          value={value}
          onChange={(event) => setValue(event.target.value)}
          className="mt-4 h-24 w-full rounded-xl border border-border bg-surface px-4 py-3 text-sm text-foreground outline-none"
        />
        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-lg bg-surface px-4 py-2 text-sm text-muted transition-colors hover:bg-surface-high hover:text-foreground"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={() => void onConfirm(value)}
            className="rounded-lg bg-foreground px-4 py-2 text-sm text-background transition-opacity hover:opacity-90"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * 渲染页面内删除确认弹窗，替代浏览器原生 confirm。
 */
function ConfirmDialog({
  title,
  description,
  confirmLabel,
  cancelLabel,
  onCancel,
  onConfirm,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel: string;
  onCancel: () => void;
  onConfirm: () => Promise<void>;
}) {
  return (
    <div className="absolute inset-0 z-40 flex items-center justify-center bg-background/35 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-2xl border border-border bg-surface-container p-5 shadow-[0_24px_64px_rgba(0,0,0,0.26)]">
        <h3 className="text-base font-semibold text-foreground">{title}</h3>
        <p className="mt-3 text-sm leading-6 text-muted">{description}</p>
        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-lg bg-surface px-4 py-2 text-sm text-muted transition-colors hover:bg-surface-high hover:text-foreground"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={() => void onConfirm()}
            className="rounded-lg bg-[#ff5b57] px-4 py-2 text-sm text-white transition-opacity hover:opacity-90"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
