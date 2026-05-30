import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeftOutlined,
  EditOutlined,
  KeyOutlined,
  PoweroffOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { Alert, Avatar, Button, Descriptions, Form, Input, Modal, Select, Space, Spin, Tag, Typography } from 'antd';

import { AdminUserApi } from '../api/adminUserApi';
import { useAdminMessage } from '../components/AdminMessageContext';
import { AdminUserDetail, AdminUserStatus, AdminUserUpdatePayload } from '../types/adminUser';

interface EditUserFormState {
  displayName: string;
  email: string;
  phone: string;
  userType: string;
  status: AdminUserStatus;
  avatarUrl: string;
}

const emptyEditUserForm: EditUserFormState = {
  displayName: '',
  email: '',
  phone: '',
  userType: 'USER',
  status: 'ACTIVE',
  avatarUrl: '',
};

/**
 * 管理端用户详情页：展示并维护单个用户信息。
 */
export function UserDetail() {
  const adminMessage = useAdminMessage();
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [user, setUser] = React.useState<AdminUserDetail | null>(null);
  const [isLoading, setIsLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');

  const [isEditDialogOpen, setIsEditDialogOpen] = React.useState(false);
  const [editForm, setEditForm] = React.useState<EditUserFormState>(emptyEditUserForm);
  const [isEditing, setIsEditing] = React.useState(false);

  const [isResetDialogOpen, setIsResetDialogOpen] = React.useState(false);
  const [newPassword, setNewPassword] = React.useState('');
  const [isResetting, setIsResetting] = React.useState(false);

  const loadUser = React.useCallback(async () => {
    if (!id) {
      return;
    }
    setIsLoading(true);
    setErrorMessage('');
    try {
      const detail = await AdminUserApi.getUserDetail(id);
      setUser(detail);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '用户详情加载失败'));
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  React.useEffect(() => {
    void loadUser();
  }, [loadUser]);

  const openEditDialog = () => {
    if (!user) {
      return;
    }
    setEditForm({
      displayName: user.displayName ?? '',
      email: user.email ?? '',
      phone: user.phone ?? '',
      userType: (user.userType as string) || 'USER',
      status: normalizeStatus(user.status),
      avatarUrl: user.avatarUrl ?? '',
    });
    setIsEditDialogOpen(true);
  };

  const handleSaveEdit = async () => {
    if (!id) {
      return;
    }
    if (!editForm.displayName.trim()) {
      void adminMessage.warning('展示名称不能为空');
      return;
    }
    const payload: AdminUserUpdatePayload = {
      displayName: editForm.displayName.trim(),
      email: editForm.email.trim() || undefined,
      phone: editForm.phone.trim() || undefined,
      avatarUrl: editForm.avatarUrl.trim() || undefined,
      userType: editForm.userType === 'ADMIN' ? 'ADMIN' : 'USER',
      status: editForm.status,
    };
    setIsEditing(true);
    try {
      await AdminUserApi.updateUser(id, payload);
      setIsEditDialogOpen(false);
      await loadUser();
      void adminMessage.success('用户信息已保存');
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '保存用户信息失败'));
    } finally {
      setIsEditing(false);
    }
  };

  const handleToggleStatus = async () => {
    if (!id || !user) {
      return;
    }
    const nextStatus: AdminUserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await AdminUserApi.updateUserStatus(id, nextStatus);
      await loadUser();
      void adminMessage.success(nextStatus === 'ACTIVE' ? '用户已启用' : '用户已禁用');
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '状态更新失败'));
    }
  };

  const handleResetPassword = async () => {
    if (!id) {
      return;
    }
    if (!newPassword.trim()) {
      void adminMessage.warning('新密码不能为空');
      return;
    }
    setIsResetting(true);
    try {
      await AdminUserApi.resetPassword(id, newPassword.trim());
      setIsResetDialogOpen(false);
      setNewPassword('');
      void adminMessage.success('密码已重置');
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '重置密码失败'));
    } finally {
      setIsResetting(false);
    }
  };

  return (
    <div className="w-full space-y-lg p-lg">
      <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>返回用户列表</Button>

      {errorMessage ? <Alert showIcon type="error" message={errorMessage} /> : null}
      {isLoading ? <Spin tip="用户详情加载中..." /> : null}

      {!isLoading && user ? (
        <>
          <section className="flex flex-col gap-lg rounded-2xl border border-border-hairline bg-surface-container-lowest p-xl lg:flex-row lg:items-start lg:justify-between">
            <Space size="large" align="start">
              <Avatar size={96} src={user.avatarUrl || fallbackAvatar(user.username)} />
              <div>
                <Space wrap>
                  <Typography.Title level={2} style={{ margin: 0 }}>{user.displayName}</Typography.Title>
                  <Tag color={user.userType === 'ADMIN' ? 'default' : undefined}>{user.userTypeLabel || toUserTypeLabel(user.userType)}</Tag>
                  <Tag color={toStatusColor(user.status)}>{user.statusLabel || toStatusLabel(user.status)}</Tag>
                </Space>
                <div className="mt-sm text-secondary">{user.email || '-'} · {user.id}</div>
              </div>
            </Space>
            <Space wrap>
              <Button icon={<EditOutlined />} onClick={openEditDialog}>编辑信息</Button>
              <Button aria-label="重置密码" icon={<KeyOutlined />} onClick={() => setIsResetDialogOpen(true)}>重置密码</Button>
              <Button danger icon={<PoweroffOutlined />} onClick={() => void handleToggleStatus()}>
                {user.status === 'ACTIVE' ? '冻结账户' : '启用账户'}
              </Button>
            </Space>
          </section>

          <Descriptions
            bordered
            column={{ xs: 1, md: 2 }}
            items={[
              { key: 'createdAt', label: '注册时间', children: formatDateTime(user.createdAt) },
              { key: 'lastLoginAt', label: '最后登录时间', children: formatDateTime(user.lastLoginAt) },
              { key: 'lastLoginIp', label: '登录 IP', children: user.lastLoginIp || '-' },
              { key: 'phone', label: '手机号', children: user.phone || '-' },
              { key: 'username', label: '用户名', children: user.username },
              { key: 'updatedAt', label: '更新时间', children: formatDateTime(user.updatedAt) },
            ]}
          />
        </>
      ) : null}

      <EditUserDialog
        form={editForm}
        editing={isEditing}
        open={isEditDialogOpen}
        onClose={() => setIsEditDialogOpen(false)}
        onChange={(field, value) => setEditForm((previous) => ({ ...previous, [field]: value }))}
        onSubmit={() => void handleSaveEdit()}
      />

      <ResetPasswordDialog
        newPassword={newPassword}
        open={isResetDialogOpen}
        resetting={isResetting}
        onChange={setNewPassword}
        onClose={() => setIsResetDialogOpen(false)}
        onSubmit={() => void handleResetPassword()}
      />
    </div>
  );
}

function EditUserDialog({
  form,
  editing,
  open,
  onClose,
  onChange,
  onSubmit,
}: {
  form: EditUserFormState;
  editing: boolean;
  open: boolean;
  onClose: () => void;
  onChange: (field: keyof EditUserFormState, value: string) => void;
  onSubmit: () => void;
}) {
  return (
    <Modal
      destroyOnHidden
      confirmLoading={editing}
      okText="保存修改"
      open={open}
      title="编辑用户信息"
      onCancel={onClose}
      onOk={onSubmit}
    >
      <Form layout="vertical">
        <Form.Item label="展示名称" required>
          <Input aria-label="展示名称" value={form.displayName} onChange={(event) => onChange('displayName', event.target.value)} />
        </Form.Item>
        <Form.Item label="邮箱">
          <Input aria-label="邮箱" value={form.email} onChange={(event) => onChange('email', event.target.value)} />
        </Form.Item>
        <Form.Item label="手机号">
          <Input aria-label="手机号" value={form.phone} onChange={(event) => onChange('phone', event.target.value)} />
        </Form.Item>
        <Form.Item label="头像地址">
          <Input aria-label="头像地址" value={form.avatarUrl} onChange={(event) => onChange('avatarUrl', event.target.value)} />
        </Form.Item>
        <Form.Item label="用户类型">
          <Select
            value={form.userType}
            options={[
              { value: 'ADMIN', label: '管理员' },
              { value: 'USER', label: '普通用户' },
            ]}
            onChange={(value) => onChange('userType', value)}
          />
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
      </Form>
    </Modal>
  );
}

function ResetPasswordDialog({
  newPassword,
  resetting,
  open,
  onClose,
  onChange,
  onSubmit,
}: {
  newPassword: string;
  resetting: boolean;
  open: boolean;
  onClose: () => void;
  onChange: (value: string) => void;
  onSubmit: () => void;
}) {
  return (
    <Modal
      destroyOnHidden
      confirmLoading={resetting}
      okButtonProps={{ danger: true, icon: <SaveOutlined />, 'aria-label': '确认重置' }}
      okText="确认重置"
      open={open}
      title="重置用户密码"
      onCancel={onClose}
      onOk={onSubmit}
    >
      <Typography.Paragraph type="secondary">请输入新的登录密码并确认重置。</Typography.Paragraph>
      <Form layout="vertical">
        <Form.Item label="新密码" required>
          <Input.Password
            aria-label="新密码"
            value={newPassword}
            onChange={(event) => onChange(event.target.value)}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function toStatusLabel(status: string): string {
  if (status === 'ACTIVE') return '正常';
  if (status === 'DISABLED') return '禁用';
  if (status === 'PENDING') return '待审核';
  return status;
}

function normalizeStatus(status: string): AdminUserStatus {
  if (status === 'ACTIVE' || status === 'DISABLED' || status === 'PENDING') {
    return status;
  }
  return 'ACTIVE';
}

function toUserTypeLabel(userType: string): string {
  if (userType === 'ADMIN') return '管理员';
  if (userType === 'USER') return '普通用户';
  return userType;
}

function toStatusColor(status: string): string | undefined {
  if (status === 'ACTIVE') return 'success';
  if (status === 'DISABLED') return 'error';
  if (status === 'PENDING') return 'warning';
  return undefined;
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

function fallbackAvatar(seed: string): string {
  return `https://api.dicebear.com/9.x/thumbs/svg?seed=${encodeURIComponent(seed)}`;
}

function extractErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
