import React from 'react';
import { motion } from 'motion/react';
import {
  ArrowUp,
  CheckCircle2,
  CircleStop,
  Database,
  FileText,
  Globe2,
  Paperclip,
  FolderOpen,
  Search,
  WandSparkles,
} from 'lucide-react';
import { ChatWorkspaceController } from './chat/types';

/**
 * 定义聊天视图的输入属性。
 */
interface ChatViewProps {
  isAuthenticated: boolean;
  onRequireLogin: () => void;
  workspace: ChatWorkspaceController;
}

/**
 * 渲染接入真实后端数据的聊天三栏工作台。
 */
export default function ChatView({ isAuthenticated, onRequireLogin, workspace }: ChatViewProps) {
  const {
    activeConversationId,
    messages,
    executionSteps,
    references,
    artifacts,
    isStreaming,
    isCancelling,
    streamError,
    inputValue,
    isBootstrapping,
    setInputValue,
    submitMessage,
    cancelCurrentStream,
  } = workspace;

  /**
   * 统一处理底部输入提交。
   * @param event 表单事件。
   */
  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
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

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="relative flex h-full overflow-hidden bg-background"
    >
      <section className="relative flex min-w-0 flex-1 flex-col overflow-hidden">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(71,96,122,0.16),transparent_38%),radial-gradient(circle_at_80%_18%,rgba(188,75,0,0.12),transparent_26%)]" />
        <div className="relative flex-1 overflow-y-auto px-4 pb-40 pt-6 md:px-8">
          {showLandingState ? (
            <div className="mx-auto flex max-w-4xl flex-col items-center justify-center px-6 py-16 text-center">
              <h1 className="max-w-3xl text-4xl font-semibold tracking-tight text-foreground md:text-6xl">
                你好，我是 CodingX
              </h1>
              <div className="mt-10 grid w-full max-w-3xl gap-4 md:grid-cols-2">
                {[
                  { icon: Globe2, title: '网页读取', desc: '解析并总结外部网页内容', iconClassName: 'text-[#8fb3da]' },
                  { icon: Search, title: '调研分析', desc: '深度搜索并生成研究报告', iconClassName: 'text-[#ff8a24]' },
                  { icon: Database, title: '数据挖掘', desc: '结构化数据提取与清洗', iconClassName: 'text-[#a78bfa]' },
                  { icon: FolderOpen, title: '文件管理', desc: '上传并与您的文档进行对话', iconClassName: 'text-[#f4f4f5]' },
                ].map((item) => (
                  <button
                    key={item.title}
                    type="button"
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
                return (
                  <div
                    key={message.id}
                    className={`flex ${isAssistant ? 'justify-start' : 'justify-end'}`}
                  >
                    <div
                      className={`max-w-3xl rounded-[28px] border px-5 py-4 shadow-sm ${
                        isAssistant
                          ? 'border-border bg-surface text-foreground'
                          : 'border-transparent bg-foreground text-background'
                      }`}
                      >
                        <div className="mb-3 flex items-center gap-2 text-[11px] uppercase tracking-[0.22em]">
                        {isAssistant ? <WandSparkles size={14} className="text-accent-breeze" /> : <CheckCircle2 size={14} />}
                        <span>{isAssistant ? 'CodingX' : 'You'}</span>
                      </div>
                      <div className="whitespace-pre-wrap text-sm leading-7">
                        {message.content || (message.status === 'streaming' ? '正在生成回答...' : '')}
                      </div>
                      {message.errorMessage ? (
                        <div className="mt-3 rounded-2xl border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs text-red-300">
                          {message.errorMessage}
                        </div>
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
              <div className="flex items-center gap-3 px-4 py-3">
                <button type="button" className="rounded-full border border-border bg-surface-container p-1.5 text-muted">
                  <Paperclip size={17} />
                </button>
                <input
                  type="text"
                  value={inputValue}
                  onChange={(event) => setInputValue(event.target.value)}
                  placeholder="输入指令以重构组件库或分析代码..."
                  className="h-9 flex-1 bg-transparent text-[14px] text-foreground outline-none placeholder:text-muted"
                />
                {isStreaming ? (
                  <button
                    type="button"
                    aria-label="停止生成"
                    onClick={() => void cancelCurrentStream()}
                    disabled={isCancelling}
                    className="rounded-full bg-red-500 px-3.5 py-1.5 text-sm font-medium text-white transition-opacity disabled:opacity-60"
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
                    className="rounded-full bg-foreground p-2.5 text-background transition-opacity hover:opacity-90 disabled:opacity-60"
                    disabled={!inputValue.trim()}
                  >
                    <ArrowUp size={17} />
                  </button>
                )}
              </div>
            </form>
          </div>
        </div>
      </section>

      {showWorkspacePanel ? (
        <aside className="hidden w-[340px] border-l border-border bg-surface/96 md:flex md:flex-col">
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
        </aside>
      ) : null}
    </motion.div>
  );
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
