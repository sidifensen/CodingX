import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import clsx from 'clsx';
import { mockUsers } from '../data';

export function UserDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const user = mockUsers.find(u => u.id === id) || mockUsers[0]; 

  return (
    <div className="p-lg w-full">
      <div className="mb-lg flex items-center gap-xs">
        <button onClick={() => navigate(-1)} className="text-secondary hover:text-ink transition-colors flex items-center gap-xs active:scale-95 group">
          <span className="material-symbols-outlined text-[18px] group-hover:-translate-x-1 transition-transform">arrow_back</span>
          返回用户列表
        </button>
      </div>

      <div className="mb-xl flex justify-between items-start bg-surface-container-lowest p-xl rounded-2xl border border-border-hairline shadow-sm">
        <div className="flex gap-xl items-center">
          <img className="w-24 h-24 rounded-full border-4 border-surface-container-low shadow-sm bg-surface-container object-cover" src={user.avatar} alt="User" />
          <div className="space-y-sm">
            <h2 className="font-headline-md text-headline-md text-ink flex items-center gap-md">
              {user.name}
              <span className={clsx("px-2 py-0.5 rounded text-[11px] font-bold uppercase border", user.role === 'Admin' ? "bg-primary text-on-primary border-primary" : "bg-surface-container-low text-secondary border-border-strong")}>{user.roleLabel}</span>
              <span className={clsx("px-2 py-0.5 rounded text-[11px] font-bold border", user.status === 'Normal' ? "bg-status-running-bg text-status-running border-status-running-border" : user.status === 'Disabled' ? "bg-status-failed-bg text-status-failed border-status-failed-border" : "bg-status-pending-bg text-status-pending border-status-pending-border")}>{user.status === 'Normal' ? '正常' : user.status === 'Disabled' ? '禁用' : '待审核'}</span>
            </h2>
            <div className="text-secondary flex items-center gap-xl">
              <span className="flex items-center gap-1.5"><span className="material-symbols-outlined text-[18px]">mail</span> {user.email}</span>
              <span className="flex items-center gap-1.5"><span className="material-symbols-outlined text-[18px]">badge</span> {user.id}</span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-sm">
          <button className="border border-border-strong bg-surface-container-lowest px-md py-2 rounded-lg text-ink hover:bg-surface-container-low transition-colors active:scale-95 text-body-sm font-medium shadow-sm">编辑信息</button>
          <button className="border border-error bg-error/5 text-error px-md py-2 rounded-lg hover:bg-error hover:text-on-error transition-colors active:scale-95 text-body-sm font-medium">冻结账户</button>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-lg">
        <div className="col-span-1 space-y-lg">
          <div className="bg-surface-container-lowest border border-border-hairline rounded-xl p-lg shadow-sm">
            <h3 className="font-title-sm text-ink mb-md flex items-center gap-2"><span className="material-symbols-outlined text-[20px] text-secondary">info</span> 基础信息</h3>
            <div className="space-y-md">
              <div className="flex flex-col">
                <span className="text-secondary text-[12px] mb-1">注册时间</span>
                <span className="text-ink font-medium">{user.createdAt}</span>
              </div>
              <div className="flex flex-col">
                <span className="text-secondary text-[12px] mb-1">最后登录时间</span>
                <span className="text-ink font-medium">{user.lastLogon}</span>
              </div>
              <div className="flex flex-col">
                <span className="text-secondary text-[12px] mb-1">登录 IP</span>
                <span className="font-data-mono text-tertiary-container">192.168.1.104</span>
              </div>
            </div>
          </div>
        </div>

        <div className="col-span-2 space-y-lg">
           <div className="bg-surface-container-lowest border border-border-hairline rounded-xl p-lg shadow-sm">
             <div className="flex justify-between items-center mb-md">
               <h3 className="font-title-sm text-ink flex items-center gap-2"><span className="material-symbols-outlined text-[20px] text-secondary">admin_panel_settings</span> 职责与权限</h3>
               <button className="text-primary hover:underline text-[12px] font-medium">添加权限</button>
             </div>
             
             <div className="space-y-sm">
               <div className="p-md flex justify-between items-center bg-surface-container-low rounded-lg border border-border-hairline group hover:border-border-strong transition-colors">
                 <div className="flex items-center gap-md">
                   <div className="w-10 h-10 bg-surface rounded flex items-center justify-center text-status-preview border border-border-hairline">
                     <span className="material-symbols-outlined">security</span>
                   </div>
                   <div>
                     <p className="font-medium text-ink">系统管理组 (Admin Role)</p>
                     <p className="text-[12px] text-secondary">全局配置、用户管理、日志审计</p>
                   </div>
                 </div>
                 <button className="text-error text-[12px] hover:underline opacity-0 group-hover:opacity-100 transition-opacity">移除</button>
               </div>
               
               <div className="p-md flex justify-between items-center bg-surface-container-low rounded-lg border border-border-hairline group hover:border-border-strong transition-colors">
                 <div className="flex items-center gap-md">
                   <div className="w-10 h-10 bg-surface rounded flex items-center justify-center text-secondary border border-border-hairline">
                     <span className="material-symbols-outlined">terminal</span>
                   </div>
                   <div>
                     <p className="font-medium text-ink">MCP 调试组</p>
                     <p className="text-[12px] text-secondary">允许使用终端并挂载测试 Server</p>
                   </div>
                 </div>
                 <button className="text-error text-[12px] hover:underline opacity-0 group-hover:opacity-100 transition-opacity">移除</button>
               </div>
             </div>
           </div>
        </div>
      </div>
    </div>
  );
}
