import React from 'react';
import { motion } from 'motion/react';
import { CheckCircle2, Link2, Link2Off, PlugZap } from 'lucide-react';

import { McpItem } from './chat/types';

interface McpViewProps {
  availableMcps: McpItem[];
  selectedMcpCodes: string[];
  mcpConnected: boolean;
  setSelectedMcpCodes: (mcpCodes: string[] | ((previous: string[]) => string[])) => void;
  setMcpConnected: (value: boolean) => void;
}

/**
 * 渲染用户侧 MCP 管理页面，统一管理可选 MCP 与连接状态。
 */
export default function McpView({
  availableMcps,
  selectedMcpCodes,
  mcpConnected,
  setSelectedMcpCodes,
  setMcpConnected,
}: McpViewProps) {
  /**
   * 切换单个 MCP 启用状态，保证页面与聊天输入区共享同一份状态。
   * @param mcpCode MCP 编码。
   */
  const toggleMcp = (mcpCode: string) => {
    setSelectedMcpCodes((previous) =>
      previous.includes(mcpCode)
        ? previous.filter((item) => item !== mcpCode)
        : [...previous, mcpCode],
    );
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: 16 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -16 }}
      className="flex h-full flex-col overflow-y-auto bg-background px-6 py-8 md:px-12"
    >
      <div className="mx-auto w-full max-w-6xl">
        <div className="mb-8 flex flex-col gap-4 rounded-2xl border border-border bg-surface-container-low p-6 md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-foreground md:text-4xl">
              MCP 管理
            </h1>
            <p className="mt-2 text-sm leading-6 text-muted">
              选择要在对话中可调用的 MCP，并控制当前会话是否连接 MCP。
            </p>
          </div>
          <button
            type="button"
            aria-label="切换MCP总连接"
            aria-pressed={mcpConnected}
            onClick={() => setMcpConnected(!mcpConnected)}
            className={`inline-flex items-center gap-2 self-start rounded-full border px-4 py-2 text-sm font-medium transition-colors md:self-auto ${
              mcpConnected
                ? 'border-border-active bg-surface-container-high text-foreground'
                : 'border-border bg-surface text-muted'
            }`}
          >
            {mcpConnected ? <Link2 size={16} /> : <Link2Off size={16} />}
            {mcpConnected ? '已连接 MCP' : '未连接 MCP'}
          </button>
        </div>

        <section className="rounded-2xl border border-border bg-surface p-4 md:p-6">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-foreground">可用 MCP</h2>
            <span className="rounded-full border border-border bg-surface-container px-3 py-1 text-xs text-muted">
              已选 {selectedMcpCodes.length} / {availableMcps.length}
            </span>
          </div>

          {availableMcps.length === 0 ? (
            <div className="rounded-xl border border-dashed border-border bg-surface-container px-4 py-6 text-sm text-muted">
              当前没有可用 MCP，请先在管理端添加并启用 MCP 配置
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {availableMcps.map((mcp) => {
                const checked = selectedMcpCodes.includes(mcp.mcpCode);
                return (
                  <button
                    key={mcp.mcpCode}
                    type="button"
                    aria-label={`切换MCP ${mcp.displayName}`}
                    aria-pressed={checked}
                    onClick={() => toggleMcp(mcp.mcpCode)}
                    className={`flex items-start justify-between rounded-xl border p-4 text-left transition-colors ${
                      checked
                        ? 'border-border-active bg-surface-container-high'
                        : 'border-border bg-surface-container hover:border-border-active'
                    }`}
                  >
                    <span className="min-w-0">
                      <span className="flex items-center gap-2 text-sm font-medium text-foreground">
                        <PlugZap size={16} />
                        <span className="truncate">{mcp.displayName}</span>
                      </span>
                      <span className="mt-1 block font-mono text-[11px] text-muted">
                        /{mcp.mcpCode}
                      </span>
                      {mcp.description ? (
                        <span className="mt-2 block text-xs leading-5 text-muted">
                          {mcp.description}
                        </span>
                      ) : null}
                    </span>
                    <span className={`ml-3 mt-0.5 ${checked ? 'text-foreground' : 'text-muted'}`}>
                      <CheckCircle2 size={16} />
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </section>
      </div>
    </motion.div>
  );
}
