import React from 'react';
import {
  AuditOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';

import {
  AdminChatApi,
  type AdminGovernanceHookAudit,
  type AdminGovernanceHookRule,
  type AdminGovernancePermissionAudit,
  type AdminGovernancePermissionPolicy,
  type AdminGovernanceProjectProfile,
  type AdminGovernanceSlashCommand,
} from '../api/adminChatApi';
import { AdminDataTable, AdminTableActions } from '../components/AdminDataTable';
import { useAdminMessage } from '../components/AdminMessageContext';

type GovernanceTabKey = 'permission' | 'hook' | 'profile' | 'command' | 'audit';
type DialogMode = 'create' | 'edit';

interface PolicyFormState {
  policyCode: string;
  policyName: string;
  toolCode: string;
  commandPattern: string;
  pathPattern: string;
  action: string;
  riskLevel: string;
  description: string;
  enabled: boolean;
  sortNo: number;
}

interface HookFormState {
  hookCode: string;
  hookName: string;
  triggerPoint: string;
  conditionKeyword: string;
  actionType: string;
  actionConfigJson: string;
  enabled: boolean;
  sortNo: number;
}

interface CommandFormState {
  commandCode: string;
  displayName: string;
  description: string;
  commandType: string;
  promptTemplate: string;
  enabled: boolean;
  sortNo: number;
}

interface ProfileScanFormState {
  workspaceId: string;
  workspacePath: string;
}

const emptyPolicyForm: PolicyFormState = {
  policyCode: '',
  policyName: '',
  toolCode: '',
  commandPattern: '',
  pathPattern: '',
  action: 'DENY',
  riskLevel: 'MEDIUM',
  description: '',
  enabled: true,
  sortNo: 0,
};

const emptyHookForm: HookFormState = {
  hookCode: '',
  hookName: '',
  triggerPoint: 'BEFORE_TOOL_CALL',
  conditionKeyword: '',
  actionType: 'AUDIT',
  actionConfigJson: '',
  enabled: true,
  sortNo: 0,
};

const emptyCommandForm: CommandFormState = {
  commandCode: '',
  displayName: '',
  description: '',
  commandType: 'BUILTIN',
  promptTemplate: '',
  enabled: true,
  sortNo: 0,
};

const emptyProfileScanForm: ProfileScanFormState = {
  workspaceId: '',
  workspacePath: '',
};

const ACTION_OPTIONS = [
  { value: 'ALLOW', label: '允许' },
  { value: 'CONFIRM', label: '确认' },
  { value: 'DENY', label: '拒绝' },
];

const RISK_OPTIONS = [
  { value: 'LOW', label: '低' },
  { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' },
];

const TRIGGER_OPTIONS = [
  { value: 'BEFORE_TOOL_CALL', label: '工具调用前' },
  { value: 'AFTER_TOOL_CALL', label: '工具调用后' },
  { value: 'TASK_COMPLETED', label: '任务完成' },
];

/**
 * 管理端治理中心：集中维护软件端工具权限、Hook、项目画像和 Slash Command。
 */
export function GovernanceCenterPage() {
  const adminMessage = useAdminMessage();
  const [activeTab, setActiveTab] = React.useState<GovernanceTabKey>('permission');
  const [loading, setLoading] = React.useState(true);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [permissionPolicies, setPermissionPolicies] = React.useState<AdminGovernancePermissionPolicy[]>([]);
  const [permissionAudits, setPermissionAudits] = React.useState<AdminGovernancePermissionAudit[]>([]);
  const [hookRules, setHookRules] = React.useState<AdminGovernanceHookRule[]>([]);
  const [hookAudits, setHookAudits] = React.useState<AdminGovernanceHookAudit[]>([]);
  const [projectProfiles, setProjectProfiles] = React.useState<AdminGovernanceProjectProfile[]>([]);
  const [slashCommands, setSlashCommands] = React.useState<AdminGovernanceSlashCommand[]>([]);

  const [policyDialog, setPolicyDialog] = React.useState<{
    mode: DialogMode;
    open: boolean;
    item: AdminGovernancePermissionPolicy | null;
  }>({ mode: 'create', open: false, item: null });
  const [hookDialog, setHookDialog] = React.useState<{
    mode: DialogMode;
    open: boolean;
    item: AdminGovernanceHookRule | null;
  }>({ mode: 'create', open: false, item: null });
  const [commandDialog, setCommandDialog] = React.useState<{
    mode: DialogMode;
    open: boolean;
    item: AdminGovernanceSlashCommand | null;
  }>({ mode: 'create', open: false, item: null });
  const [scanDialogOpen, setScanDialogOpen] = React.useState(false);
  const [deleteTarget, setDeleteTarget] = React.useState<{
    title: string;
    onConfirm: () => Promise<void>;
  } | null>(null);

  /**
   * 并行加载治理中心各分区数据；任一分区失败时展示后端原始错误文案。
   */
  const loadData = React.useCallback(async () => {
    setLoading(true);
    setErrorMessage('');
    try {
      const [
        nextPolicies,
        nextHookRules,
        nextProfiles,
        nextCommands,
        nextPermissionAudits,
        nextHookAudits,
      ] = await Promise.all([
        AdminChatApi.listPermissionPolicies(),
        AdminChatApi.listHookRules(),
        AdminChatApi.listProjectProfiles(),
        AdminChatApi.listGovernanceSlashCommands(),
        AdminChatApi.listPermissionAudits(),
        AdminChatApi.listHookAudits(),
      ]);
      setPermissionPolicies(nextPolicies ?? []);
      setHookRules(nextHookRules ?? []);
      setProjectProfiles(nextProfiles ?? []);
      setSlashCommands(nextCommands ?? []);
      setPermissionAudits(nextPermissionAudits ?? []);
      setHookAudits(nextHookAudits ?? []);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '加载治理中心数据失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void loadData();
  }, [loadData]);

  const policyColumns = React.useMemo<ColumnsType<AdminGovernancePermissionPolicy>>(() => [
    {
      title: '策略编码',
      dataIndex: 'policyCode',
      width: 190,
      fixed: 'left',
      render: (value?: string) => <Typography.Text className="font-data-mono text-[12px]" type="secondary">{value || '-'}</Typography.Text>,
    },
    { title: '策略名称', dataIndex: 'policyName', width: 180, ellipsis: true },
    { title: '工具', dataIndex: 'toolCode', width: 150, render: optionalText },
    { title: '命令片段', dataIndex: 'commandPattern', width: 170, ellipsis: true, render: optionalText },
    { title: '路径片段', dataIndex: 'pathPattern', width: 170, ellipsis: true, render: optionalText },
    {
      title: '动作',
      dataIndex: 'action',
      width: 110,
      render: (value?: string) => <Tag color={actionTagColor(value)}>{actionLabel(value)}</Tag>,
    },
    {
      title: '风险',
      dataIndex: 'riskLevel',
      width: 100,
      render: (value?: string) => <Tag>{riskLabel(value)}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 100,
      render: (value?: number) => renderEnabledTag(value),
    },
    { title: '排序', dataIndex: 'sortNo', width: 90, render: (value?: number) => value ?? 0 },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      align: 'right',
      width: 180,
      render: (_, item) => (
        <AdminTableActions
          actions={[
            {
              key: 'edit',
              label: '编辑',
              ariaLabel: `编辑策略 ${item.policyCode}`,
              icon: <EditOutlined />,
              onClick: () => setPolicyDialog({ mode: 'edit', open: true, item }),
            },
            {
              key: 'delete',
              label: '删除',
              ariaLabel: `删除策略 ${item.policyCode}`,
              danger: true,
              icon: <DeleteOutlined />,
              disabled: item.id == null,
              onClick: () => setDeleteTarget({
                title: `删除策略「${item.policyName || item.policyCode}」`,
                onConfirm: async () => {
                  if (item.id == null) {
                    return;
                  }
                  await AdminChatApi.deletePermissionPolicy(item.id);
                  await loadData();
                  void adminMessage.success('权限策略已删除');
                },
              }),
            },
          ]}
        />
      ),
    },
  ], [adminMessage, loadData]);

  const hookColumns = React.useMemo<ColumnsType<AdminGovernanceHookRule>>(() => [
    {
      title: 'Hook编码',
      dataIndex: 'hookCode',
      width: 190,
      fixed: 'left',
      render: (value?: string) => <Typography.Text className="font-data-mono text-[12px]" type="secondary">{value || '-'}</Typography.Text>,
    },
    { title: '名称', dataIndex: 'hookName', width: 180, ellipsis: true },
    { title: '触发点', dataIndex: 'triggerPoint', width: 160, render: triggerLabel },
    { title: '条件关键字', dataIndex: 'conditionKeyword', width: 180, ellipsis: true, render: optionalText },
    { title: '动作类型', dataIndex: 'actionType', width: 110, render: (value?: string) => <Tag>{value || 'AUDIT'}</Tag> },
    { title: '状态', dataIndex: 'enabled', width: 100, render: renderEnabledTag },
    { title: '排序', dataIndex: 'sortNo', width: 90, render: (value?: number) => value ?? 0 },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      align: 'right',
      width: 180,
      render: (_, item) => (
        <AdminTableActions
          actions={[
            {
              key: 'edit',
              label: '编辑',
              ariaLabel: `编辑Hook ${item.hookCode}`,
              icon: <EditOutlined />,
              onClick: () => setHookDialog({ mode: 'edit', open: true, item }),
            },
            {
              key: 'delete',
              label: '删除',
              ariaLabel: `删除Hook ${item.hookCode}`,
              danger: true,
              icon: <DeleteOutlined />,
              disabled: item.id == null,
              onClick: () => setDeleteTarget({
                title: `删除 Hook「${item.hookName || item.hookCode}」`,
                onConfirm: async () => {
                  if (item.id == null) {
                    return;
                  }
                  await AdminChatApi.deleteHookRule(item.id);
                  await loadData();
                  void adminMessage.success('Hook 规则已删除');
                },
              }),
            },
          ]}
        />
      ),
    },
  ], [adminMessage, loadData]);

  const profileColumns = React.useMemo<ColumnsType<AdminGovernanceProjectProfile>>(() => [
    { title: '工作空间ID', dataIndex: 'workspaceId', width: 120, render: optionalText },
    { title: '路径', dataIndex: 'workspacePath', width: 260, ellipsis: true },
    { title: '摘要', dataIndex: 'summary', width: 240, ellipsis: true, render: optionalText },
    { title: '技术栈', dataIndex: 'techStackJson', width: 180, ellipsis: true, render: renderJsonSummary },
    { title: '验证命令', dataIndex: 'verificationCommandsJson', width: 220, ellipsis: true, render: renderJsonSummary },
    { title: '状态', dataIndex: 'status', width: 110, render: (value?: string) => <Tag>{value || '-'}</Tag> },
    { title: '扫描时间', dataIndex: 'scannedAt', width: 180, render: formatDate },
  ], []);

  const commandColumns = React.useMemo<ColumnsType<AdminGovernanceSlashCommand>>(() => [
    {
      title: '命令',
      dataIndex: 'displayName',
      width: 150,
      fixed: 'left',
      render: (value?: string, item?: AdminGovernanceSlashCommand) => (
        <Typography.Text className="font-data-mono text-[12px]" type="secondary">{value || `/${item?.commandCode || ''}`}</Typography.Text>
      ),
    },
    { title: '编码', dataIndex: 'commandCode', width: 150 },
    { title: '说明', dataIndex: 'description', width: 260, ellipsis: true, render: optionalText },
    { title: '类型', dataIndex: 'commandType', width: 120, render: (value?: string) => <Tag>{value || 'BUILTIN'}</Tag> },
    { title: '状态', dataIndex: 'enabled', width: 100, render: renderEnabledTag },
    { title: '排序', dataIndex: 'sortNo', width: 90, render: (value?: number) => value ?? 0 },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      align: 'right',
      width: 180,
      render: (_, item) => (
        <AdminTableActions
          actions={[
            {
              key: 'edit',
              label: '编辑',
              ariaLabel: `编辑命令 ${item.displayName || item.commandCode}`,
              icon: <EditOutlined />,
              onClick: () => setCommandDialog({ mode: 'edit', open: true, item }),
            },
            {
              key: 'delete',
              label: '删除',
              ariaLabel: `删除命令 ${item.displayName || item.commandCode}`,
              danger: true,
              icon: <DeleteOutlined />,
              disabled: item.id == null,
              onClick: () => setDeleteTarget({
                title: `删除命令「${item.displayName || item.commandCode}」`,
                onConfirm: async () => {
                  if (item.id == null) {
                    return;
                  }
                  await AdminChatApi.deleteGovernanceSlashCommand(item.id);
                  await loadData();
                  void adminMessage.success('Slash Command 已删除');
                },
              }),
            },
          ]}
        />
      ),
    },
  ], [adminMessage, loadData]);

  const permissionAuditColumns = React.useMemo<ColumnsType<AdminGovernancePermissionAudit>>(() => [
    { title: '工具', dataIndex: 'toolCode', width: 150, render: optionalText },
    { title: '命中策略', dataIndex: 'matchedPolicyCode', width: 180, render: optionalText },
    { title: '决策', dataIndex: 'decision', width: 100, render: (value?: string) => <Tag>{value || '-'}</Tag> },
    { title: '结果', dataIndex: 'result', width: 120, render: optionalText },
    { title: '消息', dataIndex: 'message', width: 260, ellipsis: true, render: optionalText },
    { title: '时间', dataIndex: 'createdAt', width: 180, render: formatDate },
  ], []);

  const hookAuditColumns = React.useMemo<ColumnsType<AdminGovernanceHookAudit>>(() => [
    { title: 'Hook', dataIndex: 'hookCode', width: 180, render: optionalText },
    { title: '触发点', dataIndex: 'triggerPoint', width: 150, render: triggerLabel },
    { title: '结果', dataIndex: 'result', width: 120, render: optionalText },
    { title: '消息', dataIndex: 'message', width: 260, ellipsis: true, render: optionalText },
    { title: '时间', dataIndex: 'createdAt', width: 180, render: formatDate },
  ], []);

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex min-w-0 flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div className="min-w-0">
          <Typography.Title level={2} style={{ margin: 0 }}>治理中心</Typography.Title>
          <Typography.Text type="secondary">
            维护软件端工具权限、Hook 生命周期、项目画像和 Slash Command 命令目录。
          </Typography.Text>
        </div>
        <Space wrap>
          <Button aria-label="刷新治理中心" icon={<ReloadOutlined />} onClick={() => void loadData()}>
            刷新
          </Button>
          {activeTab === 'permission' ? (
            <Button icon={<PlusOutlined />} type="primary" onClick={() => setPolicyDialog({ mode: 'create', open: true, item: null })}>
              新增策略
            </Button>
          ) : null}
          {activeTab === 'hook' ? (
            <Button icon={<PlusOutlined />} type="primary" onClick={() => setHookDialog({ mode: 'create', open: true, item: null })}>
              新增 Hook
            </Button>
          ) : null}
          {activeTab === 'profile' ? (
            <Button icon={<SearchOutlined />} type="primary" onClick={() => setScanDialogOpen(true)}>
              扫描项目
            </Button>
          ) : null}
          {activeTab === 'command' ? (
            <Button icon={<PlusOutlined />} type="primary" onClick={() => setCommandDialog({ mode: 'create', open: true, item: null })}>
              新增命令
            </Button>
          ) : null}
        </Space>
      </header>

      {errorMessage ? <Alert showIcon type="error" message={errorMessage} /> : null}

      <section className="min-w-0 overflow-hidden rounded-xl border border-border-hairline bg-surface-container-lowest p-md shadow-sm">
        <Tabs
          activeKey={activeTab}
          onChange={(key) => setActiveTab(key as GovernanceTabKey)}
          items={[
            {
              key: 'permission',
              label: '权限策略',
              children: (
                <AdminDataTable<AdminGovernancePermissionPolicy>
                  columns={policyColumns}
                  dataSource={permissionPolicies}
                  loading={loading}
                  locale={{ emptyText: loading ? '加载中...' : '暂无权限策略' }}
                  pagination={false}
                  rowKey={(item) => String(item.id ?? item.policyCode)}
                  scroll={{ x: 1500 }}
                />
              ),
            },
            {
              key: 'hook',
              label: 'Hook',
              children: (
                <AdminDataTable<AdminGovernanceHookRule>
                  columns={hookColumns}
                  dataSource={hookRules}
                  loading={loading}
                  locale={{ emptyText: loading ? '加载中...' : '暂无 Hook 规则' }}
                  pagination={false}
                  rowKey={(item) => String(item.id ?? item.hookCode)}
                  scroll={{ x: 1160 }}
                />
              ),
            },
            {
              key: 'profile',
              label: '项目画像',
              children: (
                <AdminDataTable<AdminGovernanceProjectProfile>
                  columns={profileColumns}
                  dataSource={projectProfiles}
                  loading={loading}
                  locale={{ emptyText: loading ? '加载中...' : '暂无项目画像' }}
                  pagination={false}
                  rowKey={(item) => String(item.id ?? item.workspacePath)}
                  scroll={{ x: 1180 }}
                />
              ),
            },
            {
              key: 'command',
              label: 'Slash Command',
              children: (
                <AdminDataTable<AdminGovernanceSlashCommand>
                  columns={commandColumns}
                  dataSource={slashCommands}
                  loading={loading}
                  locale={{ emptyText: loading ? '加载中...' : '暂无 Slash Command' }}
                  pagination={false}
                  rowKey={(item) => String(item.id ?? item.commandCode)}
                  scroll={{ x: 980 }}
                />
              ),
            },
            {
              key: 'audit',
              label: '审计',
              children: (
                <div className="grid gap-md xl:grid-cols-2">
                  <section className="min-w-0">
                    <Typography.Title level={4} style={{ marginTop: 0 }}>
                      <AuditOutlined /> 权限审计
                    </Typography.Title>
                    <AdminDataTable<AdminGovernancePermissionAudit>
                      columns={permissionAuditColumns}
                      dataSource={permissionAudits}
                      loading={loading}
                      locale={{ emptyText: loading ? '加载中...' : '暂无权限审计' }}
                      pagination={false}
                      rowKey={(item) => String(item.id ?? `${item.toolCode}-${item.createdAt}`)}
                      scroll={{ x: 980 }}
                    />
                  </section>
                  <section className="min-w-0">
                    <Typography.Title level={4} style={{ marginTop: 0 }}>
                      <AuditOutlined /> Hook 审计
                    </Typography.Title>
                    <AdminDataTable<AdminGovernanceHookAudit>
                      columns={hookAuditColumns}
                      dataSource={hookAudits}
                      loading={loading}
                      locale={{ emptyText: loading ? '加载中...' : '暂无 Hook 审计' }}
                      pagination={false}
                      rowKey={(item) => String(item.id ?? `${item.hookCode}-${item.createdAt}`)}
                      scroll={{ x: 840 }}
                    />
                  </section>
                </div>
              ),
            },
          ]}
        />
      </section>

      <PolicyEditDialog
        dialog={policyDialog}
        onClose={() => setPolicyDialog((previous) => ({ ...previous, open: false }))}
        onSubmit={async (payload) => {
          if (policyDialog.mode === 'edit' && policyDialog.item?.id != null) {
            await AdminChatApi.updatePermissionPolicy(policyDialog.item.id, payload);
          } else {
            await AdminChatApi.createPermissionPolicy(payload);
          }
          setPolicyDialog((previous) => ({ ...previous, open: false }));
          await loadData();
          void adminMessage.success(policyDialog.mode === 'edit' ? '权限策略已保存' : '权限策略已创建');
        }}
      />

      <HookEditDialog
        dialog={hookDialog}
        onClose={() => setHookDialog((previous) => ({ ...previous, open: false }))}
        onSubmit={async (payload) => {
          if (hookDialog.mode === 'edit' && hookDialog.item?.id != null) {
            await AdminChatApi.updateHookRule(hookDialog.item.id, payload);
          } else {
            await AdminChatApi.createHookRule(payload);
          }
          setHookDialog((previous) => ({ ...previous, open: false }));
          await loadData();
          void adminMessage.success(hookDialog.mode === 'edit' ? 'Hook 规则已保存' : 'Hook 规则已创建');
        }}
      />

      <SlashCommandEditDialog
        dialog={commandDialog}
        onClose={() => setCommandDialog((previous) => ({ ...previous, open: false }))}
        onSubmit={async (payload) => {
          if (commandDialog.mode === 'edit' && commandDialog.item?.id != null) {
            await AdminChatApi.updateGovernanceSlashCommand(commandDialog.item.id, payload);
          } else {
            await AdminChatApi.createGovernanceSlashCommand(payload);
          }
          setCommandDialog((previous) => ({ ...previous, open: false }));
          await loadData();
          void adminMessage.success(commandDialog.mode === 'edit' ? 'Slash Command 已保存' : 'Slash Command 已创建');
        }}
      />

      <ProjectProfileScanDialog
        open={scanDialogOpen}
        onClose={() => setScanDialogOpen(false)}
        onSubmit={async (payload) => {
          await AdminChatApi.scanProjectProfile(payload);
          setScanDialogOpen(false);
          await loadData();
          void adminMessage.success('项目画像已扫描');
        }}
      />

      <Modal
        destroyOnHidden
        okButtonProps={{ danger: true, icon: <DeleteOutlined /> } as any}
        okText="确认删除"
        open={Boolean(deleteTarget)}
        title={deleteTarget?.title ?? '确认删除'}
        onCancel={() => setDeleteTarget(null)}
        onOk={() => {
          const current = deleteTarget;
          if (!current) {
            return;
          }
          void current.onConfirm().finally(() => setDeleteTarget(null));
        }}
      >
        <Typography.Paragraph>
          该配置删除后会立即从治理链路中移除，请确认当前环境不再需要它。
        </Typography.Paragraph>
      </Modal>
    </div>
  );
}

function PolicyEditDialog({
  dialog,
  onClose,
  onSubmit,
}: {
  dialog: { mode: DialogMode; open: boolean; item: AdminGovernancePermissionPolicy | null };
  onClose: () => void;
  onSubmit: (payload: AdminGovernancePermissionPolicy) => Promise<void>;
}) {
  const adminMessage = useAdminMessage();
  const [form, setForm] = React.useState<PolicyFormState>(emptyPolicyForm);
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    if (!dialog.open) {
      return;
    }
    setForm(toPolicyForm(dialog.item));
  }, [dialog.open, dialog.item]);

  const updateField = <K extends keyof PolicyFormState>(field: K, value: PolicyFormState[K]) => {
    setForm((previous) => ({ ...previous, [field]: value }));
  };

  const handleSubmit = async () => {
    if (!form.policyCode.trim() || !form.policyName.trim()) {
      void adminMessage.error('请输入策略编码和策略名称');
      return;
    }
    setSaving(true);
    try {
      await onSubmit(toPolicyPayload(form, dialog.item));
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '保存权限策略失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      destroyOnHidden
      footer={(
        <Space>
          <Button disabled={saving} onClick={onClose}>取消</Button>
          <Button aria-label="保存策略" icon={<SaveOutlined />} loading={saving} type="primary" onClick={() => void handleSubmit()}>
            保存策略
          </Button>
        </Space>
      )}
      open={dialog.open}
      title={dialog.mode === 'create' ? '新增权限策略' : '编辑权限策略'}
      width={760}
      onCancel={onClose}
    >
      <Form layout="vertical">
        <div className="grid gap-md md:grid-cols-2">
          <Form.Item label="策略编码" required>
            <Input
              aria-label="策略编码"
              value={form.policyCode}
              disabled={dialog.mode === 'edit'}
              onChange={(event) => updateField('policyCode', event.target.value)}
            />
          </Form.Item>
          <Form.Item label="策略名称" required>
            <Input
              aria-label="策略名称"
              value={form.policyName}
              onChange={(event) => updateField('policyName', event.target.value)}
            />
          </Form.Item>
          <Form.Item label="工具编码">
            <Input aria-label="工具编码" value={form.toolCode} onChange={(event) => updateField('toolCode', event.target.value)} />
          </Form.Item>
          <Form.Item label="命令片段">
            <Input aria-label="命令片段" value={form.commandPattern} onChange={(event) => updateField('commandPattern', event.target.value)} />
          </Form.Item>
          <Form.Item label="路径片段">
            <Input aria-label="路径片段" value={form.pathPattern} onChange={(event) => updateField('pathPattern', event.target.value)} />
          </Form.Item>
          <Form.Item label="策略动作">
            <Select aria-label="策略动作" value={form.action} options={ACTION_OPTIONS} onChange={(value) => updateField('action', value)} />
          </Form.Item>
          <Form.Item label="风险等级">
            <Select aria-label="风险等级" value={form.riskLevel} options={RISK_OPTIONS} onChange={(value) => updateField('riskLevel', value)} />
          </Form.Item>
          <Form.Item label="排序">
            <InputNumber
              aria-label="排序"
              className="w-full"
              value={form.sortNo}
              onChange={(value) => updateField('sortNo', Number(value ?? 0))}
            />
          </Form.Item>
          <Form.Item label="启用策略">
            <Switch checked={form.enabled} checkedChildren="启用" unCheckedChildren="停用" onChange={(checked) => updateField('enabled', checked)} />
          </Form.Item>
        </div>
        <Form.Item label="策略说明">
          <Input.TextArea
            aria-label="策略说明"
            rows={3}
            value={form.description}
            onChange={(event) => updateField('description', event.target.value)}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function HookEditDialog({
  dialog,
  onClose,
  onSubmit,
}: {
  dialog: { mode: DialogMode; open: boolean; item: AdminGovernanceHookRule | null };
  onClose: () => void;
  onSubmit: (payload: AdminGovernanceHookRule) => Promise<void>;
}) {
  const adminMessage = useAdminMessage();
  const [form, setForm] = React.useState<HookFormState>(emptyHookForm);
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    if (dialog.open) {
      setForm(toHookForm(dialog.item));
    }
  }, [dialog.open, dialog.item]);

  const updateField = <K extends keyof HookFormState>(field: K, value: HookFormState[K]) => {
    setForm((previous) => ({ ...previous, [field]: value }));
  };

  const handleSubmit = async () => {
    if (!form.hookCode.trim() || !form.hookName.trim()) {
      void adminMessage.error('请输入 Hook 编码和名称');
      return;
    }
    setSaving(true);
    try {
      await onSubmit(toHookPayload(form, dialog.item));
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '保存 Hook 规则失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      destroyOnHidden
      footer={(
        <Space>
          <Button disabled={saving} onClick={onClose}>取消</Button>
          <Button icon={<SaveOutlined />} loading={saving} type="primary" onClick={() => void handleSubmit()}>
            保存 Hook
          </Button>
        </Space>
      )}
      open={dialog.open}
      title={dialog.mode === 'create' ? '新增 Hook 规则' : '编辑 Hook 规则'}
      width={760}
      onCancel={onClose}
    >
      <Form layout="vertical">
        <div className="grid gap-md md:grid-cols-2">
          <Form.Item label="Hook编码" required>
            <Input aria-label="Hook编码" value={form.hookCode} disabled={dialog.mode === 'edit'} onChange={(event) => updateField('hookCode', event.target.value)} />
          </Form.Item>
          <Form.Item label="Hook名称" required>
            <Input aria-label="Hook名称" value={form.hookName} onChange={(event) => updateField('hookName', event.target.value)} />
          </Form.Item>
          <Form.Item label="触发点">
            <Select aria-label="触发点" value={form.triggerPoint} options={TRIGGER_OPTIONS} onChange={(value) => updateField('triggerPoint', value)} />
          </Form.Item>
          <Form.Item label="条件关键字">
            <Input aria-label="条件关键字" value={form.conditionKeyword} onChange={(event) => updateField('conditionKeyword', event.target.value)} />
          </Form.Item>
          <Form.Item label="动作类型">
            <Input aria-label="动作类型" value={form.actionType} onChange={(event) => updateField('actionType', event.target.value)} />
          </Form.Item>
          <Form.Item label="排序">
            <InputNumber aria-label="Hook排序" className="w-full" value={form.sortNo} onChange={(value) => updateField('sortNo', Number(value ?? 0))} />
          </Form.Item>
          <Form.Item label="启用 Hook">
            <Switch checked={form.enabled} checkedChildren="启用" unCheckedChildren="停用" onChange={(checked) => updateField('enabled', checked)} />
          </Form.Item>
        </div>
        <Form.Item label="动作配置JSON">
          <Input.TextArea
            aria-label="动作配置JSON"
            rows={3}
            value={form.actionConfigJson}
            onChange={(event) => updateField('actionConfigJson', event.target.value)}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function SlashCommandEditDialog({
  dialog,
  onClose,
  onSubmit,
}: {
  dialog: { mode: DialogMode; open: boolean; item: AdminGovernanceSlashCommand | null };
  onClose: () => void;
  onSubmit: (payload: AdminGovernanceSlashCommand) => Promise<void>;
}) {
  const adminMessage = useAdminMessage();
  const [form, setForm] = React.useState<CommandFormState>(emptyCommandForm);
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    if (dialog.open) {
      setForm(toCommandForm(dialog.item));
    }
  }, [dialog.open, dialog.item]);

  const updateField = <K extends keyof CommandFormState>(field: K, value: CommandFormState[K]) => {
    setForm((previous) => ({ ...previous, [field]: value }));
  };

  const handleSubmit = async () => {
    if (!form.commandCode.trim() || !form.displayName.trim()) {
      void adminMessage.error('请输入命令编码和展示名');
      return;
    }
    setSaving(true);
    try {
      await onSubmit(toCommandPayload(form, dialog.item));
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '保存 Slash Command 失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      destroyOnHidden
      footer={(
        <Space>
          <Button disabled={saving} onClick={onClose}>取消</Button>
          <Button icon={<SaveOutlined />} loading={saving} type="primary" onClick={() => void handleSubmit()}>
            保存命令
          </Button>
        </Space>
      )}
      open={dialog.open}
      title={dialog.mode === 'create' ? '新增 Slash Command' : '编辑 Slash Command'}
      width={760}
      onCancel={onClose}
    >
      <Form layout="vertical">
        <div className="grid gap-md md:grid-cols-2">
          <Form.Item label="命令编码" required>
            <Input aria-label="命令编码" value={form.commandCode} disabled={dialog.mode === 'edit'} onChange={(event) => updateField('commandCode', event.target.value)} />
          </Form.Item>
          <Form.Item label="展示名" required>
            <Input aria-label="展示名" value={form.displayName} onChange={(event) => updateField('displayName', event.target.value)} />
          </Form.Item>
          <Form.Item label="命令类型">
            <Input aria-label="命令类型" value={form.commandType} onChange={(event) => updateField('commandType', event.target.value)} />
          </Form.Item>
          <Form.Item label="排序">
            <InputNumber aria-label="命令排序" className="w-full" value={form.sortNo} onChange={(value) => updateField('sortNo', Number(value ?? 0))} />
          </Form.Item>
          <Form.Item label="启用命令">
            <Switch checked={form.enabled} checkedChildren="启用" unCheckedChildren="停用" onChange={(checked) => updateField('enabled', checked)} />
          </Form.Item>
        </div>
        <Form.Item label="说明">
          <Input.TextArea aria-label="命令说明" rows={3} value={form.description} onChange={(event) => updateField('description', event.target.value)} />
        </Form.Item>
        <Form.Item label="提示模板">
          <Input.TextArea aria-label="提示模板" rows={5} value={form.promptTemplate} onChange={(event) => updateField('promptTemplate', event.target.value)} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function ProjectProfileScanDialog({
  open,
  onClose,
  onSubmit,
}: {
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: { workspaceId?: string | number | null; workspacePath: string }) => Promise<void>;
}) {
  const adminMessage = useAdminMessage();
  const [form, setForm] = React.useState<ProfileScanFormState>(emptyProfileScanForm);
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    if (open) {
      setForm(emptyProfileScanForm);
    }
  }, [open]);

  const handleSubmit = async () => {
    if (!form.workspacePath.trim()) {
      void adminMessage.error('请输入工作空间路径');
      return;
    }
    const numericWorkspaceId = form.workspaceId.trim() ? Number(form.workspaceId.trim()) : null;
    setSaving(true);
    try {
      await onSubmit({
        workspaceId: Number.isFinite(numericWorkspaceId) ? numericWorkspaceId : null,
        workspacePath: form.workspacePath.trim(),
      });
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '扫描项目画像失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      destroyOnHidden
      footer={(
        <Space>
          <Button disabled={saving} onClick={onClose}>取消</Button>
          <Button icon={<SearchOutlined />} loading={saving} type="primary" onClick={() => void handleSubmit()}>
            开始扫描
          </Button>
        </Space>
      )}
      open={open}
      title="扫描项目画像"
      onCancel={onClose}
    >
      <Form layout="vertical">
        <Form.Item label="工作空间ID">
          <Input aria-label="工作空间ID" value={form.workspaceId} onChange={(event) => setForm((previous) => ({ ...previous, workspaceId: event.target.value }))} />
        </Form.Item>
        <Form.Item label="工作空间路径" required>
          <Input aria-label="工作空间路径" value={form.workspacePath} onChange={(event) => setForm((previous) => ({ ...previous, workspacePath: event.target.value }))} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function toPolicyForm(item: AdminGovernancePermissionPolicy | null): PolicyFormState {
  if (!item) {
    return emptyPolicyForm;
  }
  return {
    policyCode: item.policyCode ?? '',
    policyName: item.policyName ?? '',
    toolCode: item.toolCode ?? '',
    commandPattern: item.commandPattern ?? '',
    pathPattern: item.pathPattern ?? '',
    action: item.action ?? 'DENY',
    riskLevel: item.riskLevel ?? 'MEDIUM',
    description: item.description ?? '',
    enabled: item.enabled !== 0,
    sortNo: Number(item.sortNo ?? 0),
  };
}

function toPolicyPayload(form: PolicyFormState, item: AdminGovernancePermissionPolicy | null): AdminGovernancePermissionPolicy {
  return {
    id: item?.id,
    policyCode: form.policyCode.trim(),
    policyName: form.policyName.trim(),
    toolCode: form.toolCode.trim() || undefined,
    commandPattern: form.commandPattern.trim() || undefined,
    pathPattern: form.pathPattern.trim() || undefined,
    action: form.action,
    riskLevel: form.riskLevel,
    description: form.description.trim() || undefined,
    enabled: form.enabled ? 1 : 0,
    sortNo: Number(form.sortNo) || 0,
  };
}

function toHookForm(item: AdminGovernanceHookRule | null): HookFormState {
  if (!item) {
    return emptyHookForm;
  }
  return {
    hookCode: item.hookCode ?? '',
    hookName: item.hookName ?? '',
    triggerPoint: item.triggerPoint ?? 'BEFORE_TOOL_CALL',
    conditionKeyword: item.conditionKeyword ?? '',
    actionType: item.actionType ?? 'AUDIT',
    actionConfigJson: item.actionConfigJson ?? '',
    enabled: item.enabled !== 0,
    sortNo: Number(item.sortNo ?? 0),
  };
}

function toHookPayload(form: HookFormState, item: AdminGovernanceHookRule | null): AdminGovernanceHookRule {
  return {
    id: item?.id,
    hookCode: form.hookCode.trim(),
    hookName: form.hookName.trim(),
    triggerPoint: form.triggerPoint,
    conditionKeyword: form.conditionKeyword.trim() || undefined,
    actionType: form.actionType.trim() || 'AUDIT',
    actionConfigJson: form.actionConfigJson.trim() || undefined,
    enabled: form.enabled ? 1 : 0,
    sortNo: Number(form.sortNo) || 0,
  };
}

function toCommandForm(item: AdminGovernanceSlashCommand | null): CommandFormState {
  if (!item) {
    return emptyCommandForm;
  }
  return {
    commandCode: item.commandCode ?? '',
    displayName: item.displayName ?? '',
    description: item.description ?? '',
    commandType: item.commandType ?? 'BUILTIN',
    promptTemplate: item.promptTemplate ?? '',
    enabled: item.enabled !== 0,
    sortNo: Number(item.sortNo ?? 0),
  };
}

function toCommandPayload(form: CommandFormState, item: AdminGovernanceSlashCommand | null): AdminGovernanceSlashCommand {
  return {
    id: item?.id,
    commandCode: form.commandCode.trim(),
    displayName: form.displayName.trim(),
    description: form.description.trim() || undefined,
    commandType: form.commandType.trim() || 'BUILTIN',
    promptTemplate: form.promptTemplate.trim() || undefined,
    enabled: form.enabled ? 1 : 0,
    sortNo: Number(form.sortNo) || 0,
  };
}

function renderEnabledTag(value?: number) {
  return value === 0 ? <Tag>停用</Tag> : <Tag color="success">启用</Tag>;
}

function optionalText(value?: string | number | null) {
  if (value == null || String(value).trim() === '') {
    return '-';
  }
  return String(value);
}

function renderJsonSummary(value?: string) {
  if (!value || !value.trim()) {
    return '-';
  }
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) {
      return parsed.join('、') || '-';
    }
  } catch {
    return value;
  }
  return value;
}

function actionLabel(value?: string) {
  return ACTION_OPTIONS.find((item) => item.value === value)?.label || value || '-';
}

function actionTagColor(value?: string) {
  if (value === 'DENY') {
    return 'error';
  }
  if (value === 'CONFIRM') {
    return 'warning';
  }
  if (value === 'ALLOW') {
    return 'success';
  }
  return undefined;
}

function riskLabel(value?: string) {
  return RISK_OPTIONS.find((item) => item.value === value)?.label || value || '-';
}

function triggerLabel(value?: string) {
  return TRIGGER_OPTIONS.find((item) => item.value === value)?.label || value || '-';
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
