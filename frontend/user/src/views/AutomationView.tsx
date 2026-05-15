import React, { useState } from 'react';
import { motion } from 'motion/react';
import { Newspaper, ShieldAlert, Activity, LineChart, Plus, ArrowRight, Zap } from 'lucide-react';

/**
 * 渲染自动化视图页面。
 */
export default function AutomationView() {
  const [activeTab, setActiveTab] = useState('templates');

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
            自动化
          </h1>
          <p className="text-base text-muted max-w-lg leading-relaxed">
            通过编排任务流与自动化模板，将繁琐的日常工作一键托管。
          </p>
        </div>

        {/* Tabs */}
        <div className="flex border-b border-border mb-8">
          <button
            onClick={() => setActiveTab('configured')}
            className={`px-6 py-4 text-sm font-medium transition-colors border-b-2 ${activeTab === 'configured' ? 'text-foreground border-foreground' : 'text-muted border-transparent hover:text-foreground'}`}
          >
            已配置
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`px-6 py-4 text-sm font-medium transition-colors border-b-2 ${activeTab === 'history' ? 'text-foreground border-foreground' : 'text-muted border-transparent hover:text-foreground'}`}
          >
            执行历史
          </button>
          <button
            onClick={() => setActiveTab('templates')}
            className={`px-6 py-4 text-sm font-medium transition-colors border-b-2 ${activeTab === 'templates' ? 'text-foreground border-foreground' : 'text-muted border-transparent hover:text-foreground'}`}
          >
            任务模板
          </button>
        </div>

        {/* Bento Grid Layout */}
        <div className="grid grid-cols-1 md:grid-cols-12 gap-6">
          {/* Daily AI News */}
          <div className="md:col-span-8 bg-surface-container border border-border rounded-xl p-8 hover:border-border-active transition-all group flex flex-col justify-between min-h-[320px]">
            <div className="flex justify-between items-start mb-6">
              <div className="w-12 h-12 bg-surface-high border border-border rounded-full flex items-center justify-center">
                <Newspaper className="text-accent-breeze" size={24} />
              </div>
              <span className="font-mono text-[10px] text-muted bg-surface-high px-3 py-1 rounded-full border border-border tracking-widest uppercase">
                Trending
              </span>
            </div>
            <div>
              <h3 className="text-3xl font-bold text-foreground mb-3 tracking-tight">
                每日 AI 新闻简报
              </h3>
              <p className="text-sm text-muted max-w-md leading-relaxed">
                多源聚合全球 AI
                技术突破与行业动态，利用大模型自动摘要并生成结构化每日简报，支持钉钉/飞书推送。
              </p>
            </div>
            <div className="flex gap-4 mt-8">
              <button className="text-foreground text-sm font-medium flex items-center gap-2 group-hover:translate-x-1 transition-transform">
                使用此模板 <ArrowRight size={16} />
              </button>
            </div>
          </div>

          {/* Security Scan */}
          <div className="md:col-span-4 bg-surface-container border border-border rounded-xl p-8 hover:border-border-active transition-all flex flex-col justify-between min-h-[320px]">
            <div>
              <div className="w-12 h-12 bg-surface-high border border-border rounded-full flex items-center justify-center mb-6">
                <ShieldAlert className="text-error" size={24} />
              </div>
              <h3 className="text-lg font-bold text-foreground mb-2">安全漏洞扫描</h3>
              <p className="text-sm text-muted leading-relaxed">
                定期执行代码库全量安全扫描，识别潜在注入与依赖风险，自动提交 PR 建议。
              </p>
            </div>
            <div className="mt-8 pt-6 border-t border-border">
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-error"></div>
                <span className="font-mono text-[10px] text-muted tracking-widest uppercase">
                  CRITICAL TOOL
                </span>
              </div>
            </div>
          </div>

          {/* Brand Monitoring */}
          <div className="md:col-span-4 bg-surface-container border border-border rounded-xl p-8 hover:border-border-active transition-all flex flex-col justify-between h-[280px]">
            <div>
              <div className="w-12 h-12 bg-surface-high border border-border rounded-full flex items-center justify-center mb-6">
                <Activity className="text-accent-sunset" size={24} />
              </div>
              <h3 className="text-lg font-bold text-foreground mb-2">品牌舆情监控周报</h3>
              <p className="text-sm text-muted leading-relaxed">
                全网抓取品牌相关评价，智能分析情感倾向与核心痛点，周一准时送达。
              </p>
            </div>
            <button className="w-full py-2.5 mt-4 border border-border-active rounded-full text-sm text-muted hover:text-foreground hover:bg-surface-high transition-colors font-medium">
              快速配置
            </button>
          </div>

          {/* Stock Watch */}
          <div className="md:col-span-4 bg-surface-container border border-border rounded-xl overflow-hidden flex flex-col hover:border-border-active transition-all h-[280px]">
            <div className="flex-1 p-8">
              <div className="w-12 h-12 bg-surface-high border border-border rounded-full flex items-center justify-center mb-6">
                <LineChart className="text-foreground" size={24} />
              </div>
              <h3 className="text-lg font-bold text-foreground mb-2">股价监控与预警</h3>
              <p className="text-sm text-muted leading-relaxed">
                实时追踪关注标的价格波动，触发关键点位即刻通过多渠道发起紧急预警。
              </p>
            </div>
            <div className="bg-surface-high px-8 py-4 border-t border-border flex justify-between items-center">
              <span className="font-mono text-[10px] text-accent-breeze tracking-widest uppercase">
                REAL-TIME
              </span>
              <Zap size={16} className="text-muted" />
            </div>
          </div>

          {/* Custom Pipeline */}
          <div className="md:col-span-4 bg-background border border-border border-dashed rounded-xl p-8 flex flex-col items-center justify-center text-center group cursor-pointer hover:bg-surface-container transition-all h-[280px]">
            <div className="w-16 h-16 rounded-full border border-border flex items-center justify-center mb-6 group-hover:scale-110 transition-transform bg-surface-container">
              <Plus size={32} className="text-muted" />
            </div>
            <h3 className="text-lg font-bold text-foreground mb-2">自定义流程</h3>
            <p className="text-sm text-muted">连接 API 与模型，编排专属自动化流</p>
          </div>
        </div>

        {/* Footer Section */}
        <div className="mt-16 pt-8 border-t border-border flex flex-col md:flex-row justify-between items-center gap-6 pb-8">
          <div className="flex gap-8">
            <div className="flex items-center gap-3">
              <span className="font-mono text-[10px] text-muted tracking-widest uppercase">
                STATUS
              </span>
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-emerald-500"></div>
                <span className="font-mono text-[10px] text-foreground tracking-widest uppercase">
                  OPERATIONAL
                </span>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <span className="font-mono text-[10px] text-muted tracking-widest uppercase">
                TASKS TODAY
              </span>
              <span className="font-mono text-[10px] text-foreground font-bold tracking-widest uppercase">
                1,248
              </span>
            </div>
          </div>
          <p className="font-mono text-[10px] text-muted tracking-widest uppercase">
            © 2026 CODINGX RESEARCH LABS. ALL RIGHTS RESERVED.
          </p>
        </div>
      </div>
    </motion.div>
  );
}
