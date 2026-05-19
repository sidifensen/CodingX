import React from 'react';
import { useNavigate } from 'react-router-dom';
import clsx from 'clsx';

import { AdminUserApi } from '../api/adminUserApi';
import { DataTableCard } from '../components/DataTableCard';
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
 * 管理端用户管理页：对接真实后端并提供新增/审核/启停操作。
 */
export function Users() {
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
  const [createFormError, setCreateFormError] = React.useState('');

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
   * 处理审核通过动作。
   * @param userId 用户 ID。
   */
  const handleApprove = async (userId: string | number) => {
    try {
      await AdminUserApi.approveUser(userId);
      await loadUsers(pageResult.current, pageResult.size, filter);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '审核通过失败'));
    }
  };

  /**
   * 处理启用/禁用切换。
   * @param user 当前用户。
   */
  const handleToggleStatus = async (user: AdminUserSummary) => {
    const nextStatus: AdminUserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await AdminUserApi.updateUserStatus(user.id, nextStatus);
      await loadUsers(pageResult.current, pageResult.size, filter);
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '状态更新失败'));
    }
  };

  /**
   * 处理新增用户提交。
   */
  const handleCreateUser = async () => {
    setCreateFormError('');
    if (!createForm.username.trim() || !createForm.displayName.trim() || !createForm.password.trim()) {
      setCreateFormError('用户名、展示名称和初始密码不能为空');
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
    } catch (error) {
      setCreateFormError(extractErrorMessage(error, '新增用户失败'));
    } finally {
      setIsCreating(false);
    }
  };

  const onSelectFilter = (nextFilter: UserFilterTab) => {
    setFilter(nextFilter);
    setPageResult((previous) => ({ ...previous, current: 1 }));
  };

  /**
   * 表格底部摘要：统一在 DataTableCard 底部展示当前分页区间。
   */
  const tableSummaryText = React.useMemo(() => {
    if (pageResult.total <= 0) {
      return '显示 0 条，共 0 条';
    }
    const start = (pageResult.current - 1) * pageResult.size + 1;
    const end = Math.min(pageResult.current * pageResult.size, pageResult.total);
    return `显示 ${start}-${end} 条，共 ${pageResult.total} 条`;
  }, [pageResult]);

  return (
    <div className="p-lg w-full">
      <div className="mb-lg flex justify-between items-end">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">用户管理</h2>
          <p className="text-secondary mt-1">管理系统内的所有用户账户、角色分配及其活跃状态。</p>
        </div>
        <button
          type="button"
          className="bg-primary text-on-primary px-lg py-2 rounded-lg font-button text-button flex items-center gap-xs active:scale-95 transition-transform"
          onClick={() => {
            setCreateForm(emptyCreateUserForm);
            setCreateFormError('');
            setIsCreateDialogOpen(true);
          }}
        >
          <span className="material-symbols-outlined text-[18px]">person_add</span>
          新增用户
        </button>
      </div>

      {errorMessage ? (
        <div className="mb-lg rounded-xl border border-error bg-error-container px-md py-sm text-sm text-on-error-container">
          {errorMessage}
        </div>
      ) : null}

      <div className="bg-surface-container-lowest border border-border-hairline rounded-xl p-md mb-lg">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-md">
            <span className="font-label-caps text-label-caps text-secondary">状态筛选</span>
            <div className="flex bg-surface-container-low p-1 rounded-lg">
              {(['全部', '正常', '禁用', '待审核'] as UserFilterTab[]).map((tab) => (
                <button
                  key={tab}
                  onClick={() => onSelectFilter(tab)}
                  className={clsx(
                    'px-lg py-1.5 rounded-md text-body-sm transition-all',
                    filter === tab ? 'text-primary font-bold bg-surface-container-lowest shadow-sm' : 'text-secondary hover:text-ink',
                  )}
                >
                  {tab}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-12 gap-md mb-lg">
        <StatCard title="总用户数" value={String(userStats.total)} extra="" extraClassName="text-status-running" />
        <StatCard title="正常用户" value={String(userStats.active)} extra="" extraClassName="text-status-running" />
        <StatCard title="待审核" value={String(userStats.pending)} extra="需处理" extraClassName="text-status-pending" />
        <StatCard title="已禁用" value={String(userStats.disabled)} extra="" extraClassName="text-secondary" />
      </div>

      <DataTableCard
        scrollTestId="users-table-scroll"
        summaryTestId="users-table-summary"
        loading={isLoading}
        loadingText="用户加载中..."
        summaryText={tableSummaryText}
        paginationCurrent={pageResult.current}
        paginationPages={Math.max(pageResult.pages, 1)}
        onPaginationChange={(nextPage) => {
          void loadUsers(nextPage, pageResult.size, filter);
        }}
        tableContent={(
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-surface-container-low border-b border-border-hairline">
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline">用户</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline">角色</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline">状态</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline">最近登录</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline">创建时间</th>
                <th className="px-lg py-md font-label-caps text-label-caps text-secondary border-b border-border-hairline text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {pageResult.records.length === 0 ? (
                <tr>
                  <td className="px-lg py-lg text-secondary" colSpan={6}>
                    当前筛选条件下暂无用户
                  </td>
                </tr>
              ) : (
                pageResult.records.map((user) => (
                  <tr
                    key={String(user.id)}
                    className="hover:bg-surface-container-low transition-colors group cursor-pointer"
                    onClick={() => navigate(`/users/${user.id}`)}
                  >
                    <td className="px-lg py-md">
                      <div className="flex items-center gap-md">
                        <img
                          className="w-10 h-10 rounded-full border border-border-hairline bg-surface-container-low object-cover"
                          src={user.avatarUrl || fallbackAvatar(user)}
                          alt="User"
                        />
                        <div>
                          <div className="font-title-md text-ink leading-tight group-hover:text-status-preview transition-colors">{user.displayName}</div>
                          <div className="text-secondary text-[12px]">{user.email || `${user.username}@codingx.local`}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-lg py-md">
                      <span
                        className={clsx(
                          'px-2 py-0.5 rounded text-[11px] font-bold uppercase',
                          user.userType === 'ADMIN' ? 'bg-primary text-on-primary' : 'border border-border-strong text-secondary',
                        )}
                      >
                        {user.userTypeLabel || toUserTypeLabel(user.userType)}
                      </span>
                    </td>
                    <td className="px-lg py-md">
                      <div className="flex items-center gap-xs">
                        <div className={clsx('w-2 h-2 rounded-full', toStatusDotClass(user.status))}></div>
                        <span className={clsx('font-medium', toStatusTextClass(user.status))}>{user.statusLabel || toStatusLabel(user.status)}</span>
                      </div>
                    </td>
                    <td className="px-lg py-md font-data-mono text-secondary">{formatDateTime(user.lastLoginAt)}</td>
                    <td className="px-lg py-md font-data-mono text-secondary">{formatDateTime(user.createdAt)}</td>
                    <td className="px-lg py-md text-right">
                      <div className="flex items-center justify-end gap-md">
                        {user.status === 'PENDING' ? (
                          <button
                            className="text-primary hover:underline transition-colors font-bold"
                            onClick={(event) => {
                              event.stopPropagation();
                              void handleApprove(user.id);
                            }}
                          >
                            审核通过
                          </button>
                        ) : (
                          <button
                            className="text-secondary hover:text-ink transition-colors font-medium"
                            onClick={(event) => {
                              event.stopPropagation();
                              navigate(`/users/${user.id}`);
                            }}
                          >
                            详情
                          </button>
                        )}
                        <button
                          type="button"
                          aria-label={user.status === 'ACTIVE' ? '禁用用户' : '启用用户'}
                          className={clsx(
                            'w-10 h-5 rounded-full relative cursor-pointer hover:opacity-80 transition-opacity',
                            user.status === 'ACTIVE' ? 'bg-ink' : 'bg-surface-container-highest',
                          )}
                          onClick={(event) => {
                            event.stopPropagation();
                            void handleToggleStatus(user);
                          }}
                        >
                          <span
                            className={clsx(
                              'absolute top-1 w-3 h-3 bg-surface-container-lowest rounded-full shadow-sm transition-all',
                              user.status === 'ACTIVE' ? 'right-1' : 'left-1',
                            )}
                          />
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

      {isCreateDialogOpen ? (
        <CreateUserDialog
          form={createForm}
          formError={createFormError}
          creating={isCreating}
          onChange={(field, value) => setCreateForm((previous) => ({ ...previous, [field]: value }))}
          onClose={() => setIsCreateDialogOpen(false)}
          onSubmit={() => void handleCreateUser()}
        />
      ) : null}
    </div>
  );
}

function CreateUserDialog({
  form,
  formError,
  creating,
  onChange,
  onClose,
  onSubmit,
}: {
  form: CreateUserDialogState;
  formError: string;
  creating: boolean;
  onChange: (field: keyof CreateUserDialogState, value: string) => void;
  onClose: () => void;
  onSubmit: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 px-md py-lg">
      <div role="dialog" aria-modal="true" aria-label="新增用户" className="w-full max-w-2xl rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-2xl">
        <div className="flex items-start justify-between gap-md border-b border-border-hairline px-lg py-md">
          <div>
            <h3 className="font-title-md text-title-md text-ink">新增用户</h3>
            <p className="mt-1 text-body-sm text-secondary">填写基础账号信息，创建后可在详情页继续维护。</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-sm py-xs text-secondary transition-colors hover:bg-surface-container-low hover:text-ink"
            aria-label="关闭新增用户弹窗"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        <div className="space-y-md p-lg">
          {formError ? <div className="rounded-xl border border-error bg-error-container px-md py-sm text-sm text-on-error-container">{formError}</div> : null}
          <div className="grid gap-md md:grid-cols-2">
            <DialogTextField label="用户名" value={form.username} onChange={(value) => onChange('username', value)} />
            <DialogTextField label="展示名称" value={form.displayName} onChange={(value) => onChange('displayName', value)} />
            <DialogTextField label="初始密码" type="password" value={form.password} onChange={(value) => onChange('password', value)} />
            <DialogTextField label="邮箱" value={form.email} onChange={(value) => onChange('email', value)} />
            <DialogTextField label="手机号" value={form.phone} onChange={(value) => onChange('phone', value)} />
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
              disabled={creating}
              className="rounded-lg border border-border-strong bg-surface-container-lowest px-lg py-2 font-button text-button text-ink hover:bg-surface-container-low disabled:opacity-60"
            >
              取消
            </button>
            <button
              type="button"
              onClick={onSubmit}
              disabled={creating}
              className="rounded-lg bg-primary px-lg py-2 font-button text-button text-on-primary disabled:opacity-60"
            >
              {creating ? '创建中...' : '创建用户'}
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

function StatCard({ title, value, extra, extraClassName }: { title: string; value: string; extra: string; extraClassName: string }) {
  return (
    <div className="col-span-3 bg-surface-container-lowest border border-border-hairline p-md rounded-xl shadow-sm">
      <p className="font-label-caps text-label-caps text-secondary mb-1">{title}</p>
      <div className="flex items-baseline gap-xs">
        <h3 className="font-metric-lg text-metric-lg text-ink">{value}</h3>
        {extra ? <span className={clsx('text-[12px] font-bold', extraClassName)}>{extra}</span> : null}
      </div>
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

function toUserTypeLabel(userType: string): string {
  if (userType === 'ADMIN') {
    return '管理员';
  }
  if (userType === 'USER') {
    return '普通用户';
  }
  return userType;
}

function toStatusDotClass(status: string): string {
  if (status === 'ACTIVE') {
    return 'bg-status-running';
  }
  if (status === 'DISABLED') {
    return 'bg-status-failed';
  }
  return 'bg-status-pending';
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
