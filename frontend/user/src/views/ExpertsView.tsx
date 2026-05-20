import React from 'react';
import { motion } from 'motion/react';
import { Search, ArrowRight, Sparkles, User } from 'lucide-react';
import { ChatExpertItem, ChatWorkspaceController } from './chat/types';

/**
 * 渲染专家中心视图。
 */
export default function ExpertsView({
  workspace,
  onSelectExpert,
}: {
  workspace: ChatWorkspaceController;
  onSelectExpert: () => void;
}) {
  const [keyword, setKeyword] = React.useState('');
  const [activeCategory, setActiveCategory] = React.useState('全部');
  const { availableExperts, selectedExpertCode, setSelectedExpertCode, setInputValue } = workspace;

  const categories = React.useMemo(
    () => ['全部', ...Array.from(new Set(availableExperts.map((item) => item.category).filter(Boolean)))],
    [availableExperts],
  );

  const visibleExperts = React.useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return availableExperts.filter((expert) => {
      const matchesCategory = activeCategory === '全部' || expert.category === activeCategory;
      const matchesKeyword =
        normalizedKeyword.length === 0 ||
        expert.displayName.toLowerCase().includes(normalizedKeyword) ||
        (expert.description ?? '').toLowerCase().includes(normalizedKeyword) ||
        expert.expertCode.toLowerCase().includes(normalizedKeyword);
      return matchesCategory && matchesKeyword;
    });
  }, [activeCategory, availableExperts, keyword]);

  const parseTags = (expert: ChatExpertItem) => {
    try {
      const parsed = JSON.parse(expert.tagsJson ?? '[]');
      return Array.isArray(parsed) ? parsed.map((item) => String(item)) : [];
    } catch {
      return [];
    }
  };

  const activateExpert = (expert: ChatExpertItem) => {
    setSelectedExpertCode(expert.expertCode);
    if (expert.presetQuestion?.trim()) {
      setInputValue(expert.presetQuestion.trim());
    }
    onSelectExpert();
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -20 }}
      className="flex flex-col h-full overflow-y-auto px-6 md:px-12 py-8 bg-background"
    >
      <div className="max-w-7xl mx-auto w-full">
        {/* Page Header */}
        <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-6 mb-12">
          <div>
            <h1 className="text-4xl md:text-5xl font-bold text-foreground mb-2 tracking-tight">
              专家中心
            </h1>
            <p className="text-base text-muted max-w-lg leading-relaxed">
              按行业分类浏览角色型专家，为您提供精准的智力支持。
            </p>
          </div>
          <div className="w-full md:w-80 relative">
            <Search size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-muted" />
            <input
              type="text"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              className="w-full bg-surface-container border border-border rounded-full pl-12 pr-6 py-3 text-sm focus:border-foreground focus:outline-none transition-all placeholder-muted text-foreground shadow-sm"
              placeholder="搜索专家名称、描述或编码..."
            />
          </div>
        </div>

        {/* Section 1: 专家团 (Beta) */}
        <section className="mb-16">
          <div className="flex items-center gap-3 mb-6">
            <h2 className="text-2xl font-bold text-foreground tracking-tight">专家团</h2>
            <span className="bg-surface-container border border-border text-muted px-2 py-0.5 rounded-full font-mono text-[10px] tracking-widest uppercase">
              BETA
            </span>
          </div>

          {/* Tabs */}
          <div className="flex gap-3 mb-8 overflow-x-auto pb-2 scrollbar-hide">
            {categories.map((tab) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => setActiveCategory(tab)}
                  className={`px-6 py-2 rounded-full text-[13px] whitespace-nowrap transition-colors border ${
                    activeCategory === tab
                      ? 'bg-foreground text-background border-foreground font-bold'
                      : 'bg-surface-container border-border text-muted hover:text-foreground'
                  }`}
                >
                  {tab}
                </button>
              ))}
          </div>

          {/* Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {/* Team Card 1 */}
            <div className="bg-surface-container border border-border p-6 rounded-xl flex flex-col justify-between hover:border-border-active transition-colors shadow-sm">
              <div>
                <div className="flex justify-between items-start mb-4">
                  <h3 className="text-xl font-bold text-foreground tracking-tight">
                    一人公司增长组
                  </h3>
                  <div className="flex -space-x-3">
                    {[1, 2, 3].map((n) => (
                      <div
                        key={n}
                        className="w-8 h-8 rounded-full border-2 border-surface-container bg-surface-high flex items-center justify-center overflow-hidden"
                      >
                        <User size={14} className="text-muted" />
                      </div>
                    ))}
                  </div>
                </div>
                <div className="flex gap-2 mb-4 flex-wrap">
                  <span className="font-mono text-[10px] px-2 py-0.5 border border-border rounded bg-surface-high text-muted tracking-widest">
                    OPC
                  </span>
                  <span className="font-mono text-[10px] px-2 py-0.5 border border-border rounded bg-surface-high text-muted tracking-widest">
                    MARKETING
                  </span>
                </div>
                <p className="text-sm text-muted mb-8 leading-relaxed">
                  专注于小规模初创企业的市场增长方案，包含SEO、内容策略与转化率优化专家协同工作。
                </p>
              </div>
                <div className="flex gap-3">
                <button type="button" className="flex-1 bg-foreground text-background py-2.5 rounded-full text-[13px] font-bold">
                  使用专家团
                </button>
                <button type="button" className="px-4 py-2.5 border border-border-active rounded-full text-foreground hover:bg-surface-high">
                  <ArrowRight size={18} />
                </button>
              </div>
            </div>

            {/* Team Card 2 */}
            <div className="bg-surface-container border border-border p-6 rounded-xl flex flex-col justify-between hover:border-border-active transition-colors shadow-sm">
              <div>
                <div className="flex justify-between items-start mb-4">
                  <h3 className="text-xl font-bold text-foreground tracking-tight">
                    全栈交付委员会
                  </h3>
                  <div className="flex -space-x-3">
                    {[1, 2].map((n) => (
                      <div
                        key={n}
                        className="w-8 h-8 rounded-full border-2 border-surface-container bg-surface-high flex items-center justify-center overflow-hidden"
                      >
                        <User size={14} className="text-muted" />
                      </div>
                    ))}
                  </div>
                </div>
                <div className="flex gap-2 mb-4 flex-wrap">
                  <span className="font-mono text-[10px] px-2 py-0.5 border border-border rounded bg-surface-high text-muted tracking-widest">
                    CODE
                  </span>
                  <span className="font-mono text-[10px] px-2 py-0.5 border border-border rounded bg-surface-high text-muted tracking-widest">
                    ARCHITECTURE
                  </span>
                </div>
                <p className="text-sm text-muted mb-8 leading-relaxed">
                  从系统架构设计到前后端代码实现的一站式交付团队，确保代码质量与系统性能。
                </p>
              </div>
              <div className="flex gap-3">
                <button className="flex-1 bg-foreground text-background py-2.5 rounded-full text-[13px] font-bold">
                  使用专家团
                </button>
                <button className="px-4 py-2.5 border border-border-active rounded-full text-foreground hover:bg-surface-high">
                  <ArrowRight size={18} />
                </button>
              </div>
            </div>

            {/* Team Card 3 */}
            <div className="bg-surface-container border border-border p-6 rounded-xl flex flex-col justify-between hover:border-border-active transition-colors shadow-sm">
              <div>
                <div className="flex justify-between items-start mb-4">
                  <h3 className="text-xl font-bold text-foreground tracking-tight">
                    品牌创意工作组
                  </h3>
                  <div className="flex -space-x-3">
                    {[1, 2].map((n) => (
                      <div
                        key={n}
                        className="w-8 h-8 rounded-full border-2 border-surface-container bg-surface-high flex items-center justify-center overflow-hidden"
                      >
                        <User size={14} className="text-muted" />
                      </div>
                    ))}
                  </div>
                </div>
                <div className="flex gap-2 mb-4 flex-wrap">
                  <span className="font-mono text-[10px] px-2 py-0.5 border border-border rounded bg-surface-high text-muted tracking-widest">
                    BRAND
                  </span>
                  <span className="font-mono text-[10px] px-2 py-0.5 border border-border rounded bg-surface-high text-muted tracking-widest">
                    DESIGN
                  </span>
                </div>
                <p className="text-sm text-muted mb-8 leading-relaxed">
                  深度结合AI绘图与品牌心理学，为产品提供独特的视觉语言与叙事方案。
                </p>
              </div>
              <div className="flex gap-3">
                <button className="flex-1 bg-foreground text-background py-2.5 rounded-full text-[13px] font-bold">
                  使用专家团
                </button>
                <button className="px-4 py-2.5 border border-border-active rounded-full text-foreground hover:bg-surface-high">
                  <ArrowRight size={18} />
                </button>
              </div>
            </div>
          </div>
        </section>

        {/* Section 2: 专家 */}
        <section>
          <div className="flex flex-col md:flex-row items-start md:items-center justify-between mb-8 gap-4">
            <h2 className="text-2xl font-bold text-foreground tracking-tight">单个专家</h2>
            <div className="rounded-full border border-border bg-surface-container px-4 py-2 text-[12px] text-muted shadow-sm">
              当前已加载 {visibleExperts.length} 位专家
            </div>
          </div>

          {/* Expert Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {visibleExperts.map((expert) => {
              const tags = parseTags(expert);
              const isSelected = selectedExpertCode === expert.expertCode;
              return (
              <div
                key={expert.expertCode}
                className={`bg-surface-container border p-5 rounded-xl flex flex-col group transition-all shadow-sm ${
                  isSelected ? 'border-foreground shadow-[0_18px_40px_rgba(0,0,0,0.16)]' : 'border-border hover:border-foreground'
                }`}
              >
                <div className="flex items-center gap-4 mb-5">
                  <div className="w-12 h-12 rounded-full bg-surface-high flex items-center justify-center overflow-hidden shrink-0 border border-border">
                    <User size={20} className="text-muted" />
                  </div>
                  <div>
                    <h4 className="text-[15px] font-bold text-foreground tracking-tight mb-1">
                      {expert.displayName}
                    </h4>
                    <div className="flex items-center gap-1 text-muted font-mono text-[10px]">
                      <Sparkles size={10} />
                      <span>/{expert.expertCode}</span>
                    </div>
                  </div>
                </div>
                <div className="flex gap-1.5 mb-4 flex-wrap">
                  {tags.map((tag) => (
                    <span
                      key={tag}
                      className="text-[9px] font-mono px-2 py-0.5 border border-border rounded bg-surface-high text-muted tracking-widest"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
                <p className="text-[13px] text-muted line-clamp-2 leading-relaxed mb-6 flex-1">
                  {expert.description || '暂无描述'}
                </p>
                <button
                  type="button"
                  onClick={() => activateExpert(expert)}
                  className={`mt-auto w-full py-2.5 border rounded-full transition-all text-[13px] font-semibold ${
                    isSelected
                      ? 'border-foreground bg-foreground text-background'
                      : 'border-border text-foreground group-hover:bg-foreground group-hover:text-background'
                  }`}
                >
                  {isSelected ? '已选中，继续对话' : '开始对话'}
                </button>
              </div>
            )})}
          </div>
        </section>
      </div>
    </motion.div>
  );
}
