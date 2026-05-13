import {useState} from 'react';
import {ArrowUp, Bot, CheckCircle2, Copy, ExternalLink, Mic, Paperclip, User} from 'lucide-react';

/**
 * Renders the current task detail mock and its assistant conversation timeline.
 */
export function TaskDetail() {
  // Keep the composer input controlled so later task actions can reuse the same state.
  const [inputValue, setInputValue] = useState('');

  return (
    <div className="flex h-full w-full relative">
      <div className="flex-1 flex flex-col relative min-w-0 bg-canvas h-full overflow-hidden">
        <div className="flex-none p-6 md:p-8 border-b border-hairline flex flex-col gap-1 bg-canvas/90 backdrop-blur-md z-10 w-full">
          <div className="flex items-center justify-between">
            <h1 className="text-3xl md:text-4xl font-bold text-ink tracking-tight truncate">AI 系统架构分析</h1>
            <span className="text-xs font-mono text-body-mute whitespace-nowrap hidden sm:block">2024-05-20 14:32</span>
          </div>
          <div className="flex items-center gap-3 mt-2">
            <span className="px-3 py-0.5 rounded-full border border-hairline text-xs font-mono text-body-mute flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-[#ff7a17]"></span>
              进行中
            </span>
            <span className="text-xs font-mono text-body-mute">ID: TSK-0019</span>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-4 md:p-8 flex flex-col gap-8 w-full">
          <div className="flex gap-4 max-w-4xl mx-auto w-full">
            <div className="w-8 h-8 rounded-full bg-canvas-card border border-hairline flex items-center justify-center flex-shrink-0 text-ink">
              <Bot className="w-4 h-4" />
            </div>
            <div className="flex flex-col gap-3 w-full">
              <div className="text-body leading-relaxed">
                我已经完成了基于您提供的需求文档对 AI 系统架构的初步分析。以下是推荐的架构目录结构以及核心模块的 Markdown 设计文档，请查阅。
              </div>
              <div className="grid grid-cols-1 xl:grid-cols-2 gap-4 w-full mt-2">
                <div className="bg-canvas-card border border-hairline rounded-lg flex flex-col overflow-hidden shadow-sm">
                  <div className="px-3 py-2 border-b border-hairline flex items-center justify-between bg-surface-bright/50">
                    <span className="text-xs font-mono text-ink">PROJECT_STRUCTURE</span>
                    <button className="text-body-mute hover:text-ink transition-colors">
                      <Copy className="w-4 h-4" />
                    </button>
                  </div>
                  <div className="p-4 text-xs font-mono text-body whitespace-pre overflow-x-auto text-left">
{`├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── hooks/
│   │   └── utils/
│   └── package.json
├── backend/
│   ├── api/
│   │   ├── routes/
│   │   └── controllers/
│   ├── models/
│   └── main.py
└── docs/
    ├── architecture.md
    └── api_reference.md`}
                  </div>
                </div>
                
                <div className="bg-canvas-card border border-hairline rounded-lg flex flex-col overflow-hidden shadow-sm">
                  <div className="px-3 py-2 border-b border-hairline flex items-center justify-between bg-surface-bright/50">
                    <span className="text-xs font-mono text-ink">architecture.md</span>
                    <button className="text-body-mute hover:text-ink transition-colors">
                      <ExternalLink className="w-4 h-4" />
                    </button>
                  </div>
                  <div className="p-4 text-sm text-body overflow-y-auto max-h-[200px] prose prose-sm text-left dark:prose-invert">
                    <h3 className="text-ink font-semibold mt-0 mb-2 text-lg">系统组件说明</h3>
                    <p className="mb-2">前端采用 <strong className="text-ink">React + TypeScript</strong> 构建，确保类型安全与组件复用性。通过 WebSocket 与后端保持长连接，实现实时状态同步。</p>
                    <p className="mb-2">后端服务基于 <strong className="text-ink">FastAPI (Python)</strong>，提供高性能的异步请求处理能力。核心模型推理任务由独立的 Worker 集群承载，通过 Redis 消息队列进行任务调度。</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="flex gap-4 max-w-4xl self-end flex-row-reverse mx-auto w-full">
            <div className="w-8 h-8 rounded-full bg-surface-bright border border-hairline overflow-hidden flex-shrink-0">
              <User className="w-4 h-4 text-body-mute m-auto mt-2" />
            </div>
            <div className="flex flex-col gap-3 w-full items-end">
              <div className="bg-canvas-card border border-hairline shadow-sm rounded-lg p-3 text-body inline-block">
                结构看起来很清晰。能在 backend 目录下增加一个专门处理外部 API 集成的 services 目录吗？另外，请更新一下文档。
              </div>
            </div>
          </div>

          <div className="flex gap-4 max-w-4xl mx-auto w-full">
            <div className="w-8 h-8 rounded-full bg-canvas-card border border-hairline flex items-center justify-center flex-shrink-0 text-ink">
              <Bot className="w-4 h-4" />
            </div>
            <div className="flex flex-col gap-3 w-full">
              <div className="text-body leading-relaxed">
                没问题，已在 backend 目录下新增 \`services/\` 目录用于统一管理外部 API 集成（如 LLM 接口调用、第三方认证等）。
              </div>
              <div className="bg-canvas-card border border-hairline rounded-lg flex flex-col overflow-hidden max-w-2xl mt-1 shadow-sm">
                <div className="px-3 py-2 border-b border-hairline flex items-center justify-between bg-surface-bright/50">
                  <div className="flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 text-body-mute" />
                    <span className="text-xs font-mono text-ink">更新完成: 项目树 & 文档</span>
                  </div>
                  <span className="text-xs font-mono text-body-mute">v1.1</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="flex-none p-4 md:p-8 bg-canvas w-full max-w-4xl mx-auto">
          <div className="flex items-center bg-canvas-soft border border-hairline rounded-full px-3 py-2 shadow-sm focus-within:border-outline-variant transition-colors">
            <button className="p-2 text-body-mute hover:text-ink transition-colors rounded-full hover:bg-ink/5">
              <Paperclip className="w-5 h-5" />
            </button>
            <input 
              type="text" 
              className="flex-1 bg-transparent border-none text-ink placeholder:text-body-mute focus:ring-0 px-3 py-2 outline-none" 
              placeholder="输入指令或提问..." 
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
            />
            <button className="p-2 text-body-mute hover:text-ink transition-colors rounded-full hover:bg-ink/5">
              <Mic className="w-5 h-5" />
            </button>
            <button className="p-2 ml-1 bg-ink text-canvas rounded-full hover:opacity-90 transition-opacity flex items-center justify-center">
              <ArrowUp className="w-5 h-5" />
            </button>
          </div>
          <div className="text-center mt-2">
            <span className="text-xs font-mono text-body-mute">CodingX AI 可能产生不准确的信息，请核实重要内容。</span>
          </div>
        </div>
      </div>
    </div>
  );
}
