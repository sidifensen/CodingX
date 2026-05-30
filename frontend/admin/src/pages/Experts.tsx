import React, { useState } from 'react';
import { DeleteOutlined, EditOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import { Alert, Button, Form, Input, InputNumber, Modal, Space, Switch, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { AdminChatApi, AdminExpert, AdminPageResult } from '../api/adminChatApi';
import { AdminDataTable, AdminTableActions } from '../components/AdminDataTable';

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

  const columns = React.useMemo<ColumnsType<AdminExpert>>(() => [
    {
      title: '专家',
      dataIndex: 'displayName',
      width: 260,
      render: (_, expert) => (
        <div>
          <Typography.Text strong>{expert.displayName}</Typography.Text>
          <Typography.Paragraph className="mb-0" ellipsis={{ rows: 2 }} type="secondary">
            {expert.description || '暂无描述'}
          </Typography.Paragraph>
        </div>
      ),
    },
    {
      title: '编码',
      dataIndex: 'expertCode',
      width: 180,
      render: (value: string) => <Typography.Text code>/{value}</Typography.Text>,
    },
    {
      title: '分类',
      dataIndex: 'category',
      width: 140,
      render: (value?: string) => value || '未分类',
    },
    {
      title: '示例问题',
      dataIndex: 'presetQuestion',
      width: 320,
      ellipsis: true,
      render: (value?: string) => value || '未配置',
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 110,
      render: (value: number) => value === 0 ? <Tag>禁用</Tag> : <Tag color="success">启用</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 180,
      align: 'right',
      render: (_, expert) => (
        <AdminTableActions
          actions={[
            {
              key: 'edit',
              label: '编辑',
              ariaLabel: `编辑专家 ${expert.expertCode}`,
              icon: <EditOutlined />,
              onClick: () => {
                setDialogMode('edit');
                setEditingExpert(expert);
                setDialogOpen(true);
              },
            },
            {
              key: 'delete',
              label: '删除',
              ariaLabel: `删除专家 ${expert.expertCode}`,
              danger: true,
              icon: <DeleteOutlined />,
              onClick: async () => {
                if (expert.id == null) {
                  return;
                }
                await AdminChatApi.deleteExpert(expert.id);
                await loadExperts(pageNo);
              },
            },
          ]}
        />
      ),
    },
  ], [loadExperts, pageNo]);

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-wrap items-end justify-between gap-md">
        <div>
          <Typography.Title level={2} style={{ margin: 0 }}>专家管理 (Experts)</Typography.Title>
          <Typography.Text type="secondary">维护聊天专家角色、示例问题与系统提示词。</Typography.Text>
        </div>
        <Button
          aria-label="创建新专家"
          icon={<PlusOutlined />}
          type="primary"
          onClick={() => {
            setDialogMode('create');
            setEditingExpert(null);
            setDialogOpen(true);
          }}
        >
          创建新专家
        </Button>
      </header>

      {errorMessage ? <Alert showIcon type="error" message={errorMessage} /> : null}

      <AdminDataTable<AdminExpert>
        columns={columns}
        dataSource={experts}
        loading={isLoading}
        locale={{ emptyText: isLoading ? '专家加载中...' : '暂无专家数据' }}
        pagination={{
          current,
          pageSize: EXPERT_PAGE_SIZE,
          total,
          onChange: (nextPage) => setPageNo(nextPage),
        }}
        rowKey={(expert) => String(expert.id ?? expert.expertCode)}
        scroll={{ x: 1280 }}
      />

      <ExpertEditDialog
        mode={dialogMode}
        expert={editingExpert}
        open={dialogOpen}
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
    </div>
  );
}

function ExpertEditDialog({
  mode,
  expert,
  open,
  onClose,
  onSubmit,
}: {
  mode: ExpertDialogMode;
  expert: AdminExpert | null;
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: AdminExpert) => Promise<void>;
}) {
  const [form, setForm] = React.useState<ExpertFormState>(() => toExpertForm(expert));
  const [saving, setSaving] = React.useState(false);
  const [formError, setFormError] = React.useState('');

  React.useEffect(() => {
    if (open) {
      setForm(toExpertForm(expert));
      setFormError('');
    }
  }, [expert, open]);

  const updateField = (field: keyof ExpertFormState, value: string | boolean) => {
    setForm((previous) => ({ ...previous, [field]: value }));
  };

  const handleSubmit = async () => {
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
    <Modal
      destroyOnHidden
      className="overflow-hidden"
      footer={(
        <Space>
          <Button disabled={saving} onClick={onClose}>取消</Button>
          <Button
            aria-label={mode === 'create' ? '创建专家' : '保存修改'}
            icon={<SaveOutlined />}
            loading={saving}
            type="primary"
            onClick={handleSubmit}
          >
            {mode === 'create' ? '创建专家' : '保存修改'}
          </Button>
        </Space>
      )}
      open={open}
      title={mode === 'create' ? '新增专家' : '编辑专家'}
      width={760}
      onCancel={onClose}
    >
      <Typography.Paragraph type="secondary">维护专家编码、示例问题和提示词。</Typography.Paragraph>
      <div data-testid="expert-edit-dialog-body" className="max-h-[60vh] overflow-y-auto overscroll-y-contain pr-sm">
        {formError ? <Alert className="mb-md" showIcon type="error" message={formError} /> : null}
        <Form layout="vertical">
          <div className="grid gap-md md:grid-cols-2">
            <Form.Item label="专家编码" required>
              <Input
                aria-label="专家编码"
                disabled={mode === 'edit'}
                value={form.expertCode}
                onChange={(event) => updateField('expertCode', event.target.value)}
              />
            </Form.Item>
            <Form.Item label="专家名称" required>
              <Input
                aria-label="专家名称"
                value={form.displayName}
                onChange={(event) => updateField('displayName', event.target.value)}
              />
            </Form.Item>
            <Form.Item label="分类">
              <Input aria-label="分类" value={form.category} onChange={(event) => updateField('category', event.target.value)} />
            </Form.Item>
            <Form.Item label="排序">
              <InputNumber
                aria-label="排序"
                className="w-full"
                value={Number(form.sortNo)}
                onChange={(value) => updateField('sortNo', String(value ?? 0))}
              />
            </Form.Item>
            <Form.Item label="标签JSON">
              <Input aria-label="标签JSON" value={form.tagsJson} onChange={(event) => updateField('tagsJson', event.target.value)} />
            </Form.Item>
            <Form.Item label="启用专家">
              <Switch checked={form.enabled} onChange={(checked) => updateField('enabled', checked)} />
            </Form.Item>
          </div>
          <Form.Item label="专家描述">
            <Input.TextArea
              aria-label="专家描述"
              rows={3}
              value={form.description}
              onChange={(event) => updateField('description', event.target.value)}
            />
          </Form.Item>
          <Form.Item label="示例问题">
            <Input.TextArea
              aria-label="示例问题"
              rows={3}
              value={form.presetQuestion}
              onChange={(event) => updateField('presetQuestion', event.target.value)}
            />
          </Form.Item>
          <Form.Item label="系统提示词" required>
            <Input.TextArea
              aria-label="系统提示词"
              rows={8}
              value={form.systemPrompt}
              onChange={(event) => updateField('systemPrompt', event.target.value)}
            />
          </Form.Item>
        </Form>
      </div>
    </Modal>
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
