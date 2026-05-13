import {ArrowRight, History, LayoutPanelTop, TimerReset} from 'lucide-react';

/**
 * Supplies the quick actions rendered on the Tasks landing page.
 */
const quickActions = [
  '分析代码仓库结构',
  '生成阶段计划文档',
  '梳理技能与专家入口',
  '继续最近一次任务',
];

/**
 * Supplies the recent task summaries rendered on the Tasks landing page.
 */
const recentTasks = [
  {name: 'Agent Runtime 架构整理', state: '进行中', detail: '2 个步骤等待执行'},
  {name: 'Phase 1 Web Shell 收口', state: '已完成', detail: 'Vite+ 骨架迁移'},
  {name: 'MCP 管理信息架构', state: '待开始', detail: '等待需求补充'},
];

/**
 * Renders the default task landing page for the CodingX shell.
 */
export function Tasks() {
  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-10 px-6 py-8 md:px-10 xl:px-14">
      <section className="grid gap-6 xl:grid-cols-[1.4fr_0.9fr]">
        <div className="overflow-hidden rounded-[28px] border border-hairline bg-canvas-card shadow-[0_24px_80px_rgba(10,10,10,0.08)]">
          <div className="border-b border-hairline px-6 py-6 md:px-8">
            <div className="text-xs font-mono uppercase tracking-[0.28em] text-body-mute">
              Task Workspace
            </div>
            <h1 className="mt-3 text-4xl font-semibold tracking-tight text-ink md:text-5xl">
              任务工作台
            </h1>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-body md:text-base">
              这一页是新的默认入口。它承载正式导航骨架，但不改动原有展示页内容，先为后续任务列表、启动台和工作台留出稳定位置。
            </p>
          </div>

          <div className="grid gap-6 px-6 py-6 md:grid-cols-2 md:px-8">
            {quickActions.map((item) => (
              <button
                key={item}
                className="group flex items-center justify-between rounded-3xl border border-hairline bg-canvas-soft px-5 py-5 text-left transition-colors hover:border-hairline-translucent hover:bg-canvas"
              >
                <span className="text-sm font-medium text-ink">{item}</span>
                <ArrowRight className="h-4 w-4 text-body-mute transition-transform group-hover:translate-x-1 group-hover:text-ink" />
              </button>
            ))}
          </div>
        </div>

        <div className="grid gap-6">
          <div className="rounded-[28px] border border-hairline bg-canvas-card p-6">
            <div className="flex items-center gap-3">
              <LayoutPanelTop className="h-5 w-5 text-ink" />
              <h2 className="text-lg font-semibold text-ink">当前外壳范围</h2>
            </div>
            <ul className="mt-4 space-y-3 text-sm leading-6 text-body">
              <li>迁移到 Vite+</li>
              <li>顶部栏与侧边栏组件化</li>
              <li>补齐任务 / 工具 / MCP 入口</li>
              <li>保留原页面主体展示内容</li>
            </ul>
          </div>

          <div className="rounded-[28px] border border-hairline bg-canvas-card p-6">
            <div className="flex items-center gap-3">
              <TimerReset className="h-5 w-5 text-ink" />
              <h2 className="text-lg font-semibold text-ink">下一步</h2>
            </div>
            <p className="mt-4 text-sm leading-6 text-body">
              这里后续会接入真实任务创建、历史任务和工作台状态卡片。
            </p>
          </div>
        </div>
      </section>

      <section className="rounded-[28px] border border-hairline bg-canvas-card p-6 md:p-8">
        <div className="flex items-center gap-3">
          <History className="h-5 w-5 text-ink" />
          <h2 className="text-xl font-semibold text-ink">最近任务</h2>
        </div>
        <div className="mt-6 grid gap-4">
          {recentTasks.map((task) => (
            <div
              key={task.name}
              className="flex flex-col gap-2 rounded-3xl border border-hairline bg-canvas-soft px-5 py-4 md:flex-row md:items-center md:justify-between"
            >
              <div>
                <div className="text-base font-medium text-ink">{task.name}</div>
                <div className="text-sm text-body-mute">{task.detail}</div>
              </div>
              <div className="text-xs font-mono uppercase tracking-[0.22em] text-body-mute">
                {task.state}
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
