import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { ArrowLeft, FileText, Globe, Search, Terminal, TrendingUp } from 'lucide-react';

import { AuthStorage } from '../utils/authStorage';
import { ChatApi } from './chat/chatApi';
import { ChatSkillItem } from './chat/types';

/**
 * 根据技能分类选择展示图标，保证真实数据渲染后仍保持信息识别度。
 * @param category 技能分类。
 * @returns 对应图标组件。
 */
function resolveSkillIcon(category?: string) {
  const normalizedCategory = (category ?? '').toLowerCase();
  if (normalizedCategory.includes('销售') || normalizedCategory.includes('finance')) {
    return TrendingUp;
  }
  if (normalizedCategory.includes('文档') || normalizedCategory.includes('office')) {
    return FileText;
  }
  if (normalizedCategory.includes('检索') || normalizedCategory.includes('搜索')) {
    return Search;
  }
  if (normalizedCategory.includes('工程') || normalizedCategory.includes('开发')) {
    return Terminal;
  }
  return Globe;
}

/**
 * 渲染技能库视图。
 */
export default function SkillsView() {
  const [activeCategory, setActiveCategory] = useState('全部');
  const [skills, setSkills] = useState<ChatSkillItem[]>([]);
  const [selectedSkill, setSelectedSkill] = useState<ChatSkillItem | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    let disposed = false;

    const loadSkills = async () => {
      const token = AuthStorage.getSession()?.token;
      if (!token) {
        return;
      }
      setIsLoading(true);
      setErrorMessage('');
      try {
        const response = await ChatApi.listSkills(token);
        if (!disposed) {
          setSkills(response);
        }
      } catch (error) {
        if (!disposed) {
          setErrorMessage(error instanceof Error ? error.message : '技能加载失败');
          setSkills([]);
        }
      } finally {
        if (!disposed) {
          setIsLoading(false);
        }
      }
    };

    void loadSkills();
    return () => {
      disposed = true;
    };
  }, []);

  const categories = useMemo(() => {
    const runtimeCategories = skills
      .map((item) => item.category)
      .filter((category): category is string => Boolean(category?.trim()))
      .map((category) => category.trim());
    return ['全部', ...Array.from(new Set(runtimeCategories))];
  }, [skills]);

  const visibleSkills = useMemo(() => {
    if (activeCategory === '全部') {
      return skills;
    }
    return skills.filter((item) => item.category === activeCategory);
  }, [activeCategory, skills]);

  if (selectedSkill) {
    return (
      <motion.div
        initial={{ opacity: 0, x: 20 }}
        animate={{ opacity: 1, x: 0 }}
        exit={{ opacity: 0, x: -20 }}
        className="flex h-full flex-col overflow-y-auto bg-background px-6 py-8 md:px-12"
      >
        <SkillDetailPage
          skill={selectedSkill}
          onBack={() => setSelectedSkill(null)}
        />
      </motion.div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -20 }}
      className="flex h-full flex-col overflow-y-auto bg-background px-6 py-8 md:px-12"
    >
      <div className="mx-auto w-full max-w-7xl">
        <div className="mb-12">
          <h1 className="mb-2 text-4xl font-bold tracking-tight text-foreground md:text-5xl">
            技能库
          </h1>
          <p className="max-w-lg text-base leading-relaxed text-muted">
            管理和扩展您的工作台能力，发现更多强大的 AI 工具与套件。
          </p>
        </div>

        <section className="mb-16">
          <div className="mb-6 flex items-center justify-between">
            <h2 className="font-mono text-xs uppercase tracking-widest text-muted">
              已安装 ({skills.length})
            </h2>
            <button
              className="text-sm text-muted transition-colors hover:text-foreground"
              type="button"
            >
              管理
            </button>
          </div>

          {isLoading ? (
            <div className="rounded-xl border border-border bg-surface-container px-4 py-6 text-sm text-muted">
              技能加载中...
            </div>
          ) : null}

          {!isLoading && errorMessage ? (
            <div className="rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-6 text-sm text-red-300">
              {errorMessage}
            </div>
          ) : null}

          {!isLoading && !errorMessage ? (
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
              {visibleSkills.map((skill) => {
                const SkillIcon = resolveSkillIcon(skill.category);
                return (
                  <button
                    key={skill.id || skill.skillCode}
                    type="button"
                    aria-label={`查看技能详情 ${skill.displayName}`}
                    onClick={() => setSelectedSkill(skill)}
                    className="group flex h-full cursor-pointer flex-col rounded-lg border border-border bg-surface-container p-5 text-left shadow-sm outline-none transition-colors hover:border-border-active focus-visible:border-border-selected focus-visible:ring-2 focus-visible:ring-border-active"
                  >
                    <div className="mb-4 flex items-center justify-between">
                      <div className="flex h-10 w-10 items-center justify-center rounded-full border border-border bg-surface-high">
                        <SkillIcon size={20} className="text-foreground" />
                      </div>
                      <span className="rounded bg-surface-high px-2 py-0.5 font-mono text-[10px] uppercase tracking-widest text-muted">
                        {skill.sourceType || '技能'}
                      </span>
                    </div>
                    <h3 className="mt-auto mb-1 font-semibold text-foreground">
                      {skill.displayName}
                    </h3>
                    <p className="line-clamp-2 text-[13px] leading-relaxed text-muted">
                      {skill.description || '暂无描述'}
                    </p>
                    <div className="mt-3 flex items-center justify-between text-[11px] text-muted">
                      <span>{skill.category || '未分类'}</span>
                      <span className="font-mono">/{skill.skillCode}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          ) : null}
        </section>

        <section className="mb-12">
          <div className="mb-6 border-b border-border">
            <div className="flex gap-8">
              <button
                className="relative -bottom-[1px] border-b-2 border-foreground pb-3 text-sm font-bold text-foreground"
                type="button"
              >
                推荐
              </button>
              <button
                className="border-b-2 border-transparent pb-3 text-sm font-medium text-muted transition-colors hover:text-foreground"
                type="button"
              >
                SkillHub
              </button>
            </div>
          </div>

          <div className="mb-8 flex flex-wrap gap-2">
            {categories.map((category) => (
              <button
                key={category}
                type="button"
                onClick={() => setActiveCategory(category)}
                className={`rounded-full px-5 py-1.5 font-mono text-[11px] tracking-widest transition-colors ${
                  activeCategory === category
                    ? 'bg-foreground font-bold text-background'
                    : 'border border-border bg-surface-high text-muted hover:border-border-active'
                }`}
              >
                {category}
              </button>
            ))}
          </div>
        </section>
      </div>
    </motion.div>
  );
}

/**
 * 展示技能详情页面，复用列表接口返回的数据，避免用户端进入详情时重复请求。
 */
function SkillDetailPage({
  skill,
  onBack,
}: {
  skill: ChatSkillItem;
  onBack: () => void;
}) {
  const SkillIcon = resolveSkillIcon(skill.category);
  const detailRows = [
    { label: '技能编码', value: `/${skill.skillCode}` },
    { label: '分类', value: skill.category || '未分类' },
    { label: '来源', value: skill.sourceType || '技能' },
    { label: '状态', value: skill.enabled === 0 ? '已禁用' : '已启用' },
  ];

  return (
    <article className="mx-auto w-full max-w-5xl">
      <button
        type="button"
        aria-label="返回技能库"
        onClick={onBack}
        className="mb-8 inline-flex items-center gap-2 rounded-full border border-border bg-surface-container px-4 py-2 text-sm text-muted transition-colors hover:border-border-active hover:bg-surface-high hover:text-foreground"
      >
        <ArrowLeft size={16} />
        返回技能库
      </button>

      <header className="mb-10 flex flex-col gap-5 border-b border-border pb-8 md:flex-row md:items-start md:justify-between">
        <div className="flex min-w-0 items-start gap-4">
          <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full border border-border bg-surface-high">
            <SkillIcon size={26} className="text-foreground" />
          </div>
          <div className="min-w-0">
            <p className="mb-2 font-mono text-xs uppercase tracking-widest text-muted">技能详情</p>
            <h1 className="text-4xl font-bold tracking-tight text-foreground md:text-5xl">
              {skill.displayName}
            </h1>
            <p className="mt-3 max-w-2xl text-base leading-7 text-muted">
              {skill.description || '暂无描述'}
            </p>
          </div>
        </div>
        <div className="rounded-xl border border-border bg-surface-container px-4 py-3 font-mono text-sm text-foreground">
          /{skill.skillCode}
        </div>
      </header>

      <section className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div className="rounded-lg border border-border bg-surface-container p-5">
          <div className="text-[12px] font-medium text-muted">技能说明</div>
          <p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-foreground">
            {skill.description || '暂无描述'}
          </p>
        </div>

        <div className="rounded-lg border border-border bg-surface-container p-5">
          <div className="text-[12px] font-medium text-muted">使用入口</div>
          <p className="mt-3 text-sm leading-7 text-foreground">
            在聊天输入区通过技能选择器或输入技能标记调用该技能。
          </p>
          <p className="mt-3 font-mono text-sm text-foreground">/{skill.skillCode}</p>
        </div>
      </section>

      <dl className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {detailRows.map((row) => (
          <div
            key={row.label}
            className="rounded-lg border border-border bg-surface p-4"
          >
            <dt className="text-[12px] text-muted">{row.label}</dt>
            <dd className="mt-2 break-words font-mono text-sm text-foreground">{row.value}</dd>
          </div>
        ))}
      </dl>
    </article>
  );
}
