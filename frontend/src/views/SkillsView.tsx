import React, { useState } from 'react';
import { motion } from 'motion/react';
import {
  Globe,
  Terminal,
  Search,
  FileText,
  TrendingUp,
  ArrowRight,
  Sparkles,
  CloudLightning,
  Video,
  MessageSquare,
} from 'lucide-react';

/**
 * 渲染技能库视图。
 */
export default function SkillsView() {
  const [activeCategory, setActiveCategory] = useState('全部');

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -20 }}
      className="flex flex-col h-full overflow-y-auto px-6 md:px-12 py-8 bg-background"
    >
      <div className="max-w-7xl mx-auto w-full">
        {/* Page Header */}
        <div className="mb-12">
          <h1 className="text-4xl md:text-5xl font-bold text-foreground mb-2 tracking-tight">
            技能库
          </h1>
          <p className="text-base text-muted max-w-lg leading-relaxed">
            管理和扩展您的工作台能力，发现更多强大的 AI 工具与套件。
          </p>
        </div>

        {/* Section 1: Installed Skills */}
        <section className="mb-16">
          <div className="flex items-center justify-between mb-6">
            <h2 className="font-mono text-xs text-muted tracking-widest uppercase">已安装 (5)</h2>
            <button className="text-sm text-muted hover:text-foreground transition-colors">
              管理
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-6">
            {[
              {
                id: 'agent-browser',
                title: 'agent-browser',
                desc: '允许 AI 代理安全地在浏览器中执行网页浏览和操作任务。',
                icon: Globe,
                type: '套件',
              },
              {
                id: 'playwright-cli',
                title: 'playwright-cli',
                desc: '使用 Playwright 进行自动化测试和复杂的网页交互操作。',
                icon: Terminal,
                type: '套件',
              },
              {
                id: 'find-skills',
                title: 'find-skills',
                desc: '在庞大的技能库中通过语义理解快速寻找所需的能力模块。',
                icon: Search,
                type: '工具',
              },
              {
                id: 'document-skills',
                title: 'document-skills',
                desc: '文档解析与智能摘要工具，支持多格式、高并发文档处理。',
                icon: FileText,
                type: '套件',
              },
              {
                id: 'finance-data',
                title: 'finance-data',
                desc: '实时金融市场数据接入，提供专业的分析报告生成能力。',
                icon: TrendingUp,
                type: '工具',
              },
            ].map((skill) => (
              <div
                key={skill.id}
                className="bg-surface-container border border-border rounded-lg p-5 hover:border-border-active transition-colors cursor-pointer group shadow-sm flex flex-col h-full"
              >
                <div className="flex items-center justify-between mb-4">
                  <div className="w-10 h-10 rounded-full bg-surface-high border border-border flex items-center justify-center">
                    <skill.icon size={20} className="text-foreground" />
                  </div>
                  <span className="bg-surface-high text-muted font-mono text-[10px] px-2 py-0.5 rounded tracking-widest uppercase">
                    {skill.type}
                  </span>
                </div>
                <h3 className="font-semibold text-foreground mb-1 mt-auto">{skill.title}</h3>
                <p className="text-[13px] text-muted line-clamp-2 leading-relaxed">{skill.desc}</p>
              </div>
            ))}
          </div>
        </section>

        {/* Section 2: Skill Market */}
        <section className="mb-12">
          <div className="flex items-center justify-between mb-6 border-b border-border">
            <div className="flex gap-8">
              <button className="text-sm font-bold text-foreground border-b-2 border-foreground pb-3 relative -bottom-[1px]">
                推荐
              </button>
              <button className="text-sm font-medium text-muted hover:text-foreground pb-3 transition-colors border-b-2 border-transparent">
                SkillHub
              </button>
              <button className="text-sm font-medium text-muted hover:text-foreground pb-3 transition-colors border-b-2 border-transparent">
                套件
              </button>
            </div>
          </div>

          {/* Category Tags */}
          <div className="flex flex-wrap gap-2 mb-8">
            {['全部', '生活服务', '开发工具', '商务办公', '数据分析', '学术研究'].map((cat) => (
              <button
                key={cat}
                onClick={() => setActiveCategory(cat)}
                className={`font-mono text-[11px] px-5 py-1.5 rounded-full transition-colors ${
                  activeCategory === cat
                    ? 'bg-foreground text-background font-bold tracking-widest'
                    : 'bg-surface-high border border-border text-muted hover:border-border-active tracking-widest'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>

          {/* Market Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {/* Market Card 1 */}
            <div className="bg-surface-container border border-border rounded-xl p-6 hover:border-border-active transition-all flex flex-col gap-4 group cursor-pointer shadow-sm">
              <div className="flex gap-5">
                <div className="w-16 h-16 rounded-xl bg-surface-high border border-border flex items-center justify-center shrink-0 overflow-hidden">
                  <div className="w-full h-full bg-gradient-to-br from-[#1d4ed8] to-[#0f172a] opacity-80 flex items-center justify-center">
                    <FileText size={28} className="text-white" />
                  </div>
                </div>
                <div className="flex-1">
                  <div className="flex items-start justify-between">
                    <h4 className="text-base font-bold text-foreground tracking-tight">腾讯文档</h4>
                    <ArrowRight
                      size={18}
                      className="text-muted opacity-0 group-hover:opacity-100 transition-opacity"
                    />
                  </div>
                  <p className="text-[13px] text-muted mt-1 leading-relaxed">
                    集成文档、表格、幻灯片的在线协作能力。
                  </p>
                </div>
              </div>
              <div className="flex items-center justify-between border-t border-border mt-auto pt-4">
                <div className="flex -space-x-2">
                  <div className="w-6 h-6 rounded-full border border-surface bg-surface-high"></div>
                  <div className="w-6 h-6 rounded-full border border-surface bg-background flex items-center justify-center text-[8px] font-mono text-muted">
                    +12k
                  </div>
                </div>
                <button className="text-foreground text-[13px] font-medium border border-border-active px-5 py-1.5 rounded-full hover:bg-foreground hover:text-background transition-colors">
                  获取
                </button>
              </div>
            </div>

            {/* Market Card 2 */}
            <div className="bg-surface-container border border-border rounded-xl p-6 hover:border-border-active transition-all flex flex-col gap-4 group cursor-pointer shadow-sm">
              <div className="flex gap-5">
                <div className="w-16 h-16 rounded-xl bg-surface-high border border-border flex items-center justify-center shrink-0 overflow-hidden">
                  <div className="w-full h-full bg-gradient-to-br from-[#2563eb] to-[#1e3a8a] opacity-80 flex items-center justify-center">
                    <Video size={28} className="text-white" />
                  </div>
                </div>
                <div className="flex-1">
                  <div className="flex items-start justify-between">
                    <h4 className="text-base font-bold text-foreground tracking-tight">腾讯会议</h4>
                    <ArrowRight
                      size={18}
                      className="text-muted opacity-0 group-hover:opacity-100 transition-opacity"
                    />
                  </div>
                  <p className="text-[13px] text-muted mt-1 leading-relaxed">
                    AI 助手辅助会议记录与自动生成待办事项。
                  </p>
                </div>
              </div>
              <div className="flex items-center justify-between border-t border-border mt-auto pt-4">
                <div className="flex -space-x-2">
                  <div className="w-6 h-6 rounded-full border border-surface bg-surface-high"></div>
                  <div className="w-6 h-6 rounded-full border border-surface bg-background flex items-center justify-center text-[8px] font-mono text-muted">
                    +8k
                  </div>
                </div>
                <button className="text-foreground text-[13px] font-medium border border-border-active px-5 py-1.5 rounded-full hover:bg-foreground hover:text-background transition-colors">
                  获取
                </button>
              </div>
            </div>

            {/* Market Card 3 */}
            <div className="bg-surface-container border border-border rounded-xl p-6 hover:border-border-active transition-all flex flex-col gap-4 group cursor-pointer shadow-sm">
              <div className="flex gap-5">
                <div className="w-16 h-16 rounded-xl bg-surface-high border border-border flex items-center justify-center shrink-0 overflow-hidden">
                  <div className="w-full h-full bg-surface-high flex items-center justify-center">
                    <MessageSquare size={28} className="text-foreground" />
                  </div>
                </div>
                <div className="flex-1">
                  <div className="flex items-start justify-between">
                    <h4 className="text-base font-bold text-foreground tracking-tight">
                      企业微信套件
                    </h4>
                    <ArrowRight
                      size={18}
                      className="text-muted opacity-0 group-hover:opacity-100 transition-opacity"
                    />
                  </div>
                  <p className="text-[13px] text-muted mt-1 leading-relaxed">
                    连接企业内部通讯与组织架构管理能力。
                  </p>
                </div>
              </div>
              <div className="flex items-center justify-between border-t border-border mt-auto pt-4">
                <span className="bg-surface-high text-accent-breeze font-mono text-[10px] px-2 py-0.5 rounded tracking-widest uppercase">
                  官方
                </span>
                <button className="text-foreground text-[13px] font-medium border border-border-active px-5 py-1.5 rounded-full hover:bg-foreground hover:text-background transition-colors">
                  获取
                </button>
              </div>
            </div>

            {/* Market Card 4 */}
            <div className="bg-surface-container border border-border rounded-xl p-6 hover:border-border-active transition-all flex flex-col gap-4 group cursor-pointer shadow-sm">
              <div className="flex gap-5">
                <div className="w-16 h-16 rounded-xl bg-surface-high border border-border flex items-center justify-center shrink-0">
                  <TrendingUp size={28} className="text-accent-sunset" />
                </div>
                <div className="flex-1">
                  <div className="flex items-start justify-between">
                    <h4 className="text-base font-bold text-foreground tracking-tight">NeoData</h4>
                    <ArrowRight
                      size={18}
                      className="text-muted opacity-0 group-hover:opacity-100 transition-opacity"
                    />
                  </div>
                  <p className="text-[13px] text-muted mt-1 leading-relaxed">
                    下一代多源异构数据抓取与实时分析引擎。
                  </p>
                </div>
              </div>
              <div className="flex items-center justify-between border-t border-border mt-auto pt-4">
                <div className="flex gap-2">
                  <span className="font-mono text-[10px] text-muted tracking-widest">数据分析</span>
                  <span className="font-mono text-[10px] text-muted tracking-widest">爬虫</span>
                </div>
                <button className="text-foreground text-[13px] font-medium border border-border-active px-5 py-1.5 rounded-full hover:bg-foreground hover:text-background transition-colors">
                  获取
                </button>
              </div>
            </div>

            {/* Market Card 5 (Visual Variation - wider) */}
            <div className="bg-surface-container border border-border rounded-xl p-6 hover:border-border-active transition-all flex flex-col gap-6 group cursor-pointer lg:col-span-2 shadow-sm">
              <div className="flex flex-col md:flex-row gap-6">
                <div className="w-full md:w-48 h-32 rounded-xl bg-surface-high border border-border flex items-center justify-center overflow-hidden shrink-0">
                  <div className="bg-gradient-to-br from-surface-high to-background p-5 w-full h-full flex flex-col justify-end">
                    <div className="font-mono text-[10px] text-accent-breeze mb-1 tracking-widest uppercase">
                      NEW RELEASE
                    </div>
                    <div className="text-[20px] text-foreground font-bold tracking-tight">
                      Automation Suite
                    </div>
                  </div>
                </div>
                <div className="flex-1 flex flex-col">
                  <div className="flex items-start justify-between">
                    <div>
                      <h4 className="text-lg font-bold text-foreground tracking-tight">
                        自动化全家桶 Pro
                      </h4>
                      <p className="text-sm text-muted mt-2 leading-relaxed">
                        包含 15
                        个顶级自动化工具，涵盖从邮件处理、日程管理到自动代码审查的所有流程。专为高级开发者设计。
                      </p>
                    </div>
                    <button className="bg-foreground text-background text-[13px] font-medium px-6 py-2 rounded-full hover:opacity-90 transition-opacity shrink-0">
                      获取
                    </button>
                  </div>
                  <div className="flex gap-4 mt-auto pt-4">
                    <Sparkles size={20} className="text-muted" />
                    <Terminal size={20} className="text-muted" />
                    <CloudLightning size={20} className="text-muted" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </motion.div>
  );
}
