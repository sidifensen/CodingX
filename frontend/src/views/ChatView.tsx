import React from 'react';
import { motion } from 'motion/react';
import {
  ArrowUp,
  BookOpen,
  CheckCircle2,
  CircleStop,
  FileText,
  FolderArchive,
  Globe2,
  MessageSquareHeart,
  Paperclip,
  Search,
  Sparkles,
} from 'lucide-react';
import { useChatWorkspace } from './chat/useChatWorkspace';

/**
 * 定义聊天视图的输入属性。
 */
interface ChatViewProps {
  isAuthenticated: boolean;
  onRequireLogin: () => void;
}

/**
 * 渲染接入真实后端数据的聊天三栏工作台。
 */
export default function ChatView({ isAuthenticated, onRequireLogin }: ChatViewProps) {
  const {
    conversations,
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
    selectConversation,
    submitPositiveFeedback,
  } = useChatWorkspace(isAuthenticated);

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

  /**
   * 点击会话时切换回放上下文。
   * @param conversationId 会话标识。
   */
  const handleConversationSelect = async (conversationId: number) => {
    if (conversationId === activeConversationId) {
      return;
    }
    await selectConversation(conversationId);
  };

  const showEmpty = !messages.length && !isBootstrapping;

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="relative flex h-full overflow-hidden bg-background"
    >
      <section className="hidden border-r border-border bg-surface md:flex md:w-[280px] md:flex-col">
        <div className="border-b border-border px-5 py-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-mono text-[11px] uppercase tracking-[0.28em] text-muted">Conversations</p>
              <h2 className="mt-2 text-xl font-semibold text-foreground">聊天工作台</h2>
            </div>
            <div className="rounded-full border border-border bg-surface-container px-2 py-1 text-[11px] text-muted">
              {conversations.length}
            </div>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto px-3 py-3">
          {conversations.length ? (
            conversations.map((conversation) => {
              const isActive = conversation.id === activeConversationId;
              return (
                <button
                  key={conversation.id}
                  type="button"
                  onClick={() => void handleConversationSelect(conversation.id)}
                  className={`mb-2 w-full rounded-2xl border px-4 py-3 text-left transition-colors ${
                    isActive
                      ? 'border-border-active bg-surface-container-high text-foreground'
                      : 'border-transparent bg-transparent text-muted hover:border-border hover:bg-surface-container hover:text-foreground'
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="truncate text-sm font-medium">{conversation.title}</div>
                      <div className="mt-2 text-[11px] uppercase tracking-[0.2em] text-muted">
                        {conversation.lastRunId ? `Run ${conversation.lastRunId}` : 'No Run'}
                      </div>
                    </div>
                    {conversation.lastRunId ? <CheckCircle2 size={16} className="mt-0.5 text-accent-breeze" /> : null}
                  </div>
                </button>
              );
            })
          ) : (
            <div className="rounded-3xl border border-dashed border-border bg-surface-container p-5 text-sm text-muted">
              当前账号还没有可回放会话。
            </div>
          )}
        </div>
      </section>

      <section className="relative flex min-w-0 flex-1 flex-col overflow-hidden">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(71,96,122,0.16),transparent_38%),radial-gradient(circle_at_80%_18%,rgba(188,75,0,0.12),transparent_26%)]" />
        <div className="relative flex-1 overflow-y-auto px-4 pb-40 pt-6 md:px-8">
          {showEmpty ? (
            <div className="mx-auto flex max-w-3xl flex-col items-center justify-center px-6 py-16 text-center">
              <div className="mb-5 rounded-full border border-border bg-surface-container px-4 py-2 font-mono text-[11px] uppercase tracking-[0.35em] text-muted">
                Live Chat Workspace
              </div>
              <h1 className="max-w-2xl text-4xl font-semibold tracking-tight text-foreground md:text-5xl">
                会话、SSE 与工作区回放已经接入同一条真实链路
              </h1>
              <p className="mt-5 max-w-2xl text-base leading-7 text-muted">
                直接提问即可触发后端聊天流。左栏显示真实会话，中央回放消息，右栏联动步骤、来源与产物。
              </p>
              <div className="mt-10 grid w-full max-w-3xl gap-4 md:grid-cols-3">
                {[
                  { icon: Search, title: '搜索任务', desc: '请搜索 Spring Boot SSE 最佳实践' },
                  { icon: BookOpen, title: '方案整理', desc: '帮我整理前端 SSE 接入清单' },
                  { icon: FolderArchive, title: '产物回放', desc: '总结最近一次搜索结果并生成产物' },
                ].map((item) => (
                  <button
                    key={item.title}
                    type="button"
                    onClick={() => setInputValue(item.desc)}
                    className="rounded-3xl border border-border bg-surface px-5 py-5 text-left shadow-sm transition-colors hover:border-border-active hover:bg-surface-container"
                  >
                    <item.icon size={20} className="text-accent-breeze" />
                    <div className="mt-4 text-sm font-semibold text-foreground">{item.title}</div>
                    <div className="mt-2 text-sm leading-6 text-muted">{item.desc}</div>
                  </button>
                ))}
              </div>
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
                        {isAssistant ? <Sparkles size={14} className="text-accent-breeze" /> : <CheckCircle2 size={14} />}
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

        <div className="absolute bottom-0 left-0 right-0 border-t border-border bg-background/92 px-4 pb-6 pt-4 backdrop-blur-xl md:px-8">
          <div className="mx-auto max-w-4xl">
            <form
              onSubmit={(event) => void handleSubmit(event)}
              className="rounded-[28px] border border-border bg-surface shadow-[0_24px_80px_rgba(0,0,0,0.12)]"
            >
              <div className="flex items-center gap-3 px-4 pt-4">
                <button type="button" className="rounded-full border border-border bg-surface-container p-2 text-muted">
                  <Paperclip size={18} />
                </button>
                <input
                  type="text"
                  value={inputValue}
                  onChange={(event) => setInputValue(event.target.value)}
                  placeholder="输入指令以重构组件库或分析代码..."
                  className="h-11 flex-1 bg-transparent text-[15px] text-foreground outline-none placeholder:text-muted"
                />
                {isStreaming ? (
                  <button
                    type="button"
                    aria-label="停止生成"
                    onClick={() => void cancelCurrentStream()}
                    disabled={isCancelling}
                    className="rounded-full bg-red-500 px-4 py-2 text-sm font-medium text-white transition-opacity disabled:opacity-60"
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
                    className="rounded-full bg-foreground p-3 text-background transition-opacity hover:opacity-90 disabled:opacity-60"
                    disabled={!inputValue.trim()}
                  >
                    <ArrowUp size={18} />
                  </button>
                )}
              </div>
              <div className="flex items-center justify-between gap-3 px-4 pb-4 pt-3">
                <div className="flex items-center gap-3 text-[12px] text-muted">
                  <span className="rounded-full border border-border bg-surface-container px-3 py-1">
                    {activeConversationId ? `会话 #${activeConversationId}` : '未选中会话'}
                  </span>
                  <span>{isStreaming ? 'SSE 连接中' : '回放模式'}</span>
                </div>
                <button
                  type="button"
                  onClick={() => void submitPositiveFeedback()}
                  className="flex items-center gap-2 rounded-full border border-border bg-surface-container px-3 py-1.5 text-[12px] text-muted transition-colors hover:border-border-active hover:text-foreground"
                >
                  <MessageSquareHeart size={14} />
                  反馈
                </button>
              </div>
            </form>
          </div>
        </div>
      </section>

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
