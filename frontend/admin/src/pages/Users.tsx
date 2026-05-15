import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import clsx from 'clsx';
import { mockUsers } from '../data';

export function Users() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState('全部');

  const filteredUsers = mockUsers.filter(u => {
    if (filter === '全部') return true;
    if (filter === '正常') return u.status === 'Normal';
    if (filter === '禁用') return u.status === 'Disabled';
    if (filter === '待审核') return u.status === 'Pending';
    return true;
  });

  return (
    <div className="p-lg w-full">
      <div className="mb-lg flex justify-between items-end">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">用户管理</h2>
          <p className="text-secondary mt-1">管理系统内的所有用户账户、角色分配及其活跃状态。</p>
        </div>
        <button className="bg-primary text-on-primary px-lg py-2 rounded-lg font-button text-button flex items-center gap-xs active:scale-95 transition-transform">
          <span className="material-symbols-outlined text-[18px]">person_add</span>
          新增用户
        </button>
      </div>

      <div className="bg-surface-container-lowest border border-border-hairline rounded-xl p-md mb-lg">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-md">
            <span className="font-label-caps text-label-caps text-secondary">状态筛选</span>
            <div className="flex bg-surface-container-low p-1 rounded-lg">
              {['全部', '正常', '禁用', '待审核'].map((f) => (
                <button
                  key={f}
                  onClick={() => setFilter(f)}
                  className={clsx("px-lg py-1.5 rounded-md text-body-sm transition-all", filter === f ? "text-primary font-bold bg-surface-container-lowest shadow-sm" : "text-secondary hover:text-ink")}
                >
                  {f}
                </button>
              ))}
            </div>
          </div>
          <div className="flex items-center gap-xs">
            <button className="flex items-center gap-xs border border-border-strong px-md py-2 rounded-lg text-secondary hover:bg-surface-container transition-colors active:scale-95">
              <span className="material-symbols-outlined text-[18px]">filter_list</span>
              高级筛选
            </button>
            <button className="flex items-center gap-xs border border-border-strong px-md py-2 rounded-lg text-secondary hover:bg-surface-container transition-colors active:scale-95">
              <span className="material-symbols-outlined text-[18px]">download</span>
              导出数据
            </button>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-12 gap-md mb-lg">
        <div className="col-span-3 bg-surface-container-lowest border border-border-hairline p-md rounded-xl shadow-sm">
          <p className="font-label-caps text-label-caps text-secondary mb-1">总用户数</p>
          <div className="flex items-baseline gap-xs">
            <h3 className="font-metric-lg text-metric-lg text-ink">12,482</h3>
            <span className="text-status-running text-[12px] font-bold">+12% ↑</span>
          </div>
        </div>
        <div className="col-span-3 bg-surface-container-lowest border border-border-hairline p-md rounded-xl shadow-sm">
          <p className="font-label-caps text-label-caps text-secondary mb-1">今日活跃</p>
          <div className="flex items-baseline gap-xs">
            <h3 className="font-metric-lg text-metric-lg text-ink">3,120</h3>
            <span className="text-status-running text-[12px] font-bold">+5% ↑</span>
          </div>
        </div>
        <div className="col-span-3 bg-surface-container-lowest border border-border-hairline p-md rounded-xl shadow-sm">
          <p className="font-label-caps text-label-caps text-secondary mb-1">待审核</p>
          <div className="flex items-baseline gap-xs">
            <h3 className="font-metric-lg text-metric-lg text-ink">14</h3>
            <span className="text-status-pending text-[12px] font-bold">需处理</span>
          </div>
        </div>
        <div className="col-span-3 bg-surface-container-lowest border border-border-hairline p-md rounded-xl shadow-sm">
          <p className="font-label-caps text-label-caps text-secondary mb-1">已禁用</p>
          <div className="flex items-baseline gap-xs">
            <h3 className="font-metric-lg text-metric-lg text-ink">86</h3>
            <span className="text-secondary text-[12px] font-bold">-2% ↓</span>
          </div>
        </div>
      </div>

      <div className="bg-surface-container-lowest border border-border-hairline rounded-xl overflow-hidden shadow-sm">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-surface-container-low border-b border-border-hairline">
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline">用户</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline">角色</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline">状态</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline">最近登录</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline">创建时间</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline text-right">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-hairline">
            {filteredUsers.map((user) => (
              <tr key={user.id} className="hover:bg-surface-container-low transition-colors group cursor-pointer" onClick={() => navigate(`/users/${user.id}`)}>
                <td className="px-lg py-md">
                  <div className="flex items-center gap-md">
                    <img className="w-10 h-10 rounded-full border border-border-hairline bg-surface-container-low object-cover" src={user.avatar} alt="User" />
                    <div>
                      <div className="font-title-md text-ink leading-tight group-hover:text-status-preview transition-colors">{user.name}</div>
                      <div className="text-secondary text-[12px]">{user.email}</div>
                    </div>
                  </div>
                </td>
                <td className="px-lg py-md">
                  <span className={clsx("px-2 py-0.5 rounded text-[11px] font-bold uppercase", user.role === 'Admin' ? "bg-primary text-on-primary" : "border border-border-strong text-secondary")}>
                    {user.roleLabel}
                  </span>
                </td>
                <td className="px-lg py-md">
                  <div className="flex items-center gap-xs">
                    <div className={clsx("w-2 h-2 rounded-full", user.status === 'Normal' ? "bg-status-running" : user.status === 'Disabled' ? "bg-status-failed" : "bg-status-pending")}></div>
                    <span className={clsx("font-medium", user.status === 'Normal' ? "text-status-running" : user.status === 'Disabled' ? "text-status-failed" : "text-status-pending")}>
                      {user.status === 'Normal' ? '正常' : user.status === 'Disabled' ? '禁用' : '待审核'}
                    </span>
                  </div>
                </td>
                <td className="px-lg py-md font-data-mono text-secondary">{user.lastLogon}</td>
                <td className="px-lg py-md font-data-mono text-secondary">{user.createdAt}</td>
                <td className="px-lg py-md text-right">
                  <div className="flex items-center justify-end gap-md">
                    {user.status === 'Pending' ? (
                      <button className="text-primary hover:underline transition-colors font-bold" onClick={(e) => e.stopPropagation()}>审核通过</button>
                    ) : (
                      <button className="text-secondary hover:text-ink transition-colors font-medium">详情</button>
                    )}
                    <div className={clsx("w-10 h-5 rounded-full relative cursor-pointer hover:opacity-80 transition-opacity", user.status === 'Disabled' || user.status === 'Pending' ? "bg-surface-container-highest" : "bg-ink")} onClick={(e) => { e.stopPropagation(); }}>
                      <div className={clsx("absolute top-1 w-3 h-3 bg-surface-container-lowest rounded-full shadow-sm transition-all", user.status === 'Disabled' || user.status === 'Pending' ? "left-1" : "right-1")}></div>
                    </div>
                    <button className="material-symbols-outlined text-secondary hover:text-ink transition-colors active:scale-95" onClick={(e) => e.stopPropagation()}>lock_reset</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        
        <div className="px-lg py-md flex items-center justify-between bg-surface-container-low border-t border-border-hairline">
          <p className="text-secondary">显示 1-{filteredUsers.length} 条，共 {filteredUsers.length} 条</p>
          <div className="flex items-center gap-xs">
            <button className="w-8 h-8 flex items-center justify-center rounded border border-border-strong text-secondary hover:bg-surface-container-lowest transition-colors">
              <span className="material-symbols-outlined text-[18px]">chevron_left</span>
            </button>
            <button className="w-8 h-8 flex items-center justify-center rounded border border-ink bg-ink text-on-ink font-bold">1</button>
            <button className="w-8 h-8 flex items-center justify-center rounded border border-border-strong text-secondary hover:bg-surface-container-lowest transition-colors">2</button>
            <button className="w-8 h-8 flex items-center justify-center rounded border border-border-strong text-secondary hover:bg-surface-container-lowest transition-colors">3</button>
            <span className="text-secondary px-1">...</span>
            <button className="w-8 h-8 flex items-center justify-center rounded border border-border-strong text-secondary hover:bg-surface-container-lowest transition-colors">125</button>
            <button className="w-8 h-8 flex items-center justify-center rounded border border-border-strong text-secondary hover:bg-surface-container-lowest transition-colors">
              <span className="material-symbols-outlined text-[18px]">chevron_right</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
