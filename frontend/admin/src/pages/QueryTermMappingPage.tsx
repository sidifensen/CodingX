import React from 'react';
import clsx from 'clsx';

import {
  AdminChatApi,
  AdminPageResult,
  AdminQueryTermMapping,
  AdminQueryTermMappingPayload,
} from '../api/adminChatApi';

const PAGE_SIZE = 10;

const MATCH_TYPE_OPTIONS = [
  { value: 1, label: '精确匹配' },
  { value: 2, label: '前缀匹配' },
  { value: 3, label: '正则匹配' },
  { value: 4, label: '整词匹配' },
];

type MappingDialogMode = 'create' | 'edit';

interface MappingFormState {
  sourceTerm: string;
  targetTerm: string;
  matchType: number;
  priority: number;
  enabled: boolean;
  remark: string;
}

const emptyForm: MappingFormState = {
  sourceTerm: '',
  targetTerm: '',
  matchType: 1,
  priority: 0,
  enabled: true,
  remark: '',
};

/**
 * 关键词映射管理页：对齐 ragent 的操作结构，沿用 CodingX 现有设计令牌与色彩体系。
 */
export function QueryTermMappingPage() {
  const [pageData, setPageData] = React.useState<AdminPageResult<AdminQueryTermMapping> | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [pageNo, setPageNo] = React.useState(1);
  const [searchKeyword, setSearchKeyword] = React.useState('');
  const [keyword, setKeyword] = React.useState('');

  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [dialogMode, setDialogMode] = React.useState<MappingDialogMode>('create');
  const [editingItem, setEditingItem] = React.useState<AdminQueryTermMapping | null>(null);

  const [form, setForm] = React.useState<MappingFormState>(emptyForm);
  const [formError, setFormError] = React.useState('');
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [saving, setSaving] = React.useState(false);

  const [deleteTarget, setDeleteTarget] = React.useState<AdminQueryTermMapping | null>(null);
  const [deleting, setDeleting] = React.useState(false);

  /**
   * 管理端列表查询统一入口：支持关键字筛选与分页回退。
   */
  const loadData = React.useCallback(async (current = pageNo, keywordValue = keyword) => {
    setLoading(true);
    setErrorMessage('');
    try {
      const data = await AdminChatApi.listMappingsPage(current, PAGE_SIZE, keywordValue || undefined);
      setPageData(data);
      if ((data.records?.length ?? 0) === 0 && current > 1) {
        setPageNo(1);
      }
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '加载映射规则失败'));
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo]);

  React.useEffect(() => {
    void loadData(pageNo, keyword);
  }, [loadData, pageNo, keyword]);

  React.useEffect(() => {
    if (!dialogOpen) {
      setForm(emptyForm);
      setFieldErrors({});
      setFormError('');
      return;
    }
    if (dialogMode === 'edit' && editingItem) {
      setForm({
        sourceTerm: editingItem.sourceTerm || '',
        targetTerm: editingItem.targetTerm || '',
        matchType: editingItem.matchType ?? 1,
        priority: editingItem.priority ?? 0,
        enabled: editingItem.enabled !== false,
        remark: editingItem.remark || '',
      });
      return;
    }
    setForm(emptyForm);
  }, [dialogOpen, dialogMode, editingItem]);

  const records = pageData?.records ?? [];

  /**
   * 查询动作：重置到第一页，避免条件变化后页码越界。
   */
  const handleSearch = () => {
    setPageNo(1);
    setKeyword(searchKeyword.trim());
  };

  /**
   * 刷新动作：保留当前筛选条件，但强制从第一页重新拉取。
   */
  const handleRefresh = () => {
    void loadData(1, keyword);
    setPageNo(1);
  };

  const openCreateDialog = () => {
    setDialogMode('create');
    setEditingItem(null);
    setDialogOpen(true);
  };

  const openEditDialog = (item: AdminQueryTermMapping) => {
    setDialogMode('edit');
    setEditingItem(item);
    setDialogOpen(true);
  };

  const updateField = (field: keyof MappingFormState, value: string | number | boolean) => {
    setFieldErrors((previous) => ({ ...previous, [field]: '' }));
    setForm((previous) => ({ ...previous, [field]: value }));
  };

  const validate = () => {
    const nextErrors: Record<string, string> = {};
    if (!form.sourceTerm.trim()) {
      nextErrors.sourceTerm = '请输入原始词';
    }
    if (!form.targetTerm.trim()) {
      nextErrors.targetTerm = '请输入目标词';
    }
    if (!Number.isFinite(form.priority)) {
      nextErrors.priority = '优先级必须为数字';
    }
    setFieldErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const buildPayload = (): AdminQueryTermMappingPayload => ({
    sourceTerm: form.sourceTerm.trim(),
    targetTerm: form.targetTerm.trim(),
    matchType: form.matchType,
    priority: Number(form.priority) || 0,
    enabled: form.enabled,
    remark: form.remark.trim() || null,
  });

  const handleSubmit = async () => {
    setFormError('');
    if (!validate()) {
      return;
    }
    setSaving(true);
    try {
      const payload = buildPayload();
      if (dialogMode === 'create') {
        await AdminChatApi.createMapping(payload);
        setPageNo(1);
        await loadData(1, keyword);
      } else if (editingItem?.id != null) {
        await AdminChatApi.updateMapping(editingItem.id, payload);
        await loadData(pageNo, keyword);
      }
      setDialogOpen(false);
    } catch (error) {
      setFormError(extractErrorMessage(error, '保存映射规则失败'));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (deleteTarget?.id == null) {
      setDeleteTarget(null);
      return;
    }
    setDeleting(true);
    setErrorMessage('');
    try {
      await AdminChatApi.deleteMapping(deleteTarget.id);
      setDeleteTarget(null);
      setPageNo(1);
      await loadData(1, keyword);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '删除映射规则失败'));
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="w-full space-y-lg p-lg">
      <div className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">关键词映射管理</h2>
          <p className="mt-1 text-secondary">配置查询归一化映射规则，支持分页检索与完整 CRUD 操作</p>
        </div>
        <div className="flex flex-wrap gap-sm">
          <div className="flex w-full max-w-[320px] items-center gap-xs rounded-lg border border-border-hairline bg-surface-container-lowest px-sm py-2 lg:w-[320px]">
            <span className="material-symbols-outlined text-[18px] text-secondary">search</span>
            <input
              data-testid="mapping-search-input"
              value={searchKeyword}
              onChange={(event) => setSearchKeyword(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  handleSearch();
                }
              }}
              placeholder="搜索原始词/目标词"
              className="w-full bg-transparent text-ink outline-none placeholder:text-secondary"
            />
          </div>
          <button
            type="button"
            data-testid="mapping-search-btn"
            className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink transition-colors hover:bg-surface-container-low"
            onClick={handleSearch}
          >
            搜索
          </button>
          <button
            type="button"
            data-testid="mapping-refresh-btn"
            className="flex items-center gap-xs rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink transition-colors hover:bg-surface-container-low"
            onClick={handleRefresh}
          >
            <span className="material-symbols-outlined text-[18px]">refresh</span>
            刷新
          </button>
          <button
            type="button"
            data-testid="mapping-create-btn"
            className="flex items-center gap-xs rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary transition-opacity hover:opacity-90"
            onClick={openCreateDialog}
          >
            <span className="material-symbols-outlined text-[18px]">add</span>
            新增映射
          </button>
        </div>
      </div>

      {errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-lg py-md text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      <section className="space-y-md rounded-2xl border border-border-hairline bg-surface-container-lowest p-lg shadow-sm">
        <div className="overflow-hidden rounded-xl border border-border-hairline">
          <table className="min-w-[1080px] w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-border-hairline bg-surface-container-low">
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">原始词</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">目标词</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">匹配类型</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">优先级</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">状态</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">备注</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">创建时间</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary">更新时间</th>
                <th className="px-lg py-md text-right font-label-caps text-label-caps text-secondary">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {loading ? (
                <tr>
                  <td className="px-lg py-xl text-center text-secondary" colSpan={9}>
                    加载中...
                  </td>
                </tr>
              ) : records.length === 0 ? (
                <tr>
                  <td className="px-lg py-xl text-center text-secondary" colSpan={9}>
                    暂无映射规则
                  </td>
                </tr>
              ) : (
                records.map((item) => (
                  <tr key={String(item.id)} className="transition-colors hover:bg-surface-container-low">
                    <td className="max-w-[180px] truncate px-lg py-md text-ink" title={item.sourceTerm}>
                      {item.sourceTerm}
                    </td>
                    <td className="max-w-[180px] truncate px-lg py-md text-ink" title={item.targetTerm}>
                      {item.targetTerm}
                    </td>
                    <td className="px-lg py-md">
                      <MatchTypeBadge matchType={item.matchType} />
                    </td>
                    <td className="px-lg py-md text-secondary">{item.priority ?? 0}</td>
                    <td className="px-lg py-md">
                      <StatusBadge enabled={item.enabled !== false} />
                    </td>
                    <td className="max-w-[220px] truncate px-lg py-md text-secondary" title={item.remark || ''}>
                      {item.remark || '-'}
                    </td>
                    <td className="px-lg py-md text-[12px] text-secondary">{formatDate(item.createTime)}</td>
                    <td className="px-lg py-md text-[12px] text-secondary">{formatDate(item.updateTime)}</td>
                    <td className="px-lg py-md text-right">
                      <div className="inline-flex gap-sm">
                        <button
                          type="button"
                          data-testid={`mapping-edit-${String(item.id)}`}
                          className="rounded-lg border border-border-strong bg-surface-container-lowest px-sm py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
                          onClick={() => openEditDialog(item)}
                          aria-label={`编辑 ${item.sourceTerm}`}
                        >
                          编辑
                        </button>
                        <button
                          type="button"
                          data-testid={`mapping-delete-${String(item.id)}`}
                          className="rounded-lg border border-error bg-error-container px-sm py-1.5 text-[12px] text-on-error-container transition-opacity hover:opacity-90"
                          onClick={() => setDeleteTarget(item)}
                          aria-label={`删除 ${item.sourceTerm}`}
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
        </div>

        {pageData ? (
          <div className="flex flex-wrap items-center justify-between gap-sm">
            <span data-testid="mapping-total" className="text-body-sm text-secondary">共 {pageData.total} 条</span>
            <div className="flex items-center gap-sm">
              <button
                type="button"
                className="rounded-lg border border-border-strong bg-surface-container-lowest px-md py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low disabled:cursor-not-allowed disabled:opacity-50"
                onClick={() => setPageNo((previous) => Math.max(1, previous - 1))}
                disabled={pageData.current <= 1}
              >
                上一页
              </button>
              <span className="text-[12px] text-secondary">
                {pageData.current} / {Math.max(pageData.pages, 1)}
              </span>
              <button
                type="button"
                className="rounded-lg border border-border-strong bg-surface-container-lowest px-md py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low disabled:cursor-not-allowed disabled:opacity-50"
                onClick={() => setPageNo((previous) => Math.min(Math.max(pageData.pages, 1), previous + 1))}
                disabled={pageData.current >= pageData.pages}
              >
                下一页
              </button>
            </div>
          </div>
        ) : null}
      </section>

      {dialogOpen ? (
        <MappingEditDialog
          mode={dialogMode}
          form={form}
          fieldErrors={fieldErrors}
          formError={formError}
          saving={saving}
          onClose={() => setDialogOpen(false)}
          onChange={updateField}
          onSubmit={() => void handleSubmit()}
        />
      ) : null}

      {deleteTarget ? (
        <DeleteMappingDialog
          item={deleteTarget}
          deleting={deleting}
          onCancel={() => setDeleteTarget(null)}
          onConfirm={() => void handleDelete()}
        />
      ) : null}
    </div>
  );
}

function MappingEditDialog({
  mode,
  form,
  fieldErrors,
  formError,
  saving,
  onClose,
  onChange,
  onSubmit,
}: {
  mode: MappingDialogMode;
  form: MappingFormState;
  fieldErrors: Record<string, string>;
  formError: string;
  saving: boolean;
  onClose: () => void;
  onChange: (field: keyof MappingFormState, value: string | number | boolean) => void;
  onSubmit: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={mode === 'create' ? '新增映射规则' : '编辑映射规则'}
        data-testid="mapping-edit-dialog"
        className="w-full max-w-2xl rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
      >
        <div className="flex items-start justify-between gap-md border-b border-border-hairline px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">{mode === 'create' ? '新增映射规则' : '编辑映射规则'}</h3>
            <p className="mt-1 text-body-sm text-secondary">配置查询归一化关键词映射</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-sm py-xs text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭映射弹窗"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        <div className="space-y-md p-lg">
          {formError ? (
            <div className="rounded-xl border border-error bg-error-container px-md py-sm text-sm text-on-error-container">
              {formError}
            </div>
          ) : null}

          <div className="grid gap-md md:grid-cols-2">
            <TextField
              id="mapping-source-term"
              inputTestId="mapping-source-term-input"
              label="原始词"
              value={form.sourceTerm}
              error={fieldErrors.sourceTerm}
              onChange={(value) => onChange('sourceTerm', value)}
            />
            <TextField
              id="mapping-target-term"
              inputTestId="mapping-target-term-input"
              label="目标词"
              value={form.targetTerm}
              error={fieldErrors.targetTerm}
              onChange={(value) => onChange('targetTerm', value)}
            />
            <SelectField
              id="mapping-match-type"
              selectTestId="mapping-match-type-select"
              label="匹配类型"
              value={String(form.matchType)}
              options={MATCH_TYPE_OPTIONS.map((option) => ({
                value: String(option.value),
                label: option.label,
              }))}
              onChange={(value) => onChange('matchType', Number(value))}
            />
            <TextField
              id="mapping-priority"
              inputTestId="mapping-priority-input"
              label="优先级"
              type="number"
              value={String(form.priority)}
              error={fieldErrors.priority}
              onChange={(value) => onChange('priority', Number(value || 0))}
            />
            <SelectField
              id="mapping-enabled"
              selectTestId="mapping-enabled-select"
              label="状态"
              value={form.enabled ? 'true' : 'false'}
              options={[
                { value: 'true', label: '启用' },
                { value: 'false', label: '禁用' },
              ]}
              onChange={(value) => onChange('enabled', value === 'true')}
            />
            <TextField
              id="mapping-remark"
              inputTestId="mapping-remark-input"
              label="备注"
              value={form.remark}
              onChange={(value) => onChange('remark', value)}
            />
          </div>

          <div className="flex justify-end gap-sm pt-sm">
            <button
              type="button"
              onClick={onClose}
              disabled={saving}
              className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink hover:bg-surface-container-low disabled:opacity-60"
            >
              取消
            </button>
            <button
              type="button"
              data-testid="mapping-save-btn"
              onClick={onSubmit}
              disabled={saving}
              className="rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary disabled:opacity-60"
            >
              {saving ? '保存中...' : '保存'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function DeleteMappingDialog({
  item,
  deleting,
  onCancel,
  onConfirm,
}: {
  item: AdminQueryTermMapping;
  deleting: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="删除映射规则"
        data-testid="mapping-delete-dialog"
        className="w-full max-w-md rounded-2xl border border-border-hairline bg-surface-container-lowest p-lg shadow-2xl"
      >
        <h3 className="font-title-md text-title-md text-ink">确认删除</h3>
        <p className="mt-sm text-body-sm text-secondary">
          确定删除“{item.sourceTerm} -&gt; {item.targetTerm}”映射规则吗？删除后将立即失效。
        </p>
        <div className="mt-lg flex justify-end gap-sm">
          <button
            type="button"
            onClick={onCancel}
            disabled={deleting}
            className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink hover:bg-surface-container-low disabled:opacity-60"
          >
            取消
          </button>
          <button
            type="button"
            data-testid="mapping-delete-confirm-btn"
            onClick={onConfirm}
            disabled={deleting}
            className="rounded-lg border border-error bg-error-container px-lg py-2 font-button text-button text-on-error-container hover:opacity-90 disabled:opacity-60"
          >
            {deleting ? '删除中...' : '确认删除'}
          </button>
        </div>
      </div>
    </div>
  );
}

function TextField({
  id,
  inputTestId,
  label,
  value,
  error,
  type = 'text',
  onChange,
}: {
  id: string;
  inputTestId?: string;
  label: string;
  value: string;
  error?: string;
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
        data-testid={inputTestId}
        type={type}
        value={value}
        className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-ink outline-none transition-colors focus:border-border-strong"
        onChange={(event) => onChange(event.target.value)}
      />
      {error ? <p className="mt-1 text-[12px] text-error">{error}</p> : null}
    </div>
  );
}

function SelectField({
  id,
  selectTestId,
  label,
  value,
  options,
  onChange,
}: {
  id: string;
  selectTestId?: string;
  label: string;
  value: string;
  options: Array<{ value: string; label: string }>;
  onChange: (value: string) => void;
}) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-[12px] font-medium text-secondary">
        {label}
      </label>
      <select
        id={id}
        data-testid={selectTestId}
        value={value}
        className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-ink outline-none transition-colors focus:border-border-strong"
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}

function MatchTypeBadge({ matchType }: { matchType?: number }) {
  const label = MATCH_TYPE_OPTIONS.find((option) => option.value === matchType)?.label || `类型${matchType ?? 1}`;
  return (
    <span className="rounded-full border border-border-hairline bg-surface-container-low px-2 py-0.5 text-[11px] text-secondary">
      {label}
    </span>
  );
}

function StatusBadge({ enabled }: { enabled: boolean }) {
  return (
    <span
      className={clsx(
        'rounded-full border px-2 py-0.5 text-[11px] font-medium',
        enabled
          ? 'border-border-strong bg-surface-container text-ink'
          : 'border-border-hairline bg-surface-container-low text-secondary',
      )}
    >
      {enabled ? '启用' : '禁用'}
    </span>
  );
}

function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN');
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
