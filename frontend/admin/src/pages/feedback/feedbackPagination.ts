import type { AdminChatMessageFeedback, AdminPageResult } from '../../api/adminChatApi';

export interface FeedbackPaginationState {
  current: number;
  pages: number;
  pageSize: number;
  total: number;
}

/**
 * 归一化反馈分页响应，兼容历史接口返回 records 但 total/pages 无效的情况。
 */
export function normalizeFeedbackPagination(
  pageData: AdminPageResult<AdminChatMessageFeedback> | null,
  recordCount: number,
  fallbackSize: number,
  fallbackCurrent: number,
): FeedbackPaginationState {
  if (!pageData) {
    return { current: fallbackCurrent, pages: 1, pageSize: fallbackSize, total: 0 };
  }
  const rawTotal = Number(pageData.total);
  const rawPages = Number(pageData.pages);
  const rawSize = Number(pageData.size);
  const rawCurrent = Number(pageData.current);
  const safeSize = Number.isFinite(rawSize) && rawSize > 0 ? rawSize : fallbackSize;

  /**
   * 部分历史/降级接口会返回 records，但 total/pages 都是 0；此时按“一页完整数据”
   * 展示，避免 AntD 根据 dataSource 长度在前端拆出并不存在的第 2 页。
   */
  if (recordCount > 0 && (!Number.isFinite(rawTotal) || rawTotal <= 0) && (!Number.isFinite(rawPages) || rawPages <= 0)) {
    return {
      current: 1,
      pages: 1,
      pageSize: Math.max(safeSize, recordCount),
      total: recordCount,
    };
  }

  const total = Number.isFinite(rawTotal) && rawTotal >= 0 ? rawTotal : recordCount;
  const pageSize = safeSize;
  const pages = Number.isFinite(rawPages) && rawPages > 0
    ? rawPages
    : Math.max(1, Math.ceil(Math.max(total, recordCount) / Math.max(1, pageSize)));
  // 后端分页结果可能在筛选收窄后短暂返回越界页码，展示层统一夹到有效范围。
  const current = Math.min(Math.max(1, Number.isFinite(rawCurrent) ? rawCurrent : fallbackCurrent), Math.max(1, pages));
  return {
    current,
    pages,
    pageSize,
    total,
  };
}
