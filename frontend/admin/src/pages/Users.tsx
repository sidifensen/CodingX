import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  CheckCircleOutlined,
  EyeOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { Alert, Avatar, Button, Form, Input, Modal, Segmented, Select, Space, Statistic, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { AdminUserApi } from '../api/adminUserApi';
import { AdminDataTable, AdminTableActions } from '../components/AdminDataTable';
import { useAdminMessage } from '../components/AdminMessageContext';
import {
  AdminUserCreatePayload,
  AdminUserPageResult,
  AdminUserStatus,
  AdminUserSummary,
  AdminUserType,
} from '../types/adminUser';

type UserFilterTab = '全部' | '正常' | '禁用' | '待审核';

interface CreateUserDialogState {
  username: string;
  displayName: string;
  password: string;
  email: string;
  phone: string;
  userType: AdminUserType;
  status: AdminUserStatus;
}

const emptyCreateUserForm: CreateUserDialogState = {
  username: '',
  displayName: '',
  password: '',
  email: '',
  phone: '',
  userType: 'USER',
  status: 'PENDING',
};

/**
 * 管理端用户管理页：对接真实后端并提供新增、审核、启停与详情跳转。
 */
export function Users() {
  const adminMessage = useAdminMessage();
  const navigate = useNavigate();
  const [filter, setFilter] = React.useState<UserFilterTab>('全部');
  const [pageResult, setPageResult] = React.useState<AdminUserPageResult>({
    records: [],
    total: 0,
    current: 1,
    size: 10,
    pages: 1,
  });
  const [isLoading, setIsLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [isCreateDialogOpen, setIsCreateDialogOpen] = React.useState(false);
  const [createForm, setCreateForm] = React.useState<CreateUserDialogState>(emptyCreateUserForm);
  const [isCreating, setIsCreating] = React.useState(false);

  const loadUsers = React.useCallback(
    async (nextCurrent = pageResult.current, nextSize = pageResult.size, nextFilter: UserFilterTab = filter) => {
      setIsLoading(true);
      setErrorMessage('');
      try {
        const response = await AdminUserApi.listUsers({
          current: nextCurrent,
          size: nextSize,
          status: mapFilterToStatus(nextFilter),
        });
        setPageResult(response);
      } catch (error) {
        setErrorMessage(extractErrorMessage(error, '用户列表加载失败'));
        setPageResult((previous) => ({ ...previous, records: [], total: 0 }));
      } finally {
        setIsLoading(false);
      }
    },
    [filter, pageResult.current, pageResult.size],
  );

  React.useEffect(() => {
    void loadUsers(1, pageResult.size, filter);
  }, [filter, pageResult.size, loadUsers]);

  const userStats = React.useMemo(() => {
    const total = pageResult.total;
    const pending = pageResult.records.filter((user) => user.status === 'PENDING').length;
    const disabled = pageResult.records.filter((user) => user.status === 'DISABLED').length;
    const active = pageResult.records.filter((user) => user.status === 'ACTIVE').length;
    return { total, pending, disabled, active };
  }, [pageResult]);

  /**
   * 审核通过后刷新当前页，保证列表状态与后端一致。
   */
  const handleApprove = async (userId: string | number) => {
    try {
      await AdminUserApi.approveUser(userId);
      await loadUsers(pageResult.current, pageResult.size, filter);
      void adminMessage.success('用户审核已通过');
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '审核通过失败'));
    }
  };

  /**
   * 用户启停直接调用状态接口，操作列按钮文案随当前状态反转。
   */
  const handleToggleStatus = async (user: AdminUserSummary) => {
    const nextStatus: AdminUserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await AdminUserApi.updateUserStatus(user.id, nextStatus);
      await loadUsers(pageResult.current, pageResult.size, filter);
      void adminMessage.success(nextStatus === 'ACTIVE' ? '用户已启用' : '用户已禁用');
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '状态更新失败'));
    }
  };

  const handleCreateUser = async () => {
    if (!createForm.username.trim() || !createForm.displayName.trim() || !createForm.password.trim()) {
      void adminMessage.warning('用户名、展示名称和初始密码不能为空');
      return;
    }
    setIsCreating(true);
    const payload: AdminUserCreatePayload = {
      username: createForm.username.trim(),
      displayName: createForm.displayName.trim(),
      password: createForm.password.trim(),
      userType: createForm.userType,
      status: createForm.status,
      email: createForm.email.trim() || undefined,
      phone: createForm.phone.trim() || undefined,
    };
    try {
      await AdminUserApi.createUser(payload);
      setIsCreateDialogOpen(false);
      setCreateForm(emptyCreateUserForm);
      await loadUsers(1, pageResult.size, filter);
      void adminMessage.success('用户已创建');
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '新增用户失败'));
    } finally {
      setIsCreating(false);
    }
  };

  const tableSummaryText = React.useMemo(() => {
    if (pageResult.total <= 0) {
      return '显示 0 条，共 0 条';
    }
    const start = (pageResult.current - 1) * pageResult.size + 1;
    const end = Math.min(pageResult.current * pageResult.size, pageResult.total);
    return `显示 ${start}-${end} 条，共 ${pageResult.total} 条`;
  }, [pageResult]);

  const columns = React.useMemo<ColumnsType<AdminUserSummary>>(() => [
    {
      title: '用户',
      dataIndex: 'displayName',
      width: 260,
      render: (_, user) => (
        <Space>
          <Avatar src={user.avatarUrl || fallbackAvatar(user)} />
          <div>
            <Typography.Text strong>{user.displayName}</Typography.Text>
            <div className="text-[12px] text-secondary">{user.email || `${user.username}@codingx.local`}</div>
          </div>
        </Space>
      ),
    },
    {
      title: '角色',
      dataIndex: 'userType',
      width: 120,
      render: (_, user) => (
        <Tag color={user.userType === 'ADMIN' ? 'default' : undefined}>
          {user.userTypeLabel || toUserTypeLabel(user.userType)}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (_, user) => <Tag color={toStatusColor(user.status)}>{user.statusLabel || toStatusLabel(user.status)}</Tag>,
    },
    {
      title: '最近登录',
      dataIndex: 'lastLoginAt',
      width: 190,
      render: formatDateTime,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 190,
      render: formatDateTime,
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 260,
      align: 'right',
      render: (_, user) => (
        <AdminTableActions
          actions={[
            ...(user.status === 'PENDING'
              ? [{
                key: 'approve',
                label: '审核通过',
                icon: <CheckCircleOutlined />,
                type: 'primary' as const,
                onClick: (event) => {
                  event.stopPropagation();
                  void handleApprove(user.id);
                },
              }]
              : [{
                key: 'detail',
                label: '详情',
                icon: <EyeOutlined />,
                onClick: (event) => {
                  event.stopPropagation();
                  navigate(`/users/${user.id}`);
                },
              }]),
            {
              key: 'toggle',
              label: user.status === 'ACTIVE' ? '禁用' : '启用',
              ariaLabel: user.status === 'ACTIVE' ? '禁用用户' : '启用用户',
              danger: user.status === 'ACTIVE',
              icon: user.status === 'ACTIVE' ? <StopOutlined /> : <PlayCircleOutlined />,
              onClick: (event) => {
                event.stopPropagation();
                void handleToggleStatus(user);
              },
            },
          ]}
        />
      ),
    },
  ], [handleApprove, handleToggleStatus, navigate]);

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <Typography.Title level={2} style={{ margin: 0 }}>用户管理</Typography.Title>
          <Typography.Text type="secondary">管理系统内的所有用户账户、角色分配及其活跃状态。</Typography.Text>
        </div>
        <Button
          icon={<PlusOutlined />}
          type="primary"
          onClick={() => {
            setCreateForm(emptyCreateUserForm);
            setIsCreateDialogOpen(true);
          }}
        >
          新增用户
        </Button>
      </header>

      {errorMessage ? <Alert showIcon type="error" message={errorMessage} /> : null}

      <section className="rounded-xl border border-border-hairline bg-surface-container-lowest p-md">
        <Space wrap>
          <Typography.Text type="secondary">状态筛选</Typography.Text>
          <Segmented<UserFilterTab>
            options={['全部', '正常', '禁用', '待审核']}
            value={filter}
            onChange={(nextFilter) => {
              setFilter(nextFilter);
              setPageResult((previous) => ({ ...previous, current: 1 }));
            }}
          />
        </Space>
      </section>

      <section className="grid gap-md md:grid-cols-4">
        <MetricItem title="总用户数" value={userStats.total} />
        <MetricItem title="正常用户" value={userStats.active} />
        <MetricItem title="待审核" value={userStats.pending} />
        <MetricItem title="已禁用" value={userStats.disabled} />
      </section>

      <div data-testid="users-table-scroll">
        <span data-testid="users-table-summary" className="sr-only">{tableSummaryText}</span>
        <AdminDataTable<AdminUserSummary>
          columns={columns}
          dataSource={pageResult.records}
          loading={isLoading}
          locale={{ emptyText: isLoading ? '用户加载中...' : '当前筛选条件下暂无用户' }}
          pagination={{
            current: pageResult.current,
            pageSize: pageResult.size,
            total: pageResult.total,
            onChange: (nextPage) => {
              void loadUsers(nextPage, pageResult.size, filter);
            },
          }}
          rowKey={(user) => String(user.id)}
          scroll={{ x: 1120 }}
          onRow={(user) => ({
            onClick: () => navigate(`/users/${user.id}`),
          })}
        />
      </div>

      <CreateUserDialog
        form={createForm}
        creating={isCreating}
        open={isCreateDialogOpen}
        onChange={(field, value) => setCreateForm((previous) => ({ ...previous, [field]: value }))}
        onClose={() => setIsCreateDialogOpen(false)}
        onSubmit={() => void handleCreateUser()}
      />
    </div>
  );
}

function CreateUserDialog({
  form,
  creating,
  open,
  onChange,
  onClose,
  onSubmit,
}: {
  form: CreateUserDialogState;
  creating: boolean;
  open: boolean;
  onChange: (field: keyof CreateUserDialogState, value: string) => void;
  onClose: () => void;
  onSubmit: () => void;
}) {
  return (
    <Modal
      destroyOnHidden
      confirmLoading={creating}
      okText="创建用户"
      open={open}
      title="新增用户"
      onCancel={onClose}
      onOk={onSubmit}
    >
      <Typography.Paragraph type="secondary">填写基础账号信息，创建后可在详情页继续维护。</Typography.Paragraph>
      <Form layout="vertical">
        <div className="grid gap-md md:grid-cols-2">
          <Form.Item label="用户名" required>
            <Input value={form.username} onChange={(event) => onChange('username', event.target.value)} />
          </Form.Item>
          <Form.Item label="展示名称" required>
            <Input value={form.displayName} onChange={(event) => onChange('displayName', event.target.value)} />
          </Form.Item>
          <Form.Item label="初始密码" required>
            <Input.Password value={form.password} onChange={(event) => onChange('password', event.target.value)} />
          </Form.Item>
          <Form.Item label="邮箱">
            <Input value={form.email} onChange={(event) => onChange('email', event.target.value)} />
          </Form.Item>
          <Form.Item label="手机号">
            <Input value={form.phone} onChange={(event) => onChange('phone', event.target.value)} />
          </Form.Item>
          <Form.Item label="状态">
            <Select
              value={form.status}
              options={[
                { value: 'ACTIVE', label: '正常' },
                { value: 'DISABLED', label: '禁用' },
                { value: 'PENDING', label: '待审核' },
              ]}
              onChange={(value) => onChange('status', value)}
            />
          </Form.Item>
        </div>
      </Form>
    </Modal>
  );
}

function MetricItem({ title, value }: { title: string; value: number }) {
  return (
    <div className="rounded-xl border border-border-hairline bg-surface-container-lowest p-md">
      <Statistic title={title} value={value} />
    </div>
  );
}

function mapFilterToStatus(filter: UserFilterTab): 'ALL' | AdminUserStatus {
  if (filter === '正常') {
    return 'ACTIVE';
  }
  if (filter === '禁用') {
    return 'DISABLED';
  }
  if (filter === '待审核') {
    return 'PENDING';
  }
  return 'ALL';
}

function toStatusLabel(status: string): string {
  if (status === 'ACTIVE') {
    return '正常';
  }
  if (status === 'DISABLED') {
    return '禁用';
  }
  if (status === 'PENDING') {
    return '待审核';
  }
  return status;
}

function toStatusColor(status: string): string | undefined {
  if (status === 'ACTIVE') {
    return 'success';
  }
  if (status === 'DISABLED') {
    return 'error';
  }
  if (status === 'PENDING') {
    return 'warning';
  }
  return undefined;
}

function toUserTypeLabel(userType: string): string {
  if (userType === 'ADMIN') {
    return '管理员';
  }
  if (userType === 'USER') {
    return '普通用户';
  }
  return userType;
}

function formatDateTime(value?: string | null): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN');
}

function fallbackAvatar(user: AdminUserSummary): string {
  const seed = encodeURIComponent(user.username || user.displayName || 'codingx-user');
  return `https://api.dicebear.com/9.x/thumbs/svg?seed=${seed}`;
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
