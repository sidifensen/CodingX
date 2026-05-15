import React from 'react';
import { mockMCPServices } from '../data';
import clsx from 'clsx';

export function MCP() {
  return (
    <div className="p-lg w-full">
      <div className="mb-lg flex justify-between items-end">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">MCP 服务节点</h2>
          <p className="text-secondary mt-1">云端或本地托管的模型上下文提供协议 (Model Context Protocol) 节点监控。</p>
        </div>
        <button className="border border-border-strong text-ink bg-surface-container-lowest shadow-sm px-lg py-2 rounded-lg font-button text-button flex items-center gap-xs active:scale-95 transition-transform hover:bg-surface-container-low">
           <span className="material-symbols-outlined text-[18px]">add_link</span>
           接入新节点
        </button>
      </div>

      <div className="bg-surface-container-lowest border border-border-hairline rounded-xl overflow-hidden shadow-sm">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-surface-container-low border-b border-border-hairline">
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">节点名称</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">连接地址/Endpoint</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">健康状态</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">最后检测</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary text-right">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-hairline">
            {mockMCPServices.map(mcp => (
              <tr key={mcp.id} className="hover:bg-surface-container-low transition-colors group relative cursor-pointer">
                <td className="px-lg py-md font-medium text-ink flex items-center gap-sm">
                  <span className="material-symbols-outlined text-secondary text-[20px] pt-0.5">hub</span>
                  {mcp.name}
                </td>
                <td className="px-lg py-md font-data-mono text-[12px] text-tertiary-container">{mcp.endpoint}</td>
                <td className="px-lg py-md">
                   <div className="flex items-center gap-xs">
                    <span className={clsx("w-2 h-2 rounded-full", mcp.status === 'Healthy' ? "bg-status-running" : mcp.status === 'Degraded' ? "bg-status-pending" : "bg-status-failed")}></span>
                    <span className={clsx("text-body-sm font-medium", mcp.status === 'Healthy' ? "text-status-running" : mcp.status === 'Degraded' ? "text-status-pending" : "text-status-failed")}>{mcp.statusLabel}</span>
                  </div>
                </td>
                <td className="px-lg py-md text-secondary text-[12px]">{mcp.lastChecked}</td>
                <td className="px-lg py-md text-right">
                  <div className="flex justify-end gap-xs">
                    <button className="p-1 rounded text-secondary hover:text-ink hover:bg-surface-container transition-colors" title="测试连接"><span className="material-symbols-outlined text-[18px]">network_ping</span></button>
                    <button className="p-1 rounded text-secondary hover:text-ink hover:bg-surface-container transition-colors" title="配置"><span className="material-symbols-outlined text-[18px]">settings</span></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
