import React, { useState } from 'react';
import { Search, Plus, Globe, Terminal, Compass, FileText, LineChart, FileEdit, MessageSquare, Database, PenTool } from 'lucide-react';
import { cn } from '../lib/utils';
import { motion } from 'motion/react';

/**
 * Supplies the installed skill cards rendered by the Skills page.
 */
const installedSkills = [
  { id: 1, name: 'agent-browser', desc: '允许 AI 代理浏览网页、提取内容并与动态 Web 元素交互。', type: '核心', icon: Globe },
  { id: 2, name: 'playwright-cli', desc: '基于 Playwright 的命令行自动化测试和屏幕抓取工具。', type: '工具', icon: Terminal },
  { id: 3, name: 'find-skills', desc: '动态发现、加载和管理环境中的可用技能包。', type: '系统', icon: Compass },
  { id: 4, name: 'document-skills', desc: '解析、生成和转换多种文档格式 (PDF, Word, Markdown)。', type: '工具', icon: FileText },
  { id: 5, name: 'finance-data', desc: '连接实时金融 API，获取市场行情并执行基础数据分析。', type: '数据', icon: LineChart },
];

/**
 * Supplies the marketplace skill cards rendered by the Skills page.
 */
const marketSkills = [
  { id: 1, name: '腾讯文档', desc: '无缝集成腾讯文档 API。支持自动创建、读取、编辑在线电子表格和文档，实现跨团队数据协同。', tag: '效率工具', installs: '12k', icon: FileEdit },
  { id: 2, name: '企业微信套件', desc: '连接企业微信机器人生态。支持发送群消息、接收回调事件、管理审批流，实现自动化办公通讯。', tag: '企业集成', installs: '8.5k', icon: MessageSquare },
  { id: 3, name: 'PostgreSQL Explorer', desc: '直连 PostgreSQL 数据库。允许 Agent 安全地执行 SQL 查询、分析数据结构并生成可视化报表。', tag: '开发工具', installs: '5.2k', icon: Database },
  { id: 4, name: 'Figma Sync', desc: '桥接设计与开发。自动提取 Figma 标注、导出资产，并将设计 Token 同步至代码仓库。', tag: '开发工具', installs: '3.8k', icon: PenTool },
];

/**
 * Renders the skill discovery page for installed and marketplace capabilities.
 */
export function Skills() {
  // Track the active market tab and filter selection for the mock shell view.
  const [activeTab, setActiveTab] = useState('推荐');
  const [activeFilter, setActiveFilter] = useState('全部');

  return (
    <div className="p-6 md:p-12 lg:p-16 w-full max-w-7xl mx-auto flex flex-col gap-12 pb-24 text-left relative">
      <header className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <h1 className="text-4xl md:text-5xl lg:text-7xl font-bold text-ink tracking-tight mb-2">技能</h1>
          <p className="text-lg text-body-mute">赋予 CodingX 更强大的能力</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative">
            <Search className="w-5 h-5 absolute left-4 top-1/2 -translate-y-1/2 text-body-mute" />
            <input 
              type="text" 
              placeholder="搜索技能..." 
              className="w-full md:w-64 pl-12 pr-4 py-2 bg-canvas-soft border border-hairline rounded-lg text-ink focus:border-outline-variant focus:outline-none transition-colors"
            />
          </div>
          <button className="px-6 py-2 bg-canvas border border-hairline-translucent text-ink rounded-full text-sm font-medium hover:bg-ink/5 transition-colors flex items-center gap-2 whitespace-nowrap shadow-sm">
            <Plus className="w-4 h-4" />
            添加技能
          </button>
        </div>
      </header>

      <section>
        <div className="flex items-center gap-3 mb-6">
          <span className="text-sm font-mono text-body-mute uppercase tracking-[1.4px]">已安装技能</span>
          <div className="h-px bg-hairline flex-1"></div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {installedSkills.map(skill => (
            <motion.div whileHover={{ y: -2 }} key={skill.id} className="bg-canvas-card border border-hairline rounded-2xl p-6 flex flex-col gap-4 hover:border-outline-variant transition-colors group cursor-pointer shadow-sm">
              <div className="flex items-start justify-between">
                <div className="w-12 h-12 bg-surface-bright border border-hairline rounded-lg flex items-center justify-center">
                  <skill.icon className="w-6 h-6 text-ink" />
                </div>
                <span className="px-3 py-1 border border-hairline rounded-full text-xs font-mono text-body-mute">{skill.type}</span>
              </div>
              <div>
                <h3 className="text-base font-bold text-ink mb-1">{skill.name}</h3>
                <p className="text-sm text-body-mute line-clamp-2">{skill.desc}</p>
              </div>
            </motion.div>
          ))}
        </div>
      </section>

      <section>
        <div className="flex items-center gap-3 mb-6">
          <span className="text-sm font-mono text-body-mute uppercase tracking-[1.4px]">技能市场</span>
          <div className="h-px bg-hairline flex-1"></div>
        </div>
        
        <div className="flex border-b border-hairline mb-8">
          {['推荐', 'SkillHub', '套件'].map(tab => (
            <button 
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={cn(
                "px-6 py-3 text-base transition-colors relative",
                activeTab === tab ? "text-ink" : "text-body-mute hover:text-ink"
              )}
            >
              {tab}
              {activeTab === tab && (
                <motion.div layoutId="skillTab" className="absolute bottom-[-1px] left-0 right-0 h-0.5 bg-ink" />
              )}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap gap-2 mb-8">
          {['全部', '开发工具', '效率工具', '数据处理', '企业集成'].map(filter => (
            <button 
              key={filter}
              onClick={() => setActiveFilter(filter)}
              className={cn(
                "px-6 py-2 rounded-full text-sm font-medium transition-colors border",
                activeFilter === filter 
                  ? "bg-ink text-canvas border-transparent shadow-sm" 
                  : "bg-surface-bright text-body-mute border-hairline-translucent hover:text-ink hover:border-outline-variant"
              )}
            >
              {filter}
            </button>
          ))}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {marketSkills.map(skill => (
            <div key={skill.id} className="bg-canvas-card border border-hairline rounded-2xl p-6 flex gap-6 items-start hover:border-outline-variant transition-colors group shadow-sm">
              <div className="w-16 h-16 shrink-0 bg-surface-bright border border-hairline rounded-lg flex items-center justify-center">
                <skill.icon className="w-8 h-8 text-ink" />
              </div>
              <div className="flex-1">
                <div className="flex flex-col flex-wrap sm:flex-row sm:items-center justify-between mb-2 gap-2">
                  <h3 className="text-lg font-bold text-ink">{skill.name}</h3>
                  <button className="px-4 py-1.5 bg-transparent border border-hairline-translucent text-ink rounded-full text-xs font-mono hover:bg-ink/5 transition-colors self-start sm:self-auto">
                    获取
                  </button>
                </div>
                <p className="text-sm text-body-mute mb-4 line-clamp-2">{skill.desc}</p>
                <div className="flex items-center gap-2">
                  <span className="text-xs font-mono text-body-mute">{skill.tag}</span>
                  <span className="text-hairline">•</span>
                  <span className="text-xs font-mono text-body-mute">{skill.installs} 安装</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      <footer className="fixed bottom-0 right-0 p-6 bg-transparent text-xs font-mono text-body-mute flex gap-6 z-50 pointer-events-none">
        <div className="pointer-events-auto">© 2024 CodingX. Frontier Research.</div>
        <div className="flex gap-6 pointer-events-auto">
          <a href="#" className="hover:text-ink underline transition-all">Privacy</a>
          <a href="#" className="hover:text-ink underline transition-all">Terms</a>
          <a href="#" className="hover:text-ink underline transition-all">API</a>
        </div>
      </footer>
    </div>
  );
}
