import React from 'react';

import clsx from 'clsx';

import {
  AdminChatApi,
  AdminChatTool,
  AdminChatToolHealthView,
  AdminChatToolInvokeView,
} from '../api/adminChatApi';
import { DataTableCard } from '../components/DataTableCard';
import { ToolIntentTreePanel } from '../components/ToolIntentTreePanel';

type ToolDialogMode = 'create' | 'edit';
type ToolViewMode = 'list' | 'intentTree';

interface ToolFormState {
  toolCode: string;
  displayName: string;
  description: string;
  category: string;
  sourceType: string;
  sortNo: string;
  enabled: boolean;
}

interface UnifiedToolRow {
  toolCode: string;
  config: AdminChatTool | null;
  health: AdminChatToolHealthView | null;
}

const emptyToolForm: ToolFormState = {
  toolCode: '',
  displayName: '',
  description: '',
  category: '',
  sourceType: 'codex-cli',
  sortNo: '0',
  enabled: true,
};

const TOOL_TABLE_PAGE_SIZE = 10;

/**
 * 管理端工具管理页：独立维护 chat_tool 表，不复用 MCP 管理页。
 */
export function ToolsPage() {
  const [tools, setTools] = React.useState<AdminChatTool[]>([]);
  const [toolHealthViews, setToolHealthViews] = React.useState<AdminChatToolHealthView[]>([]);
  const [configLoading, setConfigLoading] = React.useState(true);
  const [healthLoading, setHealthLoading] = React.useState(true);
  const [configErrorMessage, setConfigErrorMessage] = React.useState('');
  const [healthErrorMessage, setHealthErrorMessage] = React.useState('');
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [dialogMode, setDialogMode] = React.useState<ToolDialogMode>('create');
  const [editingTool, setEditingTool] = React.useState<AdminChatTool | null>(null);
  const [deleteTarget, setDeleteTarget] = React.useState<AdminChatTool | null>(null);
  const [pingingToolCode, setPingingToolCode] = React.useState<string | null>(null);
  const [pingResult, setPingResult] = React.useState<AdminChatToolHealthView | null>(null);
  const [invokeDialogToolCode, setInvokeDialogToolCode] = React.useState<string | null>(null);
  const [invoking, setInvoking] = React.useState(false);
  const [invokeQuestion, setInvokeQuestion] = React.useState('');
  const [invokeResult, setInvokeResult] = React.useState<AdminChatToolInvokeView | null>(null);
  const [invokeErrorMessage, setInvokeErrorMessage] = React.useState('');
  const [viewMode, setViewMode] = React.useState<ToolViewMode>('list');
  const [pageNo, setPageNo] = React.useState(1);
  const [selectedToolCode, setSelectedToolCode] = React.useState<string | null>(null);
  const mergedRows = React.useMemo(() => mergeToolsAndHealthViews(tools, toolHealthViews), [tools, toolHealthViews]);
  const isTableLoading = configLoading || healthLoading;
  const totalRows = mergedRows.length;
  const pageCount = Math.max(1, Math.ceil(totalRows / TOOL_TABLE_PAGE_SIZE));
  const safePageNo = Math.min(pageNo, pageCount);
  const pagedRows = React.useMemo(() => {
    const start = (safePageNo - 1) * TOOL_TABLE_PAGE_SIZE;
    return mergedRows.slice(start, start + TOOL_TABLE_PAGE_SIZE);
  }, [mergedRows, safePageNo]);
  // 对齐 Trace 管理：加载阶段若暂无数据则展示骨架行，不直接留空表格。
  const showEmptyState = !isTableLoading && pagedRows.length === 0;
  const showSkeletonRows = isTableLoading && pagedRows.length === 0;

  React.useEffect(() => {
    if (pageNo > pageCount) {
      setPageNo(pageCount);
    }
  }, [pageNo, pageCount]);

  React.useEffect(() => {
    setSelectedToolCode((previous) => {
      if (previous && mergedRows.some((row) => normalizeToolCode(row.toolCode) === normalizeToolCode(previous))) {
        return previous;
      }
      return mergedRows[0]?.toolCode ?? null;
    });
  }, [mergedRows]);

  /**
   * 加载工具配置列表。
   */
  const loadTools = React.useCallback(async () => {
    setConfigLoading(true);
    setConfigErrorMessage('');
    try {
      const result = await AdminChatApi.listTools();
      setTools(result ?? []);
    } catch (error) {
      setConfigErrorMessage(extractErrorMessage(error, '加载工具配置失败'));
    } finally {
      setConfigLoading(false);
    }
  }, []);

  /**
   * 加载工具执行器健康视图。
   */
  const loadToolHealthViews = React.useCallback(async () => {
    setHealthLoading(true);
    setHealthErrorMessage('');
    try {
      const result = await AdminChatApi.listToolHealthViews();
      setToolHealthViews(result ?? []);
    } catch (error) {
      setHealthErrorMessage(extractErrorMessage(error, '加载工具探测状态失败'));
    } finally {
      setHealthLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void Promise.all([loadTools(), loadToolHealthViews()]);
  }, [loadTools, loadToolHealthViews]);

  const openCreateDialog = () => {
    setDialogMode('create');
    setEditingTool(null);
    setDialogOpen(true);
  };

  const openEditDialog = (tool: AdminChatTool) => {
    setDialogMode('edit');
    setEditingTool(tool);
    setDialogOpen(true);
  };

  const openInvokeDialog = (toolCode: string, sampleQuestion?: string) => {
    setInvokeDialogToolCode(toolCode);
    setInvokeQuestion(sampleQuestion ?? '');
    setInvokeResult(null);
    setInvokeErrorMessage('');
  };

  const handlePing = async (toolCode: string) => {
    setPingingToolCode(toolCode);
    try {
      const result = await AdminChatApi.pingTool(toolCode);
      setPingResult(result);
      await loadToolHealthViews();
    } catch (error) {
      setPingResult({
        toolCode,
        displayName: toolCode,
        status: 'failed',
        statusLabel: '异常',
        ok: false,
        message: extractErrorMessage(error, '探测失败'),
      });
    } finally {
      setPingingToolCode(null);
    }
  };

  const handleInvoke = async () => {
    if (!invokeDialogToolCode) {
      return;
    }
    setInvoking(true);
    setInvokeErrorMessage('');
    try {
      const result = await AdminChatApi.invokeTool(invokeDialogToolCode, invokeQuestion.trim() || undefined);
      setInvokeResult(result);
      await loadToolHealthViews();
    } catch (error) {
      setInvokeErrorMessage(extractErrorMessage(error, '调用工具失败'));
    } finally {
      setInvoking(false);
    }
  };

  return (
    <div className="w-full space-y-lg p-lg">
      <div className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">工具管理</h2>
          <p className="mt-1 text-secondary">独立维护 chat_tool 工具目录，预置 Codex CLI 工具清单。</p>
        </div>
        <div className="flex flex-wrap items-center gap-sm">
          <div className="inline-flex items-center rounded-lg border border-border-hairline bg-surface-container-lowest p-1">
            <button
              type="button"
              aria-label="列表视图"
              className={[
                'rounded-md px-sm py-1.5 text-[12px] transition-colors',
                viewMode === 'list' ? 'bg-primary text-on-primary' : 'text-secondary hover:bg-surface-container-low hover:text-ink',
              ].join(' ')}
              onClick={() => setViewMode('list')}
            >
              列表视图
            </button>
            <button
              type="button"
              aria-label="意图树视图"
              className={[
                'rounded-md px-sm py-1.5 text-[12px] transition-colors',
                viewMode === 'intentTree'
                  ? 'bg-primary text-on-primary'
                  : 'text-secondary hover:bg-surface-container-low hover:text-ink',
              ].join(' ')}
              onClick={() => setViewMode('intentTree')}
            >
              意图树视图
            </button>
          </div>
          <button
            type="button"
            aria-label="刷新工具列表"
            className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink transition-colors hover:bg-surface-container-low"
            onClick={() => void Promise.all([loadTools(), loadToolHealthViews()])}
          >
            刷新列表
          </button>
          <button
            type="button"
            aria-label="新增工具配置"
            className="rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary transition-opacity hover:opacity-90"
            onClick={openCreateDialog}
          >
            新增工具配置
          </button>
        </div>
      </div>

      {configErrorMessage || healthErrorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {configErrorMessage || healthErrorMessage}
        </div>
      ) : null}

      {viewMode === 'list' ? (
        <DataTableCard
          scrollTestId="tools-table-scroll"
          loading={isTableLoading}
          loadingText="加载中..."
          summaryText={`第 ${safePageNo} / ${pageCount} 页，共 ${totalRows.toLocaleString('zh-CN')} 条`}
          paginationCurrent={safePageNo}
          paginationPages={pageCount}
          onPaginationChange={setPageNo}
          tableContent={(
          <table className="w-full min-w-[1420px] border-collapse text-left">
            <thead>
              <tr className="border-b border-border-hairline bg-surface-container-low">
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">编码</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">名称</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">分类</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">来源</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">状态</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">执行器</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">样例问题</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">最近探测</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md font-label-caps text-label-caps text-secondary">排序</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-lg py-md text-right font-label-caps text-label-caps text-secondary">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {showEmptyState ? (
                <tr>
                  <td className="px-lg py-xl text-center text-secondary" colSpan={10}>
                    暂无工具配置
                  </td>
                </tr>
              ) : (
                pagedRows.map((row) => {
                  const tool = row.config;
                  const health = row.health;
                  const toolCode = row.toolCode;
                  return (
                    <tr key={String(tool?.id ?? toolCode)} className="transition-colors hover:bg-surface-container-low">
                      <td className="px-lg py-md font-data-mono text-[12px] text-tertiary-container">/{toolCode}</td>
                      <td className="px-lg py-md text-ink">{tool?.displayName || health?.displayName || toolCode}</td>
                      <td className="px-lg py-md text-body-sm text-secondary">{tool?.category || health?.category || '-'}</td>
                      <td className="px-lg py-md text-body-sm text-secondary">{tool?.sourceType || health?.source || '-'}</td>
                      <td className="px-lg py-md">
                        <span
                          className={[
                            'rounded-full border px-2 py-0.5 text-[11px] font-medium',
                            tool?.enabled === 0
                              ? 'border-border-hairline bg-surface-container-low text-secondary'
                              : 'border-border-strong bg-surface-container text-ink',
                          ].join(' ')}
                        >
                          {tool?.enabled === 0 ? '停用' : '启用'}
                        </span>
                      </td>
                      <td className="px-lg py-md">
                        <div className="flex items-center gap-xs">
                          <span className={clsx('h-2 w-2 rounded-full', statusDotClass(health?.status))} />
                          <span className={clsx('text-body-sm font-medium', statusTextClass(health?.status))}>
                            {health?.statusLabel || '未接入'}
                          </span>
                        </div>
                      </td>
                      <td className="max-w-[16rem] truncate px-lg py-md text-body-sm text-secondary">
                        {health?.sampleQuestion || '-'}
                      </td>
                      <td className="px-lg py-md text-[12px] text-secondary">{health?.checkedAt || '-'}</td>
                      <td className="px-lg py-md text-body-sm text-secondary">{tool?.sortNo ?? 0}</td>
                      <td className="px-lg py-md text-right">
                        <div className="inline-flex gap-sm">
                          <button
                            type="button"
                            aria-label={`测试工具 ${toolCode}`}
                            className="rounded-lg border border-border-hairline bg-surface-container-lowest px-sm py-1.5 text-[12px] text-secondary transition-colors hover:bg-surface-container-low hover:text-ink disabled:opacity-60"
                            onClick={() => void handlePing(toolCode)}
                            disabled={pingingToolCode === toolCode}
                          >
                            {pingingToolCode === toolCode ? '探测中...' : '探测'}
                          </button>
                          <button
                            type="button"
                            aria-label={`调用工具 ${toolCode}`}
                            className="rounded-lg border border-border-strong bg-surface-container-lowest px-sm py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
                            onClick={() => openInvokeDialog(toolCode, health?.sampleQuestion)}
                          >
                            调用
                          </button>
                          <button
                            type="button"
                            aria-label={`编辑工具 ${toolCode}`}
                            className="rounded-lg border border-border-strong bg-surface-container-lowest px-sm py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
                            onClick={() => tool ? openEditDialog(tool) : null}
                            disabled={!tool}
                          >
                            编辑
                          </button>
                          <button
                            type="button"
                            aria-label={`删除工具 ${toolCode}`}
                            className="rounded-lg border border-error bg-error-container px-sm py-1.5 text-[12px] text-on-error-container transition-opacity hover:opacity-90"
                            onClick={() => (tool ? setDeleteTarget(tool) : null)}
                            disabled={!tool}
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
                  <tr key={`tools-loading-row-${index}`} data-testid="tools-loading-skeleton-row">
                    <td colSpan={10} className="px-lg py-md">
                      <div className="h-6 w-full animate-pulse rounded bg-surface-container-low" />
                    </td>
                  </tr>
                ))
                : null}
            </tbody>
          </table>
          )}
        />
      ) : (
        <ToolIntentTreePanel
          rows={mergedRows}
          selectedToolCode={selectedToolCode}
          pingingToolCode={pingingToolCode}
          onSelectTool={setSelectedToolCode}
          onPingTool={(toolCode) => void handlePing(toolCode)}
          onInvokeTool={openInvokeDialog}
          onEditTool={openEditDialog}
          onDeleteTool={setDeleteTarget}
        />
      )}

      {dialogOpen ? (
        <ToolEditDialog
          mode={dialogMode}
          tool={editingTool}
          onClose={() => setDialogOpen(false)}
          onSubmit={async (payload) => {
            if (dialogMode === 'edit' && editingTool?.id != null) {
              await AdminChatApi.updateTool(editingTool.id, payload);
            } else {
              await AdminChatApi.createTool(payload);
            }
            setDialogOpen(false);
            await Promise.all([loadTools(), loadToolHealthViews()]);
          }}
        />
      ) : null}

      {deleteTarget ? (
        <DeleteToolDialog
          tool={deleteTarget}
          onCancel={() => setDeleteTarget(null)}
          onConfirm={async () => {
            if (deleteTarget.id == null) {
              setConfigErrorMessage('工具配置缺少主键，无法删除');
              setDeleteTarget(null);
              return;
            }
            try {
              await AdminChatApi.deleteTool(deleteTarget.id);
              setDeleteTarget(null);
              await Promise.all([loadTools(), loadToolHealthViews()]);
            } catch (error) {
              setConfigErrorMessage(extractErrorMessage(error, '删除工具配置失败'));
            }
          }}
        />
      ) : null}

      {pingResult ? <PingResultDialog result={pingResult} onClose={() => setPingResult(null)} /> : null}

      {invokeDialogToolCode ? (
        <InvokeToolDialog
          toolCode={invokeDialogToolCode}
          question={invokeQuestion}
          invoking={invoking}
          result={invokeResult}
          errorMessage={invokeErrorMessage}
          onChangeQuestion={setInvokeQuestion}
          onClose={() => {
            setInvokeDialogToolCode(null);
            setInvokeQuestion('');
            setInvokeResult(null);
            setInvokeErrorMessage('');
          }}
          onInvoke={() => void handleInvoke()}
        />
      ) : null}
    </div>
  );
}

interface ToolEditDialogProps {
  mode: ToolDialogMode;
  tool: AdminChatTool | null;
  onClose: () => void;
  onSubmit: (payload: AdminChatTool) => Promise<void>;
}

/**
 * 工具配置编辑弹窗。
 */
function ToolEditDialog({ mode, tool, onClose, onSubmit }: ToolEditDialogProps) {
  const [form, setForm] = React.useState<ToolFormState>(() => toToolForm(tool));
  const [saving, setSaving] = React.useState(false);
  const [formError, setFormError] = React.useState('');
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});

  const updateField = (field: keyof ToolFormState, value: string | boolean) => {
    setFieldErrors((previous) => ({ ...previous, [field]: '' }));
    setForm((previous) => ({ ...previous, [field]: value }));
  };

  const validate = () => {
    const nextErrors: Record<string, string> = {};
    if (!form.toolCode.trim()) {
      nextErrors.toolCode = '请输入工具编码';
    }
    if (!form.displayName.trim()) {
      nextErrors.displayName = '请输入工具名称';
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
      await onSubmit(toToolPayload(form, tool));
    } catch (error) {
      setFormError(extractErrorMessage(error, mode === 'create' ? '新增工具配置失败' : '保存工具配置失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={mode === 'create' ? '新增工具配置' : '编辑工具配置'}
        className="max-h-[88vh] w-full max-w-2xl overflow-y-auto rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
      >
        <div className="sticky top-0 z-10 flex items-start justify-between gap-md border-b border-border-hairline bg-surface-container-lowest px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">{mode === 'create' ? '新增工具配置' : '编辑工具配置'}</h3>
            <p className="mt-1 text-body-sm text-secondary">维护工具编码、展示信息、启用状态和排序。</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-sm py-xs text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭工具弹窗"
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
                id="tool-code"
                label="工具编码"
                value={form.toolCode}
                error={fieldErrors.toolCode}
                disabled={mode === 'edit'}
                onChange={(value) => updateField('toolCode', value)}
              />
              <TextField
                id="tool-display-name"
                label="工具名称"
                value={form.displayName}
                error={fieldErrors.displayName}
                onChange={(value) => updateField('displayName', value)}
              />
              <TextField
                id="tool-category"
                label="分类"
                value={form.category}
                onChange={(value) => updateField('category', value)}
              />
              <TextField
                id="tool-source-type"
                label="来源类型"
                value={form.sourceType}
                onChange={(value) => updateField('sourceType', value)}
              />
              <TextField
                id="tool-sort"
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
                启用工具
              </label>
            </div>
          </fieldset>

          <fieldset className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
            <legend className="px-xs font-title-sm text-ink">描述</legend>
            <TextAreaField
              id="tool-description"
              label="工具说明"
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

function DeleteToolDialog({
  tool,
  onCancel,
  onConfirm,
}: {
  tool: AdminChatTool;
  onCancel: () => void;
  onConfirm: () => Promise<void>;
}) {
  // 关键约束：显式使用 rem 宽度，避免 max-w-md 在当前主题下被 spacing token 覆盖成 16px。
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="删除工具配置"
        className="w-full max-w-[28rem] rounded-2xl border border-border-hairline bg-surface-container-lowest p-lg shadow-2xl"
      >
        <h3 className="font-title-md text-title-md text-ink">删除工具配置</h3>
        <p className="mt-sm text-body-sm text-secondary">
          确认删除工具配置「{tool.displayName}」吗？删除后该工具将不在管理端目录展示。
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

function toToolForm(tool: AdminChatTool | null): ToolFormState {
  if (!tool) {
    return emptyToolForm;
  }
  return {
    toolCode: tool.toolCode ?? '',
    displayName: tool.displayName ?? '',
    description: tool.description ?? '',
    category: tool.category ?? '',
    sourceType: tool.sourceType ?? 'codex-cli',
    sortNo: String(tool.sortNo ?? 0),
    enabled: tool.enabled !== 0,
  };
}

function toToolPayload(form: ToolFormState, editingTool: AdminChatTool | null): AdminChatTool {
  const parsedSortNo = Number(form.sortNo);
  return {
    id: editingTool?.id,
    toolCode: form.toolCode.trim(),
    displayName: form.displayName.trim(),
    description: form.description.trim() || undefined,
    category: form.category.trim() || undefined,
    sourceType: form.sourceType.trim() || 'codex-cli',
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

function statusDotClass(status?: string): string {
  switch (status) {
    case 'healthy':
      return 'bg-status-running';
    case 'degraded':
      return 'bg-status-pending';
    default:
      return 'bg-status-failed';
  }
}

function statusTextClass(status?: string): string {
  switch (status) {
    case 'healthy':
      return 'text-status-running';
    case 'degraded':
      return 'text-status-pending';
    default:
      return 'text-status-failed';
  }
}

function mergeToolsAndHealthViews(
  tools: AdminChatTool[],
  toolHealthViews: AdminChatToolHealthView[],
): UnifiedToolRow[] {
  const toolMap = new Map<string, AdminChatTool>();
  const healthMap = new Map<string, AdminChatToolHealthView>();
  const codeSet = new Set<string>();

  tools.forEach((tool) => {
    const code = normalizeToolCode(tool.toolCode);
    if (!code) {
      return;
    }
    toolMap.set(code, tool);
    codeSet.add(code);
  });

  toolHealthViews.forEach((health) => {
    const code = normalizeToolCode(health.toolCode);
    if (!code) {
      return;
    }
    healthMap.set(code, health);
    codeSet.add(code);
  });

  return Array.from(codeSet)
    .map((code) => ({
      toolCode: toolMap.get(code)?.toolCode ?? healthMap.get(code)?.toolCode ?? code,
      config: toolMap.get(code) ?? null,
      health: healthMap.get(code) ?? null,
    }))
    .sort((left, right) => {
      const leftSort = left.config?.sortNo ?? Number.MAX_SAFE_INTEGER;
      const rightSort = right.config?.sortNo ?? Number.MAX_SAFE_INTEGER;
      if (leftSort !== rightSort) {
        return leftSort - rightSort;
      }
      return left.toolCode.localeCompare(right.toolCode);
    });
}

function normalizeToolCode(code?: string): string {
  return (code ?? '').trim().toLowerCase();
}

function PingResultDialog({ result, onClose }: { result: AdminChatToolHealthView; onClose: () => void }) {
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
            <p className="mt-1 break-all font-data-mono text-[12px] text-secondary">{result.toolCode}</p>
          </div>
          <button
            type="button"
            className="rounded-lg px-sm py-xs text-secondary hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭探测结果弹窗"
            onClick={onClose}
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        <div className="space-y-md p-lg">
          <ResultMeta label="状态" value={result.statusLabel || '-'} />
          <ResultMeta label="消息" value={result.message || '无返回消息'} />
          <div className="grid grid-cols-1 gap-sm md:grid-cols-2">
            <ResultMeta label="耗时" value={typeof result.durationMs === 'number' ? `${result.durationMs} ms` : '-'} />
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

function InvokeToolDialog({
  toolCode,
  question,
  invoking,
  result,
  errorMessage,
  onChangeQuestion,
  onClose,
  onInvoke,
}: {
  toolCode: string;
  question: string;
  invoking: boolean;
  result: AdminChatToolInvokeView | null;
  errorMessage: string;
  onChangeQuestion: (value: string) => void;
  onClose: () => void;
  onInvoke: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="调用工具"
        className="w-full max-w-3xl rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
      >
        <div className="flex items-start justify-between gap-md border-b border-border-hairline px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">调用工具</h3>
            <p className="mt-1 break-all font-data-mono text-[12px] text-secondary">{toolCode}</p>
          </div>
          <button
            type="button"
            className="rounded-lg px-sm py-xs text-secondary hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭调用弹窗"
            onClick={onClose}
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        <div className="space-y-md p-lg">
          <div>
            <label htmlFor="tool-invoke-question" className="mb-1 block text-[12px] font-medium text-secondary">
              调用参数（自然语言或 JSON）
            </label>
            <textarea
              id="tool-invoke-question"
              rows={5}
              value={question}
              onChange={(event) => onChangeQuestion(event.target.value)}
              className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-ink outline-none transition-colors focus:border-border-strong"
            />
          </div>

          {errorMessage ? (
            <div className="rounded-xl border border-error bg-error-container px-md py-sm text-sm text-on-error-container">
              {errorMessage}
            </div>
          ) : null}

          {result ? (
            <div className="space-y-sm rounded-xl border border-border-hairline bg-surface-container-low p-md">
              <ResultMeta label="状态" value={result.statusLabel || '-'} />
              <ResultMeta label="消息" value={result.message || '-'} />
              <ResultMeta label="输出" value={result.content || '-'} />
              <ResultMeta label="元数据" value={JSON.stringify(result.metadata ?? {}, null, 2)} />
            </div>
          ) : null}

          <div className="flex justify-end gap-sm">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink hover:bg-surface-container-low"
            >
              关闭
            </button>
            <button
              type="button"
              onClick={onInvoke}
              disabled={invoking}
              className="rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary disabled:opacity-60"
            >
              {invoking ? '调用中...' : '开始调用'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function ResultMeta({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border-hairline bg-surface-container-low px-md py-sm">
      <p className="text-[12px] text-secondary">{label}</p>
      <p className="mt-1 whitespace-pre-wrap break-words text-body-sm text-ink">{value}</p>
    </div>
  );
}
