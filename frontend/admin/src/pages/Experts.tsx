import React, { useState } from 'react';

import { AdminChatApi, AdminExpert, AdminPageResult } from '../api/adminChatApi';
import { DataTableCard } from '../components/DataTableCard';

const EXPERT_PAGE_SIZE = 10;

type ExpertDialogMode = 'create' | 'edit';

interface ExpertFormState {
  expertCode: string;
  displayName: string;
  category: string;
  description: string;
  tagsJson: string;
  presetQuestion: string;
  systemPrompt: string;
  enabled: boolean;
  sortNo: string;
}

const emptyExpertForm: ExpertFormState = {
  expertCode: '',
  displayName: '',
  category: '',
  description: '',
  tagsJson: '[]',
  presetQuestion: '',
  systemPrompt: '',
  enabled: true,
  sortNo: '0',
};

/**
 * 管理端专家管理页：支持分页列表和新增编辑。
 */
export function Experts() {
  const [pageNo, setPageNo] = useState(1);
  const [pageData, setPageData] = useState<AdminPageResult<AdminExpert> | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState<ExpertDialogMode>('create');
  const [editingExpert, setEditingExpert] = useState<AdminExpert | null>(null);

  const experts = pageData?.records ?? [];
  const current = pageData?.current ?? pageNo;
  const pages = pageData?.pages ?? 1;
  const total = pageData?.total ?? 0;

  const loadExperts = React.useCallback(async (currentPage = pageNo) => {
    setIsLoading(true);
    setErrorMessage('');
    try {
      const response = await AdminChatApi.listExperts({
        current: currentPage,
        size: EXPERT_PAGE_SIZE,
      });
      setPageData(response);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '专家加载失败'));
      setPageData(null);
    } finally {
      setIsLoading(false);
    }
  }, [pageNo]);

  React.useEffect(() => {
    void loadExperts(pageNo);
  }, [loadExperts, pageNo]);

  return (
    <div className="w-full p-lg">
      <div className="mb-lg flex flex-wrap items-end justify-between gap-md">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">专家管理 (Experts)</h2>
          <p className="mt-1 text-secondary">维护聊天专家角色、示例问题与系统提示词。</p>
        </div>
        <button
          type="button"
          aria-label="创建新专家"
          className="flex items-center gap-xs rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary transition-transform hover:shadow-md active:scale-95"
          onClick={() => {
            setDialogMode('create');
            setEditingExpert(null);
            setDialogOpen(true);
          }}
        >
          <span className="material-symbols-outlined text-[18px]">add</span>
          创建新专家
        </button>
      </div>

      {errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-4 py-6 text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      <DataTableCard
        scrollTestId="experts-table-scroll"
        loading={isLoading}
        loadingText="专家加载中..."
        summaryText={`第 ${current} / ${Math.max(1, pages)} 页，共 ${total.toLocaleString('zh-CN')} 条`}
        paginationCurrent={current}
        paginationPages={pages}
        onPaginationChange={setPageNo}
        tableContent={(
          <table className="w-full min-w-[1280px] border-collapse text-left">
            <thead>
              <tr className="border-b border-border-hairline bg-surface-container-low">
                <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-[12px] text-secondary">专家</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-[12px] text-secondary">编码</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-[12px] text-secondary">分类</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-[12px] text-secondary">示例问题</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-[12px] text-secondary">状态</th>
                <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-right text-[12px] text-secondary">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {!isLoading && experts.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-md py-lg text-center text-sm text-secondary">
                    暂无专家数据
                  </td>
                </tr>
              ) : (
                experts.map((expert) => (
                  <tr
                    key={expert.id ?? expert.expertCode}
                    className="text-[13px] text-ink transition-colors hover:bg-surface-container-low"
                  >
                    <td className="px-md py-sm">
                      <div className="font-medium text-ink">{expert.displayName}</div>
                      <p className="line-clamp-2 text-[12px] text-secondary">{expert.description || '暂无描述'}</p>
                    </td>
                    <td className="px-md py-sm font-data-mono text-[12px] text-secondary">/{expert.expertCode}</td>
                    <td className="px-md py-sm text-secondary">{expert.category || '未分类'}</td>
                    <td className="px-md py-sm text-secondary">{expert.presetQuestion || '未配置'}</td>
                    <td className="px-md py-sm">
                      <span
                        className={[
                          'rounded px-2 py-1 text-[11px]',
                          expert.enabled === 0 ? 'bg-surface-container text-secondary' : 'bg-primary/15 text-primary',
                        ].join(' ')}
                      >
                        {expert.enabled === 0 ? '禁用' : '启用'}
                      </span>
                    </td>
                    <td className="px-md py-sm text-right">
                      <div className="inline-flex gap-xs">
                        <button
                          type="button"
                          aria-label={`编辑专家 ${expert.expertCode}`}
                          className="rounded-md border border-border-strong bg-surface-container-lowest px-sm py-1 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
                          onClick={() => {
                            setDialogMode('edit');
                            setEditingExpert(expert);
                            setDialogOpen(true);
                          }}
                        >
                          编辑
                        </button>
                        <button
                          type="button"
                          aria-label={`删除专家 ${expert.expertCode}`}
                          className="rounded-md border border-error/30 bg-error-container px-sm py-1 text-[12px] text-on-error-container transition-colors hover:opacity-90"
                          onClick={async () => {
                            if (expert.id == null) {
                              return;
                            }
                            await AdminChatApi.deleteExpert(expert.id);
                            await loadExperts(pageNo);
                          }}
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
        )}
      />

      {dialogOpen ? (
        <ExpertEditDialog
          mode={dialogMode}
          expert={editingExpert}
          onClose={() => setDialogOpen(false)}
          onSubmit={async (payload) => {
            if (dialogMode === 'edit' && editingExpert?.id != null) {
              await AdminChatApi.updateExpert(editingExpert.id, payload);
            } else {
              await AdminChatApi.createExpert(payload);
            }
            setDialogOpen(false);
            await loadExperts(pageNo);
          }}
        />
      ) : null}
    </div>
  );
}

function ExpertEditDialog({
  mode,
  expert,
  onClose,
  onSubmit,
}: {
  mode: ExpertDialogMode;
  expert: AdminExpert | null;
  onClose: () => void;
  onSubmit: (payload: AdminExpert) => Promise<void>;
}) {
  const [form, setForm] = React.useState<ExpertFormState>(() => toExpertForm(expert));
  const [saving, setSaving] = React.useState(false);
  const [formError, setFormError] = React.useState('');

  const updateField = (field: keyof ExpertFormState, value: string | boolean) => {
    setForm((previous) => ({ ...previous, [field]: value }));
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError('');
    if (!form.expertCode.trim() || !form.displayName.trim() || !form.systemPrompt.trim()) {
      setFormError('请至少填写专家编码、名称和提示词');
      return;
    }
    setSaving(true);
    try {
      await onSubmit(toExpertPayload(form, expert));
    } catch (error) {
      setFormError(extractErrorMessage(error, mode === 'create' ? '新增专家失败' : '保存专家失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={mode === 'create' ? '新增专家' : '编辑专家'}
        className="max-h-[88vh] w-full max-w-3xl overflow-y-auto rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
      >
        <div className="sticky top-0 z-10 flex items-start justify-between gap-md border-b border-border-hairline bg-surface-container-lowest px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">{mode === 'create' ? '新增专家' : '编辑专家'}</h3>
            <p className="mt-1 text-body-sm text-secondary">维护专家编码、示例问题和提示词。</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-sm py-xs text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭专家弹窗"
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

          <div className="grid gap-md md:grid-cols-2">
            <TextField id="expert-code" label="专家编码" value={form.expertCode} disabled={mode === 'edit'} onChange={(value) => updateField('expertCode', value)} />
            <TextField id="expert-display-name" label="专家名称" value={form.displayName} onChange={(value) => updateField('displayName', value)} />
            <TextField id="expert-category" label="分类" value={form.category} onChange={(value) => updateField('category', value)} />
            <TextField id="expert-sort-no" label="排序" type="number" value={form.sortNo} onChange={(value) => updateField('sortNo', value)} />
            <TextField id="expert-tags-json" label="标签JSON" value={form.tagsJson} onChange={(value) => updateField('tagsJson', value)} />
            <label className="flex items-center gap-sm rounded-xl border border-border-hairline bg-surface-container-lowest px-md py-sm text-ink">
              <input type="checkbox" checked={form.enabled} onChange={(event) => updateField('enabled', event.target.checked)} />
              启用专家
            </label>
          </div>

          <TextAreaField id="expert-description" label="专家描述" value={form.description} rows={3} onChange={(value) => updateField('description', value)} />
          <TextAreaField id="expert-preset-question" label="示例问题" value={form.presetQuestion} rows={3} onChange={(value) => updateField('presetQuestion', value)} />
          <TextAreaField id="expert-system-prompt" label="系统提示词" value={form.systemPrompt} rows={8} onChange={(value) => updateField('systemPrompt', value)} />

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
              {saving ? '保存中...' : mode === 'create' ? '创建专家' : '保存修改'}
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
  disabled,
  type = 'text',
  onChange,
}: {
  id: string;
  label: string;
  value: string;
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

function toExpertForm(expert: AdminExpert | null): ExpertFormState {
  if (!expert) {
    return emptyExpertForm;
  }
  return {
    expertCode: expert.expertCode ?? '',
    displayName: expert.displayName ?? '',
    category: expert.category ?? '',
    description: expert.description ?? '',
    tagsJson: expert.tagsJson ?? '[]',
    presetQuestion: expert.presetQuestion ?? '',
    systemPrompt: expert.systemPrompt ?? '',
    enabled: expert.enabled !== 0,
    sortNo: String(expert.sortNo ?? 0),
  };
}

function toExpertPayload(form: ExpertFormState, editingExpert: AdminExpert | null): AdminExpert {
  const parsedSortNo = Number(form.sortNo);
  return {
    id: editingExpert?.id,
    expertCode: form.expertCode.trim(),
    displayName: form.displayName.trim(),
    category: form.category.trim() || undefined,
    description: form.description.trim() || undefined,
    tagsJson: form.tagsJson.trim() || '[]',
    presetQuestion: form.presetQuestion.trim() || undefined,
    systemPrompt: form.systemPrompt.trim(),
    enabled: form.enabled ? 1 : 0,
    sortNo: Number.isFinite(parsedSortNo) ? parsedSortNo : 0,
  };
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
