import React, { useState } from 'react';
import { Search, TrendingUp, Code2, PenTool, BarChart3, Bookmark, Terminal, ArrowRight } from 'lucide-react';
import { cn } from '../lib/utils';
import { motion } from 'motion/react';

/**
 * Renders the expert discovery page for the CodingX shell.
 */
export function Experts() {
  // Track the active tab so the page can switch between ranking modes.
  const [activeTab, setActiveTab] = useState('最热');

  return (
    <div className="p-6 md:p-12 lg:p-16 w-full max-w-7xl mx-auto flex flex-col gap-12 pb-24 text-left">
      <section className="flex flex-col md:flex-row md:items-end justify-between gap-6 w-full">
        <div className="flex flex-col gap-2">
          <div className="text-sm font-mono text-body-mute uppercase tracking-[1.4px]">DIRECTORY</div>
          <h1 className="text-5xl md:text-6xl lg:text-7xl font-bold text-ink tracking-tighter leading-none mb-1">专家中心</h1>
          <p className="text-lg text-body-mute max-w-2xl mt-2">按行业分类浏览角色型专家。</p>
        </div>
        <div className="w-full md:w-72">
          <div className="relative flex items-center w-full h-12 bg-canvas-soft border border-hairline rounded-lg px-3 focus-within:border-outline-variant transition-colors">
            <Search className="w-5 h-5 text-body-mute mr-2" />
            <input 
              type="text" 
              placeholder="搜索专家..." 
              className="w-full bg-transparent border-none text-ink text-base placeholder:text-body-mute focus:ring-0 p-0 outline-none"
            />
          </div>
        </div>
      </section>

      <div className="w-full h-px bg-hairline my-2"></div>

      <section className="w-full">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-3">
            <h2 className="text-3xl font-bold text-ink">专家团</h2>
            <span className="text-xs font-mono text-canvas bg-accent-twilight px-2 py-0.5 rounded-sm">BETA</span>
          </div>
          <div className="hidden md:flex gap-2">
            {['全部', '研发', '设计', '数据'].map(category => (
              <button 
                key={category}
                className={cn(
                  "px-4 py-1.5 rounded-full text-sm font-medium transition-colors border",
                  category === '全部' 
                    ? "bg-surface-bright text-ink border-transparent shadow-sm" 
                    : "bg-transparent text-body-mute border-hairline hover:text-ink hover:border-hairline-translucent"
                )}
              >
                {category}
              </button>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="bg-canvas-card border border-hairline p-8 rounded-2xl flex flex-col justify-between min-h-[240px] hover:border-outline-variant transition-colors group cursor-pointer relative overflow-hidden shadow-sm">
            <div className="absolute inset-0 bg-gradient-to-br from-ink/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity"></div>
            <div className="relative z-10 flex justify-between items-start">
              <div className="flex flex-col gap-2">
                <span className="text-xs font-mono text-body-mute">DEVELOPMENT SQUAD</span>
                <h3 className="text-3xl font-bold text-ink mb-1">全栈研发先锋团</h3>
              </div>
              <div className="flex items-center gap-1.5 text-body-mute bg-surface-bright px-3 py-1 rounded-full border border-hairline">
                <TrendingUp className="w-4 h-4" />
                <span className="text-xs font-mono">12.4K USES</span>
              </div>
            </div>
            <div className="relative z-10 flex items-end justify-between mt-8">
              <div className="flex -space-x-2">
                {['DE', 'UI', 'QA', '+2'].map((abbr, i) => (
                  <div key={i} className={cn(
                    "w-10 h-10 rounded-full border-2 border-canvas-card flex items-center justify-center text-xs font-mono font-medium",
                    i === 0 ? "bg-surface-variant text-ink" :
                    i === 1 ? "bg-surface-bright text-ink" :
                    i === 2 ? "bg-canvas-mid text-ink" :
                    "bg-canvas-soft text-body-mute"
                  )}>
                    {abbr}
                  </div>
                ))}
              </div>
              <button className="px-5 py-2 rounded-full border border-hairline-translucent text-ink text-sm font-medium hover:bg-ink/5 transition-colors flex items-center gap-2 shadow-sm">
                EXPLORE <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </div>

          <div className="bg-canvas-card border border-hairline p-8 rounded-2xl flex flex-col justify-between min-h-[240px] hover:border-outline-variant transition-colors group cursor-pointer relative overflow-hidden shadow-sm">
            <div className="absolute inset-0 bg-gradient-to-br from-ink/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity"></div>
            <div className="relative z-10 flex justify-between items-start">
              <div className="flex flex-col gap-2">
                <span className="text-xs font-mono text-body-mute">DATA INTELLIGENCE</span>
                <h3 className="text-3xl font-bold text-ink mb-1">数据分析智囊团</h3>
              </div>
              <div className="flex items-center gap-1.5 text-body-mute bg-surface-bright px-3 py-1 rounded-full border border-hairline">
                <TrendingUp className="w-4 h-4" />
                <span className="text-xs font-mono">8.1K USES</span>
              </div>
            </div>
            <div className="relative z-10 flex items-end justify-between mt-8">
              <div className="flex -space-x-2">
                {['DA', 'BI', '+1'].map((abbr, i) => (
                  <div key={i} className={cn(
                    "w-10 h-10 rounded-full border-2 border-canvas-card flex items-center justify-center text-xs font-mono font-medium",
                    i === 0 ? "bg-surface-variant text-ink" :
                    i === 1 ? "bg-surface-bright text-ink" :
                    "bg-canvas-soft text-body-mute"
                  )}>
                    {abbr}
                  </div>
                ))}
              </div>
              <button className="px-5 py-2 rounded-full border border-hairline-translucent text-ink text-sm font-medium hover:bg-ink/5 transition-colors flex items-center gap-2 shadow-sm">
                EXPLORE <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </section>

      <div className="w-full h-px bg-hairline my-2"></div>

      <section className="w-full flex-1">
        <div className="flex items-center justify-between mb-8">
          <h2 className="text-3xl font-bold text-ink">独立专家</h2>
          <div className="flex bg-canvas-soft border border-hairline rounded-lg p-1">
            <button 
              onClick={() => setActiveTab('最热')}
              className={cn("px-4 py-1.5 rounded-md text-sm font-medium transition-colors", activeTab === '最热' ? "bg-canvas-card text-ink border border-hairline shadow-sm" : "text-body-mute")}
            >
              最热
            </button>
            <button 
              onClick={() => setActiveTab('最新')}
              className={cn("px-4 py-1.5 rounded-md text-sm font-medium transition-colors", activeTab === '最新' ? "bg-canvas-card text-ink border border-hairline shadow-sm" : "text-body-mute")}
            >
              最新
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <motion.div whileHover={{ y: -2 }} className="bg-canvas-card border border-hairline p-6 xl:p-8 rounded-2xl flex flex-col gap-4 hover:border-outline-variant transition-all duration-300 group relative shadow-sm">
            <div className="flex justify-between items-start">
              <div className="w-12 h-12 rounded-xl bg-surface-bright flex items-center justify-center border border-hairline">
                <Code2 className="w-6 h-6 text-ink" />
              </div>
              <button className="w-8 h-8 rounded-full border border-hairline flex items-center justify-center text-body-mute hover:text-ink hover:border-hairline-translucent transition-colors">
                <Bookmark className="w-4 h-4" />
              </button>
            </div>
            <div className="flex flex-col gap-2 mt-2">
              <h3 className="text-xl font-bold text-ink">高级开发工程师</h3>
              <p className="text-sm text-body-mute line-clamp-2">精通架构设计与性能调优，提供全栈技术解决方案与代码重构建议。</p>
            </div>
            <div className="flex gap-2 mt-auto pt-4">
              <span className="px-2 py-1 border border-hairline text-body-mute text-xs font-mono rounded">RUST</span>
              <span className="px-2 py-1 border border-hairline text-body-mute text-xs font-mono rounded">NODE</span>
            </div>
            <button className="w-full mt-4 px-4 py-3 rounded-full border border-hairline-translucent text-ink text-sm font-medium hover:bg-ink/5 transition-colors flex items-center justify-center gap-2 shadow-sm">
              INITIALIZE <Terminal className="w-4 h-4" />
            </button>
          </motion.div>

          <motion.div whileHover={{ y: -2 }} className="bg-canvas-card border border-hairline p-6 xl:p-8 rounded-2xl flex flex-col gap-4 hover:border-outline-variant transition-all duration-300 group relative shadow-sm">
            <div className="flex justify-between items-start">
              <div className="w-12 h-12 rounded-xl bg-surface-bright flex items-center justify-center border border-hairline">
                <PenTool className="w-6 h-6 text-ink" />
              </div>
              <button className="w-8 h-8 rounded-full border border-hairline flex items-center justify-center text-body-mute hover:text-ink hover:border-hairline-translucent transition-colors">
                <Bookmark className="w-4 h-4" />
              </button>
            </div>
            <div className="flex flex-col gap-2 mt-2">
              <h3 className="text-xl font-bold text-ink">UI 设计师</h3>
              <p className="text-sm text-body-mute line-clamp-2">专注于极简主义与可用性，提供组件化设计系统与交互规范指导。</p>
            </div>
            <div className="flex gap-2 mt-auto pt-4">
              <span className="px-2 py-1 border border-hairline text-body-mute text-xs font-mono rounded">FIGMA</span>
              <span className="px-2 py-1 border border-hairline text-body-mute text-xs font-mono rounded">SYSTEMS</span>
            </div>
            <button className="w-full mt-4 px-4 py-3 rounded-full border border-hairline-translucent text-ink text-sm font-medium hover:bg-ink/5 transition-colors flex items-center justify-center gap-2 shadow-sm">
              INITIALIZE <Terminal className="w-4 h-4" />
            </button>
          </motion.div>

          <motion.div whileHover={{ y: -2 }} className="bg-canvas-card border border-hairline p-6 xl:p-8 rounded-2xl flex flex-col gap-4 hover:border-outline-variant transition-all duration-300 group relative shadow-sm">
            <div className="flex justify-between items-start">
              <div className="w-12 h-12 rounded-xl bg-surface-bright flex items-center justify-center border border-hairline">
                <BarChart3 className="w-6 h-6 text-ink" />
              </div>
              <button className="w-8 h-8 rounded-full border border-hairline flex items-center justify-center text-body-mute hover:text-ink hover:border-hairline-translucent transition-colors">
                <Bookmark className="w-4 h-4" />
              </button>
            </div>
            <div className="flex flex-col gap-2 mt-2">
              <h3 className="text-xl font-bold text-ink">数据分析报告师</h3>
              <p className="text-sm text-body-mute line-clamp-2">提取复杂数据集中的业务洞察，自动生成结构化、可视化分析报告。</p>
            </div>
            <div className="flex gap-2 mt-auto pt-4">
              <span className="px-2 py-1 border border-hairline text-body-mute text-xs font-mono rounded">SQL</span>
              <span className="px-2 py-1 border border-hairline text-body-mute text-xs font-mono rounded">PYTHON</span>
            </div>
            <button className="w-full mt-4 px-4 py-3 rounded-full border border-hairline-translucent text-ink text-sm font-medium hover:bg-ink/5 transition-colors flex items-center justify-center gap-2 shadow-sm">
              INITIALIZE <Terminal className="w-4 h-4" />
            </button>
          </motion.div>
        </div>
      </section>
    </div>
  );
}
