import {Blocks, Network, ShieldCheck} from 'lucide-react';

/**
 * Supplies the MCP overview modules rendered on the MCP landing page.
 */
const modules = [
  {
    title: 'MCP 服务目录',
    body: '统一展示已接入与待接入的 MCP 服务，而不是散落在页面里。',
    icon: Blocks,
  },
  {
    title: '连接状态',
    body: '后续将承载服务在线状态、权限范围和作用域展示。',
    icon: Network,
  },
  {
    title: '权限边界',
    body: '为平台内置工具与外部 MCP 扩展建立清晰分层。',
    icon: ShieldCheck,
  },
];

/**
 * Renders the MCP landing page and reserves space for future hub management features.
 */
export function Mcp() {
  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-8 px-6 py-8 md:px-10 xl:px-14">
      <header>
        <div className="text-xs font-mono uppercase tracking-[0.28em] text-body-mute">
          MCP
        </div>
        <h1 className="mt-3 text-4xl font-semibold tracking-tight text-ink">MCP</h1>
        <p className="mt-3 max-w-2xl text-sm leading-7 text-body">
          当前先补正式导航入口和版位，后续再接入真实 MCP Hub、连接状态和管理逻辑。
        </p>
      </header>

      <section className="grid gap-5 md:grid-cols-3">
        {modules.map((module) => (
          <article
            key={module.title}
            className="rounded-[28px] border border-hairline bg-canvas-card p-6"
          >
            <module.icon className="h-6 w-6 text-ink" />
            <h2 className="mt-6 text-lg font-semibold text-ink">{module.title}</h2>
            <p className="mt-3 text-sm leading-6 text-body">{module.body}</p>
          </article>
        ))}
      </section>
    </div>
  );
}
