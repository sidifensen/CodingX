import { ApiResponseParser } from './apiResponse';
import { AuthStorage } from '../utils/authStorage';
import {
  AdminUserCreatePayload,
  AdminUserDetail,
  AdminUserListQuery,
  AdminUserPageResult,
  AdminUserStatus,
  AdminUserUpdatePayload,
} from '../types/adminUser';

/**
 * 统一封装管理端用户管理接口调用，复用现有鉴权头与错误语义。
 */
export class AdminUserApi {
  static async listUsers(query: AdminUserListQuery = {}): Promise<AdminUserPageResult> {
    const searchParams = new URLSearchParams();
    searchParams.set('current', String(query.current ?? 1));
    searchParams.set('size', String(query.size ?? 10));
    searchParams.set('status', query.status ?? 'ALL');
    if (query.keyword && query.keyword.trim()) {
      searchParams.set('keyword', query.keyword.trim());
    }
    return this.request<AdminUserPageResult>(`/api/admin/users?${searchParams.toString()}`);
  }

  static async getUserDetail(userId: string | number): Promise<AdminUserDetail> {
    return this.request<AdminUserDetail>(`/api/admin/users/${encodeURIComponent(String(userId))}`);
  }

  static async createUser(payload: AdminUserCreatePayload): Promise<AdminUserDetail> {
    return this.request<AdminUserDetail>('/api/admin/users', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  static async updateUser(userId: string | number, payload: AdminUserUpdatePayload): Promise<void> {
    await this.request<AdminUserDetail>(`/api/admin/users/${encodeURIComponent(String(userId))}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  }

  static async updateUserStatus(userId: string | number, status: AdminUserStatus): Promise<void> {
    await this.request<void>(`/api/admin/users/${encodeURIComponent(String(userId))}/status`, {
      method: 'POST',
      body: JSON.stringify({ status }),
    });
  }

  static async approveUser(userId: string | number): Promise<void> {
    await this.request<void>(`/api/admin/users/${encodeURIComponent(String(userId))}/approve`, {
      method: 'POST',
    });
  }

  static async resetPassword(userId: string | number, newPassword: string): Promise<void> {
    await this.request<void>(`/api/admin/users/${encodeURIComponent(String(userId))}/reset-password`, {
      method: 'POST',
      body: JSON.stringify({ newPassword }),
    });
  }

  private static async request<T>(path: string, init?: RequestInit): Promise<T> {
    const token = AuthStorage.getSession()?.token ?? '';
    const response = await fetch(path, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        satoken: token,
        ...(init?.headers ?? {}),
      },
    });

    const envelope = await ApiResponseParser.parseEnvelope<T>(response, '用户管理请求失败');
    ApiResponseParser.assertSuccess(response, envelope, '用户管理请求失败');
    return envelope.data;
  }
}
