import React from 'react';
import clsx from 'clsx';
import { Button, Space, Table, Tooltip } from 'antd';
import type { ButtonProps, TableProps } from 'antd';

interface AdminDataTablePagination {
  current: number;
  pageSize: number;
  total: number;
  onChange: (page: number, pageSize: number) => void;
  showSizeChanger?: boolean;
}

export interface AdminDataTableProps<RecordType extends object>
  extends Omit<TableProps<RecordType>, 'bordered' | 'pagination' | 'size'> {
  /**
   * 统一分页配置：由组件集中生成中文摘要，避免各页面重复实现分页文案。
   */
  pagination?: false | AdminDataTablePagination;
}

export interface AdminTableAction {
  key: React.Key;
  label: string;
  /**
   * 操作列按钮强制传入图标，保证表格右侧操作区有稳定的视觉锚点。
   */
  icon: React.ReactNode;
  ariaLabel?: string;
  danger?: boolean;
  disabled?: boolean;
  href?: string;
  loading?: boolean;
  testId?: string;
  type?: ButtonProps['type'];
  onClick?: ButtonProps['onClick'];
}

interface AdminTableActionsProps {
  actions: AdminTableAction[];
}

/**
 * 管理端统一 AntD 表格：关闭 bordered 竖线，集中控制单元格留白、分页摘要与空状态。
 */
export function AdminDataTable<RecordType extends object>({
  className,
  pagination,
  ...tableProps
}: AdminDataTableProps<RecordType>) {
  const mergedPagination = React.useMemo<TableProps<RecordType>['pagination']>(() => {
    if (!pagination) {
      return pagination;
    }
    const safePageSize = Math.max(1, pagination.pageSize);
    /**
     * AntD 会把它推导出的实时 total 传给 showTotal；这里必须优先使用回调参数，
     * 不能只闭包读取 pagination.total，否则在数据源长度与外部 total 短暂不一致时
     * 会出现“页码已更新，摘要仍停留旧值”的错位。
     */
    const resolveTotal = (total?: number) => {
      if (typeof total === 'number' && Number.isFinite(total)) {
        return total;
      }
      return pagination.total;
    };
    return {
      current: pagination.current,
      pageSize: safePageSize,
      total: pagination.total,
      showSizeChanger: pagination.showSizeChanger ?? false,
      showTotal: (total) => {
        const liveTotal = resolveTotal(total);
        const livePages = Math.max(1, Math.ceil(liveTotal / safePageSize));
        return `第 ${pagination.current} / ${livePages} 页，共 ${liveTotal.toLocaleString('zh-CN')} 条`;
      },
      itemRender: (page, type, originalElement) => {
        if (type !== 'page') {
          return originalElement;
        }
        return (
          <Button
            aria-label={`第 ${page} 页`}
            className="admin-pagination-page-button"
            size="small"
            type="text"
            onClick={(event) => {
              event.stopPropagation();
              pagination.onChange(Number(page), safePageSize);
            }}
          >
            {page}
          </Button>
        );
      },
      onChange: pagination.onChange,
    };
  }, [pagination]);

  return (
    <Table<RecordType>
      {...tableProps}
      bordered={false}
      className={clsx('admin-data-table', className)}
      pagination={mergedPagination}
      size="middle"
    />
  );
}

/**
 * 表格操作列按钮组：所有操作按钮都通过 AntD Button 与 icon 渲染，保持各页面一致。
 */
export function AdminTableActions({ actions }: AdminTableActionsProps) {
  return (
    <Space className="admin-table-actions" size={8} wrap>
      {actions.map((action) => (
        <Tooltip key={action.key} title={action.label}>
          <Button
            aria-label={action.ariaLabel || action.label}
            danger={action.danger}
            data-testid={action.testId}
            disabled={action.disabled}
            href={action.href}
            icon={action.icon}
            loading={action.loading}
            size="small"
            type={action.type}
            onClick={action.onClick}
          >
            {action.label}
          </Button>
        </Tooltip>
      ))}
    </Space>
  );
}
