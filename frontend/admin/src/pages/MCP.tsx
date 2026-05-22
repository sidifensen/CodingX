import React from 'react';
import clsx from 'clsx';
import { createPortal } from 'react-dom';

import { AdminChatApi, AdminMcpConfig, AdminMcpToolView } from '../api/adminChatApi';
import { DataTableCard } from '../components/DataTableCard';

type McpDialogMode = 'create' | 'edit';

interface McpFormState {
  mcpCode: string;
  displayName: string;
  description: string;
  category: string;
  sourceType: string;
  sortNo: string;
  enabled: boolean;
}

interface UnifiedMcpRow {
  mcpCode: string;
  config: AdminMcpConfig | null;
  tool: AdminMcpToolView | null;
}

const emptyMcpForm: McpFormState = {
  mcpCode: '',
  displayName: '',
  description: '',
  category: '',
  sourceType: 'built-in',
  sortNo: '0',
  enabled: true,
};

const MCP_TABLE_PAGE_SIZE = 10;

/**
 * 管理端 MCP 页面：同时承载数据库配置管理和在线探测能力。
 */
export function MCP() {
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
  const totalRows = mergedRows.length;
  const pageCount = Math.max(1, Math.ceil(totalRows / MCP_TABLE_PAGE_SIZE));
  const safePageNo = Math.min(pageNo, pageCount);
  const pagedRows = React.useMemo(() => {
    const start = (safePageNo - 1) * MCP_TABLE_PAGE_SIZE;
    return mergedRows.slice(start, start + MCP_TABLE_PAGE_SIZE);
  }, [mergedRows, safePageNo]);
  // 对齐 Trace 管理：首屏加载且无记录时在表格内渲染骨架行。
  const showEmptyState = !isTableLoading && pagedRows.length === 0;
  const showSkeletonRows = isTableLoading && pagedRows.length === 0;

  React.useEffect(() => {
    if (pageNo > pageCount) {
      setPageNo(pageCount);
    }
  }, [pageNo, pageCount]);

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
   * @param mcpCode MCP 编码。
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
   * @param config 目标配置。
   */
  const openEditDialog = (config: AdminMcpConfig) => {
    setDialogMode('edit');
    setEditingConfig(config);
    setDialogOpen(true);
  };

  /**
   * 执行一次在线探测，并把结果放到页面内弹窗展示。
   * @param toolId 工具标识。
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
      <div className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">MCP 管理</h2>
          <p className="mt-1 text-secondary">管理数据库 MCP 配置，并在线探测后端工具可用性。</p>
        </div>
        <div className="flex flex-wrap items-center gap-sm">
          <button
            type="button"
            aria-label="刷新列表"
            className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink transition-colors hover:bg-surface-container-low"
            onClick={() => void Promise.all([loadConfigs(), loadTools()])}
          >
            刷新列表
          </button>
          <button
            type="button"
            aria-label="新增MCP配置"
            className="rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary transition-opacity hover:opacity-90"
            onClick={openCreateDialog}
          >
            新增MCP配置
          </button>
          <button
            type="button"
            aria-label="刷新全部"
            className="flex items-center gap-xs rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink shadow-sm transition-transform hover:bg-surface-container-low active:scale-95"
            onClick={() => void Promise.all([loadConfigs(), loadTools()])}
          >
            <span className="material-symbols-outlined text-[18px]">refresh</span>
            刷新全部
          </button>
        </div>
      </div>

      {configErrorMessage || toolErrorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {configErrorMessage || toolErrorMessage}
        </div>
      ) : null}

      <DataTableCard
        scrollTestId="mcp-table-scroll"
        loading={isTableLoading}
        loadingText="加载中..."
        summaryText={`第 ${safePageNo} / ${pageCount} 页，共 ${totalRows.toLocaleString('zh-CN')} 条`}
        paginationCurrent={safePageNo}
        paginationPages={pageCount}
        onPaginationChange={setPageNo}
        tableContent={(
          <table className="w-full min-w-[1280px] border-collapse text-left">
            <thead>
              <tr className="bg-surface-container-low border-b border-border-hairline">
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">编码</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">名称</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">分类</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">来源</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">配置状态</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">执行器状态</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">最近探测</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">排序</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md text-right font-label-caps text-label-caps text-secondary">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {showEmptyState ? (
                <tr>
                  <td className="px-lg py-xl text-center text-secondary" colSpan={9}>
                    暂无 MCP 配置
                  </td>
                </tr>
              ) : (
                pagedRows.map((row) => {
                  const config = row.config;
                  const tool = row.tool;
                  const rowKey = String(config?.id ?? row.mcpCode);
                  const statusLabel = tool?.statusLabel ?? (config ? '未接入' : '仅执行器');
                  const status = tool?.status ?? 'failed';
                  return (
                    <tr key={rowKey} className="hover:bg-surface-container-low transition-colors">
                      <td className="px-lg py-md font-data-mono text-[12px] text-tertiary-container">/{row.mcpCode}</td>
                      <td className="px-lg py-md text-ink">{config?.displayName || tool?.displayName || row.mcpCode}</td>
                      <td className="px-lg py-md text-secondary text-body-sm">{config?.category || tool?.category || '-'}</td>
                      <td className="px-lg py-md text-secondary text-body-sm">{config?.sourceType || tool?.source || '-'}</td>
                      <td className="px-lg py-md">
                        <EnabledBadge enabled={(config?.enabled ?? 0) !== 0} />
                      </td>
                      <td className="px-lg py-md">
                        <div className="flex items-center gap-xs">
                          <span className={clsx('w-2 h-2 rounded-full', statusDotClass(status))} />
                          <span className={clsx('text-body-sm font-medium', statusTextClass(status))}>{statusLabel}</span>
                        </div>
                      </td>
                      <td className="px-lg py-md text-secondary text-[12px]">{tool?.checkedAt || '-'}</td>
                      <td className="px-lg py-md text-secondary text-body-sm">{config?.sortNo ?? 0}</td>
                      <td className="px-lg py-md text-right">
                        <div className="inline-flex gap-sm">
                          <button
                            type="button"
                            aria-label={`测试 ${row.mcpCode}`}
                            className="rounded-lg border border-border-hairline bg-surface-container-lowest px-sm py-1.5 text-[12px] text-secondary transition-colors hover:bg-surface-container-low hover:text-ink disabled:opacity-60"
                            onClick={() => void handlePing(row.mcpCode)}
                            disabled={pingingToolId === row.mcpCode}
                          >
                            {pingingToolId === row.mcpCode ? '探测中...' : '测试'}
                          </button>
                          <button
                            type="button"
                            aria-label={`编辑配置 ${row.mcpCode}`}
                            className="rounded-lg border border-border-strong bg-surface-container-lowest px-sm py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
                            onClick={() => config ? openEditDialog(config) : openCreateDialogWithCode(row.mcpCode)}
                          >
                            {config ? '编辑' : '补配置'}
                          </button>
                          <button
                            type="button"
                            aria-label={`删除配置 ${row.mcpCode}`}
                            className="rounded-lg border border-error bg-error-container px-sm py-1.5 text-[12px] text-on-error-container transition-opacity hover:opacity-90"
                            onClick={() => config ? setDeleteTarget(config) : undefined}
                            disabled={!config}
                          >
                            删除
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
              {showSkeletonRows
                ? Array.from({ length: 10 }, (_, index) => (
                  <tr key={`mcp-loading-row-${index}`} data-testid="mcp-loading-skeleton-row">
                    <td colSpan={9} className="px-lg py-md">
                      <div className="h-6 w-full animate-pulse rounded bg-surface-container-low" />
                    </td>
                  </tr>
                ))
                : null}
            </tbody>
          </table>
        )}
      />

      {dialogOpen ? (
        <McpEditDialog
          mode={dialogMode}
          config={editingConfig}
          onClose={() => setDialogOpen(false)}
          onSubmit={async (payload) => {
            if (dialogMode === 'edit' && editingConfig?.id != null) {
              await AdminChatApi.updateMcpConfig(editingConfig.id, payload);
            } else {
              await AdminChatApi.createMcpConfig(payload);
            }
            setDialogOpen(false);
            await loadConfigs();
          }}
        />
      ) : null}

      {deleteTarget ? (
        <DeleteMcpDialog
          config={deleteTarget}
          onCancel={() => setDeleteTarget(null)}
          onConfirm={async () => {
            if (deleteTarget.id == null) {
              setConfigErrorMessage('MCP 配置缺少主键，无法删除');
              setDeleteTarget(null);
              return;
            }
            try {
              await AdminChatApi.deleteMcpConfig(deleteTarget.id);
              setDeleteTarget(null);
              await loadConfigs();
            } catch (error) {
              setConfigErrorMessage(extractErrorMessage(error, '删除 MCP 配置失败'));
            }
          }}
        />
      ) : null}

      {dialogResult ? (
        <PingResultDialog result={dialogResult} onClose={() => setDialogResult(null)} />
      ) : null}
    </div>
  );
}

/**
 * 配置启用状态标签。
 */
function EnabledBadge({ enabled }: { enabled: boolean }) {
  return (
    <span
      className={clsx(
        'rounded-full border px-2 py-0.5 text-[11px] font-medium',
        enabled
          ? 'border-border-strong bg-surface-container text-ink'
          : 'border-border-hairline bg-surface-container-low text-secondary',
      )}
    >
      {enabled ? '启用' : '停用'}
    </span>
  );
}

interface McpEditDialogProps {
  mode: McpDialogMode;
  config: AdminMcpConfig | null;
  onClose: () => void;
  onSubmit: (payload: AdminMcpConfig) => Promise<void>;
}

/**
 * MCP 配置编辑弹窗。
 */
function McpEditDialog({ mode, config, onClose, onSubmit }: McpEditDialogProps) {
  const [form, setForm] = React.useState<McpFormState>(() => toMcpForm(config));
  const [saving, setSaving] = React.useState(false);
  const [formError, setFormError] = React.useState('');
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});

  /**
   * 更新表单字段并清除对应错误。
   * @param field 字段名。
   * @param value 字段值。
   */
  const updateField = (field: keyof McpFormState, value: string | boolean) => {
    setFieldErrors((previous) => ({ ...previous, [field]: '' }));
    setForm((previous) => ({ ...previous, [field]: value }));
  };

  /**
   * 提交前做最小必填校验。
   */
  const validate = () => {
    const nextErrors: Record<string, string> = {};
    if (!form.mcpCode.trim()) {
      nextErrors.mcpCode = '请输入MCP编码';
    }
    if (!form.displayName.trim()) {
      nextErrors.displayName = '请输入MCP名称';
    }
    setFieldErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError('');
    if (!validate()) {
      return;
    }

    setSaving(true);
    try {
      await onSubmit(toMcpPayload(form, config));
    } catch (error) {
      setFormError(extractErrorMessage(error, mode === 'create' ? '新增MCP配置失败' : '保存MCP配置失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={mode === 'create' ? '新增MCP配置' : '编辑MCP配置'}
        className="max-h-[88vh] w-full max-w-2xl overflow-y-auto rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
      >
        <div className="sticky top-0 z-10 flex items-start justify-between gap-md border-b border-border-hairline bg-surface-container-lowest px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">
              {mode === 'create' ? '新增MCP配置' : '编辑MCP配置'}
            </h3>
            <p className="mt-1 text-body-sm text-secondary">维护MCP编码、展示信息、启用状态和排序。</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-sm py-xs text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭配置弹窗"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        <form className="space-y-lg p-lg" onSubmit={handleSubmit}>
          {formError ? (
            <div className="rounded-xl border border-error bg-error-container px-md py-sm text-sm text-on-error-container">
              {formError}
            </div>
          ) : null}

          <fieldset className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
            <legend className="px-xs font-title-sm text-ink">基础信息</legend>
            <div className="grid gap-md md:grid-cols-2">
              <TextField
                id="mcp-code"
                label="MCP编码"
                value={form.mcpCode}
                error={fieldErrors.mcpCode}
                disabled={mode === 'edit'}
                onChange={(value) => updateField('mcpCode', value)}
              />
              <TextField
                id="mcp-display-name"
                label="MCP名称"
                value={form.displayName}
                error={fieldErrors.displayName}
                onChange={(value) => updateField('displayName', value)}
              />
              <TextField
                id="mcp-category"
                label="分类"
                value={form.category}
                onChange={(value) => updateField('category', value)}
              />
              <TextField
                id="mcp-source-type"
                label="来源类型"
                value={form.sourceType}
                onChange={(value) => updateField('sourceType', value)}
              />
              <TextField
                id="mcp-sort"
                label="排序"
                value={form.sortNo}
                type="number"
                onChange={(value) => updateField('sortNo', value)}
              />
              <label className="flex items-center gap-sm rounded-xl border border-border-hairline bg-surface-container-lowest px-md py-sm text-ink">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(event) => updateField('enabled', event.target.checked)}
                />
                启用MCP
              </label>
            </div>
          </fieldset>

          <fieldset className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
            <legend className="px-xs font-title-sm text-ink">描述</legend>
            <TextAreaField
              id="mcp-description"
              label="MCP说明"
              value={form.description}
              rows={4}
              onChange={(value) => updateField('description', value)}
            />
          </fieldset>

          <div className="flex flex-wrap justify-end gap-sm">
            <button
              type="button"
              onClick={onClose}
              disabled={saving}
              className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink hover:bg-surface-container-low disabled:opacity-60"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={saving}
              className="rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary disabled:opacity-60"
            >
              {saving ? '保存中...' : mode === 'create' ? '创建配置' : '保存修改'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function TextField({
  id,
  label,
  value,
  error,
  disabled,
  type = 'text',
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  error?: string;
  disabled?: boolean;
  type?: string;
  onChange: (value: string) => void;
}) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-[12px] font-medium text-secondary">
        {label}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        disabled={disabled}
        className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-ink outline-none transition-colors focus:border-border-strong disabled:cursor-not-allowed disabled:opacity-60"
        onChange={(event) => onChange(event.target.value)}
      />
      {error ? <p className="mt-1 text-[12px] text-error">{error}</p> : null}
    </div>
  );
}

function TextAreaField({
  id,
  label,
  value,
  rows,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  rows: number;
  onChange: (value: string) => void;
}) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-[12px] font-medium text-secondary">
        {label}
      </label>
      <textarea
        id={id}
        rows={rows}
        value={value}
        className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-ink outline-none transition-colors focus:border-border-strong"
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}

function DeleteMcpDialog({
  config,
  onCancel,
  onConfirm,
}: {
  config: AdminMcpConfig;
  onCancel: () => void;
  onConfirm: () => Promise<void>;
}) {
  // 关键约束：删除确认弹窗通过 Portal 挂载到 body，避免受页面容器 transform/overflow 影响而出现宽度压缩。
  return createPortal(
    <div
      data-testid="mcp-delete-dialog-overlay"
      className="fixed inset-0 z-[1200] flex items-center justify-center bg-ink/45 px-md"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="删除MCP配置"
        className="w-full max-w-md rounded-2xl border border-border-hairline bg-surface-container-lowest p-lg shadow-2xl"
      >
        <h3 className="font-title-md text-title-md text-ink">删除MCP配置</h3>
        <p className="mt-sm text-body-sm text-secondary">
          确认删除 MCP 配置「{config.displayName}」吗？删除后用户端将无法选择该 MCP。
        </p>
        <div className="mt-lg flex justify-end gap-sm">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink hover:bg-surface-container-low"
          >
            取消
          </button>
          <button
            type="button"
            onClick={() => void onConfirm()}
            className="rounded-lg border border-error bg-error-container px-lg py-2 font-button text-button text-on-error-container hover:opacity-90"
          >
            确认删除
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

/**
 * MCP 状态点颜色映射。
 *
 * @param status 状态码。
 * @returns 对应样式。
 */
function statusDotClass(status: string): string {
  switch (status) {
    case 'healthy':
      return 'bg-status-running';
    case 'degraded':
      return 'bg-status-pending';
    default:
      return 'bg-status-failed';
  }
}

/**
 * MCP 状态文本颜色映射。
 *
 * @param status 状态码。
 * @returns 对应样式。
 */
function statusTextClass(status: string): string {
  switch (status) {
    case 'healthy':
      return 'text-status-running';
    case 'degraded':
      return 'text-status-pending';
    default:
      return 'text-status-failed';
  }
}

function PingResultDialog({
  result,
  onClose,
}: {
  result: AdminMcpToolView;
  onClose: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="工具探测结果"
        className="w-full max-w-[42rem] rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
      >
        <div className="flex items-start justify-between gap-md border-b border-border-hairline px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">工具探测结果</h3>
            <p className="mt-1 break-all font-data-mono text-[12px] text-secondary">{result.toolId}</p>
          </div>
          <button
            type="button"
            className="rounded-lg px-sm py-xs text-secondary hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭结果弹窗"
            onClick={onClose}
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        <div className="space-y-md p-lg">
          <div className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
            <p className="text-[12px] text-secondary">探测消息</p>
            <p className="mt-1 break-words text-body-sm text-ink">{result.message || '无返回消息'}</p>
          </div>
          <div className="grid grid-cols-1 gap-sm md:grid-cols-3">
            <ResultMeta label="状态" value={result.statusLabel || '-'} />
            <ResultMeta
              label="耗时"
              value={typeof result.durationMs === 'number' ? `${result.durationMs} ms` : '-'}
            />
            <ResultMeta label="时间" value={result.checkedAt || '-'} />
          </div>
          <div className="flex justify-end">
            <button
              type="button"
              className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 text-button font-button text-ink hover:bg-surface-container-low"
              onClick={onClose}
            >
              知道了
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * 探测结果元信息展示块，统一字段视觉密度。
 */
function ResultMeta({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border-hairline bg-surface-container-low px-md py-sm">
      <p className="text-[12px] text-secondary">{label}</p>
      <p className="mt-1 break-words text-body-sm text-ink">{value}</p>
    </div>
  );
}

function toMcpForm(config: AdminMcpConfig | null): McpFormState {
  if (!config) {
    return emptyMcpForm;
  }
  return {
    mcpCode: config.mcpCode ?? '',
    displayName: config.displayName ?? '',
    description: config.description ?? '',
    category: config.category ?? '',
    sourceType: config.sourceType ?? 'built-in',
    sortNo: String(config.sortNo ?? 0),
    enabled: config.enabled !== 0,
  };
}

function toMcpPayload(form: McpFormState, editingConfig: AdminMcpConfig | null): AdminMcpConfig {
  const parsedSortNo = Number(form.sortNo);
  return {
    id: editingConfig?.id,
    mcpCode: form.mcpCode.trim(),
    displayName: form.displayName.trim(),
    description: form.description.trim() || undefined,
    category: form.category.trim() || undefined,
    sourceType: form.sourceType.trim() || 'built-in',
    sortNo: Number.isFinite(parsedSortNo) ? parsedSortNo : 0,
    enabled: form.enabled ? 1 : 0,
  };
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

/**
 * 合并数据库配置与执行器探测结果，统一输出页面展示行。
 * @param configs 数据库配置列表。
 * @param tools 执行器探测列表。
 * @returns 合并后的展示行。
 */
function mergeConfigAndTools(configs: AdminMcpConfig[], tools: AdminMcpToolView[]): UnifiedMcpRow[] {
  const configByCode = new Map<string, AdminMcpConfig>();
  const toolByCode = new Map<string, AdminMcpToolView>();
  const codeSet = new Set<string>();

  for (const config of configs) {
    const normalizedCode = normalizeMcpCode(config.mcpCode);
    if (!normalizedCode) {
      continue;
    }
    configByCode.set(normalizedCode, config);
    codeSet.add(normalizedCode);
  }

  for (const tool of tools) {
    const normalizedCode = normalizeMcpCode(tool.toolId);
    if (!normalizedCode) {
      continue;
    }
    toolByCode.set(normalizedCode, tool);
    codeSet.add(normalizedCode);
  }

  return Array.from(codeSet)
    .map((code) => {
      const config = configByCode.get(code) ?? null;
      const tool = toolByCode.get(code) ?? null;
      return {
        mcpCode: config?.mcpCode ?? tool?.toolId ?? code,
        config,
        tool,
      };
    })
    .sort((left, right) => {
      const leftSort = left.config?.sortNo ?? Number.MAX_SAFE_INTEGER;
      const rightSort = right.config?.sortNo ?? Number.MAX_SAFE_INTEGER;
      if (leftSort !== rightSort) {
        return leftSort - rightSort;
      }
      return left.mcpCode.localeCompare(right.mcpCode);
    });
}

/**
 * 规范化编码用于大小写无关匹配。
 * @param code 原始编码。
 * @returns 规范化编码。
 */
function normalizeMcpCode(code?: string): string {
  return (code ?? '').trim().toLowerCase();
}
