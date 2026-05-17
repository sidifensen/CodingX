import React from 'react';

import { AdminChatApi, AdminChatTool } from '../api/adminChatApi';

type ToolDialogMode = 'create' | 'edit';

interface ToolFormState {
  toolCode: string;
  displayName: string;
  description: string;
  category: string;
  sourceType: string;
  sortNo: string;
  enabled: boolean;
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

/**
 * 管理端工具管理页：独立维护 chat_tool 表，不复用 MCP 管理页。
 */
export function ToolsPage() {
  const [tools, setTools] = React.useState<AdminChatTool[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [dialogMode, setDialogMode] = React.useState<ToolDialogMode>('create');
  const [editingTool, setEditingTool] = React.useState<AdminChatTool | null>(null);
  const [deleteTarget, setDeleteTarget] = React.useState<AdminChatTool | null>(null);

  /**
   * 加载工具配置列表。
   */
  const loadTools = React.useCallback(async () => {
    setLoading(true);
    setErrorMessage('');
    try {
      const result = await AdminChatApi.listTools();
      setTools(result ?? []);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '加载工具配置失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void loadTools();
  }, [loadTools]);

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

  return (
    <div className="w-full space-y-lg p-lg">
      <div className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">工具管理</h2>
          <p className="mt-1 text-secondary">独立维护 chat_tool 工具目录，预置 Codex CLI 工具清单。</p>
        </div>
        <div className="flex gap-sm">
          <button
            type="button"
            aria-label="刷新工具列表"
            className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink transition-colors hover:bg-surface-container-low"
            onClick={() => void loadTools()}
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

      {errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      <section className="overflow-hidden rounded-xl border border-border-hairline bg-surface-container-lowest">
        <table className="w-full border-collapse text-left">
          <thead>
            <tr className="border-b border-border-hairline bg-surface-container-low">
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">编码</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">名称</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">分类</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">来源</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">状态</th>
              <th className="px-lg py-md font-label-caps text-label-caps text-secondary">排序</th>
              <th className="px-lg py-md text-right font-label-caps text-label-caps text-secondary">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-hairline">
            {loading ? (
              <tr>
                <td className="px-lg py-xl text-center text-secondary" colSpan={7}>
                  加载中...
                </td>
              </tr>
            ) : tools.length === 0 ? (
              <tr>
                <td className="px-lg py-xl text-center text-secondary" colSpan={7}>
                  暂无工具配置
                </td>
              </tr>
            ) : (
              tools.map((tool) => (
                <tr key={String(tool.id ?? tool.toolCode)} className="transition-colors hover:bg-surface-container-low">
                  <td className="px-lg py-md font-data-mono text-[12px] text-tertiary-container">/{tool.toolCode}</td>
                  <td className="px-lg py-md text-ink">{tool.displayName}</td>
                  <td className="px-lg py-md text-body-sm text-secondary">{tool.category || '-'}</td>
                  <td className="px-lg py-md text-body-sm text-secondary">{tool.sourceType || '-'}</td>
                  <td className="px-lg py-md">
                    <span
                      className={[
                        'rounded-full border px-2 py-0.5 text-[11px] font-medium',
                        tool.enabled === 0
                          ? 'border-border-hairline bg-surface-container-low text-secondary'
                          : 'border-border-strong bg-surface-container text-ink',
                      ].join(' ')}
                    >
                      {tool.enabled === 0 ? '停用' : '启用'}
                    </span>
                  </td>
                  <td className="px-lg py-md text-body-sm text-secondary">{tool.sortNo ?? 0}</td>
                  <td className="px-lg py-md text-right">
                    <div className="inline-flex gap-sm">
                      <button
                        type="button"
                        aria-label={`编辑工具 ${tool.toolCode}`}
                        className="rounded-lg border border-border-strong bg-surface-container-lowest px-sm py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
                        onClick={() => openEditDialog(tool)}
                      >
                        编辑
                      </button>
                      <button
                        type="button"
                        aria-label={`删除工具 ${tool.toolCode}`}
                        className="rounded-lg border border-error bg-error-container px-sm py-1.5 text-[12px] text-on-error-container transition-opacity hover:opacity-90"
                        onClick={() => setDeleteTarget(tool)}
                      >
                        删除
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </section>

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
            await loadTools();
          }}
        />
      ) : null}

      {deleteTarget ? (
        <DeleteToolDialog
          tool={deleteTarget}
          onCancel={() => setDeleteTarget(null)}
          onConfirm={async () => {
            if (deleteTarget.id == null) {
              setErrorMessage('工具配置缺少主键，无法删除');
              setDeleteTarget(null);
              return;
            }
            try {
              await AdminChatApi.deleteTool(deleteTarget.id);
              setDeleteTarget(null);
              await loadTools();
            } catch (error) {
              setErrorMessage(extractErrorMessage(error, '删除工具配置失败'));
            }
          }}
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
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="删除工具配置"
        className="w-full max-w-md rounded-2xl border border-border-hairline bg-surface-container-lowest p-lg shadow-2xl"
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