import React from 'react';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import {
  AdminChatApi,
  AdminPageResult,
  AdminQueryTermMapping,
  AdminQueryTermMappingPayload,
} from '../api/adminChatApi';
import { AdminDataTable, AdminTableActions } from '../components/AdminDataTable';

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
 * 关键词映射管理页：配置查询归一化映射规则，支持分页检索与 CRUD。
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

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(searchKeyword.trim());
  };

  const handleRefresh = () => {
    setPageNo(1);
    void loadData(1, keyword);
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

  const columns = React.useMemo<ColumnsType<AdminQueryTermMapping>>(() => [
    { title: '原始词', dataIndex: 'sourceTerm', width: 160, ellipsis: true },
    { title: '目标词', dataIndex: 'targetTerm', width: 180, ellipsis: true },
    {
      title: '匹配类型',
      dataIndex: 'matchType',
      width: 130,
      render: (value?: number) => <Tag>{MATCH_TYPE_OPTIONS.find((item) => item.value === value)?.label || `类型${value ?? 1}`}</Tag>,
    },
    { title: '优先级', dataIndex: 'priority', width: 100 },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 100,
      render: (value?: boolean) => value === false ? <Tag>禁用</Tag> : <Tag color="success">启用</Tag>,
    },
    { title: '备注', dataIndex: 'remark', width: 220, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatDate },
    { title: '更新时间', dataIndex: 'updateTime', width: 180, render: formatDate },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 160,
      align: 'right',
      render: (_, item) => (
        <AdminTableActions
          actions={[
            {
              key: 'edit',
              label: '编辑',
              ariaLabel: `编辑 ${item.sourceTerm}`,
              icon: <EditOutlined />,
              testId: `mapping-edit-${item.id}`,
              onClick: () => openEditDialog(item),
            },
            {
              key: 'delete',
              label: '删除',
              ariaLabel: `删除 ${item.sourceTerm}`,
              danger: true,
              icon: <DeleteOutlined />,
              testId: `mapping-delete-${item.id}`,
              onClick: () => setDeleteTarget(item),
            },
          ]}
        />
      ),
    },
  ], []);

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <Typography.Title level={2} style={{ margin: 0 }}>关键词映射管理</Typography.Title>
          <Typography.Text type="secondary">配置查询归一化映射规则，支持分页检索与完整 CRUD 操作</Typography.Text>
        </div>
        <Space wrap>
          <Input
            allowClear
            data-testid="mapping-search-input"
            prefix={<SearchOutlined />}
            value={searchKeyword}
            placeholder="搜索原始词/目标词"
            onChange={(event) => setSearchKeyword(event.target.value)}
            onPressEnter={handleSearch}
          />
          <Button data-testid="mapping-search-btn" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
          <Button data-testid="mapping-refresh-btn" icon={<ReloadOutlined />} onClick={handleRefresh}>刷新</Button>
          <Button data-testid="mapping-create-btn" icon={<PlusOutlined />} type="primary" onClick={openCreateDialog}>新增映射</Button>
        </Space>
      </header>

      {errorMessage ? <Alert showIcon type="error" message={errorMessage} /> : null}

      <section className="rounded-xl border border-border-hairline bg-surface-container-lowest p-md shadow-sm">
        <span data-testid="mapping-total" className="sr-only">{pageData ? `共 ${pageData.total} 条` : '共 0 条'}</span>
        <AdminDataTable<AdminQueryTermMapping>
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: loading ? '加载中...' : '暂无映射规则' }}
          pagination={{
            current: pageData?.current ?? pageNo,
            pageSize: PAGE_SIZE,
            total: pageData?.total ?? 0,
            onChange: (nextPage) => setPageNo(nextPage),
          }}
          rowKey={(item) => String(item.id)}
          scroll={{ x: 1080 }}
        />
      </section>

      <MappingEditDialog
        mode={dialogMode}
        form={form}
        fieldErrors={fieldErrors}
        formError={formError}
        open={dialogOpen}
        saving={saving}
        onChange={updateField}
        onClose={() => setDialogOpen(false)}
        onSubmit={() => void handleSubmit()}
      />

      <Modal
        destroyOnHidden
        confirmLoading={deleting}
        okButtonProps={{ danger: true, 'data-testid': 'mapping-delete-confirm-btn' } as any}
        okText="确认删除"
        open={Boolean(deleteTarget)}
        title="删除映射规则"
        modalRender={(node) => <div data-testid="mapping-delete-dialog">{node}</div>}
        onCancel={() => setDeleteTarget(null)}
        onOk={() => void handleDelete()}
      >
        <Typography.Paragraph>
          确定删除“{deleteTarget?.sourceTerm} -&gt; {deleteTarget?.targetTerm}”映射规则吗？删除后将立即失效。
        </Typography.Paragraph>
      </Modal>
    </div>
  );
}

function MappingEditDialog({
  mode,
  form,
  fieldErrors,
  formError,
  open,
  saving,
  onClose,
  onChange,
  onSubmit,
}: {
  mode: MappingDialogMode;
  form: MappingFormState;
  fieldErrors: Record<string, string>;
  formError: string;
  open: boolean;
  saving: boolean;
  onClose: () => void;
  onChange: (field: keyof MappingFormState, value: string | number | boolean) => void;
  onSubmit: () => void;
}) {
  return (
    <Modal
      destroyOnHidden
      confirmLoading={saving}
      footer={(
        <Space>
          <Button disabled={saving} onClick={onClose}>取消</Button>
          <Button data-testid="mapping-save-btn" icon={<SaveOutlined />} loading={saving} type="primary" onClick={onSubmit}>
            保存
          </Button>
        </Space>
      )}
      open={open}
      title={mode === 'create' ? '新增映射规则' : '编辑映射规则'}
      modalRender={(node) => <div data-testid="mapping-edit-dialog">{node}</div>}
      onCancel={onClose}
    >
      <div>
        {formError ? <Alert className="mb-md" showIcon type="error" message={formError} /> : null}
        <Form layout="vertical">
          <Form.Item label="原始词" required validateStatus={fieldErrors.sourceTerm ? 'error' : undefined} help={fieldErrors.sourceTerm}>
            <Input
              data-testid="mapping-source-term-input"
              value={form.sourceTerm}
              onChange={(event) => onChange('sourceTerm', event.target.value)}
            />
          </Form.Item>
          <Form.Item label="目标词" required validateStatus={fieldErrors.targetTerm ? 'error' : undefined} help={fieldErrors.targetTerm}>
            <Input
              data-testid="mapping-target-term-input"
              value={form.targetTerm}
              onChange={(event) => onChange('targetTerm', event.target.value)}
            />
          </Form.Item>
          <Form.Item label="匹配类型">
            <Select
              data-testid="mapping-match-type-select"
              value={form.matchType}
              options={MATCH_TYPE_OPTIONS}
              onChange={(value) => onChange('matchType', value)}
            />
          </Form.Item>
          <Form.Item label="优先级" validateStatus={fieldErrors.priority ? 'error' : undefined} help={fieldErrors.priority}>
            <InputNumber
              className="w-full"
              data-testid="mapping-priority-input"
              value={form.priority}
              onChange={(value) => onChange('priority', Number(value ?? 0))}
            />
          </Form.Item>
          <Form.Item label="状态">
            <Select
              data-testid="mapping-enabled-select"
              value={String(form.enabled)}
              options={[
                { value: 'true', label: '启用' },
                { value: 'false', label: '禁用' },
              ]}
              onChange={(value) => onChange('enabled', value === 'true')}
            />
          </Form.Item>
          <Form.Item label="备注">
            <Input
              data-testid="mapping-remark-input"
              value={form.remark}
              onChange={(event) => onChange('remark', event.target.value)}
            />
          </Form.Item>
        </Form>
      </div>
    </Modal>
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
