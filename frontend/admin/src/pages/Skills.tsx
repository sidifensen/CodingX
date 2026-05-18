import React, { useState } from 'react';

import {
  AdminChatApi,
  AdminPageResult,
  AdminSkill,
  AdminSkillPackageEntry,
  AdminSkillPackageFileContent,
} from '../api/adminChatApi';
import { DataTableCard } from '../components/DataTableCard';

type SkillDialogMode = 'create' | 'edit';
type SkillViewMode = 'list' | 'card';

const SKILL_PAGE_SIZE = 10;

interface SkillFormState {
  skillCode: string;
  displayName: string;
  description: string;
  category: string;
  sourceType: string;
  sortNo: string;
  enabled: boolean;
}

interface SkillPackageTreeRow extends AdminSkillPackageEntry {
  depth: number;
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
 * 管理端技能管理页：支持列表/卡片切换、上传、编辑以及技能包在线预览。
 */
export function Skills() {
  const [pageNo, setPageNo] = useState(1);
  const [pageData, setPageData] = useState<AdminPageResult<AdminSkill> | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [uploadDialogOpen, setUploadDialogOpen] = useState(false);
  const [previewSkill, setPreviewSkill] = useState<AdminSkill | null>(null);
  const [dialogMode, setDialogMode] = useState<SkillDialogMode>('create');
  const [viewMode, setViewMode] = useState<SkillViewMode>('list');
  const [editingSkill, setEditingSkill] = useState<AdminSkill | null>(null);

  const skills = pageData?.records ?? [];
  const current = pageData?.current ?? pageNo;
  const pages = pageData?.pages ?? 1;
  const total = pageData?.total ?? 0;

  /**
   * 统一加载技能分页，供初始化与保存后刷新复用。
   */
  const loadSkills = React.useCallback(async (currentPage = pageNo) => {
    setIsLoading(true);
    setErrorMessage('');
    try {
      const response = await AdminChatApi.listSkills({
        current: currentPage,
        size: SKILL_PAGE_SIZE,
      });
      setPageData(response);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '技能加载失败'));
      setPageData(null);
    } finally {
      setIsLoading(false);
    }
  }, [pageNo]);

  React.useEffect(() => {
    void loadSkills(pageNo);
  }, [loadSkills, pageNo]);

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

  /**
   * 打开技能包资源预览弹窗，仅对已上传技能生效。
   * @param skill 目标技能。
   */
  const openPackagePreviewDialog = (skill: AdminSkill) => {
    if (!skill.id || !skill.storageKey) {
      return;
    }
    setPreviewSkill(skill);
  };

  return (
    <div className="w-full p-lg">
      <div className="mb-lg flex flex-wrap items-end justify-between gap-md">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">技能管理 (Skills)</h2>
          <p className="mt-1 text-secondary">管理应用内部搭载的各类型代理技能与上传技能包。</p>
        </div>
        <div className="flex flex-wrap items-center gap-sm">
          <div className="flex items-center rounded-lg border border-border-hairline bg-surface-container-lowest p-1">
            <button
              type="button"
              aria-label="列表视图"
              className={[
                'rounded-md px-sm py-1.5 text-[12px] transition-colors',
                viewMode === 'list' ? 'bg-primary text-on-primary' : 'text-secondary hover:bg-surface-container-low hover:text-ink',
              ].join(' ')}
              onClick={() => setViewMode('list')}
            >
              列表
            </button>
            <button
              type="button"
              aria-label="卡片视图"
              className={[
                'rounded-md px-sm py-1.5 text-[12px] transition-colors',
                viewMode === 'card' ? 'bg-primary text-on-primary' : 'text-secondary hover:bg-surface-container-low hover:text-ink',
              ].join(' ')}
              onClick={() => setViewMode('card')}
            >
              卡片
            </button>
          </div>
          <button
            type="button"
            aria-label="上传技能包"
            className="flex items-center gap-xs rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink transition-colors hover:bg-surface-container-low active:scale-95"
            onClick={() => setUploadDialogOpen(true)}
          >
            <span className="material-symbols-outlined text-[18px]">upload_file</span>
            上传技能包
          </button>
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
        viewMode === 'list' ? (
          <SkillListView
            skills={skills}
            current={current}
            pages={pages}
            total={total}
            loading={isLoading}
            onChangePage={setPageNo}
            onEditSkill={openEditDialog}
            onPreviewSkill={openPackagePreviewDialog}
          />
        ) : (
          <SkillCardView
            skills={skills}
            onEditSkill={openEditDialog}
            onPreviewSkill={openPackagePreviewDialog}
          />
        )
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
            await loadSkills(pageNo);
          }}
        />
      ) : null}

      {uploadDialogOpen ? (
        <SkillUploadDialog
          onClose={() => setUploadDialogOpen(false)}
          onUploaded={async () => {
            setUploadDialogOpen(false);
            await loadSkills(pageNo);
          }}
        />
      ) : null}

      {previewSkill ? (
        <SkillPackagePreviewDialog
          skill={previewSkill}
          onClose={() => setPreviewSkill(null)}
        />
      ) : null}
    </div>
  );
}

function SkillListView({
  skills,
  current,
  pages,
  total,
  loading,
  onChangePage,
  onEditSkill,
  onPreviewSkill,
}: {
  skills: AdminSkill[];
  current: number;
  pages: number;
  total: number;
  loading: boolean;
  onChangePage: (page: number) => void;
  onEditSkill: (skill: AdminSkill) => void;
  onPreviewSkill: (skill: AdminSkill) => void;
}) {
  return (
    <DataTableCard
      title="技能列表"
      description="统一展示技能配置信息，支持资源预览与编辑。"
      scrollTestId="skills-table-scroll"
      loading={loading}
      loadingText="技能加载中..."
      summaryText={`第 ${current} / ${Math.max(1, pages)} 页，共 ${total.toLocaleString('zh-CN')} 条`}
      paginationCurrent={current}
      paginationPages={pages}
      onPaginationChange={onChangePage}
      tableContent={(
        <table className="w-full min-w-[1160px] border-collapse text-left">
          <thead>
            <tr className="border-b border-border-hairline bg-surface-container-low">
              <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-[12px] text-secondary">技能</th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-[12px] text-secondary">编码</th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-[12px] text-secondary">分类</th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-[12px] text-secondary">来源</th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-[12px] text-secondary">状态</th>
              <th className="sticky top-0 z-10 bg-surface-container-low px-md py-sm text-right text-[12px] text-secondary">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-hairline">
            {skills.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-md py-lg text-center text-sm text-secondary">
                  暂无技能数据
                </td>
              </tr>
            ) : (
              skills.map((skill) => (
                <tr
                  key={skill.id ?? skill.skillCode}
                  className="text-[13px] text-ink transition-colors hover:bg-surface-container-low"
                >
                  <td className="px-md py-sm">
                    <div className="font-medium text-ink">{skill.displayName}</div>
                    <p className="line-clamp-2 text-[12px] text-secondary">{skill.description || '暂无描述'}</p>
                  </td>
                  <td className="px-md py-sm font-data-mono text-[12px] text-secondary">/{skill.skillCode}</td>
                  <td className="px-md py-sm text-secondary">{skill.category || '未分类'}</td>
                  <td className="px-md py-sm text-secondary">{skill.sourceType || 'built-in'}</td>
                  <td className="px-md py-sm">
                    <span
                      className={[
                        'rounded px-2 py-1 text-[11px]',
                        skill.enabled === 0
                          ? 'bg-surface-container text-secondary'
                          : 'bg-primary/15 text-primary',
                      ].join(' ')}
                    >
                      {skill.enabled === 0 ? '禁用' : '启用'}
                    </span>
                  </td>
                  <td className="px-md py-sm text-right">
                    <div className="inline-flex gap-xs">
                      <button
                        type="button"
                        aria-label={`资源预览 ${skill.skillCode}`}
                        disabled={!skill.id || !skill.storageKey}
                        className="rounded-md border border-border-hairline bg-surface-container-low px-sm py-1 text-[12px] text-ink transition-colors hover:bg-surface-container disabled:cursor-not-allowed disabled:opacity-45"
                        onClick={() => onPreviewSkill(skill)}
                      >
                        资源预览
                      </button>
                      <button
                        type="button"
                        aria-label={`编辑技能 ${skill.skillCode}`}
                        className="rounded-md border border-border-strong bg-surface-container-lowest px-sm py-1 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
                        onClick={() => onEditSkill(skill)}
                      >
                        编辑
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
  );
}

function SkillCardView({
  skills,
  onEditSkill,
  onPreviewSkill,
}: {
  skills: AdminSkill[];
  onEditSkill: (skill: AdminSkill) => void;
  onPreviewSkill: (skill: AdminSkill) => void;
}) {
  if (skills.length === 0) {
    return (
      <div className="rounded-xl border border-border-hairline bg-surface-container-lowest px-4 py-6 text-sm text-secondary">
        暂无技能数据
      </div>
    );
  }

  return (
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
          <div className="mt-md flex justify-end gap-xs">
            <button
              type="button"
              aria-label={`资源预览 ${skill.skillCode}`}
              disabled={!skill.id || !skill.storageKey}
              className="rounded-lg border border-border-hairline bg-surface-container-low px-sm py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container disabled:cursor-not-allowed disabled:opacity-45"
              onClick={() => onPreviewSkill(skill)}
            >
              资源预览
            </button>
            <button
              type="button"
              aria-label={`编辑技能 ${skill.skillCode}`}
              className="rounded-lg border border-border-strong bg-surface-container-lowest px-sm py-1.5 text-[12px] text-ink transition-colors hover:bg-surface-container-low"
              onClick={() => onEditSkill(skill)}
            >
              编辑
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

interface SkillPackagePreviewDialogProps {
  skill: AdminSkill;
  onClose: () => void;
}

/**
 * 技能包资源管理器：按目录浏览归档文件并在线预览文本内容。
 */
function SkillPackagePreviewDialog({ skill, onClose }: SkillPackagePreviewDialogProps) {
  const [entries, setEntries] = React.useState<AdminSkillPackageEntry[]>([]);
  const [expandedPaths, setExpandedPaths] = React.useState<Set<string>>(new Set());
  const [selectedPath, setSelectedPath] = React.useState('');
  const [loadingEntries, setLoadingEntries] = React.useState(true);
  const [entriesError, setEntriesError] = React.useState('');
  const [loadingContent, setLoadingContent] = React.useState(false);
  const [contentError, setContentError] = React.useState('');
  const [fileContent, setFileContent] = React.useState<AdminSkillPackageFileContent | null>(null);

  const treeRows = React.useMemo(() => buildTreeRows(entries, expandedPaths), [entries, expandedPaths]);

  /**
   * 进入弹窗先加载目录树，并默认展开一级目录，便于快速定位 SKILL.md。
   */
  React.useEffect(() => {
    let cancelled = false;

    const loadEntries = async () => {
      setLoadingEntries(true);
      setEntriesError('');
      setEntries([]);
      setExpandedPaths(new Set());
      setFileContent(null);
      setContentError('');
      setSelectedPath('');

      if (!skill.id) {
        setEntriesError('技能主键缺失，无法预览资源');
        setLoadingEntries(false);
        return;
      }

      try {
        const response = await AdminChatApi.listSkillPackageEntries(skill.id);
        if (cancelled) {
          return;
        }
        setEntries(response);

        const nextExpanded = new Set<string>();
        response.forEach((entry) => {
          if (entry.directory && !entry.path.includes('/')) {
            nextExpanded.add(entry.path);
          }
        });
        setExpandedPaths(nextExpanded);

        const manifestFile = response.find((entry) => !entry.directory && entry.path.toLowerCase() === 'skill.md');
        if (manifestFile) {
          void loadFileContent(manifestFile.path);
        }
      } catch (error) {
        if (!cancelled) {
          setEntriesError(extractErrorMessage(error, '技能包目录加载失败'));
        }
      } finally {
        if (!cancelled) {
          setLoadingEntries(false);
        }
      }
    };

    void loadEntries();

    return () => {
      cancelled = true;
    };
  }, [skill.id]);

  /**
   * 读取技能包中的文本文件内容；二进制或不可读文件由后端统一返回中文错误语义。
   */
  const loadFileContent = async (path: string) => {
    if (!skill.id) {
      return;
    }
    setSelectedPath(path);
    setContentError('');
    setLoadingContent(true);
    try {
      const response = await AdminChatApi.getSkillPackageFileContent(skill.id, path);
      setFileContent(response);
    } catch (error) {
      setFileContent(null);
      setContentError(extractErrorMessage(error, '文件预览失败'));
    } finally {
      setLoadingContent(false);
    }
  };

  const toggleDirectory = (path: string) => {
    setExpandedPaths((previous) => {
      const next = new Set(previous);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={`技能包资源预览 ${skill.skillCode}`}
        className="max-h-[90vh] w-full max-w-6xl overflow-hidden rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
      >
        <div className="flex items-start justify-between gap-md border-b border-border-hairline bg-surface-container-lowest px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">技能包资源预览</h3>
            <p className="mt-1 text-body-sm text-secondary">
              当前技能：{skill.displayName}（/{skill.skillCode}）
            </p>
            <p className="mt-1 text-[12px] text-secondary">对象键：{skill.storageKey || '未配置'}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-sm py-xs text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭资源预览"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        <div className="grid h-[72vh] grid-cols-1 gap-0 lg:grid-cols-[340px_1fr]">
          <aside className="border-r border-border-hairline bg-surface-container-low">
            <div className="border-b border-border-hairline px-md py-sm text-[12px] text-secondary">文件目录</div>
            <div className="h-full overflow-y-auto px-xs py-xs">
              {loadingEntries ? (
                <div className="px-sm py-sm text-[12px] text-secondary">目录加载中...</div>
              ) : entriesError ? (
                <div className="rounded-md border border-error bg-error-container px-sm py-sm text-[12px] text-on-error-container">
                  {entriesError}
                </div>
              ) : treeRows.length === 0 ? (
                <div className="px-sm py-sm text-[12px] text-secondary">目录为空</div>
              ) : (
                treeRows.map((row) => (
                  <button
                    key={row.path}
                    type="button"
                    className={[
                      'flex w-full items-center gap-xs rounded-md px-sm py-1.5 text-left text-[12px] transition-colors',
                      selectedPath === row.path
                        ? 'bg-primary/15 text-primary'
                        : 'text-ink hover:bg-surface-container-lowest',
                    ].join(' ')}
                    style={{ paddingLeft: `${8 + row.depth * 16}px` }}
                    onClick={() => {
                      if (row.directory) {
                        toggleDirectory(row.path);
                        return;
                      }
                      void loadFileContent(row.path);
                    }}
                  >
                    {row.directory ? (
                      <span className="material-symbols-outlined text-[16px] text-secondary">
                        {expandedPaths.has(row.path) ? 'folder_open' : 'folder'}
                      </span>
                    ) : (
                      <span className="material-symbols-outlined text-[16px] text-secondary">description</span>
                    )}
                    <span className="truncate">{row.name}</span>
                    {!row.directory && row.size != null ? (
                      <span className="ml-auto shrink-0 font-data-mono text-[10px] text-secondary">{formatBytes(row.size)}</span>
                    ) : null}
                  </button>
                ))
              )}
            </div>
          </aside>

          <section className="flex min-h-0 flex-col bg-surface-container-lowest">
            <div className="flex items-center justify-between border-b border-border-hairline px-md py-sm text-[12px] text-secondary">
              <span>文件预览</span>
              <span className="font-data-mono">{selectedPath || '未选择文件'}</span>
            </div>
            <div className="min-h-0 flex-1 overflow-auto">
              {loadingContent ? (
                <div className="px-md py-md text-[12px] text-secondary">文件加载中...</div>
              ) : contentError ? (
                <div className="m-md rounded-md border border-error bg-error-container px-sm py-sm text-[12px] text-on-error-container">
                  {contentError}
                </div>
              ) : fileContent ? (
                <div className="px-md py-md">
                  {fileContent.truncated ? (
                    <div className="mb-sm rounded-md border border-border-hairline bg-surface-container-low px-sm py-sm text-[12px] text-secondary">
                      文件较大，已截断到 128KB 进行预览。
                    </div>
                  ) : null}
                  <pre className="min-h-[320px] whitespace-pre-wrap break-words rounded-lg border border-border-hairline bg-surface-container px-md py-md font-data-mono text-[12px] leading-relaxed text-ink">
                    {fileContent.content}
                  </pre>
                </div>
              ) : (
                <div className="px-md py-md text-[12px] text-secondary">请选择左侧文件进行在线预览。</div>
              )}
            </div>
          </section>
        </div>
      </div>
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

interface SkillUploadDialogProps {
  onClose: () => void;
  onUploaded: () => Promise<void>;
}

/**
 * 上传技能包弹窗：提交 zip/skill 文件并交由后端解析 SKILL.md。
 */
function SkillUploadDialog({ onClose, onUploaded }: SkillUploadDialogProps) {
  const [file, setFile] = React.useState<File | null>(null);
  const [category, setCategory] = React.useState('');
  const [submitting, setSubmitting] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setErrorMessage('');
    if (!file) {
      setErrorMessage('请选择技能包文件');
      return;
    }
    setSubmitting(true);
    try {
      await AdminChatApi.uploadSkillPackage(file, category);
      await onUploaded();
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '技能包上传失败'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="上传技能包"
        className="max-h-[88vh] w-full max-w-2xl overflow-y-auto rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
      >
        <div className="sticky top-0 z-10 flex items-start justify-between gap-md border-b border-border-hairline bg-surface-container-lowest px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">上传技能包</h3>
            <p className="mt-1 text-body-sm text-secondary">上传包含根级 SKILL.md 的 zip 或 .skill 文件。</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-sm py-xs text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭上传弹窗"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        <form className="space-y-lg p-lg" onSubmit={handleSubmit}>
          {errorMessage ? (
            <div className="rounded-xl border border-error bg-error-container px-md py-sm text-sm text-on-error-container">
              {errorMessage}
            </div>
          ) : null}
          <fieldset className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
            <legend className="px-xs font-title-sm text-ink">文件与分类</legend>
            <div className="space-y-md">
              <div>
                <label htmlFor="skill-package-file" className="mb-1 block text-[12px] font-medium text-secondary">
                  技能包文件
                </label>
                <input
                  id="skill-package-file"
                  aria-label="技能包文件"
                  type="file"
                  accept=".zip,.skill"
                  className="block w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-ink file:mr-sm file:rounded-md file:border-0 file:bg-surface-container file:px-sm file:py-1.5 file:text-ink"
                  onChange={(event) => {
                    const selectedFile = event.target.files?.[0] ?? null;
                    setFile(selectedFile);
                  }}
                />
                <p className="mt-1 text-[12px] text-secondary">
                  仅支持 .zip / .skill，且压缩包根目录必须包含 SKILL.md
                </p>
              </div>
              <TextField
                id="skill-upload-category"
                label="分类（可选）"
                value={category}
                onChange={(value) => setCategory(value)}
              />
            </div>
          </fieldset>

          <div className="flex flex-wrap justify-end gap-sm">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink hover:bg-surface-container-low disabled:opacity-60"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary disabled:opacity-60"
            >
              {submitting ? '上传中...' : '确认上传'}
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

/**
 * 将扁平路径转换为可展开文件树行。
 * 规则：目录优先，其次同级按名称升序。
 */
function buildTreeRows(entries: AdminSkillPackageEntry[], expandedPaths: Set<string>): SkillPackageTreeRow[] {
  const childrenMap = new Map<string, AdminSkillPackageEntry[]>();
  entries.forEach((entry) => {
    const parentPath = getParentPath(entry.path);
    const children = childrenMap.get(parentPath) ?? [];
    children.push(entry);
    childrenMap.set(parentPath, children);
  });

  childrenMap.forEach((children, parentPath) => {
    children.sort((left, right) => {
      if (left.directory !== right.directory) {
        return left.directory ? -1 : 1;
      }
      return left.name.localeCompare(right.name);
    });
    childrenMap.set(parentPath, children);
  });

  const rows: SkillPackageTreeRow[] = [];
  const appendRows = (parentPath: string, depth: number) => {
    const children = childrenMap.get(parentPath) ?? [];
    children.forEach((child) => {
      rows.push({ ...child, depth });
      if (child.directory && expandedPaths.has(child.path)) {
        appendRows(child.path, depth + 1);
      }
    });
  };

  appendRows('', 0);
  return rows;
}

function getParentPath(path: string): string {
  const separatorIndex = path.lastIndexOf('/');
  if (separatorIndex < 0) {
    return '';
  }
  return path.substring(0, separatorIndex);
}

function formatBytes(size: number): string {
  if (size < 1024) {
    return `${size}B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)}KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)}MB`;
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
