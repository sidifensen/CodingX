import React, { useEffect, useState } from 'react';

import { AdminChatApi, AdminSkill } from '../api/adminChatApi';

/**
 * 管理端技能管理页：读取后端真实技能配置并渲染。
 */
export function Skills() {
  const [skills, setSkills] = useState<AdminSkill[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    let disposed = false;

    const loadSkills = async () => {
      setIsLoading(true);
      setErrorMessage('');
      try {
        const response = await AdminChatApi.listSkills();
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

  return (
    <div className="w-full p-lg">
      <div className="mb-lg flex items-end justify-between">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">技能管理 (Skills)</h2>
          <p className="mt-1 text-secondary">管理应用内部搭载的各类型代理技能。</p>
        </div>
        <button className="flex items-center gap-xs rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary transition-transform hover:shadow-md active:scale-95">
          <span className="material-symbols-outlined text-[18px]">add</span>
          创建新技能
        </button>
      </div>

      {isLoading ? <div className="rounded-xl border border-border-hairline bg-surface-container-lowest px-4 py-6 text-sm text-secondary">技能加载中...</div> : null}
      {!isLoading && errorMessage ? <div className="rounded-xl border border-red-300 bg-red-50 px-4 py-6 text-sm text-red-600">{errorMessage}</div> : null}

      {!isLoading && !errorMessage ? (
        <div className="grid grid-cols-1 gap-md md:grid-cols-2 lg:grid-cols-3">
          {skills.map((skill) => (
            <div
              key={skill.id ?? skill.skillCode}
              className="group relative cursor-pointer overflow-hidden rounded-xl border border-border-hairline bg-surface-container-lowest p-lg transition-all hover:border-border-strong hover:shadow-md"
            >
              <div className="absolute top-0 right-0 h-16 w-16 bg-gradient-to-bl from-surface-container-low to-transparent opacity-50 mix-blend-multiply transition-opacity group-hover:opacity-100" />
              <div className="relative mb-md flex items-start justify-between">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl border border-border-hairline bg-surface-container transition-colors group-hover:bg-primary group-hover:text-on-primary">
                  <span className="material-symbols-outlined text-[24px]">extension</span>
                </div>
                <span className="rounded border border-border-hairline bg-surface-container-low px-2 py-1 font-data-mono text-[10px] text-secondary">
                  {skill.sourceType || 'built-in'}
                </span>
              </div>
              <h3 className="mb-1 font-title-md text-ink transition-colors group-hover:text-primary">{skill.displayName}</h3>
              <p className="mb-lg line-clamp-2 text-[12px] text-secondary">{skill.description || '暂无描述'}</p>
              <div className="mt-auto flex items-center justify-between text-[12px] text-secondary">
                <span>{skill.category || '未分类'}</span>
                <span className="font-data-mono">/{skill.skillCode}</span>
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
