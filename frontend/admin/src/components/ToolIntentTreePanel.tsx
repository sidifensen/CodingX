import React from 'react';

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
    <div className="grid grid-cols-1 gap-lg xl:grid-cols-[minmax(320px,0.95fr)_minmax(0,1.25fr)]">
      <section
        aria-label="工具意图树"
        className="rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-sm xl:flex xl:min-h-0 xl:flex-col xl:overflow-hidden"
      >
        <div className="border-b border-border-hairline px-lg py-md">
          <h3 className="font-title-md text-title-md text-ink">工具意图树</h3>
          <p className="mt-1 text-body-sm text-secondary">按分类组织工具节点，便于快速定位和筛选。</p>
        </div>
        <div data-testid="tool-intent-tree-scroll" className="space-y-sm p-md xl:flex-1 xl:min-h-0 xl:overflow-y-auto">
          {rows.length === 0 ? (
            <div className="rounded-xl bg-surface-container-low px-lg py-xl text-center text-secondary">
              暂无工具配置
            </div>
          ) : (
            categoryGroups.map((group) => {
              const isExpanded = expandedMap[group.key] ?? true;
              return (
                <div key={group.key} className="rounded-xl border border-border-hairline bg-surface-container-lowest">
                  <button
                    type="button"
                    aria-label={`${isExpanded ? '收起' : '展开'}分类 ${group.label}`}
                    className="flex w-full items-center justify-between gap-sm px-md py-sm text-left hover:bg-surface-container-low"
                    onClick={() => toggleCategory(group.key)}
                  >
                    <span className="font-medium text-ink">{group.label}</span>
                    <span className="inline-flex items-center gap-xs text-[12px] text-secondary">
                      <span>{group.rows.length}</span>
                      <span className="material-symbols-outlined text-[18px]">
                        {isExpanded ? 'expand_more' : 'chevron_right'}
                      </span>
                    </span>
                  </button>
                  {isExpanded ? (
                    <div className="space-y-xs border-t border-border-hairline p-sm">
                      {group.rows.map((row) => {
                        const isSelected =
                          normalizeToolCode(row.toolCode) === normalizeToolCode(selectedToolCode);
                        return (
                          <button
                            key={row.toolCode}
                            type="button"
                            aria-label={`选择工具 ${resolveToolDisplayName(row)}`}
                            className={clsx(
                              'w-full rounded-lg border px-sm py-sm text-left transition-colors',
                              isSelected
                                ? 'border-border-strong bg-surface-container text-ink shadow-sm'
                                : 'border-transparent text-secondary hover:bg-surface-container-low hover:text-ink',
                            )}
                            onClick={() => onSelectTool(row.toolCode)}
                          >
                            <div className="flex items-start justify-between gap-xs">
                              <div className="min-w-0">
                                <p className="truncate font-medium text-ink">{resolveToolDisplayName(row)}</p>
                                <p className="mt-0.5 truncate font-data-mono text-[11px] text-secondary">
                                  /{row.toolCode}
                                </p>
                              </div>
                              <span
                                className={clsx(
                                  'shrink-0 rounded-full border px-2 py-0.5 text-[11px] font-medium',
                                  statusBadgeClass(row.health?.status),
                                )}
                              >
                                {row.health?.statusLabel || '未接入'}
                              </span>
                            </div>
                          </button>
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
                  <span className={clsx('rounded-full border px-2 py-0.5 text-[11px] font-medium', statusBadgeClass(selectedRow.health?.status))}>
                    {selectedRow.health?.statusLabel || '未接入'}
                  </span>
                  <span
                    className={clsx(
                      'rounded-full border px-2 py-0.5 text-[11px] font-medium',
                      selectedRow.config?.enabled === 0
                        ? 'border-border-hairline bg-surface-container-low text-secondary'
                        : 'border-border-strong bg-surface-container text-ink',
                    )}
                  >
                    {selectedRow.config?.enabled === 0 ? '停用' : '启用'}
                  </span>
                </div>
                <p className="mt-1 font-data-mono text-[12px] text-secondary">/{selectedRow.toolCode}</p>
              </div>

              <div className="flex flex-wrap gap-xs">
                <button
                  type="button"
                  aria-label={`探测工具 ${selectedRow.toolCode}`}
                  className="rounded-lg border border-border-hairline bg-surface-container-lowest px-md py-2 text-button font-button text-secondary transition-colors hover:bg-surface-container-low hover:text-ink disabled:opacity-60"
                  onClick={() => onPingTool(selectedRow.toolCode)}
                  disabled={pingingToolCode === selectedRow.toolCode}
                >
                  {pingingToolCode === selectedRow.toolCode ? '探测中...' : '探测'}
                </button>
                <button
                  type="button"
                  aria-label={`调用工具 ${selectedRow.toolCode}`}
                  className="rounded-lg border border-border-strong bg-surface-container-lowest px-md py-2 text-button font-button text-ink hover:bg-surface-container-low"
                  onClick={() => onInvokeTool(selectedRow.toolCode, selectedRow.health?.sampleQuestion)}
                >
                  调用
                </button>
                <button
                  type="button"
                  aria-label={`编辑工具 ${selectedRow.toolCode}`}
                  className="rounded-lg border border-border-strong bg-surface-container-lowest px-md py-2 text-button font-button text-ink hover:bg-surface-container-low disabled:opacity-60"
                  onClick={() => (selectedRow.config ? onEditTool(selectedRow.config) : null)}
                  disabled={!selectedRow.config}
                >
                  编辑
                </button>
                <button
                  type="button"
                  aria-label={`删除工具 ${selectedRow.toolCode}`}
                  className="rounded-lg border border-error bg-error-container px-md py-2 text-button font-button text-on-error-container hover:opacity-90 disabled:opacity-60"
                  onClick={() => (selectedRow.config ? onDeleteTool(selectedRow.config) : null)}
                  disabled={!selectedRow.config}
                >
                  删除
                </button>
              </div>
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
          <div className="p-xl text-center text-secondary">请先在左侧选择工具节点</div>
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

