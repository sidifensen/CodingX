import {ArrowUpRight, Sparkles} from 'lucide-react';

/**
 * Supplies the starter prompts rendered on the new task page.
 */
const starterPrompts = [
  '帮我规划一个新功能的前后端实现',
  '分析这个仓库的结构和下一步工作',
  '整理技能、专家、MCP 的产品信息架构',
];

/**
 * Renders the blank new-task page used as the shell entry for future task creation.
 */
export function NewTask() {
  return (
    <div className="mx-auto flex min-h-[calc(100vh-4rem)] w-full max-w-7xl flex-col px-6 py-10 md:px-10 xl:px-14">
      <section className="grid flex-1 gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <div className="flex flex-col rounded-[32px] border border-hairline bg-canvas-card shadow-[0_24px_80px_rgba(10,10,10,0.08)]">
          <div className="border-b border-hairline px-7 py-7 md:px-9">
            <div className="text-xs font-mono uppercase tracking-[0.28em] text-body-mute">
              New Task
            </div>
            <h1 className="mt-4 text-4xl font-semibold tracking-tight text-ink md:text-5xl">
              新建任务
            </h1>
            <p className="mt-4 max-w-2xl text-sm leading-7 text-body md:text-base">
              从这里开始输入新的任务目标。
            </p>
          </div>

          <div className="flex flex-1 flex-col justify-between px-7 py-7 md:px-9">
            <div className="rounded-[28px] border border-dashed border-hairline-translucent bg-canvas-soft/70 p-6">
              <textarea
                aria-label="任务目标输入框"
                placeholder="例如：帮我为 CodingX 设计一个新的任务启动流程，并输出阶段计划。"
                className="min-h-[280px] w-full resize-none bg-transparent text-base leading-8 text-ink outline-none placeholder:text-body-mute"
              />
            </div>

            <div className="mt-6 flex items-center justify-between gap-4 rounded-[28px] border border-hairline bg-canvas-soft px-5 py-4">
              <div className="text-sm text-body-mute">
                当前是全新任务页，右侧不会展示旧任务内容。
              </div>
              <button className="flex items-center gap-2 rounded-full bg-ink px-5 py-2.5 text-sm font-medium text-canvas transition-opacity hover:opacity-90">
                开始执行
                <ArrowUpRight className="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>

        <aside className="rounded-[32px] border border-hairline bg-canvas-card p-6 md:p-8">
          <div className="flex items-center gap-3">
            <Sparkles className="h-5 w-5 text-ink" />
            <h2 className="text-lg font-semibold text-ink">推荐起手式</h2>
          </div>
          <div className="mt-6 flex flex-col gap-4">
            {starterPrompts.map((prompt) => (
              <button
                key={prompt}
                className="rounded-3xl border border-hairline bg-canvas-soft px-5 py-5 text-left text-sm leading-7 text-ink transition-colors hover:border-hairline-translucent hover:bg-canvas"
              >
                {prompt}
              </button>
            ))}
          </div>
        </aside>
      </section>
    </div>
  );
}
