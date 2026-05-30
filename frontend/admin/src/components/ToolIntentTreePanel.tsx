import React from 'react';

import {
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  PlayCircleOutlined,
  RightOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Button, Empty, Space, Tag } from 'antd';
import clsx from 'clsx';

import { AdminChatTool, AdminChatToolHealthView } from '../api/adminChatApi';

interface ToolIntentTreeRow {
  toolCode: string;
  config: AdminChatTool | null;
  health: AdminChatToolHealthView | null;
}

interface ToolIntentTreePanelProps {
  rows: ToolIntentTreeRow[];
  selectedToolCode: string | null;
  pingingToolCode: string | null;
  onSelectTool: (toolCode: string) => void;
  onPingTool: (toolCode: string) => void;
  onInvokeTool: (toolCode: string, sampleQuestion?: string) => void;
  onEditTool: (tool: AdminChatTool) => void;
  onDeleteTool: (tool: AdminChatTool) => void;
}

interface ToolCategoryGroup {
  key: string;
  label: string;
  rows: ToolIntentTreeRow[];
}

const UNCATEGORIZED_LABEL = '未分类';

/**
 * 工具意图树面板：以“分类 -> 工具”双栏结构替代表格扫描，便于按意图域快速定位工具。
 */
export function ToolIntentTreePanel({
  rows,
  selectedToolCode,
  pingingToolCode,
  onSelectTool,
  onPingTool,
  onInvokeTool,
  onEditTool,
  onDeleteTool,
}: ToolIntentTreePanelProps) {
  const categoryGroups = React.useMemo(() => groupRowsByCategory(rows), [rows]);
  const [expandedMap, setExpandedMap] = React.useState<Record<string, boolean>>({});

  React.useEffect(() => {
    setExpandedMap((previous) => {
      const next: Record<string, boolean> = {};
      categoryGroups.forEach((group) => {
        next[group.key] = previous[group.key] ?? true;
      });
      return next;
    });
  }, [categoryGroups]);

  const selectedRow = React.useMemo(
    () => rows.find((row) => normalizeToolCode(row.toolCode) === normalizeToolCode(selectedToolCode)) ?? null,
    [rows, selectedToolCode],
  );

  const toggleCategory = (categoryKey: string) => {
    setExpandedMap((previous) => ({
      ...previous,
      [categoryKey]: !(previous[categoryKey] ?? true),
    }));
  };

  return (
    <div className="admin-tool-intent-layout grid grid-cols-1 gap-lg xl:grid-cols-[minmax(360px,0.95fr)_minmax(0,1.25fr)]">
      <section
        aria-label="工具意图树"
        className="admin-tool-intent-tree-panel rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-sm xl:flex xl:min-h-0 xl:flex-col xl:overflow-hidden"
      >
        <div className="border-b border-border-hairline px-lg py-md">
          <h3 className="font-title-md text-title-md text-ink">工具意图树</h3>
          <p className="mt-1 text-body-sm text-secondary">按分类组织工具节点，便于快速定位和筛选。</p>
        </div>
        <div data-testid="tool-intent-tree-scroll" className="admin-tool-intent-tree-scroll space-y-sm p-md xl:flex-1 xl:min-h-0 xl:overflow-y-auto">
          {rows.length === 0 ? (
            <Empty className="rounded-xl bg-surface-container-low px-lg py-xl" description="暂无工具配置" />
          ) : (
            categoryGroups.map((group) => {
              const isExpanded = expandedMap[group.key] ?? true;
              return (
                <div key={group.key} className="admin-tool-intent-category rounded-xl border border-border-hairline bg-surface-container-lowest">
                  {/* AntD Button 会包一层内部 span，这些稳定类名用于保证树节点内容始终全宽左对齐。 */}
                  <Button
                    aria-label={`${isExpanded ? '收起' : '展开'}分类 ${group.label}`}
                    block
                    className="admin-tool-intent-category-toggle h-auto text-left"
                    icon={isExpanded ? <DownOutlined /> : <RightOutlined />}
                    type="text"
                    onClick={() => toggleCategory(group.key)}
                  >
                    <span className="admin-tool-intent-category-summary">
                      <span className="font-medium text-ink">{group.label}</span>
                      <Tag className="m-0">{group.rows.length}</Tag>
                    </span>
                  </Button>
                  {isExpanded ? (
                    <div className="admin-tool-intent-node-list space-y-xs border-t border-border-hairline p-sm">
                      {group.rows.map((row) => {
                        const isSelected =
                          normalizeToolCode(row.toolCode) === normalizeToolCode(selectedToolCode);
                        return (
                          <Button
                            key={row.toolCode}
                            aria-label={`选择工具 ${resolveToolDisplayName(row)}`}
                            block
                            className={clsx(
                              'admin-tool-intent-tool-button',
                              isSelected
                                ? 'admin-tool-intent-tool-button-selected'
                                : '',
                            )}
                            type="text"
                            onClick={() => onSelectTool(row.toolCode)}
                          >
                            <div className="admin-tool-intent-tool-row">
                              <div className="admin-tool-intent-tool-copy">
                                <p className="admin-tool-intent-tool-name">{resolveToolDisplayName(row)}</p>
                                <p className="admin-tool-intent-tool-code">
                                  /{row.toolCode}
                                </p>
                              </div>
                              <Tag
                                className={clsx(
                                  'm-0 shrink-0 rounded-full border px-2 py-0.5 text-[11px] font-medium',
                                  statusBadgeClass(row.health?.status),
                                )}
                              >
                                {row.health?.statusLabel || '未接入'}
                              </Tag>
                            </div>
                          </Button>
                        );
                      })}
                    </div>
                  ) : null}
                </div>
              );
            })
          )}
        </div>
      </section>

      <section
        aria-label="工具详情"
        className="rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-sm xl:flex xl:min-h-0 xl:flex-col xl:overflow-hidden"
      >
        <div className="border-b border-border-hairline px-lg py-md">
          <h3 className="font-title-md text-title-md text-ink">工具详情</h3>
          <p className="mt-1 text-body-sm text-secondary">查看选中工具状态并执行探测、调用和配置维护。</p>
        </div>

        {selectedRow ? (
          <div className="space-y-lg p-lg xl:flex-1 xl:min-h-0 xl:overflow-y-auto">
            <div className="flex flex-col gap-md lg:flex-row lg:items-start lg:justify-between">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-xs">
                  <h4 className="font-title-md text-title-md text-ink">{resolveToolDisplayName(selectedRow)}</h4>
                  <Tag className={clsx('m-0 rounded-full border px-2 py-0.5 text-[11px] font-medium', statusBadgeClass(selectedRow.health?.status))}>
                    {selectedRow.health?.statusLabel || '未接入'}
                  </Tag>
                  <Tag
                    className={clsx(
                      'm-0 rounded-full border px-2 py-0.5 text-[11px] font-medium',
                      selectedRow.config?.enabled === 0
                        ? 'border-border-hairline bg-surface-container-low text-secondary'
                        : 'border-border-strong bg-surface-container text-ink',
                    )}
                  >
                    {selectedRow.config?.enabled === 0 ? '停用' : '启用'}
                  </Tag>
                </div>
                <p className="mt-1 font-data-mono text-[12px] text-secondary">/{selectedRow.toolCode}</p>
              </div>

              <Space wrap size={8}>
                <Button
                  aria-label={`探测工具 ${selectedRow.toolCode}`}
                  icon={<ThunderboltOutlined />}
                  onClick={() => onPingTool(selectedRow.toolCode)}
                  loading={pingingToolCode === selectedRow.toolCode}
                  disabled={pingingToolCode === selectedRow.toolCode}
                >
                  探测
                </Button>
                <Button
                  aria-label={`调用工具 ${selectedRow.toolCode}`}
                  icon={<PlayCircleOutlined />}
                  onClick={() => onInvokeTool(selectedRow.toolCode, selectedRow.health?.sampleQuestion)}
                >
                  调用
                </Button>
                <Button
                  aria-label={`编辑工具 ${selectedRow.toolCode}`}
                  icon={<EditOutlined />}
                  onClick={() => (selectedRow.config ? onEditTool(selectedRow.config) : null)}
                  disabled={!selectedRow.config}
                >
                  编辑
                </Button>
                <Button
                  aria-label={`删除工具 ${selectedRow.toolCode}`}
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => (selectedRow.config ? onDeleteTool(selectedRow.config) : null)}
                  disabled={!selectedRow.config}
                >
                  删除
                </Button>
              </Space>
            </div>

            <div className="grid gap-sm md:grid-cols-2">
              <DetailItem label="分类" value={resolveCategoryLabel(selectedRow)} />
              <DetailItem label="来源" value={selectedRow.config?.sourceType || selectedRow.health?.source || '-'} />
              <DetailItem label="最近探测" value={selectedRow.health?.checkedAt || '-'} />
              <DetailItem label="样例问题" value={selectedRow.health?.sampleQuestion || '-'} />
              <DetailItem label="排序" value={String(selectedRow.config?.sortNo ?? 0)} />
            </div>

            <div className="rounded-xl border border-border-hairline bg-surface-container-low px-md py-sm">
              <p className="text-[12px] text-secondary">描述</p>
              <p className="mt-1 whitespace-pre-wrap text-body-sm text-ink">
                {selectedRow.config?.description || selectedRow.health?.description || '暂无描述'}
              </p>
            </div>
          </div>
        ) : (
          <Empty className="p-xl" image={Empty.PRESENTED_IMAGE_SIMPLE} description="请先在左侧选择工具节点" />
        )}
      </section>
    </div>
  );
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border-hairline bg-surface-container-low px-md py-sm">
      <p className="text-[12px] text-secondary">{label}</p>
      <p className="mt-1 break-words text-body-sm text-ink">{value}</p>
    </div>
  );
}

/**
 * 以首出现顺序分组，保持与列表排序语义一致，避免切换视图后顺序跳变。
 */
function groupRowsByCategory(rows: ToolIntentTreeRow[]): ToolCategoryGroup[] {
  const groups = new Map<string, ToolCategoryGroup>();
  rows.forEach((row) => {
    const categoryLabel = resolveCategoryLabel(row);
    const categoryKey = normalizeCategory(categoryLabel);
    if (!groups.has(categoryKey)) {
      groups.set(categoryKey, {
        key: categoryKey,
        label: categoryLabel,
        rows: [],
      });
    }
    groups.get(categoryKey)?.rows.push(row);
  });
  return Array.from(groups.values());
}

function resolveToolDisplayName(row: ToolIntentTreeRow): string {
  return row.config?.displayName || row.health?.displayName || row.toolCode;
}

function resolveCategoryLabel(row: ToolIntentTreeRow): string {
  const category = row.config?.category || row.health?.category;
  return category?.trim() || UNCATEGORIZED_LABEL;
}

function normalizeCategory(category: string): string {
  return category.trim().toLowerCase();
}

function normalizeToolCode(code?: string | null): string {
  return (code ?? '').trim().toLowerCase();
}

function statusBadgeClass(status?: string): string {
  switch (status) {
    case 'healthy':
      return 'border-status-running/20 bg-status-running/10 text-status-running';
    case 'degraded':
      return 'border-status-pending/20 bg-status-pending/10 text-status-pending';
    default:
      return 'border-border-hairline bg-surface-container-low text-secondary';
  }
}
