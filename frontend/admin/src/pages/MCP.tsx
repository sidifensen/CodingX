import React from 'react';
import { Alert } from 'antd';

import { AdminChatApi, type AdminMcpConfig, type AdminMcpToolView } from '../api/adminChatApi';
import { useAdminMessage } from '../components/AdminMessageContext';
import { McpConfigModal } from './mcp/McpConfigModal';
import { McpDeleteModal } from './mcp/McpDeleteModal';
import { McpPingResultModal } from './mcp/McpPingResultModal';
import { McpTable } from './mcp/McpTable';
import { MCP_TABLE_PAGE_SIZE, type McpDialogMode } from './mcp/mcpTypes';
import { extractErrorMessage, mergeConfigAndTools } from './mcp/mcpUtils';
import { McpToolbar } from './mcp/McpToolbar';

/**
 * 管理端 MCP 页面：同时承载数据库配置管理和在线探测能力。
 */
export function MCP() {
  const adminMessage = useAdminMessage();
  const [configs, setConfigs] = React.useState<AdminMcpConfig[]>([]);
  const [tools, setTools] = React.useState<AdminMcpToolView[]>([]);
  const [configLoading, setConfigLoading] = React.useState(true);
  const [toolLoading, setToolLoading] = React.useState(true);
  const [configErrorMessage, setConfigErrorMessage] = React.useState('');
  const [toolErrorMessage, setToolErrorMessage] = React.useState('');
  const [pingingToolId, setPingingToolId] = React.useState<string | null>(null);
  const [dialogResult, setDialogResult] = React.useState<AdminMcpToolView | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [dialogMode, setDialogMode] = React.useState<McpDialogMode>('create');
  const [editingConfig, setEditingConfig] = React.useState<AdminMcpConfig | null>(null);
  const [deleteTarget, setDeleteTarget] = React.useState<AdminMcpConfig | null>(null);
  const [pageNo, setPageNo] = React.useState(1);
  const mergedRows = React.useMemo(() => mergeConfigAndTools(configs, tools), [configs, tools]);
  const isTableLoading = configLoading || toolLoading;
  const pageCount = Math.max(1, Math.ceil(mergedRows.length / MCP_TABLE_PAGE_SIZE));
  const safePageNo = Math.min(pageNo, pageCount);

  React.useEffect(() => {
    if (pageNo > pageCount) {
      setPageNo(pageCount);
    }
  }, [pageCount, pageNo]);

  /**
   * 加载数据库中的 MCP 配置列表。
   */
  const loadConfigs = React.useCallback(async () => {
    setConfigLoading(true);
    setConfigErrorMessage('');
    try {
      const result = await AdminChatApi.listMcpConfigs();
      setConfigs(result ?? []);
    } catch (error) {
      setConfigErrorMessage(extractErrorMessage(error, '加载 MCP 配置失败'));
    } finally {
      setConfigLoading(false);
    }
  }, []);

  /**
   * 加载后端执行器探测列表。
   */
  const loadTools = React.useCallback(async () => {
    setToolLoading(true);
    setToolErrorMessage('');
    try {
      const result = await AdminChatApi.listMcpTools();
      setTools(result ?? []);
    } catch (error) {
      setToolErrorMessage(extractErrorMessage(error, '加载 MCP 工具失败'));
    } finally {
      setToolLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void Promise.all([loadConfigs(), loadTools()]);
  }, [loadConfigs, loadTools]);

  const refreshAll = React.useCallback(() => {
    void Promise.all([loadConfigs(), loadTools()]);
  }, [loadConfigs, loadTools]);

  /**
   * 新增配置弹窗入口。
   */
  const openCreateDialog = () => {
    setDialogMode('create');
    setEditingConfig(null);
    setDialogOpen(true);
  };

  /**
   * 以指定编码打开新增弹窗，便于对“仅执行器”记录快速补齐配置。
   */
  const openCreateDialogWithCode = (mcpCode: string) => {
    setDialogMode('create');
    setEditingConfig({
      mcpCode,
      displayName: mcpCode,
      sourceType: 'built-in',
      enabled: 1,
      sortNo: 0,
    });
    setDialogOpen(true);
  };

  /**
   * 编辑配置弹窗入口。
   */
  const openEditDialog = (config: AdminMcpConfig) => {
    setDialogMode('edit');
    setEditingConfig(config);
    setDialogOpen(true);
  };

  /**
   * 执行一次在线探测，并把结果放到页面内弹窗展示。
   */
  const handlePing = async (toolId: string) => {
    setPingingToolId(toolId);
    try {
      const result = await AdminChatApi.pingMcpTool(toolId);
      setDialogResult(result);
    } catch (error) {
      setDialogResult({
        toolId,
        displayName: toolId,
        category: '自定义',
        source: '内置后端',
        status: 'failed',
        statusLabel: '异常',
        ok: false,
        message: extractErrorMessage(error, '探测失败'),
      });
    } finally {
      setPingingToolId(null);
    }
  };

  return (
    <div className="w-full space-y-lg p-lg">
      <McpToolbar loading={isTableLoading} onRefresh={refreshAll} onCreate={openCreateDialog} />

      {configErrorMessage || toolErrorMessage ? (
        <Alert showIcon type="error" message={configErrorMessage || toolErrorMessage} />
      ) : null}

      <McpTable
        rows={mergedRows}
        loading={isTableLoading}
        currentPage={safePageNo}
        pingingToolId={pingingToolId}
        onPageChange={setPageNo}
        onPing={(toolId) => void handlePing(toolId)}
        onEdit={openEditDialog}
        onCreateWithCode={openCreateDialogWithCode}
        onDelete={setDeleteTarget}
      />

      <McpConfigModal
        open={dialogOpen}
        mode={dialogMode}
        config={editingConfig}
        onCancel={() => setDialogOpen(false)}
        onSubmit={async (payload) => {
          if (dialogMode === 'edit' && editingConfig?.id != null) {
            await AdminChatApi.updateMcpConfig(editingConfig.id, payload);
          } else {
            await AdminChatApi.createMcpConfig(payload);
          }
          setDialogOpen(false);
          await loadConfigs();
          void adminMessage.success(dialogMode === 'edit' ? 'MCP 配置已保存' : 'MCP 配置已创建');
        }}
      />

      <McpDeleteModal
        config={deleteTarget}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={async () => {
          if (deleteTarget?.id == null) {
            void adminMessage.error('MCP 配置缺少主键，无法删除');
            setDeleteTarget(null);
            return;
          }
          try {
            await AdminChatApi.deleteMcpConfig(deleteTarget.id);
            setDeleteTarget(null);
            await loadConfigs();
            void adminMessage.success('MCP 配置已删除');
          } catch (error) {
            void adminMessage.error(extractErrorMessage(error, '删除 MCP 配置失败'));
          }
        }}
      />

      <McpPingResultModal result={dialogResult} onClose={() => setDialogResult(null)} />
    </div>
  );
}
