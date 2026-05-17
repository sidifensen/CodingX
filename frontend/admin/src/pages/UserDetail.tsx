import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import clsx from 'clsx';

import { AdminUserApi } from '../api/adminUserApi';
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
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [user, setUser] = React.useState<AdminUserDetail | null>(null);
  const [isLoading, setIsLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');

  const [isEditDialogOpen, setIsEditDialogOpen] = React.useState(false);
  const [editForm, setEditForm] = React.useState<EditUserFormState>(emptyEditUserForm);
  const [isEditing, setIsEditing] = React.useState(false);
  const [editError, setEditError] = React.useState('');

  const [isResetDialogOpen, setIsResetDialogOpen] = React.useState(false);
  const [newPassword, setNewPassword] = React.useState('');
  const [resetError, setResetError] = React.useState('');
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
    setEditError('');
    setIsEditDialogOpen(true);
  };

  const handleSaveEdit = async () => {
    if (!id) {
      return;
    }
    setEditError('');
    if (!editForm.displayName.trim()) {
      setEditError('展示名称不能为空');
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
    } catch (error) {
      setEditError(extractErrorMessage(error, '保存用户信息失败'));
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
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '状态更新失败'));
    }
  };

  const handleResetPassword = async () => {
    if (!id) {
      return;
    }
    setResetError('');
    if (!newPassword.trim()) {
      setResetError('新密码不能为空');
      return;
    }
    setIsResetting(true);
    try {
      await AdminUserApi.resetPassword(id, newPassword.trim());
      setIsResetDialogOpen(false);
      setNewPassword('');
    } catch (error) {
      setResetError(extractErrorMessage(error, '重置密码失败'));
    } finally {
      setIsResetting(false);
    }
  };

  return (
    <div className="p-lg w-full">
      <div className="mb-lg flex items-center gap-xs">
        <button
          onClick={() => navigate(-1)}
          className="text-secondary hover:text-ink transition-colors flex items-center gap-xs active:scale-95 group"
        >
          <span className="material-symbols-outlined text-[18px] group-hover:-translate-x-1 transition-transform">arrow_back</span>
          返回用户列表
        </button>
      </div>

      {errorMessage ? (
        <div className="mb-lg rounded-xl border border-error bg-error-container px-md py-sm text-sm text-on-error-container">{errorMessage}</div>
      ) : null}

      {isLoading ? (
        <div className="rounded-xl border border-border-hairline bg-surface-container-lowest px-lg py-lg text-secondary">用户详情加载中...</div>
      ) : null}

      {!isLoading && user ? (
        <>
          <div className="mb-xl flex justify-between items-start bg-surface-container-lowest p-xl rounded-2xl border border-border-hairline shadow-sm">
            <div className="flex gap-xl items-center">
              <img
                className="w-24 h-24 rounded-full border-4 border-surface-container-low shadow-sm bg-surface-container object-cover"
                src={user.avatarUrl || fallbackAvatar(user.username)}
                alt="User"
              />
              <div className="space-y-sm">
                <h2 className="font-headline-md text-headline-md text-ink flex items-center gap-md">
                  {user.displayName}
                  <span
                    className={clsx(
                      'px-2 py-0.5 rounded text-[11px] font-bold uppercase border',
                      user.userType === 'ADMIN' ? 'bg-primary text-on-primary border-primary' : 'bg-surface-container-low text-secondary border-border-strong',
                    )}
                  >
                    {user.userTypeLabel || toUserTypeLabel(user.userType)}
                  </span>
                  <span className={clsx('px-2 py-0.5 rounded text-[11px] font-bold border', toStatusBadgeClass(user.status))}>
                    {user.statusLabel || toStatusLabel(user.status)}
                  </span>
                </h2>
                <div className="text-secondary flex items-center gap-xl">
                  <span className="flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-[18px]">mail</span> {user.email || '-'}
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-[18px]">badge</span> {user.id}
                  </span>
                </div>
              </div>
            </div>
            <div className="flex items-center gap-sm">
              <button
                type="button"
                className="border border-border-strong bg-surface-container-lowest px-md py-2 rounded-lg text-ink hover:bg-surface-container-low transition-colors active:scale-95 text-body-sm font-medium shadow-sm"
                onClick={openEditDialog}
              >
                编辑信息
              </button>
              <button
                type="button"
                className="border border-border-strong bg-surface-container-lowest px-md py-2 rounded-lg text-ink hover:bg-surface-container-low transition-colors active:scale-95 text-body-sm font-medium shadow-sm"
                onClick={() => setIsResetDialogOpen(true)}
              >
                重置密码
              </button>
              <button
                type="button"
                className="border border-error bg-error/5 text-error px-md py-2 rounded-lg hover:bg-error hover:text-on-error transition-colors active:scale-95 text-body-sm font-medium"
                onClick={() => void handleToggleStatus()}
              >
                {user.status === 'ACTIVE' ? '冻结账户' : '启用账户'}
              </button>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-lg">
            <div className="col-span-1 space-y-lg">
              <div className="bg-surface-container-lowest border border-border-hairline rounded-xl p-lg shadow-sm">
                <h3 className="font-title-sm text-ink mb-md flex items-center gap-2">
                  <span className="material-symbols-outlined text-[20px] text-secondary">info</span> 基础信息
                </h3>
                <div className="space-y-md">
                  <InfoRow label="注册时间" value={formatDateTime(user.createdAt)} />
                  <InfoRow label="最后登录时间" value={formatDateTime(user.lastLoginAt)} />
                  <InfoRow label="登录 IP" value={user.lastLoginIp || '-'} mono />
                  <InfoRow label="手机号" value={user.phone || '-'} />
                  <InfoRow label="用户名" value={user.username} mono />
                </div>
              </div>
            </div>

            <div className="col-span-2 space-y-lg">
              <div className="bg-surface-container-lowest border border-border-hairline rounded-xl p-lg shadow-sm">
                <div className="flex justify-between items-center mb-md">
                  <h3 className="font-title-sm text-ink flex items-center gap-2">
                    <span className="material-symbols-outlined text-[20px] text-secondary">admin_panel_settings</span> 账户状态
                  </h3>
                </div>
                <div className="space-y-sm">
                  <StatePill label="当前状态" value={user.statusLabel || toStatusLabel(user.status)} className={toStatusTextClass(user.status)} />
                  <StatePill label="用户类型" value={user.userTypeLabel || toUserTypeLabel(user.userType)} className="text-secondary" />
                  <StatePill label="更新时间" value={formatDateTime(user.updatedAt)} className="text-secondary" />
                </div>
              </div>
            </div>
          </div>
        </>
      ) : null}

      {isEditDialogOpen ? (
        <EditUserDialog
          form={editForm}
          errorMessage={editError}
          editing={isEditing}
          onClose={() => setIsEditDialogOpen(false)}
          onChange={(field, value) => setEditForm((previous) => ({ ...previous, [field]: value }))}
          onSubmit={() => void handleSaveEdit()}
        />
      ) : null}

      {isResetDialogOpen ? (
        <ResetPasswordDialog
          newPassword={newPassword}
          errorMessage={resetError}
          resetting={isResetting}
          onClose={() => setIsResetDialogOpen(false)}
          onChange={setNewPassword}
          onSubmit={() => void handleResetPassword()}
        />
      ) : null}
    </div>
  );
}

function InfoRow({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex flex-col">
      <span className="text-secondary text-[12px] mb-1">{label}</span>
      <span className={clsx('font-medium text-ink', mono ? 'font-data-mono text-tertiary-container' : '')}>{value}</span>
    </div>
  );
}

function StatePill({ label, value, className }: { label: string; value: string; className: string }) {
  return (
    <div className="p-md flex justify-between items-center bg-surface-container-low rounded-lg border border-border-hairline">
      <span className="text-secondary">{label}</span>
      <span className={clsx('font-medium', className)}>{value}</span>
    </div>
  );
}

function EditUserDialog({
  form,
  errorMessage,
  editing,
  onClose,
  onChange,
  onSubmit,
}: {
  form: EditUserFormState;
  errorMessage: string;
  editing: boolean;
  onClose: () => void;
  onChange: (field: keyof EditUserFormState, value: string) => void;
  onSubmit: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div role="dialog" aria-modal="true" aria-label="编辑用户信息" className="w-full max-w-2xl rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl">
        <div className="flex items-start justify-between gap-md border-b border-border-hairline px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">编辑用户信息</h3>
            <p className="mt-1 text-body-sm text-secondary">维护用户基础资料、类型和状态。</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-sm py-xs text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭编辑用户弹窗"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        <div className="space-y-md p-lg">
          {errorMessage ? <div className="rounded-xl border border-error bg-error-container px-md py-sm text-sm text-on-error-container">{errorMessage}</div> : null}
          <div className="grid gap-md md:grid-cols-2">
            <DialogTextField label="展示名称" value={form.displayName} onChange={(value) => onChange('displayName', value)} />
            <DialogTextField label="邮箱" value={form.email} onChange={(value) => onChange('email', value)} />
            <DialogTextField label="手机号" value={form.phone} onChange={(value) => onChange('phone', value)} />
            <DialogTextField label="头像地址" value={form.avatarUrl} onChange={(value) => onChange('avatarUrl', value)} />
            <DialogSelectField
              label="用户类型"
              value={form.userType}
              options={[
                { value: 'ADMIN', label: '管理员' },
                { value: 'USER', label: '普通用户' },
              ]}
              onChange={(value) => onChange('userType', value)}
            />
            <DialogSelectField
              label="状态"
              value={form.status}
              options={[
                { value: 'ACTIVE', label: '正常' },
                { value: 'DISABLED', label: '禁用' },
                { value: 'PENDING', label: '待审核' },
              ]}
              onChange={(value) => onChange('status', value)}
            />
          </div>
          <div className="flex justify-end gap-sm pt-sm">
            <button
              type="button"
              onClick={onClose}
              disabled={editing}
              className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink hover:bg-surface-container-low disabled:opacity-60"
            >
              取消
            </button>
            <button
              type="button"
              onClick={onSubmit}
              disabled={editing}
              className="rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary disabled:opacity-60"
            >
              {editing ? '保存中...' : '保存修改'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function ResetPasswordDialog({
  newPassword,
  errorMessage,
  resetting,
  onClose,
  onChange,
  onSubmit,
}: {
  newPassword: string;
  errorMessage: string;
  resetting: boolean;
  onClose: () => void;
  onChange: (value: string) => void;
  onSubmit: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div role="dialog" aria-modal="true" aria-label="重置用户密码" className="w-full max-w-lg rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl">
        <div className="flex items-start justify-between gap-md border-b border-border-hairline px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">重置用户密码</h3>
            <p className="mt-1 text-body-sm text-secondary">请输入新的登录密码并确认重置。</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-sm py-xs text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭重置密码弹窗"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>
        <div className="space-y-md p-lg">
          {errorMessage ? <div className="rounded-xl border border-error bg-error-container px-md py-sm text-sm text-on-error-container">{errorMessage}</div> : null}
          <DialogTextField label="新密码" type="password" value={newPassword} onChange={onChange} />
          <div className="flex justify-end gap-sm pt-sm">
            <button
              type="button"
              onClick={onClose}
              disabled={resetting}
              className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink hover:bg-surface-container-low disabled:opacity-60"
            >
              取消
            </button>
            <button
              type="button"
              onClick={onSubmit}
              disabled={resetting}
              className="rounded-lg border border-error bg-error-container px-lg py-2 font-button text-button text-on-error-container disabled:opacity-60"
            >
              {resetting ? '重置中...' : '确认重置'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function DialogTextField({
  label,
  value,
  type = 'text',
  onChange,
}: {
  label: string;
  value: string;
  type?: string;
  onChange: (value: string) => void;
}) {
  return (
    <div>
      <label className="mb-1 block text-[12px] font-medium text-secondary">{label}</label>
      <input
        aria-label={label}
        type={type}
        value={value}
        className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-ink outline-none transition-colors focus:border-border-strong"
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}

function DialogSelectField({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: Array<{ value: string; label: string }>;
  onChange: (value: string) => void;
}) {
  return (
    <div>
      <label className="mb-1 block text-[12px] font-medium text-secondary">{label}</label>
      <select
        aria-label={label}
        value={value}
        className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-ink outline-none transition-colors focus:border-border-strong"
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
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

function normalizeStatus(status: string): AdminUserStatus {
  if (status === 'ACTIVE' || status === 'DISABLED' || status === 'PENDING') {
    return status;
  }
  return 'ACTIVE';
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

function toStatusBadgeClass(status: string): string {
  if (status === 'ACTIVE') {
    return 'bg-status-running-bg text-status-running border-status-running-border';
  }
  if (status === 'DISABLED') {
    return 'bg-status-failed-bg text-status-failed border-status-failed-border';
  }
  return 'bg-status-pending-bg text-status-pending border-status-pending-border';
}

function toStatusTextClass(status: string): string {
  if (status === 'ACTIVE') {
    return 'text-status-running';
  }
  if (status === 'DISABLED') {
    return 'text-status-failed';
  }
  return 'text-status-pending';
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

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
