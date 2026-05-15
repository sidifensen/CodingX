import React from 'react';
import { mockSkills } from '../data';

export function Skills() {
  return (
    <div className="p-lg w-full">
      <div className="mb-lg flex justify-between items-end">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">技能管理 (Skills)</h2>
          <p className="text-secondary mt-1">管理应用内部搭载的各类型代理技能。</p>
        </div>
        <button className="bg-primary text-on-primary px-lg py-2 rounded-lg font-button text-button flex items-center gap-xs active:scale-95 transition-transform hover:shadow-md">
           <span className="material-symbols-outlined text-[18px]">add</span>
           创建新技能
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-md">
        {mockSkills.map(skill => (
          <div key={skill.id} className="bg-surface-container-lowest border border-border-hairline rounded-xl p-lg hover:border-border-strong hover:shadow-md transition-all group cursor-pointer relative overflow-hidden">
            <div className="absolute top-0 right-0 w-16 h-16 bg-gradient-to-bl from-surface-container-low to-transparent mix-blend-multiply opacity-50 group-hover:opacity-100 transition-opacity"></div>
            <div className="flex justify-between items-start mb-md relative">
              <div className="w-12 h-12 rounded-xl bg-surface-container flex items-center justify-center border border-border-hairline group-hover:bg-primary group-hover:text-on-primary transition-colors">
                <span className="material-symbols-outlined text-[24px]">extension</span>
              </div>
              <span className="px-2 py-1 bg-surface-container-low border border-border-hairline rounded text-[10px] font-data-mono text-secondary">v{skill.version}</span>
            </div>
            <h3 className="font-title-md text-ink mb-1 group-hover:text-primary transition-colors">{skill.name}</h3>
            <p className="text-secondary text-[12px] mb-lg line-clamp-2">{skill.description}</p>
            <div className="flex items-center justify-between mt-auto">
               <span className="text-secondary text-[12px] flex items-center gap-1"><span className="material-symbols-outlined text-[14px]">person</span> {skill.author}</span>
               <button className="text-primary font-medium text-[12px] hover:underline opacity-0 group-hover:opacity-100 transition-opacity">配置此项</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
