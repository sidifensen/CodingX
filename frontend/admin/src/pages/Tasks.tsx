import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import clsx from 'clsx';
import { mockTasks } from '../data';

export function Tasks() {
  const [filter, setFilter] = useState('全部');

  const tasks = mockTasks.filter(t => {
    if (filter === '全部') return true;
    if (filter === '运行中') return t.status === 'Running';
    if (filter === '已完成') return t.status === 'Completed';
    if (filter === '失败') return t.status === 'Failed';
    return true;
  });

  return (
    <div className="p-lg w-full">
      <div className="mb-lg flex justify-between items-end">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">任务管理</h2>
          <p className="text-secondary mt-1">查看和管理全局调度任务的状态、耗时与结果。</p>
        </div>
        <button className="bg-ink text-on-ink px-lg py-2 rounded-lg font-button text-button flex items-center gap-xs active:scale-95 transition-transform hover:shadow-lg">
           <span className="material-symbols-outlined text-[18px]">add</span>
           新建任务
        </button>
      </div>
      
      <div className="bg-surface-container-lowest border border-border-hairline rounded-xl p-md mb-lg flex justify-between items-center">
        <div className="flex gap-2">
          {['全部', '运行中', '已完成', '失败'].map(f => (
            <button 
              key={f}
              onClick={() => setFilter(f)}
              className={clsx("px-md py-1.5 rounded-md transition-colors text-body-sm font-medium border", filter === f ? "bg-surface-container text-ink shadow-sm border-border-strong" : "bg-transparent text-secondary border-transparent hover:bg-surface-container-low hover:text-ink")}
            >
              {f}
            </button>
          ))}
        </div>
        <div className="relative w-64">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-secondary text-[18px]">search</span>
          <input type="text" placeholder="搜索任务描述或 ID" className="w-full pl-9 pr-3 py-1.5 border border-border-hairline rounded-md bg-surface-container-low text-body-sm focus:outline-none focus:border-ink focus:ring-1 focus:ring-ink transition-all" />
        </div>
      </div>

      <div className="bg-surface-container-lowest border border-border-hairline rounded-xl overflow-hidden shadow-sm">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-surface-container-low border-b border-border-hairline">
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">Task ID</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">描述</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">执行人</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">状态</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">消耗</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary text-right">时间</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-hairline">
            {tasks.map((task) => (
              <tr key={task.id} className="hover:bg-surface-container-low transition-colors group">
                <td className="px-lg py-md font-data-mono text-tertiary-container"><Link to={`/tasks/${task.id}`} className="hover:text-primary transition-colors hover:underline">#{task.id}</Link></td>
                <td className="px-lg py-md text-ink font-medium max-w-sm truncate" title={task.description}>{task.description}</td>
                <td className="px-lg py-md text-secondary text-[12px]"><span className="material-symbols-outlined text-[14px] align-middle mr-1">person</span>{task.assignee}</td>
                <td className="px-lg py-md">
                   <div className="flex items-center gap-1.5">
                    <span className={clsx("w-1.5 h-1.5 rounded-full", task.status === 'Running' ? "bg-status-running animate-pulse" : task.status === 'Completed' ? "bg-border-strong" : task.status === 'Failed' ? "bg-status-failed" : "bg-status-pending")}></span>
                    <span className={clsx("text-body-sm font-medium", task.status === 'Running' ? "text-status-running" : task.status === 'Completed' ? "text-secondary" : task.status === 'Failed' ? "text-status-failed" : "text-status-pending")}>{task.statusLabel}</span>
                  </div>
                </td>
                <td className="px-lg py-md text-secondary text-[12px]"><span className="material-symbols-outlined text-[14px] align-middle mr-1 text-tertiary-container">timer</span>{task.duration}</td>
                <td className="px-lg py-md text-right text-[12px] text-tertiary-container">{task.createdAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
        
        {tasks.length === 0 && (
          <div className="p-xl text-center flex flex-col items-center justify-center text-secondary">
             <span className="material-symbols-outlined text-[48px] text-border-strong mb-sm">assignment</span>
             <p>暂无任务数据</p>
          </div>
        )}
      </div>
    </div>
  );
}
