import React from 'react';
import {
  ApiOutlined,
  BranchesOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  ExperimentOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  StopOutlined,
  ToolOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Badge,
  Button,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';

import {
  AdminChatApi,
  AdminChatTool,
  AdminChatToolHealthView,
  AdminChatToolInvokeView,
} from '../api/adminChatApi';
import { AdminDataTable, AdminTableActions } from '../components/AdminDataTable';
import { useAdminMessage } from '../components/AdminMessageContext';
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
 * 管理端工具管理页：独立维护 chat_tool 表，并合并执行器健康状态展示。
 */
export function ToolsPage() {
  const adminMessage = useAdminMessage();
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
    try {
      const result = await AdminChatApi.invokeTool(invokeDialogToolCode, invokeQuestion.trim() || undefined);
      setInvokeResult(result);
      await loadToolHealthViews();
      void adminMessage.success(result.message || '工具调用成功');
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '调用工具失败'));
    } finally {
      setInvoking(false);
    }
  };

  const handleToggleToolEnabled = async (tool: AdminChatTool) => {
    if (tool.id == null) {
      void adminMessage.error('工具配置缺少主键，无法切换状态');
      return;
    }
    try {
      await AdminChatApi.updateTool(tool.id, {
        ...tool,
        enabled: tool.enabled === 0 ? 1 : 0,
      });
      await Promise.all([loadTools(), loadToolHealthViews()]);
      void adminMessage.success(tool.enabled === 0 ? '工具已启用' : '工具已禁用');
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, tool.enabled === 0 ? '启用工具失败' : '禁用工具失败'));
    }
  };

  const columns = React.useMemo<ColumnsType<UnifiedToolRow>>(() => [
    {
      title: '编码',
      dataIndex: 'toolCode',
      width: 180,
      fixed: 'left',
      render: (value: string) => <Typography.Text className="font-data-mono text-[12px]" type="secondary">/{value}</Typography.Text>,
    },
    {
      title: '名称',
      key: 'displayName',
      width: 180,
      render: (_, row) => row.config?.displayName || row.health?.displayName || row.toolCode,
    },
    {
      title: '分类',
      key: 'category',
      width: 130,
      render: (_, row) => row.config?.category || row.health?.category || '-',
    },
    {
      title: '来源',
      key: 'source',
      width: 140,
      render: (_, row) => <Tag>{row.config?.sourceType || row.health?.source || '-'}</Tag>,
    },
    {
      title: '状态',
      key: 'enabled',
      width: 100,
      render: (_, row) => row.config?.enabled === 0 ? <Tag>停用</Tag> : <Tag color="success">启用</Tag>,
    },
    {
      title: '执行器',
      key: 'health',
      width: 130,
      render: (_, row) => (
        <Badge
          color={healthBadgeColor(row.health?.status)}
          text={<span className="text-body-sm">{row.health?.statusLabel || '未接入'}</span>}
        />
      ),
    },
    {
      title: '样例问题',
      key: 'sampleQuestion',
      width: 260,
      ellipsis: true,
      render: (_, row) => row.health?.sampleQuestion || '-',
    },
    {
      title: '最近探测',
      key: 'checkedAt',
      width: 170,
      render: (_, row) => row.health?.checkedAt || '-',
    },
    {
      title: '排序',
      key: 'sortNo',
      width: 90,
      render: (_, row) => row.config?.sortNo ?? 0,
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 360,
      align: 'right',
      render: (_, row) => {
        const tool = row.config;
        const toolCode = row.toolCode;
        return (
          <AdminTableActions
            actions={[
              {
                key: 'ping',
                label: pingingToolCode === toolCode ? '探测中' : '探测',
                ariaLabel: `测试工具 ${toolCode}`,
                icon: <ExperimentOutlined />,
                loading: pingingToolCode === toolCode,
                onClick: () => void handlePing(toolCode),
              },
              {
                key: 'invoke',
                label: '调用',
                ariaLabel: `调用工具 ${toolCode}`,
                icon: <PlayCircleOutlined />,
                onClick: () => openInvokeDialog(toolCode, row.health?.sampleQuestion),
              },
              {
                key: 'toggle',
                label: tool?.enabled === 0 ? '启用' : '禁用',
                ariaLabel: `${tool?.enabled === 0 ? '启用' : '禁用'}工具 ${toolCode}`,
                disabled: !tool,
                icon: tool?.enabled === 0 ? <CheckCircleOutlined /> : <StopOutlined />,
                onClick: () => (tool ? void handleToggleToolEnabled(tool) : undefined),
              },
              {
                key: 'edit',
                label: '编辑',
                ariaLabel: `编辑工具 ${toolCode}`,
                disabled: !tool,
                icon: <EditOutlined />,
                onClick: () => (tool ? openEditDialog(tool) : undefined),
              },
              {
                key: 'delete',
                label: '删除',
                ariaLabel: `删除工具 ${toolCode}`,
                danger: true,
                disabled: !tool,
                icon: <DeleteOutlined />,
                onClick: () => (tool ? setDeleteTarget(tool) : undefined),
              },
            ]}
          />
        );
      },
    },
  ], [pingingToolCode]);

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <Typography.Title level={2} style={{ margin: 0 }}>工具管理</Typography.Title>
          <Typography.Text type="secondary">独立维护 chat_tool 工具目录，预置 Codex CLI 工具清单。</Typography.Text>
        </div>
        <Space wrap>
          <Space.Compact>
            <Button
              aria-label="列表视图"
              aria-pressed={viewMode === 'list'}
              icon={<UnorderedListOutlined />}
              type={viewMode === 'list' ? 'primary' : 'default'}
              onClick={() => setViewMode('list')}
            >
              列表视图
            </Button>
            <Button
              aria-label="意图树视图"
              aria-pressed={viewMode === 'intentTree'}
              icon={<BranchesOutlined />}
              type={viewMode === 'intentTree' ? 'primary' : 'default'}
              onClick={() => setViewMode('intentTree')}
            >
              意图树视图
            </Button>
          </Space.Compact>
          <Button
            aria-label="刷新工具列表"
            icon={<ReloadOutlined />}
            onClick={() => void Promise.all([loadTools(), loadToolHealthViews()])}
          >
            刷新列表
          </Button>
          <Button aria-label="新增工具配置" icon={<PlusOutlined />} type="primary" onClick={openCreateDialog}>
            新增工具配置
          </Button>
        </Space>
      </header>

      {configErrorMessage || healthErrorMessage ? (
        <Alert showIcon type="error" message={configErrorMessage || healthErrorMessage} />
      ) : null}

      {viewMode === 'list' ? (
        <section className="rounded-xl border border-border-hairline bg-surface-container-lowest p-md shadow-sm">
          {showSkeletonRows
            ? Array.from({ length: 10 }, (_, index) => (
              <span key={`tools-loading-row-${index}`} data-testid="tools-loading-skeleton-row" className="sr-only" />
            ))
            : null}
          <AdminDataTable<UnifiedToolRow>
            columns={columns}
            dataSource={pagedRows}
            loading={isTableLoading}
            locale={{ emptyText: isTableLoading ? '加载中...' : '暂无工具配置' }}
            pagination={{
              current: safePageNo,
              pageSize: TOOL_TABLE_PAGE_SIZE,
              total: totalRows,
              onChange: (nextPage) => setPageNo(nextPage),
            }}
            rowKey={(row) => row.toolCode}
            scroll={{ x: 1420 }}
          />
        </section>
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

      <ToolEditDialog
        mode={dialogMode}
        open={dialogOpen}
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
          void adminMessage.success(dialogMode === 'edit' ? '工具配置已保存' : '工具配置已创建');
        }}
      />

      <DeleteToolDialog
        tool={deleteTarget}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={async () => {
          if (deleteTarget?.id == null) {
            void adminMessage.error('工具配置缺少主键，无法删除');
            setDeleteTarget(null);
            return;
          }
          try {
            await AdminChatApi.deleteTool(deleteTarget.id);
            setDeleteTarget(null);
            await Promise.all([loadTools(), loadToolHealthViews()]);
            void adminMessage.success('工具配置已删除');
          } catch (error) {
            void adminMessage.error(extractErrorMessage(error, '删除工具配置失败'));
          }
        }}
      />

      <PingResultDialog result={pingResult} onClose={() => setPingResult(null)} />

      <InvokeToolDialog
        toolCode={invokeDialogToolCode}
        question={invokeQuestion}
        invoking={invoking}
        result={invokeResult}
        onChangeQuestion={setInvokeQuestion}
        onClose={() => {
          setInvokeDialogToolCode(null);
          setInvokeQuestion('');
          setInvokeResult(null);
        }}
        onInvoke={() => void handleInvoke()}
      />
    </div>
  );
}

interface ToolEditDialogProps {
  mode: ToolDialogMode;
  open: boolean;
  tool: AdminChatTool | null;
  onClose: () => void;
  onSubmit: (payload: AdminChatTool) => Promise<void>;
}

/**
 * 工具配置编辑弹窗：使用 AntD Form 控件承载校验与暗色主题样式。
 */
function ToolEditDialog({ mode, open, tool, onClose, onSubmit }: ToolEditDialogProps) {
  const adminMessage = useAdminMessage();
  const [form, setForm] = React.useState<ToolFormState>(() => toToolForm(tool));
  const [saving, setSaving] = React.useState(false);
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});

  React.useEffect(() => {
    if (open) {
      setForm(toToolForm(tool));
      setFieldErrors({});
    }
  }, [open, tool]);

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

  const handleSubmit = async () => {
    if (!validate()) {
      return;
    }

    setSaving(true);
    try {
      await onSubmit(toToolPayload(form, tool));
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, mode === 'create' ? '新增工具配置失败' : '保存工具配置失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      destroyOnHidden
      confirmLoading={saving}
      footer={(
        <Space>
          <Button disabled={saving} onClick={onClose}>取消</Button>
          <Button
            aria-label={mode === 'create' ? '创建配置' : '保存修改'}
            icon={<SaveOutlined />}
            loading={saving}
            type="primary"
            onClick={() => void handleSubmit()}
          >
            {mode === 'create' ? '创建配置' : '保存修改'}
          </Button>
        </Space>
      )}
      open={open}
      title={mode === 'create' ? '新增工具配置' : '编辑工具配置'}
      onCancel={onClose}
    >
      <Form layout="vertical">
        <div className="grid gap-md md:grid-cols-2">
          <Form.Item htmlFor="tool-code" label="工具编码" required validateStatus={fieldErrors.toolCode ? 'error' : undefined} help={fieldErrors.toolCode}>
            <Input
              id="tool-code"
              value={form.toolCode}
              disabled={mode === 'edit'}
              onChange={(event) => updateField('toolCode', event.target.value)}
            />
          </Form.Item>
          <Form.Item htmlFor="tool-display-name" label="工具名称" required validateStatus={fieldErrors.displayName ? 'error' : undefined} help={fieldErrors.displayName}>
            <Input
              id="tool-display-name"
              value={form.displayName}
              onChange={(event) => updateField('displayName', event.target.value)}
            />
          </Form.Item>
          <Form.Item htmlFor="tool-category" label="分类">
            <Input id="tool-category" value={form.category} onChange={(event) => updateField('category', event.target.value)} />
          </Form.Item>
          <Form.Item htmlFor="tool-source-type" label="来源类型">
            <Input id="tool-source-type" value={form.sourceType} onChange={(event) => updateField('sourceType', event.target.value)} />
          </Form.Item>
          <Form.Item htmlFor="tool-sort" label="排序">
            <InputNumber
              className="w-full"
              id="tool-sort"
              value={Number(form.sortNo)}
              onChange={(value) => updateField('sortNo', String(value ?? 0))}
            />
          </Form.Item>
          <Form.Item label="启用工具">
            <Switch
              checked={form.enabled}
              checkedChildren="启用"
              unCheckedChildren="停用"
              onChange={(checked) => updateField('enabled', checked)}
            />
          </Form.Item>
        </div>
        <Form.Item htmlFor="tool-description" label="工具说明">
          <Input.TextArea
            id="tool-description"
            rows={4}
            value={form.description}
            onChange={(event) => updateField('description', event.target.value)}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function DeleteToolDialog({
  tool,
  onCancel,
  onConfirm,
}: {
  tool: AdminChatTool | null;
  onCancel: () => void;
  onConfirm: () => Promise<void>;
}) {
  return (
    <Modal
      destroyOnHidden
      okButtonProps={{ danger: true, icon: <DeleteOutlined />, 'aria-label': '确认删除' } as any}
      okText="确认删除"
      open={Boolean(tool)}
      title="删除工具配置"
      onCancel={onCancel}
      onOk={() => void onConfirm()}
    >
      <Typography.Paragraph>
        确认删除工具配置「{tool?.displayName}」吗？删除后该工具将不在管理端目录展示。
      </Typography.Paragraph>
    </Modal>
  );
}

function PingResultDialog({ result, onClose }: { result: AdminChatToolHealthView | null; onClose: () => void }) {
  return (
    <Modal
      destroyOnHidden
      footer={<Button aria-label="关闭探测结果弹窗" icon={<CheckCircleOutlined />} onClick={onClose}>知道了</Button>}
      maskTransitionName=""
      open={Boolean(result)}
      title="工具探测结果"
      transitionName=""
      onCancel={onClose}
    >
      {result ? (
        <Descriptions
          bordered
          column={1}
          size="small"
          items={[
            { key: 'toolCode', label: '工具', children: result.toolCode },
            { key: 'status', label: '状态', children: result.statusLabel || '-' },
            { key: 'message', label: '消息', children: result.message || '无返回消息' },
            { key: 'duration', label: '耗时', children: typeof result.durationMs === 'number' ? `${result.durationMs} ms` : '-' },
            { key: 'checkedAt', label: '时间', children: result.checkedAt || '-' },
          ]}
        />
      ) : null}
    </Modal>
  );
}

function InvokeToolDialog({
  toolCode,
  question,
  invoking,
  result,
  onChangeQuestion,
  onClose,
  onInvoke,
}: {
  toolCode: string | null;
  question: string;
  invoking: boolean;
  result: AdminChatToolInvokeView | null;
  onChangeQuestion: (value: string) => void;
  onClose: () => void;
  onInvoke: () => void;
}) {
  return (
    <Modal
      destroyOnHidden
      footer={(
        <Space>
          <Button onClick={onClose}>关闭</Button>
          <Button aria-label="开始调用" icon={<PlayCircleOutlined />} loading={invoking} type="primary" onClick={onInvoke}>
            开始调用
          </Button>
        </Space>
      )}
      open={Boolean(toolCode)}
      title="调用工具"
      width={760}
      onCancel={onClose}
    >
      <Space className="w-full" direction="vertical" size={16}>
        <Typography.Text className="font-data-mono" type="secondary">{toolCode}</Typography.Text>
        <Form layout="vertical">
          <Form.Item htmlFor="tool-invoke-question" label="调用参数（自然语言或 JSON）">
            <Input.TextArea
              id="tool-invoke-question"
              rows={5}
              value={question}
              onChange={(event) => onChangeQuestion(event.target.value)}
            />
          </Form.Item>
        </Form>
        {result ? (
          <Descriptions
            bordered
            column={1}
            size="small"
            items={[
              { key: 'status', label: '状态', children: result.statusLabel || '-' },
              { key: 'message', label: '消息', children: result.message || '-' },
              { key: 'content', label: '输出', children: result.content || '-' },
              { key: 'metadata', label: '元数据', children: <pre className="m-0 whitespace-pre-wrap">{JSON.stringify(result.metadata ?? {}, null, 2)}</pre> },
            ]}
          />
        ) : null}
      </Space>
    </Modal>
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

function healthBadgeColor(status?: string): string {
  switch (status) {
    case 'healthy':
      return 'var(--color-status-running)';
    case 'degraded':
      return 'var(--color-status-pending)';
    default:
      return 'var(--color-status-failed)';
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
