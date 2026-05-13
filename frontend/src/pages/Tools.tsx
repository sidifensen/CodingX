import {BriefcaseBusiness, Terminal, Wrench} from 'lucide-react';

/**
 * Supplies the tool overview cards rendered on the Tools page.
 */
const toolCards = [
  {
    title: '内置工具目录',
    body: '为后续网页搜索、文件处理、命令执行等平台原生工具预留统一入口。',
    icon: BriefcaseBusiness,
  },
  {
    title: '运行能力',
    body: '后续在这里区分云端能力、本地能力与宿主能力暴露情况。',
    icon: Terminal,
  },
  {
    title: '工具策略',
    body: '未来会把工具与任务模式、专家、技能的绑定关系在这一层管理。',
    icon: Wrench,
  },
];

/**
 * Renders the tool capability landing page for the current shell milestone.
 */
export function Tools() {
  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-8 px-6 py-8 md:px-10 xl:px-14">
      <header>
        <div className="text-xs font-mono uppercase tracking-[0.28em] text-body-mute">
          Tools
        </div>
        <h1 className="mt-3 text-4xl font-semibold tracking-tight text-ink">工具</h1>
        <p className="mt-3 max-w-2xl text-sm leading-7 text-body">
          这是新的正式入口。当前只补壳层，不接真实工具逻辑。
        </p>
      </header>

      <section className="grid gap-5 md:grid-cols-3">
        {toolCards.map((card) => (
          <article
            key={card.title}
            className="rounded-[28px] border border-hairline bg-canvas-card p-6"
          >
            <card.icon className="h-6 w-6 text-ink" />
            <h2 className="mt-6 text-lg font-semibold text-ink">{card.title}</h2>
            <p className="mt-3 text-sm leading-6 text-body">{card.body}</p>
          </article>
        ))}
      </section>
    </div>
  );
}
