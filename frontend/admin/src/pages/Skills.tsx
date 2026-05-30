import React, { useState } from 'react';
import {
  AppstoreOutlined,
  CloseOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  SaveOutlined,
  StopOutlined,
  SyncOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { Alert, Avatar, Button, Card, Empty, Form, Input, Modal, Space, Spin, Switch, Tag, Typography, Upload } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import {
  AdminChatApi,
  AdminPageResult,
  AdminSkill,
  AdminSkillPackageEntry,
  AdminSkillPackageFileContent,
} from '../api/adminChatApi';
import { AdminDataTable, AdminTableActions } from '../components/AdminDataTable';
import { useAdminMessage } from '../components/AdminMessageContext';

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
  const adminMessage = useAdminMessage();
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
  const [deleteConfirmSkill, setDeleteConfirmSkill] = useState<AdminSkill | null>(null);

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

  /**
   * 切换技能启用/禁用状态。
   * @param skill 目标技能。
   */
  const handleToggleEnabled = async (skill: AdminSkill) => {
    if (!skill.id) {
      return;
    }
    try {
      const newEnabled = skill.enabled === 0 ? 1 : 0;
      await AdminChatApi.updateSkill(skill.id, { ...skill, enabled: newEnabled });
      await loadSkills(pageNo);
      void adminMessage.success(newEnabled === 1 ? '技能已启用' : '技能已禁用');
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '更新技能状态失败'));
    }
  };

  /**
   * 删除技能（物理删除，同时删除 rustfs 中的文件）。
   * @param skill 目标技能。
   */
  const handleDeleteSkill = (skill: AdminSkill) => {
    setDeleteConfirmSkill(skill);
  };

  const confirmDeleteSkill = async () => {
    if (!deleteConfirmSkill?.id) {
      return;
    }
    try {
      await AdminChatApi.deleteSkill(deleteConfirmSkill.id);
      setDeleteConfirmSkill(null);
      await loadSkills(pageNo);
      void adminMessage.success('技能已删除');
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '删除技能失败'));
      setDeleteConfirmSkill(null);
    }
  };

  return (
    <div className="w-full p-lg">
      <div className="mb-lg flex flex-wrap items-end justify-between gap-md">
        <div>
          <Typography.Title level={2} style={{ margin: 0 }}>技能管理 (Skills)</Typography.Title>
          <Typography.Text type="secondary">管理应用内部搭载的各类型代理技能与上传技能包。</Typography.Text>
        </div>
        <Space wrap>
          <Space.Compact>
            <Button
              aria-label="列表视图"
              aria-pressed={viewMode === 'list'}
              icon={<AppstoreOutlined />}
              type={viewMode === 'list' ? 'primary' : 'default'}
              onClick={() => setViewMode('list')}
            >
              列表
            </Button>
            <Button
              aria-label="卡片视图"
              aria-pressed={viewMode === 'card'}
              icon={<AppstoreOutlined />}
              type={viewMode === 'card' ? 'primary' : 'default'}
              onClick={() => setViewMode('card')}
            >
              卡片
            </Button>
          </Space.Compact>
          <Button
            aria-label="上传技能包"
            icon={<UploadOutlined />}
            onClick={() => setUploadDialogOpen(true)}
          >
            上传技能包
          </Button>
          <Button
            aria-label="创建新技能"
            icon={<PlusOutlined />}
            type="primary"
            onClick={openCreateDialog}
          >
            创建新技能
          </Button>
        </Space>
      </div>

      {errorMessage ? (
        <Alert className="mb-lg" showIcon type="error" message={errorMessage} />
      ) : null}

      {!errorMessage ? (
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
            onToggleEnabled={handleToggleEnabled}
            onDeleteSkill={handleDeleteSkill}
          />
        ) : (
          isLoading ? (
            <div className="rounded-xl border border-border-hairline bg-surface-container-lowest px-4 py-6 text-center">
              <Spin />
              <Typography.Paragraph className="mt-sm" type="secondary">技能加载中...</Typography.Paragraph>
            </div>
          ) : (
            <SkillCardView
              skills={skills}
              onEditSkill={openEditDialog}
              onPreviewSkill={openPackagePreviewDialog}
              onToggleEnabled={handleToggleEnabled}
              onDeleteSkill={handleDeleteSkill}
            />
          )
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
            void adminMessage.success(dialogMode === 'edit' ? '技能已保存' : '技能已创建');
          }}
        />
      ) : null}

      {uploadDialogOpen ? (
        <SkillUploadDialog
          onClose={() => setUploadDialogOpen(false)}
          onUploaded={async () => {
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

      {deleteConfirmSkill ? (
        <DeleteConfirmDialog
          skill={deleteConfirmSkill}
          onClose={() => setDeleteConfirmSkill(null)}
          onConfirm={confirmDeleteSkill}
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
  onToggleEnabled,
  onDeleteSkill,
}: {
  skills: AdminSkill[];
  current: number;
  pages: number;
  total: number;
  loading: boolean;
  onChangePage: (page: number) => void;
  onEditSkill: (skill: AdminSkill) => void;
  onPreviewSkill: (skill: AdminSkill) => void;
  onToggleEnabled: (skill: AdminSkill) => void;
  onDeleteSkill: (skill: AdminSkill) => void;
}) {
  // 对齐 Trace 管理加载体验：仅在首屏加载且无记录时渲染骨架行。
  const showSkeletonRows = loading && skills.length === 0;
  const columns: ColumnsType<AdminSkill> = [
    {
      title: '技能',
      key: 'skill',
      width: 280,
      render: (_, skill) => (
        <div>
          <Typography.Text strong>{skill.displayName}</Typography.Text>
          <Typography.Paragraph className="mb-0 line-clamp-2 text-[12px]" type="secondary">
            {skill.description || '暂无描述'}
          </Typography.Paragraph>
        </div>
      ),
    },
    {
      title: '编码',
      dataIndex: 'skillCode',
      width: 180,
      render: (value: string) => <Typography.Text className="font-data-mono text-[12px]" type="secondary">/{value}</Typography.Text>,
    },
    { title: '分类', dataIndex: 'category', width: 140, render: (value?: string) => value || '未分类' },
    { title: '来源', dataIndex: 'sourceType', width: 140, render: (value?: string) => <Tag>{value || 'built-in'}</Tag> },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 100,
      render: (value?: number) => value === 0 ? <Tag>禁用</Tag> : <Tag color="success">启用</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      align: 'right',
      fixed: 'right',
      width: 300,
      render: (_, skill) => (
        <AdminTableActions
          actions={[
            {
              key: 'toggle',
              label: skill.enabled === 0 ? '启用' : '禁用',
              ariaLabel: `${skill.enabled === 0 ? '启用' : '禁用'}技能 ${skill.skillCode}`,
              icon: <StopOutlined />,
              onClick: () => onToggleEnabled(skill),
            },
            {
              key: 'preview',
              label: '预览',
              ariaLabel: `资源预览 ${skill.skillCode}`,
              disabled: !skill.id || !skill.storageKey,
              icon: <EyeOutlined />,
              onClick: () => onPreviewSkill(skill),
            },
            {
              key: 'edit',
              label: '编辑',
              ariaLabel: `编辑技能 ${skill.skillCode}`,
              icon: <EditOutlined />,
              onClick: () => onEditSkill(skill),
            },
            {
              key: 'delete',
              label: '删除',
              ariaLabel: `删除技能 ${skill.skillCode}`,
              danger: true,
              icon: <DeleteOutlined />,
              onClick: () => onDeleteSkill(skill),
            },
          ]}
        />
      ),
    },
  ];

  return (
    <section className="rounded-xl border border-border-hairline bg-surface-container-lowest p-md shadow-sm">
      {showSkeletonRows
        ? Array.from({ length: 10 }, (_, index) => (
          <span key={`skills-loading-row-${index}`} data-testid="skills-loading-skeleton-row" className="sr-only" />
        ))
        : null}
      <AdminDataTable
        columns={columns}
        dataSource={skills}
        loading={loading}
        locale={{ emptyText: loading ? '技能加载中...' : '暂无技能数据' }}
        pagination={{
          current,
          pageSize: SKILL_PAGE_SIZE,
          total,
          onChange: onChangePage,
        }}
        rowKey={(skill) => String(skill.id ?? skill.skillCode)}
        scroll={{ x: 1160 }}
      />
    </section>
  );
}

function SkillCardView({
  skills,
  onEditSkill,
  onPreviewSkill,
  onToggleEnabled,
  onDeleteSkill,
}: {
  skills: AdminSkill[];
  onEditSkill: (skill: AdminSkill) => void;
  onPreviewSkill: (skill: AdminSkill) => void;
  onToggleEnabled: (skill: AdminSkill) => void;
  onDeleteSkill: (skill: AdminSkill) => void;
}) {
  if (skills.length === 0) {
    return (
      <Empty className="rounded-xl border border-border-hairline bg-surface-container-lowest px-4 py-6" description="暂无技能数据" />
    );
  }

  return (
    <div className="grid grid-cols-1 gap-md md:grid-cols-2 lg:grid-cols-3">
      {skills.map((skill) => (
        <Card
          key={skill.id ?? skill.skillCode}
          className="admin-skill-card"
          actions={[
            <Button
              key="toggle"
              aria-label={`${skill.enabled === 0 ? '启用' : '禁用'}技能 ${skill.skillCode}`}
              icon={<StopOutlined />}
              type="text"
              onClick={() => onToggleEnabled(skill)}
            >
              {skill.enabled === 0 ? '启用' : '禁用'}
            </Button>,
            <Button
              key="preview"
              aria-label={`资源预览 ${skill.skillCode}`}
              disabled={!skill.id || !skill.storageKey}
              icon={<EyeOutlined />}
              type="text"
              onClick={() => onPreviewSkill(skill)}
            >
              预览
            </Button>,
            <Button key="edit" aria-label={`编辑技能 ${skill.skillCode}`} icon={<EditOutlined />} type="text" onClick={() => onEditSkill(skill)}>
              编辑
            </Button>,
            <Button key="delete" aria-label={`删除技能 ${skill.skillCode}`} danger icon={<DeleteOutlined />} type="text" onClick={() => onDeleteSkill(skill)}>
              删除
            </Button>,
          ]}
        >
          <Card.Meta
            avatar={<Avatar icon={<AppstoreOutlined />} />}
            title={skill.displayName}
            description={(
              <Space direction="vertical" size={8}>
                <Typography.Paragraph className="mb-0 line-clamp-2" type="secondary">{skill.description || '暂无描述'}</Typography.Paragraph>
                <Space wrap size={6}>
                  <Tag>{skill.sourceType || 'built-in'}</Tag>
                  <Tag>{skill.category || '未分类'}</Tag>
                  <Tag color={skill.enabled === 0 ? undefined : 'success'}>{skill.enabled === 0 ? '禁用' : '启用'}</Tag>
                  <Typography.Text className="font-data-mono text-[12px]" type="secondary">/{skill.skillCode}</Typography.Text>
                </Space>
              </Space>
            )}
          />
        </Card>
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
        className="flex h-[90vh] max-h-[90vh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
      >
        <div className="flex items-start justify-between gap-md border-b border-border-hairline bg-surface-container-lowest px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">技能包资源预览</h3>
            <p className="mt-1 text-body-sm text-secondary">
              当前技能：{skill.displayName}（/{skill.skillCode}）
            </p>
            <p className="mt-1 text-[12px] text-secondary">对象键：{skill.storageKey || '未配置'}</p>
          </div>
          <Button
            aria-label="关闭资源预览"
            icon={<CloseOutlined />}
            onClick={onClose}
            type="text"
          />
        </div>

        <div
          data-testid="skill-package-preview-body"
          className="grid min-h-0 flex-1 grid-cols-1 gap-0 overflow-hidden lg:grid-cols-[340px_1fr]"
        >
          <aside className="flex min-h-0 flex-col border-r border-border-hairline bg-surface-container-low">
            <div className="shrink-0 border-b border-border-hairline px-md py-sm text-[12px] text-secondary">文件目录</div>
            <div
              data-testid="skill-package-tree-scroll"
              // 资源树文件数可能远多于预览区高度，独立滚动可避免左侧目录把弹窗整体撑开。
              className="skill-package-tree-scroll min-h-0 flex-1 overflow-y-scroll px-xs py-xs"
            >
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
                  <Button
                    key={row.path}
                    aria-label={row.directory ? `切换目录 ${row.name}` : `预览文件 ${row.name}`}
                    block
                    className={[
                      'h-auto w-full justify-start rounded-md px-sm py-1.5 text-left text-[12px] transition-colors',
                      selectedPath === row.path
                        ? 'bg-primary/15 text-primary'
                        : 'text-ink hover:bg-surface-container-lowest',
                    ].join(' ')}
                    icon={row.directory ? <FolderOpenOutlined /> : <FileTextOutlined />}
                    style={{ paddingLeft: `${8 + row.depth * 16}px` }}
                    type="text"
                    onClick={() => {
                      if (row.directory) {
                        toggleDirectory(row.path);
                        return;
                      }
                      void loadFileContent(row.path);
                    }}
                  >
                    <span className="truncate">{row.name}</span>
                    {!row.directory && row.size != null ? (
                      <span className="ml-auto shrink-0 font-data-mono text-[10px] text-secondary">{formatBytes(row.size)}</span>
                    ) : null}
                  </Button>
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
                  {fileContent.content.startsWith('data:image/') ? (
                    <div className="flex justify-center rounded-lg border border-border-hairline bg-surface-container p-md">
                      <img
                        src={fileContent.content}
                        alt={fileContent.path}
                        className="max-h-[600px] max-w-full object-contain"
                      />
                    </div>
                  ) : (
                    <pre className="min-h-[320px] whitespace-pre-wrap break-words rounded-lg border border-border-hairline bg-surface-container px-md py-md font-data-mono text-[12px] leading-relaxed text-ink">
                      {fileContent.content}
                    </pre>
                  )}
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
  const adminMessage = useAdminMessage();
  const [form, setForm] = React.useState<SkillFormState>(() => toSkillForm(skill));
  const [saving, setSaving] = React.useState(false);
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
    if (!validate()) {
      return;
    }

    setSaving(true);
    try {
      await onSubmit(toSkillPayload(form, skill));
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, mode === 'create' ? '新增技能失败' : '保存技能失败'));
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
          <Button
            aria-label="关闭技能弹窗"
            icon={<CloseOutlined />}
            onClick={onClose}
            type="text"
          />
        </div>

        <form className="space-y-lg p-lg" onSubmit={handleSubmit}>
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
              <Form.Item className="mb-0" label="启用状态">
                <Switch
                  checked={form.enabled}
                  checkedChildren="启用"
                  unCheckedChildren="停用"
                  onChange={(checked) => updateField('enabled', checked)}
                />
              </Form.Item>
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
            <Button
              onClick={onClose}
              disabled={saving}
            >
              取消
            </Button>
            <Button
              aria-label={mode === 'create' ? '创建技能' : '保存修改'}
              htmlType="submit"
              icon={<SaveOutlined />}
              loading={saving}
              disabled={saving}
              type="primary"
            >
              {mode === 'create' ? '创建技能' : '保存修改'}
            </Button>
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
  const adminMessage = useAdminMessage();
  const [file, setFile] = React.useState<File | null>(null);
  const [directoryFiles, setDirectoryFiles] = React.useState<File[]>([]);
  const [category, setCategory] = React.useState('');
  const [submitting, setSubmitting] = React.useState(false);
  const [migrating, setMigrating] = React.useState(false);
  const [showOverwriteConfirm, setShowOverwriteConfirm] = React.useState(false);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!file && directoryFiles.length === 0) {
      void adminMessage.warning('请选择技能包文件或文件夹');
      return;
    }
    await uploadSkill(false);
  };

  const uploadSkill = async (forceOverwrite: boolean) => {
    setSubmitting(true);
    try {
      await AdminChatApi.uploadSkillPackage(file, category, directoryFiles, forceOverwrite);
      await onUploaded();
      void adminMessage.success('技能包上传成功');
      onClose();
    } catch (error) {
      const errorMsg = extractErrorMessage(error, '技能包上传失败');
      // 检查是否是重名错误
      if (errorMsg.includes('技能存储键已存在') || errorMsg.includes('CHAT_SKILL_STORAGE_KEY_DUPLICATE')) {
        setShowOverwriteConfirm(true);
      } else {
        void adminMessage.error(errorMsg);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirmOverwrite = async () => {
    setShowOverwriteConfirm(false);
    await uploadSkill(true);
  };

  /**
   * 执行历史技能包迁移，并使用 AntD message 给出一次性摘要，避免在上传弹窗内堆叠反馈块。
   */
  const handleMigratePackages = async () => {
    setMigrating(true);
    try {
      const summary = await AdminChatApi.migrateSkillPackages();
      const failureCount = summary.failures?.length ?? 0;
      const firstFailure = failureCount > 0 ? `；失败示例：${summary.failures[0].skillCode}（${summary.failures[0].reason}）` : '';
      void adminMessage.success(`迁移完成：总计 ${summary.total}，成功 ${summary.migrated}，跳过 ${summary.skipped}，失败 ${failureCount}${firstFailure}`);
      await onUploaded();
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '历史技能包迁移失败'));
    } finally {
      setMigrating(false);
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
            <p className="mt-1 text-body-sm text-secondary">支持上传文件夹（推荐）或 zip/.skill 压缩包，均需包含根级 SKILL.md。</p>
          </div>
          <Button
            aria-label="关闭上传弹窗"
            icon={<CloseOutlined />}
            onClick={onClose}
            type="text"
          />
        </div>

        <form className="space-y-lg p-lg" onSubmit={handleSubmit}>
          <fieldset className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
            <legend className="px-xs font-title-sm text-ink">文件与分类</legend>
            <div className="space-y-md">
              <Form.Item className="mb-0" label="技能包文件">
                <Upload
                  accept=".zip,.skill"
                  beforeUpload={(selectedFile) => {
                    setFile(selectedFile as File);
                    setDirectoryFiles([]);
                    return false;
                  }}
                  fileList={file ? [{ uid: file.name, name: file.name, status: 'done' }] : []}
                  maxCount={1}
                  onRemove={() => {
                    setFile(null);
                  }}
                >
                  <Button aria-label="技能包文件" icon={<UploadOutlined />}>选择技能包文件</Button>
                </Upload>
                <p className="mt-1 text-[12px] text-secondary">
                  仅支持 .zip / .skill，且压缩包根目录必须包含 SKILL.md
                </p>
              </Form.Item>
              <Form.Item className="mb-0" label="技能文件夹">
                <Upload
                  beforeUpload={(_, selectedFiles) => {
                    const nextFiles = selectedFiles as File[];
                    setDirectoryFiles(nextFiles);
                    if (nextFiles.length > 0) {
                      setFile(null);
                    }
                    return false;
                  }}
                  directory
                  fileList={directoryFiles.map((directoryFile) => ({
                    uid: directoryFile.name,
                    name: directoryFile.name,
                    status: 'done',
                  }))}
                  multiple
                  onChange={(info) => {
                    const selectedFiles = info.fileList
                      .map((item) => item.originFileObj)
                      .filter((item): item is File => Boolean(item));
                    setDirectoryFiles(selectedFiles);
                    if (selectedFiles.length > 0) {
                      setFile(null);
                    }
                  }}
                  onRemove={(removedFile) => {
                    setDirectoryFiles((previous) => previous.filter((item) => item.name !== removedFile.name));
                  }}
                >
                  <Button aria-label="技能文件夹" icon={<FolderOpenOutlined />}>选择技能文件夹</Button>
                </Upload>
                <p className="mt-1 text-[12px] text-secondary">
                  优先推荐上传文件夹，文件夹根目录需包含 SKILL.md
                </p>
              </Form.Item>
              <TextField
                id="skill-upload-category"
                label="分类（可选）"
                value={category}
                onChange={(value) => setCategory(value)}
              />
            </div>
          </fieldset>

          <div className="flex flex-wrap items-center justify-between gap-sm">
            <Button
              aria-label="迁移历史技能包"
              disabled={submitting || migrating}
              icon={<SyncOutlined />}
              loading={migrating}
              onClick={() => {
                void handleMigratePackages();
              }}
            >
              迁移历史技能包
            </Button>
            <Button
              onClick={onClose}
              disabled={submitting}
            >
              取消
            </Button>
            <Button
              aria-label="确认上传"
              htmlType="submit"
              disabled={submitting}
              icon={<UploadOutlined />}
              loading={submitting}
              type="primary"
            >
              确认上传
            </Button>
          </div>
        </form>
      </div>

      {showOverwriteConfirm ? (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-ink/45 px-md py-lg">
          <div
            role="dialog"
            aria-modal="true"
            aria-label="确认覆盖同名技能"
            className="w-full max-w-md overflow-hidden rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl"
          >
            <div className="border-b border-border-hairline bg-surface-container-lowest px-lg py-md">
              <h3 className="font-title-md text-title-md text-ink">检测到同名技能</h3>
            </div>

            <div className="space-y-md p-lg">
              <p className="text-body-md text-ink">
                已存在同名的技能存储键，是否继续上传？
              </p>
              <p className="text-body-sm text-secondary">
                选择继续将在存储键后添加时间戳后缀以避免冲突。
              </p>
            </div>

            <div className="flex justify-end gap-sm border-t border-border-hairline bg-surface-container-low px-lg py-md">
              <Button
                onClick={() => setShowOverwriteConfirm(false)}
                disabled={submitting}
              >
                取消
              </Button>
              <Button
                onClick={handleConfirmOverwrite}
                disabled={submitting}
                loading={submitting}
                type="primary"
              >
                继续上传
              </Button>
            </div>
          </div>
        </div>
      ) : null}
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
    <Form.Item
      className="mb-0"
      help={error}
      htmlFor={id}
      label={label}
      validateStatus={error ? 'error' : undefined}
    >
      <Input
        id={id}
        type={type}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
      />
    </Form.Item>
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
    <Form.Item className="mb-0" htmlFor={id} label={label}>
      <Input.TextArea
        id={id}
        rows={rows}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </Form.Item>
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

interface DeleteConfirmDialogProps {
  skill: AdminSkill;
  onClose: () => void;
  onConfirm: () => void;
}

/**
 * 删除确认对话框。
 */
function DeleteConfirmDialog({ skill, onClose, onConfirm }: DeleteConfirmDialogProps) {
  const [deleting, setDeleting] = React.useState(false);

  const handleConfirm = async () => {
    setDeleting(true);
    try {
      await onConfirm();
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Modal
      centered
      closeIcon={<CloseOutlined aria-hidden="true" />}
      cancelText="取消"
      maskTransitionName=""
      okButtonProps={{ danger: true, icon: <DeleteOutlined />, 'aria-label': '确认删除', loading: deleting }}
      okText="确认删除"
      open
      title="删除技能确认"
      transitionName=""
      onCancel={onClose}
      onOk={() => void handleConfirm()}
    >
      <div className="space-y-md">
        <Typography.Paragraph className="mb-0">
          确定要删除技能 <span className="font-medium text-primary">{skill.displayName}</span> 吗？
        </Typography.Paragraph>
        <Typography.Paragraph className="mb-0" type="secondary">
          此操作将物理删除技能记录，并删除 RustFS 中的所有相关文件，无法恢复。
        </Typography.Paragraph>
        {skill.storageKey ? (
          <Typography.Paragraph className="rounded-lg border border-border-hairline bg-surface-container-low px-sm py-sm font-data-mono text-[11px]" type="secondary">
            存储键: {skill.storageKey}
          </Typography.Paragraph>
        ) : null}
      </div>
    </Modal>
  );
}
