import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import clsx from 'clsx';
import { mockTasks } from '../data';

export function TaskDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const task = mockTasks.find(t => t.id === id) || mockTasks[0];

  return (
    <div className="p-lg w-full">
       <div className="mb-lg flex items-center gap-xs">
        <button onClick={() => navigate(-1)} className="text-secondary hover:text-ink transition-colors flex items-center gap-xs active:scale-95 group">
          <span className="material-symbols-outlined text-[18px] group-hover:-translate-x-1 transition-transform">arrow_back</span>
          返回任务列表
        </button>
      </div>
      
      <div className="bg-surface-container-lowest p-xl rounded-2xl border border-border-hairline shadow-sm">
        <div className="flex justify-between items-start mb-xl pb-lg border-b border-border-hairline">
          <div>
            <div className="flex items-center gap-sm mb-xs">
              <span className="font-data-mono text-secondary px-2 py-1 bg-surface-container-low rounded border border-border-hairline text-[12px]">#{task.id}</span>
              <span className={clsx("px-2 py-0.5 rounded text-[11px] font-bold border flex items-center gap-1", task.status === 'Running' ? "bg-status-running-bg text-status-running border-status-running-border" : task.status === 'Failed' ? "bg-status-failed-bg text-status-failed border-status-failed-border" : task.status === 'Completed' ? "bg-surface-container-low text-secondary border-border-strong" : "bg-status-pending-bg text-status-pending border-status-pending-border")}>
                {task.status === 'Running' && <span className="w-1.5 h-1.5 rounded-full bg-status-running animate-pulse"></span>}
                {task.statusLabel}
              </span>
            </div>
            <h2 className="font-headline-sm text-headline-sm text-ink">{task.description}</h2>
          </div>
          <div className="flex gap-sm">
             <button className="border border-border-strong px-md py-2 text-ink rounded-lg hover:bg-surface-container transition-colors shadow-sm flex items-center gap-1"><span className="material-symbols-outlined text-[18px]">play_arrow</span>重试</button>
             <button className="border border-border-strong px-md py-2 text-error rounded-lg hover:bg-error/5 transition-colors flex items-center gap-1"><span className="material-symbols-outlined text-[18px]">stop</span>终止</button>
          </div>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-lg mb-xl">
           <div>
             <p className="text-secondary text-[12px] mb-1">执行人</p>
             <p className="text-ink font-medium flex items-center gap-1"><span className="material-symbols-outlined text-[16px] text-tertiary-container">person</span> {task.assignee}</p>
           </div>
           <div>
             <p className="text-secondary text-[12px] mb-1">创建时间</p>
             <p className="text-ink font-medium">{task.createdAt}</p>
           </div>
           <div>
             <p className="text-secondary text-[12px] mb-1">进度耗时</p>
             <p className="text-ink font-medium">{task.duration}</p>
           </div>
           <div>
             <p className="text-secondary text-[12px] mb-1">重试次数</p>
             <p className="text-ink font-medium">0</p>
           </div>
        </div>

        <div>
          <h3 className="font-title-sm text-ink mb-md flex items-center gap-xs"><span className="material-symbols-outlined text-[20px] text-secondary">terminal</span> 执行日志</h3>
          <div className="bg-surface-container-lowest border border-border-hairline rounded-xl p-md font-data-mono text-[13px] leading-relaxed overflow-x-auto">
            <div className="text-secondary mb-1">[2023-11-20 10:00:00] INFO: Initializing task context...</div>
            <div className="text-secondary mb-1">[2023-11-20 10:00:02] INFO: Connecting to Database MCP...</div>
            <div className="text-status-running mb-1">[2023-11-20 10:00:05] SUCCESS: Connection established.</div>
            <div className="text-secondary mb-1">[2023-11-20 10:00:06] INFO: Executing data migration script {task.id}...</div>
            {task.status === 'Running' && <div className="text-status-pending mt-2 animate-pulse">Waiting for process to output logs...</div>}
            {task.status === 'Failed' && <div className="text-status-failed mt-2">[2023-11-20 10:00:10] ERROR: Expected table 'users_v2' not found. Terminating.</div>}
            {task.status === 'Completed' && <div className="text-status-running mt-2">[2023-11-20 10:04:22] SUCCESS: Data migration completed. 10420 rows affected.</div>}
          </div>
        </div>
      </div>
    </div>
  );
}
