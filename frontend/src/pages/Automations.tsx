import {Plus, Play, Settings2, TrendingUp, Zap} from 'lucide-react';

/**
 * Supplies the static automation list rendered on the Automations page.
 */
const automationsList = [
  { id: 1, name: '每日 GitHub 代码提交汇总', desc: '每天下午 6 点自动拉取指定仓库的 commit 记录，并生成 AI 总结报告推送到工作群。', trigger: '定时触发', stats: { runs: 124, last: '1 小时前', status: 'success' } },
  { id: 2, name: '异常日志告警通知', desc: '监控生产环境日志流，匹配关键错误特征后，立即通过电话和短信通知值班人。', trigger: 'Webhook', stats: { runs: 8, last: '2 天前', status: 'success' } },
  { id: 3, name: '用户反馈情感分析', desc: '监听收集到的新产品反馈表单，自动执行情感极性分析，严重负向反馈自动创建高优工单。', trigger: '事件触发', stats: { runs: 45, last: '15 分钟前', status: 'error' } },
  { id: 4, name: '月底账单自动化生成', desc: '每月最后一天连接财务数据库，生成多维度支出报表并发送至管理团队邮箱。', trigger: '定时触发', stats: { runs: 12, last: '5 天前', status: 'success' } },
];

/**
 * Renders the automation landing page for the current shell milestone.
 */
export function Automations() {
  return (
    <div className="p-6 md:p-12 lg:p-16 w-full max-w-7xl mx-auto flex flex-col gap-10 pb-24 text-left">
      <header className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <h1 className="text-4xl md:text-5xl lg:text-7xl font-bold text-ink tracking-tight mb-2">自动化流水线</h1>
          <p className="text-lg text-body-mute">连接应用与数据，构建无人值守的自动化工作流。</p>
        </div>
        <div className="flex items-center gap-3">
          <button className="px-6 py-2.5 bg-ink text-canvas rounded-full text-sm font-medium hover:opacity-90 transition-opacity flex items-center gap-2 whitespace-nowrap shadow-sm">
            <Plus className="w-4 h-4" />
            新建工作流
          </button>
        </div>
      </header>

      <section>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
          <div className="bg-canvas-soft border border-hairline rounded-xl p-5 flex flex-col gap-2">
            <span className="text-xs font-mono text-body-mute uppercase">运行总计</span>
            <span className="text-3xl font-bold text-ink">1,204</span>
          </div>
          <div className="bg-canvas-soft border border-hairline rounded-xl p-5 flex flex-col gap-2">
            <span className="text-xs font-mono text-body-mute uppercase">当前活跃</span>
            <span className="text-3xl font-bold text-ink">18</span>
          </div>
          <div className="bg-surface-variant border border-hairline rounded-xl p-5 flex flex-col gap-2">
            <span className="text-xs font-mono text-body-mute uppercase">执行成功率</span>
            <div className="flex items-center gap-2">
              <span className="text-3xl font-bold text-ink">99.8%</span>
              <TrendingUp className="w-4 h-4 text-ink opacity-50" />
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3 mb-6">
          <span className="text-sm font-mono text-body-mute uppercase tracking-[1.4px]">所有编排 (4)</span>
          <div className="h-px bg-hairline flex-1"></div>
        </div>

        <div className="flex flex-col gap-4">
          {automationsList.map(item => (
            <div key={item.id} className="bg-canvas-card border border-hairline rounded-xl p-6 flex flex-col lg:flex-row lg:items-center justify-between gap-6 hover:border-outline-variant transition-colors group shadow-sm">
              <div className="flex gap-4 items-start flex-1 min-w-0">
                <div className="w-10 h-10 rounded-full bg-surface-bright border border-hairline flex items-center justify-center shrink-0 mt-1">
                  <Zap className="w-5 h-5 text-ink" />
                </div>
                <div className="flex flex-col gap-1 min-w-0">
                  <div className="flex items-center gap-3 flex-wrap">
                    <h3 className="text-base font-bold text-ink truncate">{item.name}</h3>
                    <span className="px-2 py-0.5 rounded text-xs font-mono bg-canvas-soft border border-hairline text-body-mute">{item.trigger}</span>
                  </div>
                  <p className="text-sm text-body-mute line-clamp-2 md:line-clamp-1">{item.desc}</p>
                </div>
              </div>
              
              <div className="flex items-center justify-between lg:justify-end gap-6 shrink-0 border-t border-hairline lg:border-t-0 pt-4 lg:pt-0">
                <div className="flex gap-6">
                  <div className="flex flex-col gap-1">
                    <span className="text-xs font-mono text-body-mute uppercase">RUNS</span>
                    <span className="text-sm font-medium text-ink">{item.stats.runs}</span>
                  </div>
                  <div className="flex flex-col gap-1">
                    <span className="text-xs font-mono text-body-mute uppercase">LAST</span>
                    <span className="text-sm font-medium text-ink flex items-center gap-1.5">
                      {item.stats.status === 'success' ? (
                        <span className="w-2 h-2 rounded-full bg-[#10b981]" />
                      ) : (
                        <span className="w-2 h-2 rounded-full bg-[#ef4444]" />
                      )}
                      {item.stats.last}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button className="w-8 h-8 rounded border border-hairline-translucent flex items-center justify-center text-body-mute hover:text-ink hover:border-outline transition-colors" title="立即运行">
                    <Play className="w-4 h-4" />
                  </button>
                  <button className="w-8 h-8 rounded border border-hairline-translucent flex items-center justify-center text-body-mute hover:text-ink hover:border-outline transition-colors" title="编辑编排">
                    <Settings2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
