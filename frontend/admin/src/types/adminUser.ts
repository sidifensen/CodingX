/**
 * 管理端用户状态枚举。
 */
export type AdminUserStatus = 'ACTIVE' | 'DISABLED' | 'PENDING';

/**
 * 管理端用户类型枚举。
 */
export type AdminUserType = 'ADMIN' | 'USER';

/**
 * 管理端用户列表项数据结构。
 */
export interface AdminUserSummary {
  id: string | number;
  username: string;
  displayName: string;
  email?: string | null;
  phone?: string | null;
  avatarUrl?: string | null;
  userType: AdminUserType | string;
  userTypeLabel: string;
  status: AdminUserStatus | string;
  statusLabel: string;
  lastLoginAt?: string | null;
  createdAt?: string | null;
}

/**
 * 管理端用户详情数据结构。
 */
export interface AdminUserDetail extends AdminUserSummary {
  lastLoginIp?: string | null;
  updatedAt?: string | null;
}

/**
 * 管理端分页返回结构。
 */
export interface AdminUserPageResult {
  records: AdminUserSummary[];
  total: number;
  current: number;
  size: number;
  pages: number;
}

/**
 * 用户列表查询参数。
 */
export interface AdminUserListQuery {
  current?: number;
  size?: number;
  status?: 'ALL' | AdminUserStatus;
  keyword?: string;
}

/**
 * 新增用户请求参数。
 */
export interface AdminUserCreatePayload {
  username: string;
  displayName: string;
  password: string;
  userType?: AdminUserType;
  status?: AdminUserStatus;
  email?: string;
  phone?: string;
  avatarUrl?: string;
}

/**
 * 编辑用户请求参数。
 */
export interface AdminUserUpdatePayload {
  displayName: string;
  userType?: AdminUserType;
  status?: AdminUserStatus;
  email?: string;
  phone?: string;
  avatarUrl?: string;
}
