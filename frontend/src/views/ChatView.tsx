import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  FolderOpen,
  Terminal,
  FileText,
  Code,
  Database,
  Copy,
  Paperclip,
  Mic,
  ArrowUp,
  Sparkles,
  Globe2,
  BookOpen,
  ChartColumn,
  FolderArchive,
  Search, // 部分图标没有完全对应的语义，当前使用近似替代图标
} from 'lucide-react';

/**
 * 定义聊天视图的输入属性。
 */
interface ChatViewProps {
  isAuthenticated: boolean;
  onRequireLogin: () => void;
}

/**
 * 渲染聊天工作台视图。
 */
export default function ChatView({ isAuthenticated, onRequireLogin }: ChatViewProps) {
  const [hasStarted, setHasStarted] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [inputValue, setInputValue] = useState('');

  /**
   * 处理聊天提交动作，未登录时仅唤起登录弹窗，不触发 AI 请求链路。
   * @param e 可选表单事件对象。
   */
  const handleSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!inputValue.trim()) return;
    if (!isAuthenticated) {
      // 步骤：未登录时拦截发送动作，改为提示用户先登录。
      onRequireLogin();
      return;
    }

    // 步骤：仅在已登录场景进入聊天执行状态。
    setHasStarted(true);
    setIsSubmitting(true);

    // 步骤：模拟请求返回延迟
    setTimeout(() => {
      setIsSubmitting(false);
    }, 1500);
  };

  const EmptyState = () => (
    <div className="flex flex-col items-center justify-center min-h-[60%] py-16 text-center">
      <h2 className="text-5xl font-bold text-foreground mb-12">你好，我是 CodingX</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 max-w-2xl w-full px-6">
        {[
          {
            title: '网页读取',
            desc: '解析并总结外部网页内容',
            icon: Globe2,
            color: 'text-accent-breeze',
          },
          {
            title: '调研分析',
            desc: '深度搜索并生成研究报告',
            icon: Search,
            color: 'text-accent-sunset',
          },
          {
            title: '数据挖掘',
            desc: '结构化数据提取与清洗',
            icon: Database,
            color: 'text-accent-dusk',
          },
          {
            title: '文件管理',
            desc: '上传并与您的文档进行对话',
            icon: FolderOpen,
            color: 'text-foreground',
          },
        ].map((item, idx) => (
          <div
            key={idx}
            className="bg-surface-container border border-border p-5 rounded-lg hover:border-primary transition-all cursor-pointer group flex items-center gap-5 text-left"
          >
            <div className="w-12 h-12 rounded-full bg-surface-high border border-border flex items-center justify-center group-hover:bg-primary/10">
              <item.icon className={item.color} size={24} />
            </div>
            <div>
              <h3 className="text-base font-semibold text-foreground">{item.title}</h3>
              <p className="text-[13px] text-muted">{item.desc}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );

  const ActiveState = () => (
    <div className="flex-1 overflow-y-auto px-4 md:px-12 py-6 space-y-12 pb-32 md:pb-32">
      {/* User Input representation */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col items-start max-w-3xl"
      >
        <div className="flex items-center gap-3 mb-3">
          <span className="font-mono text-[11px] font-semibold tracking-widest text-muted uppercase">
            USER_INPUT
          </span>
        </div>
        <div className="bg-surface-container border border-border p-6 rounded-lg text-sm leading-relaxed">
          {inputValue ||
            '请协助我分析当前项目的架构，并针对核心组件库的重构提供结构化建议。我需要涵盖前端 React 框架、后端 Spring Boot 接口以及完整的技术文档路径。'}
        </div>
      </motion.div>

      {/* AI Response representation */}
      <div className="flex flex-col items-start w-full">
        <div className="flex items-center gap-3 mb-3">
          <Sparkles className="text-accent-breeze" size={16} />
          <span className="font-mono text-[11px] font-semibold tracking-widest text-foreground uppercase">
            CODINGX_ARCHITECT
          </span>
        </div>

        {isSubmitting ? (
          <div className="space-y-4 w-full max-w-3xl animate-pulse">
            <div className="h-4 bg-surface-high rounded w-3/4"></div>
            <div className="h-4 bg-surface-high rounded w-1/2"></div>
            <div className="h-32 bg-surface-high rounded w-full mt-6"></div>
          </div>
        ) : (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.1 }}>
            {/* Bento Grid */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 w-full max-w-5xl">
              {/* Frontend Block */}
              <div className="bg-surface-container border border-border p-5 rounded-lg group hover:border-border-active transition-all">
                <div className="flex items-center justify-between mb-5">
                  <h3 className="font-mono text-xs font-semibold tracking-widest text-foreground uppercase">
                    Frontend (React)
                  </h3>
                  <Code size={16} className="text-muted group-hover:text-foreground" />
                </div>
                <div className="space-y-2 font-mono text-[11px]">
                  <div className="flex items-center gap-2 text-muted">
                    <FolderOpen size={14} /> src/components
                  </div>
                  <div className="flex items-center gap-2 text-foreground ml-3">
                    <Terminal size={14} /> atomic-ui/
                  </div>
                  <div className="flex items-center gap-2 text-foreground ml-3">
                    <Terminal size={14} /> complex-modules/
                  </div>
                  <div className="flex items-center gap-2 text-muted">
                    <FileText size={14} /> tailwind.config.ts
                  </div>
                </div>
              </div>

              {/* Backend Block */}
              <div className="bg-surface-container border border-border p-5 rounded-lg group hover:border-border-active transition-all">
                <div className="flex items-center justify-between mb-5">
                  <h3 className="font-mono text-xs font-semibold tracking-widest text-foreground uppercase">
                    Backend (Spring Boot)
                  </h3>
                  <Database size={16} className="text-muted group-hover:text-foreground" />
                </div>
                <div className="space-y-2 font-mono text-[11px]">
                  <div className="flex items-center gap-2 text-muted">
                    <FolderOpen size={14} /> com.codingx.api
                  </div>
                  <div className="flex items-center gap-2 text-foreground ml-3">
                    <Terminal size={14} /> controller/
                  </div>
                  <div className="flex items-center gap-2 text-foreground ml-3">
                    <Terminal size={14} /> service/
                  </div>
                  <div className="flex items-center gap-2 text-muted">
                    <FileText size={14} /> application.yml
                  </div>
                </div>
              </div>

              {/* Docs Block */}
              <div className="bg-surface-container border border-border p-5 rounded-lg group hover:border-border-active transition-all">
                <div className="flex items-center justify-between mb-5">
                  <h3 className="font-mono text-xs font-semibold tracking-widest text-foreground uppercase">
                    Docs (Documentation)
                  </h3>
                  <BookOpen size={16} className="text-muted group-hover:text-foreground" />
                </div>
                <div className="space-y-2 font-mono text-[11px]">
                  <div className="flex items-center gap-2 text-muted">
                    <FolderOpen size={14} /> docs/
                  </div>
                  <div className="flex items-center gap-2 text-foreground ml-3">
                    <FileText size={14} /> architecture.md
                  </div>
                  <div className="flex items-center gap-2 text-foreground ml-3">
                    <FileText size={14} /> api-specs.json
                  </div>
                  <div className="flex items-center gap-2 text-muted">
                    <FileText size={14} /> guide.pdf
                  </div>
                </div>
              </div>
            </div>

            {/* Code Block Detail */}
            <div className="mt-6 w-full max-w-5xl bg-surface-high border border-border rounded-lg overflow-hidden">
              <div className="flex items-center justify-between px-5 py-2 bg-surface-container-high border-b border-border">
                <span className="font-mono text-[10px] text-muted uppercase tracking-widest">
                  system_prompt.py
                </span>
                <Copy size={14} className="text-muted cursor-pointer hover:text-foreground" />
              </div>
              <div className="p-6 font-mono text-[12px] text-muted leading-loose overflow-x-auto whitespace-pre">
                <span className="text-accent-breeze">def</span>{' '}
                <span className="text-foreground">analyze_architecture</span>(project_root):
                <br /> structure = fetch_tree(project_root)
                <br />{' '}
                <span className="text-muted/60"># Identify key components for refactoring</span>
                <br /> components = [node <span className="text-accent-breeze">for</span> node{' '}
                <span className="text-accent-breeze">in</span> structure{' '}
                <span className="text-accent-breeze">if</span>{' '}
                <span className="text-foreground">'ui'</span>{' '}
                <span className="text-accent-breeze">in</span> node.name]
                <br /> <span className="text-accent-breeze">return</span>{' '}
                generate_report(components)
              </div>
            </div>
          </motion.div>
        )}
      </div>
    </div>
  );

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex flex-col h-full relative"
    >
      <div className="flex-1 overflow-y-auto">{!hasStarted ? <EmptyState /> : <ActiveState />}</div>

      {/* Input Bar pinned to bottom */}
      <div className="absolute bottom-0 left-0 right-0 bg-background/80 backdrop-blur-md px-4 md:px-12 pb-6 pt-4">
        <div className="max-w-4xl mx-auto flex flex-col gap-3 relative group">
          <form
            onSubmit={handleSubmit}
            className="bg-surface border border-border rounded-[24px] flex items-center px-3 py-2 md:py-3 transition-all focus-within:border-foreground shadow-sm"
          >
            <button type="button" className="text-muted hover:text-foreground p-1 shrink-0">
              <Paperclip size={20} />
            </button>
            <input
              type="text"
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              placeholder="输入指令以重构组件库或分析代码..."
              className="flex-1 min-w-0 w-full bg-transparent border-none focus:outline-none focus:ring-0 text-[15px] px-3 text-foreground placeholder-muted/70"
            />
            <div className="flex items-center gap-2 shrink-0">
              <button type="button" className="text-muted hover:text-foreground p-1">
                <Mic size={20} />
              </button>
              <button
                disabled={isSubmitting}
                type="submit"
                aria-label="发送消息"
                className="bg-foreground text-background rounded-full w-8 h-8 flex items-center justify-center hover:opacity-90 active:scale-95 transition-all disabled:opacity-50"
              >
                <ArrowUp size={18} />
              </button>
            </div>
          </form>
        </div>
      </div>
    </motion.div>
  );
}
