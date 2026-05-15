import React, { useState } from 'react';
import { mockNotifications, Notification } from '../data';
import clsx from 'clsx';

export function Notifications() {
  const [notifications, setNotifications] = useState<Notification[]>(mockNotifications);
  
  const markAsRead = (id: string) => {
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, isRead: true } : n));
  };

  const markAllAsRead = () => {
    setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
  };

  const unreadCount = notifications.filter(n => !n.isRead).length;

  return (
    <div className="p-lg w-full">
      <div className="mb-lg flex justify-between items-end">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">通知中心</h2>
          <p className="text-secondary mt-1">系统告警、任务完成通知及团队消息协作。</p>
        </div>
        {unreadCount > 0 && (
          <button 
            onClick={markAllAsRead}
            className="text-primary hover:underline text-[12px] font-medium transition-colors"
          >
            全部标记为已读
          </button>
        )}
      </div>

      <div className="space-y-sm">
        {notifications.map(note => (
          <div 
            key={note.id} 
            className={clsx(
              "p-md rounded-xl border transition-all cursor-pointer group flex gap-md",
              note.isRead ? "bg-surface-container-lowest border-border-hairline" : "bg-sky-wash border-primary/20 relative"
            )}
            onClick={() => markAsRead(note.id)}
          >
            {!note.isRead && <div className="absolute top-1/2 -translate-y-1/2 left-3 w-1.5 h-1.5 rounded-full bg-primary shadow-[0_0_8px_rgba(var(--color-primary-rgb),0.5)]"></div>}
            
            <div className={clsx("w-10 h-10 rounded-full flex items-center justify-center shrink-0 ml-xs", 
              note.type === 'Alert' ? "bg-status-failed-bg text-status-failed" :
              note.type === 'Info' ? "bg-primary-container text-primary" : "bg-status-running-bg text-status-running"
            )}>
              <span className="material-symbols-outlined text-[20px]">
                {note.type === 'Alert' ? 'warning' : note.type === 'Info' ? 'info' : 'check_circle'}
              </span>
            </div>
            
            <div className="flex-1 min-w-0">
               <div className="flex justify-between items-start mb-1">
                 <h3 className={clsx("font-medium text-[13px]", note.isRead ? "text-secondary" : "text-ink")}>{note.title}</h3>
                 <span className="text-[11px] text-tertiary-container whitespace-nowrap">{note.createdAt}</span>
               </div>
               <p className={clsx("text-[12px] leading-relaxed line-clamp-2", note.isRead ? "text-tertiary-container" : "text-secondary")}>{note.message}</p>
            </div>
            
            {!note.isRead && (
              <div className="shrink-0 flex items-center justify-center w-8">
                 <button className="opacity-0 group-hover:opacity-100 material-symbols-outlined text-secondary hover:text-ink text-[18px] transition-all" title="标记已读">done</button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
