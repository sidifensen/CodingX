import React from 'react';
import { Link } from 'react-router-dom';
import {
  AdminChatApi,
  AdminChatRuntimeDashboardView,
  AdminDashboardView,
} from '../api/adminChatApi';

export function Dashboard() {
  const [dashboard, setDashboard] = React.useState<AdminDashboardView | null>(null);
  const [runtimeDashboard, setRuntimeDashboard] = React.useState<AdminChatRuntimeDashboardView | null>(null);

  React.useEffect(() => {
    void AdminChatApi.getDashboard().then(setDashboard);
    void AdminChatApi.getRuntimeDashboard().then(setRuntimeDashboard);
  }, []);

  return (
    <>
      <div className="sky-wash pt-xl pb-section px-xl">
        <div className="w-full">
          <div className="mb-lg">
            <h2 className="font-headline-md text-headline-md text-ink">工作台首页</h2>
            <p className="text-secondary mt-xs">欢迎回来，这是您的系统实时运行概览。</p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-4 gap-md">
            <Link to="/users" className="bg-surface-container-lowest p-lg rounded-xl border border-border-hairline hover:shadow-[0_4px_12px_rgba(0,0,0,0.04)] transition-all group block active:scale-[0.98]">
              <div className="flex justify-between items-start mb-sm">
                <span className="font-label-caps text-label-caps text-secondary uppercase tracking-widest">用户总数</span>
                <span className="material-symbols-outlined text-secondary group-hover:text-primary transition-colors">group</span>
              </div>
              <div className="font-metric-lg text-metric-lg text-ink font-bold">{dashboard?.intentNodeCount ?? '-'}</div>
              <div className="mt-xs flex items-center gap-1 text-[12px] text-status-running">
                <span className="material-symbols-outlined text-[14px]">trending_up</span>
                <span>+12% 较上月</span>
              </div>
            </Link>
            
            <Link to="/tasks" className="bg-surface-container-lowest p-lg rounded-xl border border-border-hairline hover:shadow-[0_4px_12px_rgba(0,0,0,0.04)] transition-all group block active:scale-[0.98]">
              <div className="flex justify-between items-start mb-sm">
                <span className="font-label-caps text-label-caps text-secondary uppercase tracking-widest">会话总数</span>
                <span className="material-symbols-outlined text-secondary group-hover:text-primary transition-colors">assignment</span>
              </div>
              <div className="font-metric-lg text-metric-lg text-ink font-bold">{dashboard?.mappingCount ?? '-'}</div>
              <div className="mt-xs flex items-center gap-1 text-[12px] text-secondary">
                <span className="material-symbols-outlined text-[14px]">history</span>
                <span>近24小时新增 142</span>
              </div>
            </Link>

            <Link to="/tasks" className="bg-surface-container-lowest p-lg rounded-xl border border-border-hairline hover:shadow-[0_4px_12px_rgba(0,0,0,0.04)] transition-all group block active:scale-[0.98]">
              <div className="flex justify-between items-start mb-sm">
                <span className="font-label-caps text-label-caps text-secondary uppercase tracking-widest">活跃会话</span>
                <div className="w-2 h-2 rounded-full bg-status-running animate-pulse"></div>
              </div>
              <div className="font-metric-lg text-metric-lg text-ink font-bold">{dashboard?.runningTraceCount ?? '-'}</div>
              <div className="mt-xs">
                <span className="px-xs py-0.5 bg-status-running-bg text-status-running rounded text-[10px] font-bold border border-status-running-border">HEALTHY</span>
              </div>
            </Link>

            <Link to="/tasks" className="bg-surface-container-lowest p-lg rounded-xl border border-border-hairline hover:shadow-[0_4px_12px_rgba(0,0,0,0.04)] transition-all group block active:scale-[0.98]">
              <div className="flex justify-between items-start mb-sm">
                <span className="font-label-caps text-label-caps text-secondary uppercase tracking-widest">异常会话</span>
                <span className="material-symbols-outlined text-status-failed transition-colors">error_outline</span>
              </div>
              <div className="font-metric-lg text-metric-lg text-ink font-bold">{dashboard?.traceCount ?? '-'}</div>
              <div className="mt-xs">
                <span className="px-xs py-0.5 bg-status-failed-bg text-status-failed rounded text-[10px] font-bold border border-status-failed-border">CRITICAL</span>
              </div>
            </Link>
          </div>
        </div>
      </div>

      <div className="px-xl -mt-md pb-section">
        <div className="w-full space-y-xl">
          <section>
            <h3 className="font-title-md text-title-md text-ink mb-md flex items-center gap-xs">
              <span className="material-symbols-outlined text-secondary">bolt</span>
              快捷入口
            </h3>
            <div className="grid grid-cols-2 md:grid-cols-5 gap-md">
              <Link to="/users" className="flex flex-col items-center justify-center p-xl bg-surface-container-lowest border border-border-hairline rounded-xl hover:border-ink hover:bg-surface-container-low transition-all group active:scale-95">
                <div className="w-12 h-12 bg-surface-container rounded-lg flex items-center justify-center mb-sm group-hover:bg-ink group-hover:text-on-ink transition-colors">
                  <span className="material-symbols-outlined text-[28px]">group</span>
                </div>
                <span className="font-medium text-ink">用户管理</span>
              </Link>
              <Link to="/tasks" className="flex flex-col items-center justify-center p-xl bg-surface-container-lowest border border-border-hairline rounded-xl hover:border-ink hover:bg-surface-container-low transition-all group active:scale-95">
                <div className="w-12 h-12 bg-surface-container rounded-lg flex items-center justify-center mb-sm group-hover:bg-ink group-hover:text-on-ink transition-colors">
                  <span className="material-symbols-outlined text-[28px]">assignment</span>
                </div>
                <span className="font-medium text-ink">会话管理</span>
              </Link>
              <Link to="/skills" className="flex flex-col items-center justify-center p-xl bg-surface-container-lowest border border-border-hairline rounded-xl hover:border-ink hover:bg-surface-container-low transition-all group active:scale-95">
                <div className="w-12 h-12 bg-surface-container rounded-lg flex items-center justify-center mb-sm group-hover:bg-ink group-hover:text-on-ink transition-colors">
                  <span className="material-symbols-outlined text-[28px]">extension</span>
                </div>
                <span className="font-medium text-ink">技能管理</span>
              </Link>
              <Link to="/tools" className="flex flex-col items-center justify-center p-xl bg-surface-container-lowest border border-border-hairline rounded-xl hover:border-ink hover:bg-surface-container-low transition-all group active:scale-95">
                <div className="w-12 h-12 bg-surface-container rounded-lg flex items-center justify-center mb-sm group-hover:bg-ink group-hover:text-on-ink transition-colors">
                  <span className="material-symbols-outlined text-[28px]">build_circle</span>
                </div>
                <span className="font-medium text-ink">工具管理</span>
              </Link>
              <Link to="/mcp" className="flex flex-col items-center justify-center p-xl bg-surface-container-lowest border border-border-hairline rounded-xl hover:border-ink hover:bg-surface-container-low transition-all group active:scale-95">
                <div className="w-12 h-12 bg-surface-container rounded-lg flex items-center justify-center mb-sm group-hover:bg-ink group-hover:text-on-ink transition-colors">
                  <span className="material-symbols-outlined text-[28px]">terminal</span>
                </div>
                <span className="font-medium text-ink">MCP管理</span>
              </Link>
              <Link to="/traces" className="flex flex-col items-center justify-center p-xl bg-surface-container-lowest border border-border-hairline rounded-xl hover:border-ink hover:bg-surface-container-low transition-all group active:scale-95">
                <div className="w-12 h-12 bg-surface-container rounded-lg flex items-center justify-center mb-sm group-hover:bg-ink group-hover:text-on-ink transition-colors">
                  <span className="material-symbols-outlined text-[28px]">account_tree</span>
                </div>
                <span className="font-medium text-ink">Trace管理</span>
              </Link>
              <Link to="/intent-tree" className="flex flex-col items-center justify-center p-xl bg-surface-container-lowest border border-border-hairline rounded-xl hover:border-ink hover:bg-surface-container-low transition-all group active:scale-95">
                <div className="w-12 h-12 bg-surface-container rounded-lg flex items-center justify-center mb-sm group-hover:bg-ink group-hover:text-on-ink transition-colors">
                  <span className="material-symbols-outlined text-[28px]">schema</span>
                </div>
                <span className="font-medium text-ink">意图树</span>
              </Link>
              <Link to="/query-term-mappings" className="flex flex-col items-center justify-center p-xl bg-surface-container-lowest border border-border-hairline rounded-xl hover:border-ink hover:bg-surface-container-low transition-all group active:scale-95">
                <div className="w-12 h-12 bg-surface-container rounded-lg flex items-center justify-center mb-sm group-hover:bg-ink group-hover:text-on-ink transition-colors">
                  <span className="material-symbols-outlined text-[28px]">manage_search</span>
                </div>
                <span className="font-medium text-ink">关键词映射</span>
              </Link>
              <Link to="/settings" className="flex flex-col items-center justify-center p-xl bg-surface-container-lowest border border-border-hairline rounded-xl hover:border-ink hover:bg-surface-container-low transition-all group active:scale-95">
                <div className="w-12 h-12 bg-surface-container rounded-lg flex items-center justify-center mb-sm group-hover:bg-ink group-hover:text-on-ink transition-colors">
                  <span className="material-symbols-outlined text-[28px]">settings</span>
                </div>
                <span className="font-medium text-ink">系统配置</span>
              </Link>
            </div>
          </section>

          <section>
            <h3 className="font-title-md text-title-md text-ink mb-md flex items-center gap-xs">
              <span className="material-symbols-outlined text-secondary">monitoring</span>
              聊天运行时观测
            </h3>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-md">
              <div className="rounded-xl border border-border-hairline bg-surface-container-lowest p-lg">
                <div className="flex items-center justify-between mb-sm">
                  <span className="font-label-caps text-label-caps text-secondary uppercase tracking-widest">队列门控</span>
                  <span className="rounded-full border border-border-hairline px-2 py-0.5 text-[11px] text-secondary">
                    {runtimeDashboard?.queue.mode ?? '-'}
                  </span>
                </div>
                <div className="grid grid-cols-2 gap-sm text-sm">
                  <div className="rounded-lg bg-surface-container px-3 py-2">
                    <div className="text-secondary text-[12px]">最大并发</div>
                    <div className="text-ink font-semibold">{runtimeDashboard?.queue.maxConcurrent ?? '-'}</div>
                  </div>
                  <div className="rounded-lg bg-surface-container px-3 py-2">
                    <div className="text-secondary text-[12px]">活跃执行</div>
                    <div className="text-ink font-semibold">{runtimeDashboard?.queue.activeCount ?? '-'}</div>
                  </div>
                  <div className="rounded-lg bg-surface-container px-3 py-2">
                    <div className="text-secondary text-[12px]">排队数量</div>
                    <div className="text-ink font-semibold">{runtimeDashboard?.queue.waitingCount ?? '-'}</div>
                  </div>
                  <div className="rounded-lg bg-surface-container px-3 py-2">
                    <div className="text-secondary text-[12px]">可用许可</div>
                    <div className="text-ink font-semibold">{runtimeDashboard?.queue.availablePermits ?? '-'}</div>
                  </div>
                </div>
              </div>

              <div className="rounded-xl border border-border-hairline bg-surface-container-lowest p-lg">
                <div className="mb-sm">
                  <span className="font-label-caps text-label-caps text-secondary uppercase tracking-widest">线程池状态</span>
                </div>
                <div className="space-y-sm text-sm">
                  <div className="rounded-lg border border-border-hairline px-3 py-2">
                    <div className="font-medium text-ink mb-1">chatStreamExecutor</div>
                    <div className="text-secondary">
                      活跃 {runtimeDashboard?.executor.streamActiveCount ?? '-'} / 池 {runtimeDashboard?.executor.streamPoolSize ?? '-'} / 队列 {runtimeDashboard?.executor.streamQueueSize ?? '-'} / 剩余 {runtimeDashboard?.executor.streamQueueRemainingCapacity ?? '-'}
                    </div>
                  </div>
                  <div className="rounded-lg border border-border-hairline px-3 py-2">
                    <div className="font-medium text-ink mb-1">searchExecutor</div>
                    <div className="text-secondary">
                      活跃 {runtimeDashboard?.executor.searchActiveCount ?? '-'} / 池 {runtimeDashboard?.executor.searchPoolSize ?? '-'} / 队列 {runtimeDashboard?.executor.searchQueueSize ?? '-'} / 剩余 {runtimeDashboard?.executor.searchQueueRemainingCapacity ?? '-'}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-xl">
            <div className="lg:col-span-2 bg-surface-container-lowest rounded-xl border border-border-hairline overflow-hidden">
              <div className="px-lg py-md border-b border-border-hairline flex justify-between items-center">
                <h3 className="font-title-md text-title-md text-ink">最新系统动态</h3>
                <button className="text-tertiary-container hover:underline font-medium text-[12px]">查看全部</button>
              </div>
              <table className="w-full text-left">
                <thead className="bg-surface-container-low font-label-caps text-label-caps text-on-secondary-container">
                  <tr>
                    <th className="px-lg py-sm font-semibold uppercase">会话 ID</th>
                    <th className="px-lg py-sm font-semibold uppercase">执行人</th>
                    <th className="px-lg py-sm font-semibold uppercase">状态</th>
                    <th className="px-lg py-sm font-semibold uppercase">耗时</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-hairline">
                  <tr className="hover:bg-surface-container-low transition-colors group cursor-pointer">
                    <td className="px-lg py-md font-data-mono text-secondary">#TX-9021</td>
                    <td className="px-lg py-md text-ink">Li Wei (Admin)</td>
                    <td className="px-lg py-md">
                      <span className="flex items-center gap-1.5 text-status-running">
                        <span className="w-1.5 h-1.5 rounded-full bg-status-running"></span> 运行中
                      </span>
                    </td>
                    <td className="px-lg py-md text-secondary">12m 4s</td>
                  </tr>
                  <tr className="hover:bg-surface-container-low transition-colors group cursor-pointer">
                    <td className="px-lg py-md font-data-mono text-secondary">#TX-8998</td>
                    <td className="px-lg py-md text-ink">Zhang San</td>
                    <td className="px-lg py-md">
                      <span className="flex items-center gap-1.5 text-ink">
                        <span className="w-1.5 h-1.5 rounded-full bg-border-strong"></span> 已完成
                      </span>
                    </td>
                    <td className="px-lg py-md text-secondary">4m 22s</td>
                  </tr>
                  <tr className="hover:bg-surface-container-low transition-colors group cursor-pointer">
                    <td className="px-lg py-md font-data-mono text-secondary">#TX-8842</td>
                    <td className="px-lg py-md text-ink">System Root</td>
                    <td className="px-lg py-md">
                      <span className="flex items-center gap-1.5 text-status-failed">
                        <span className="w-1.5 h-1.5 rounded-full bg-status-failed"></span> 失败
                      </span>
                    </td>
                    <td className="px-lg py-md text-secondary">--</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div className="space-y-md">
              <div className="bg-surface-container-lowest p-lg rounded-xl border border-border-hairline">
                <h3 className="font-title-md text-title-md text-ink mb-md">MCP 状态监控</h3>
                <div className="space-y-sm">
                  <div className="flex justify-between items-center text-body-sm">
                    <span className="text-secondary">API 服务响应</span>
                    <span className="text-status-running font-medium">99.98%</span>
                  </div>
                  <div className="w-full bg-surface-container rounded-full h-1">
                    <div className="bg-status-running h-1 rounded-full" style={{ width: '99.98%' }}></div>
                  </div>
                  <div className="flex justify-between items-center text-body-sm pt-xs">
                    <span className="text-secondary">计算节点负载</span>
                    <span className="text-status-pending font-medium">64%</span>
                  </div>
                  <div className="w-full bg-surface-container rounded-full h-1">
                    <div className="bg-status-pending h-1 rounded-full" style={{ width: '64%' }}></div>
                  </div>
                  <div className="flex justify-between items-center text-body-sm pt-xs">
                    <span className="text-secondary">存储容量</span>
                    <span className="text-ink font-medium">1.2 TB / 4 TB</span>
                  </div>
                  <div className="w-full bg-surface-container rounded-full h-1">
                    <div className="bg-ink h-1 rounded-full" style={{ width: '30%' }}></div>
                  </div>
                </div>
              </div>

              <div className="relative h-40 rounded-xl overflow-hidden group">
                <img className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-110" alt="Networking visual" src="https://lh3.googleusercontent.com/aida-public/AB6AXuC7ZhEOC4yrviB1_9fvteqs4M1Uv-08f-0ebu3HUFSYEXbAS6l1vX1V0MrcFyqxRL2dHZYPWLpCMhiSkkaFLtJqFsIwB1vFV_Ro4heyzwBhxqqmGc-VEXRRIf6YGmj_2vy9Wyp2vXRl6RhMpOnMjh7kuPh-WkvlHVDPRvRKD9EbEBSR4U2gGad4UaUCM3uFXzKjqG_rYYZqfEyOKC9_9oXIP_gWZDG-yjrmfVNogSyUBjkw1tN4dQgt3ukFfSZUBzrREyvC327ASAU"/>
                <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent flex items-end p-md">
                  <div>
                    <p className="text-white font-bold">MCP v3.0 已上线</p>
                    <p className="text-white/70 text-[11px]">探索更高效的资源调度机制</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <button className="fixed bottom-lg right-lg w-14 h-14 bg-ink text-on-ink rounded-full shadow-lg hover:scale-105 active:scale-95 transition-all flex items-center justify-center group z-50 overflow-visible">
        <span className="material-symbols-outlined text-[28px]">add</span>
        <span className="absolute right-full mr-md px-sm py-1 bg-ink text-on-ink text-[12px] rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none">创建新会话</span>
      </button>
    </>
  );
}
