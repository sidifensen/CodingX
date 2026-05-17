import React, { useState } from 'react';

import { AdminChatApi, AdminSkill } from '../api/adminChatApi';

type SkillDialogMode = 'create' | 'edit';

interface SkillFormState {
  skillCode: string;
  displayName: string;
  description: string;
  category: string;
  sourceType: string;
  sortNo: string;
  enabled: boolean;
}

const emptySkillForm: SkillFormState = {
  skillCode: '',
  displayName: '',
  description: '',
  category: '',
  sourceType: 'built-in',
  sortNo: '0',
  enabled: true,
};

/**
 * 管理端技能管理页：读取后端真实技能配置并提供新增/编辑能力。
 */
export function Skills() {
  const [skills, setSkills] = useState<AdminSkill[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState<SkillDialogMode>('create');
  const [editingSkill, setEditingSkill] = useState<AdminSkill | null>(null);

  /**
   * 统一加载技能列表，供初始化与保存后刷新复用。
   */
  const loadSkills = React.useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');
    try {
      const response = await AdminChatApi.listSkills();
      setSkills(response);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '技能加载失败'));
      setSkills([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void loadSkills();
  }, [loadSkills]);

  /**
   * 打开新增技能弹窗。
   */
  const openCreateDialog = () => {
    setDialogMode('create');
    setEditingSkill(null);
    setDialogOpen(true);
  };

  /**
   * 打开编辑技能弹窗。
   * @param skill 目标技能。
   */
  const openEditDialog = (skill: AdminSkill) => {
    setDialogMode('edit');
    setEditingSkill(skill);
    setDialogOpen(true);
  };

  return (
    <div className="w-full p-lg">
      <div className="mb-lg flex items-end justify-between">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">技能管理 (Skills)</h2>
          <p className="mt-1 text-secondary">管理应用内部搭载的各类型代理技能。</p>
        </div>
        <button
          type="button"
          aria-label="创建新技能"
          className="flex items-center gap-xs rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary transition-transform hover:shadow-md active:scale-95"
          onClick={openCreateDialog}
        >
          <span className="material-symbols-outlined text-[18px]">add</span>
          创建新技能
        </button>
      </div>

      {isLoading ? (
        <div className="rounded-xl border border-border-hairline bg-surface-container-lowest px-4 py-6 text-sm text-secondary">
          技能加载中...
        </div>
      ) : null}
      {!isLoading && errorMessage ? (
        <div className="rounded-xl border border-error bg-error-container px-4 py-6 text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      {!isLoading && !errorMessage ? (
        <div className="grid grid-cols-1 gap-md md:grid-cols-2 lg:grid-cols-3">
          {skills.map((skill) => (
            <div
              key={skill.id ?? skill.skillCode}
              className="group relative overflow-hidden rounded-xl border border-border-hairline bg-surface-container-lowest p-lg transition-all hover:border-border-strong hover:shadow-md"
            >
              <div className="absolute top-0 right-0 h-16 w-16 bg-gradient-to-bl from-surface-container-low to-transparent opacity-50 mix-blend-multiply transition-opacity group-hover:opacity-100" />
              <div className="relative mb-md flex items-start justify-between">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl border border-border-hairline bg-surface-container transition-colors group-hover:bg-primary group-hover:text-on-primary">
                  <span className="material-symbols-outlined text-[24px]">extension</span>
                </div>
                <span className="rounded border border-border-hairline bg-surface-container-low px-2 py-1 font-data-mono text-[10px] text-secondary">
                  {skill.sourceType || 'built-in'}
                </span>
              </div>
              <h3 className="mb-1 font-title-md text-ink transition-colors group-hover:text-primary">{skill.displayName}</h3>
              <p className="mb-lg line-clamp-2 text-[12px] text-secondary">{skill.description || '暂无描述'}</p>
              <div className="mt-auto flex items-center justify-between text-[12px] text-secondary">
                <span>{skill.category || '未分类'}</span>
                <span className="font-data-mono">/{skill.skillCode}</span>
              </div>
              <div className="mt-md flex justify-end">
                <button
                  type="button"
                  aria-label={`编辑技能 ${skill.skillCode}`}
                  className="rounded-lg border border-border-strong bg-surface-container-lowest px-sm py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
                  onClick={() => openEditDialog(skill)}
                >
                  编辑
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : null}

      {dialogOpen ? (
        <SkillEditDialog
          mode={dialogMode}
          skill={editingSkill}
          onClose={() => setDialogOpen(false)}
          onSubmit={async (payload) => {
            if (dialogMode === 'edit' && editingSkill?.id != null) {
              await AdminChatApi.updateSkill(editingSkill.id, payload);
            } else {
              await AdminChatApi.createSkill(payload);
            }
            setDialogOpen(false);
            await loadSkills();
          }}
        />
      ) : null}
    </div>
  );
}

interface SkillEditDialogProps {
  mode: SkillDialogMode;
  skill: AdminSkill | null;
  onClose: () => void;
  onSubmit: (payload: AdminSkill) => Promise<void>;
}

/**
 * 技能新增/编辑弹窗，统一表单与校验逻辑。
 */
function SkillEditDialog({ mode, skill, onClose, onSubmit }: SkillEditDialogProps) {
  const [form, setForm] = React.useState<SkillFormState>(() => toSkillForm(skill));
  const [saving, setSaving] = React.useState(false);
  const [formError, setFormError] = React.useState('');
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});

  /**
   * 更新表单字段并清理对应字段的校验错误。
   * @param field 字段名。
   * @param value 字段值。
   */
  const updateField = (field: keyof SkillFormState, value: string | boolean) => {
    setFieldErrors((previous) => ({ ...previous, [field]: '' }));
    setForm((previous) => ({ ...previous, [field]: value }));
  };

  /**
   * 最小必填校验，避免提交空编码/空名称。
   */
  const validate = () => {
    const nextErrors: Record<string, string> = {};
    if (!form.skillCode.trim()) {
      nextErrors.skillCode = '请输入技能编码';
    }
    if (!form.displayName.trim()) {
      nextErrors.displayName = '请输入技能名称';
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
      await onSubmit(toSkillPayload(form, skill));
    } catch (error) {
      setFormError(extractErrorMessage(error, mode === 'create' ? '新增技能失败' : '保存技能失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={mode === 'create' ? '新增技能' : '编辑技能'}
        className="max-h-[88vh] w-full max-w-2xl overflow-y-auto rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
      >
        <div className="sticky top-0 z-10 flex items-start justify-between gap-md border-b border-border-hairline bg-surface-container-lowest px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">{mode === 'create' ? '新增技能' : '编辑技能'}</h3>
            <p className="mt-1 text-body-sm text-secondary">维护技能编码、展示信息、启用状态和排序。</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-sm py-xs text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭技能弹窗"
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
                id="skill-code"
                label="技能编码"
                value={form.skillCode}
                error={fieldErrors.skillCode}
                disabled={mode === 'edit'}
                onChange={(value) => updateField('skillCode', value)}
              />
              <TextField
                id="skill-display-name"
                label="技能名称"
                value={form.displayName}
                error={fieldErrors.displayName}
                onChange={(value) => updateField('displayName', value)}
              />
              <TextField id="skill-category" label="分类" value={form.category} onChange={(value) => updateField('category', value)} />
              <TextField
                id="skill-source-type"
                label="来源类型"
                value={form.sourceType}
                onChange={(value) => updateField('sourceType', value)}
              />
              <TextField
                id="skill-sort"
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
                启用技能
              </label>
            </div>
          </fieldset>

          <fieldset className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
            <legend className="px-xs font-title-sm text-ink">描述</legend>
            <TextAreaField
              id="skill-description"
              label="技能说明"
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
              {saving ? '保存中...' : mode === 'create' ? '创建技能' : '保存修改'}
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

function toSkillForm(skill: AdminSkill | null): SkillFormState {
  if (!skill) {
    return emptySkillForm;
  }
  return {
    skillCode: skill.skillCode ?? '',
    displayName: skill.displayName ?? '',
    description: skill.description ?? '',
    category: skill.category ?? '',
    sourceType: skill.sourceType ?? 'built-in',
    sortNo: String(skill.sortNo ?? 0),
    enabled: skill.enabled !== 0,
  };
}

function toSkillPayload(form: SkillFormState, editingSkill: AdminSkill | null): AdminSkill {
  const parsedSortNo = Number(form.sortNo);
  return {
    id: editingSkill?.id,
    skillCode: form.skillCode.trim(),
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
