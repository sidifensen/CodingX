import React from 'react';
import clsx from 'clsx';
import { AdminChatApi, AdminRuntimeSetting } from '../api/adminChatApi';

type SettingsViewMode = 'cards' | 'navigator' | 'compact';

interface GroupedCategory {
  categoryCode: string;
  categoryLabel: string;
  settings: AdminRuntimeSetting[];
}

interface CategoryOverview {
  total: number;
  modified: number;
  restartRequired: number;
}

const VIEW_MODE_META: Array<{ mode: SettingsViewMode; label: string }> = [
  { mode: 'cards', label: '分组卡片' },
  { mode: 'navigator', label: '目录导航' },
  { mode: 'compact', label: '紧凑表格' },
];

const CATEGORY_LABELS: Record<string, string> = {
  'chat.memory': '聊天历史压缩',
  search: '搜索链路',
  queue: '并发门控',
  code_search: '代码检索运行时',
  'ai.routing': '模型路由',
};

/**
 * 系统配置管理页：提供多种展示模式，降低配置项增多后的浏览和维护成本。
 */
export function Settings() {
  const [settings, setSettings] = React.useState<AdminRuntimeSetting[]>([]);
  const [draftSettings, setDraftSettings] = React.useState<AdminRuntimeSetting[]>([]);
  const [saving, setSaving] = React.useState(false);
  const [loading, setLoading] = React.useState(true);
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);
  const [successMessage, setSuccessMessage] = React.useState<string | null>(null);
  const [viewMode, setViewMode] = React.useState<SettingsViewMode>('cards');
  const [searchKeyword, setSearchKeyword] = React.useState('');
  const [expandedCategories, setExpandedCategories] = React.useState<Record<string, boolean>>({});
  const [activeCategory, setActiveCategory] = React.useState<string>('');
  const [tableCategoryFilter, setTableCategoryFilter] = React.useState('all');

  React.useEffect(() => {
    void loadSettings();
  }, []);

  const settingMapByKey = React.useMemo(() => {
    const map = new Map<string, AdminRuntimeSetting>();
    settings.forEach((item) => {
      map.set(item.settingKey, item);
    });
    return map;
  }, [settings]);

  const draftMapByKey = React.useMemo(() => {
    const map = new Map<string, AdminRuntimeSetting>();
    draftSettings.forEach((item) => {
      map.set(item.settingKey, item);
    });
    return map;
  }, [draftSettings]);

  const keyword = searchKeyword.trim().toLowerCase();
  const modifiedSettingKeys = React.useMemo(() => {
    const keys: string[] = [];
    draftSettings.forEach((item) => {
      const original = settingMapByKey.get(item.settingKey);
      if (!original) {
        return;
      }
      if ((original.settingValue ?? '') !== (item.settingValue ?? '')) {
        keys.push(item.settingKey);
      }
    });
    return new Set(keys);
  }, [draftSettings, settingMapByKey]);

  const groupedCategories = React.useMemo(() => {
    const grouped = new Map<string, AdminRuntimeSetting[]>();
    draftSettings
      .slice()
      .sort((left, right) => {
        const categoryCompare = String(left.categoryCode ?? '').localeCompare(String(right.categoryCode ?? ''));
        if (categoryCompare !== 0) {
          return categoryCompare;
        }
        const sortCompare = Number(left.sortNo ?? 0) - Number(right.sortNo ?? 0);
        if (sortCompare !== 0) {
          return sortCompare;
        }
        return left.settingKey.localeCompare(right.settingKey);
      })
      .forEach((setting) => {
        const categoryCode = setting.categoryCode ?? 'general';
        const bucket = grouped.get(categoryCode) ?? [];
        bucket.push(setting);
        grouped.set(categoryCode, bucket);
      });

    return Array.from(grouped.entries()).map(([categoryCode, categorySettings]) => ({
      categoryCode,
      categoryLabel: CATEGORY_LABELS[categoryCode] ?? categoryCode,
      settings: categorySettings,
    }));
  }, [draftSettings]);

  React.useEffect(() => {
    if (groupedCategories.length === 0) {
      setActiveCategory('');
      return;
    }
    setActiveCategory((previous) => {
      if (previous && groupedCategories.some((category) => category.categoryCode === previous)) {
        return previous;
      }
      return groupedCategories[0].categoryCode;
    });
  }, [groupedCategories]);

  React.useEffect(() => {
    if (groupedCategories.length === 0) {
      setExpandedCategories({});
      return;
    }
    setExpandedCategories((previous) => {
      const next: Record<string, boolean> = {};
      groupedCategories.forEach((category) => {
        if (typeof previous[category.categoryCode] === 'boolean') {
          next[category.categoryCode] = previous[category.categoryCode];
        } else {
          next[category.categoryCode] = true;
        }
      });
      return next;
    });
  }, [groupedCategories]);

  const filteredCategories = React.useMemo(() => {
    const result: GroupedCategory[] = [];
    groupedCategories.forEach((category) => {
      const matchedSettings = category.settings.filter((setting) => {
        if (!keyword) {
          return true;
        }
        const searchableText = [
          setting.settingKey,
          setting.description,
          setting.settingValue,
          category.categoryLabel,
          category.categoryCode,
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        return searchableText.includes(keyword);
      });
      if (matchedSettings.length > 0) {
        result.push({
          categoryCode: category.categoryCode,
          categoryLabel: category.categoryLabel,
          settings: matchedSettings,
        });
      }
    });
    return result;
  }, [groupedCategories, keyword, modifiedSettingKeys]);

  const overviewByCategory = React.useMemo(() => {
    const map = new Map<string, CategoryOverview>();
    groupedCategories.forEach((category) => {
      const summary: CategoryOverview = {
        total: category.settings.length,
        modified: category.settings.filter((setting) => modifiedSettingKeys.has(setting.settingKey)).length,
        restartRequired: category.settings.filter((setting) => setting.restartRequired === true).length,
      };
      map.set(category.categoryCode, summary);
    });
    return map;
  }, [groupedCategories, modifiedSettingKeys]);

  const activeCategoryEntry = React.useMemo(
    () => filteredCategories.find((category) => category.categoryCode === activeCategory) ?? filteredCategories[0],
    [filteredCategories, activeCategory],
  );

  const compactRows = React.useMemo(() => {
    const rows = filteredCategories.flatMap((category) =>
      category.settings.map((setting) => ({
        categoryCode: category.categoryCode,
        categoryLabel: category.categoryLabel,
        setting,
      })),
    );
    if (tableCategoryFilter === 'all') {
      return rows;
    }
    return rows.filter((item) => item.categoryCode === tableCategoryFilter);
  }, [filteredCategories, tableCategoryFilter]);

  async function loadSettings() {
    try {
      setLoading(true);
      const loaded = await AdminChatApi.listSettings();
      setSettings(loaded);
      setDraftSettings(loaded);
      setErrorMessage(null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '加载系统配置失败');
    } finally {
      setLoading(false);
    }
  }

  function updateDraft(settingKey: string, nextValue: string) {
    setDraftSettings((previous) =>
      previous.map((item) => (item.settingKey === settingKey ? { ...item, settingValue: nextValue } : item)),
    );
  }

  function restoreDraft() {
    setDraftSettings(settings);
    setErrorMessage(null);
    setSuccessMessage(null);
  }

  async function saveDraft() {
    try {
      setSaving(true);
      const saved = await AdminChatApi.saveSettings(draftSettings);
      setSettings(saved);
      setDraftSettings(saved);
      setSuccessMessage('系统配置已保存并刷新缓存');
      setErrorMessage(null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '保存系统配置失败');
      setSuccessMessage(null);
    } finally {
      setSaving(false);
    }
  }

  function inputTypeByValueType(valueType?: string): React.HTMLInputTypeAttribute {
    const normalized = String(valueType ?? '').toUpperCase();
    if (normalized === 'INTEGER' || normalized === 'LONG') {
      return 'number';
    }
    return 'text';
  }

  function isModified(settingKey: string): boolean {
    return modifiedSettingKeys.has(settingKey);
  }

  function toggleCategory(categoryCode: string) {
    setExpandedCategories((previous) => ({
      ...previous,
      [categoryCode]: !previous[categoryCode],
    }));
  }

  return (
    <div className="w-full space-y-md p-lg">
      <div className="flex flex-col gap-md xl:flex-row xl:items-end xl:justify-between">
        <div>
          <h2 className="font-headline-md text-headline-md text-ink">系统配置</h2>
          <p className="mt-1 text-secondary">支持分组卡片、目录导航、紧凑表格三种视图，适配配置规模增长。</p>
        </div>
        <div className="flex flex-wrap items-center gap-sm">
          <button
            type="button"
            className="rounded-lg border border-border-strong bg-surface-container-lowest px-md py-2 text-button text-ink hover:bg-surface-container-low"
            onClick={() => void loadSettings()}
          >
            刷新配置
          </button>
          <button
            type="button"
            className="rounded-lg border border-border-strong bg-surface-container-lowest px-md py-2 text-button text-ink hover:bg-surface-container-low"
            onClick={restoreDraft}
          >
            取消更改
          </button>
          <button
            type="button"
            onClick={() => void saveDraft()}
            disabled={saving}
            className="rounded-lg bg-ink px-lg py-2 text-button text-on-ink disabled:cursor-not-allowed disabled:opacity-60"
          >
            {saving ? '保存中...' : '保存覆盖配置'}
          </button>
        </div>
      </div>

      {/* 视图切换与搜索入口合并在同一卡片内，减少视线往返并压缩顶部占位。 */}
      <section className="rounded-2xl border border-border-hairline bg-surface-container-lowest p-md shadow-sm">
        <div className="flex flex-col gap-sm xl:flex-row xl:items-center xl:justify-between">
          <div className="flex flex-wrap gap-xs">
            {VIEW_MODE_META.map((item) => (
              <button
                key={item.mode}
                type="button"
                aria-pressed={viewMode === item.mode}
                onClick={() => setViewMode(item.mode)}
                className={clsx(
                  'rounded-lg px-sm py-2 text-[12px] transition-colors',
                  viewMode === item.mode
                    ? 'bg-ink text-on-ink'
                    : 'border border-border-hairline bg-surface-container-low text-secondary hover:text-ink',
                )}
              >
                {item.label}
              </button>
            ))}
          </div>
          <div className="w-full xl:w-[420px]">
            <div className="flex items-center gap-sm rounded-lg border border-border-hairline bg-surface-container-low px-sm py-2">
              <span className="material-symbols-outlined text-[18px] text-secondary">search</span>
              <input
                value={searchKeyword}
                onChange={(event) => setSearchKeyword(event.target.value)}
                placeholder="按配置键、描述、值搜索"
                className="w-full bg-transparent text-ink outline-none placeholder:text-secondary"
              />
            </div>
          </div>
        </div>
      </section>

      {errorMessage ? (
        <p className="rounded-lg border border-error/40 bg-error-container/40 px-sm py-xs text-body-sm text-error">
          {errorMessage}
        </p>
      ) : null}

      {successMessage ? (
        <p className="rounded-lg border border-status-running-border bg-status-running-bg px-sm py-xs text-body-sm text-status-running">
          {successMessage}
        </p>
      ) : null}

      {loading ? (
        <section className="rounded-2xl border border-border-hairline bg-surface-container-lowest px-lg py-xl text-center text-secondary">
          加载中...
        </section>
      ) : filteredCategories.length === 0 ? (
        <section className="rounded-2xl border border-border-hairline bg-surface-container-lowest px-lg py-xl text-center text-secondary">
          没有匹配到配置项，请调整筛选条件。
        </section>
      ) : viewMode === 'cards' ? (
        <CardModeView
          categories={filteredCategories}
          expandedCategories={expandedCategories}
          modifiedSettingKeys={modifiedSettingKeys}
          onToggleCategory={toggleCategory}
          onChangeSettingValue={updateDraft}
          inputTypeByValueType={inputTypeByValueType}
        />
      ) : viewMode === 'navigator' ? (
        <NavigatorModeView
          categories={filteredCategories}
          activeCategoryCode={activeCategoryEntry?.categoryCode ?? ''}
          overviewByCategory={overviewByCategory}
          modifiedSettingKeys={modifiedSettingKeys}
          onSelectCategory={setActiveCategory}
          onChangeSettingValue={updateDraft}
          inputTypeByValueType={inputTypeByValueType}
        />
      ) : (
        <CompactModeView
          categories={filteredCategories}
          rows={compactRows}
          tableCategoryFilter={tableCategoryFilter}
          modifiedSettingKeys={modifiedSettingKeys}
          onChangeTableCategoryFilter={setTableCategoryFilter}
          onChangeSettingValue={updateDraft}
          inputTypeByValueType={inputTypeByValueType}
        />
      )}
    </div>
  );

  function CardModeView({
    categories,
    expandedCategories,
    modifiedSettingKeys,
    onToggleCategory,
    onChangeSettingValue,
    inputTypeByValueType,
  }: {
    categories: GroupedCategory[];
    expandedCategories: Record<string, boolean>;
    modifiedSettingKeys: Set<string>;
    onToggleCategory: (categoryCode: string) => void;
    onChangeSettingValue: (settingKey: string, nextValue: string) => void;
    inputTypeByValueType: (valueType?: string) => React.HTMLInputTypeAttribute;
  }) {
    return (
      <section className="space-y-md">
        {categories.map((category) => {
          const expanded = expandedCategories[category.categoryCode] ?? true;
          const modified = category.settings.filter((setting) => modifiedSettingKeys.has(setting.settingKey)).length;
          // 分组卡片默认展开，方便快速浏览全部配置；手动收起后仅由本地状态控制，不影响保存数据。
          return (
            <div key={category.categoryCode} className="rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-sm">
              <button
                type="button"
                className="flex w-full items-center justify-between gap-sm px-lg py-md text-left hover:bg-surface-container-low"
                onClick={() => onToggleCategory(category.categoryCode)}
              >
                <div className="min-w-0">
                  <p className="font-title-md text-title-md text-ink">{category.categoryLabel}</p>
                  <p className="mt-1 text-[12px] text-secondary">
                    共 {category.settings.length} 项
                    {modified > 0 ? ` · 已修改 ${modified} 项` : ''}
                  </p>
                </div>
                <span className="material-symbols-outlined text-secondary">
                  {expanded ? 'expand_more' : 'chevron_right'}
                </span>
              </button>
              {expanded ? (
                <div className="grid gap-sm border-t border-border-hairline p-md md:grid-cols-2">
                  {category.settings.map((setting) => {
                    const modified = modifiedSettingKeys.has(setting.settingKey);
                    return (
                      <div
                        key={setting.settingKey}
                        className={clsx(
                          'rounded-xl border px-md py-sm transition-colors',
                          modified
                            ? 'border-status-pending-border bg-status-pending-bg/40'
                            : 'border-border-hairline bg-surface-container-low',
                        )}
                      >
                        <div className="mb-2 flex flex-wrap items-center gap-xs">
                          <p className="font-medium text-ink">{setting.description ?? setting.settingKey}</p>
                          <span className="rounded-full border border-border-hairline bg-surface-container-lowest px-2 py-0.5 text-[11px] text-secondary">
                            {setting.settingKey}
                          </span>
                          {modified ? (
                            <span className="rounded-full border border-status-pending-border bg-status-pending-bg px-2 py-0.5 text-[11px] text-status-pending">
                              已修改
                            </span>
                          ) : null}
                          {setting.restartRequired ? (
                            <span className="rounded-full border border-status-pending-border bg-status-pending-bg px-2 py-0.5 text-[11px] text-status-pending">
                              重启生效
                            </span>
                          ) : null}
                        </div>
                        <input
                          data-testid={`setting-value-${setting.settingKey}`}
                          type={inputTypeByValueType(setting.valueType)}
                          value={setting.settingValue}
                          onChange={(event) => onChangeSettingValue(setting.settingKey, event.target.value)}
                          className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-body-sm text-ink outline-none transition-colors focus:border-border-strong"
                        />
                      </div>
                    );
                  })}
                </div>
              ) : null}
            </div>
          );
        })}
      </section>
    );
  }

  function NavigatorModeView({
    categories,
    activeCategoryCode,
    overviewByCategory,
    modifiedSettingKeys,
    onSelectCategory,
    onChangeSettingValue,
    inputTypeByValueType,
  }: {
    categories: GroupedCategory[];
    activeCategoryCode: string;
    overviewByCategory: Map<string, CategoryOverview>;
    modifiedSettingKeys: Set<string>;
    onSelectCategory: (categoryCode: string) => void;
    onChangeSettingValue: (settingKey: string, nextValue: string) => void;
    inputTypeByValueType: (valueType?: string) => React.HTMLInputTypeAttribute;
  }) {
    const activeCategory = categories.find((item) => item.categoryCode === activeCategoryCode) ?? categories[0];
    return (
      <section className="grid gap-md xl:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="rounded-2xl border border-border-hairline bg-surface-container-lowest p-sm shadow-sm">
          <p className="px-sm py-xs text-[12px] text-secondary">配置分类</p>
          <div className="space-y-1">
            {categories.map((category) => {
              const summary = overviewByCategory.get(category.categoryCode);
              const selected = category.categoryCode === activeCategory.categoryCode;
              return (
                <button
                  key={category.categoryCode}
                  type="button"
                  onClick={() => onSelectCategory(category.categoryCode)}
                  className={clsx(
                    'w-full rounded-lg border px-sm py-sm text-left transition-colors',
                    selected
                      ? 'border-border-strong bg-surface-container text-ink'
                      : 'border-transparent text-secondary hover:bg-surface-container-low',
                  )}
                >
                  <p className="font-medium">{category.categoryLabel}</p>
                  <p className="mt-1 text-[11px]">
                    {summary?.total ?? category.settings.length} 项
                    {summary && summary.modified > 0 ? ` · 改动 ${summary.modified}` : ''}
                  </p>
                </button>
              );
            })}
          </div>
        </aside>

        <div className="rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-sm">
          <div className="border-b border-border-hairline px-lg py-md">
            <h3 className="font-title-md text-title-md text-ink">{activeCategory.categoryLabel}</h3>
            <p className="mt-1 text-[12px] text-secondary">{activeCategory.categoryCode}</p>
          </div>
          <div className="grid gap-sm p-md md:grid-cols-2">
            {activeCategory.settings.map((setting) => {
              const modified = modifiedSettingKeys.has(setting.settingKey);
              return (
                <div
                  key={setting.settingKey}
                  className={clsx(
                    'rounded-xl border px-md py-sm transition-colors',
                    modified
                      ? 'border-status-pending-border bg-status-pending-bg/40'
                      : 'border-border-hairline bg-surface-container-low',
                  )}
                >
                  <div className="mb-2 flex flex-wrap items-center gap-xs">
                    <p className="font-medium text-ink">{setting.description ?? setting.settingKey}</p>
                    {modified ? (
                      <span className="rounded-full border border-status-pending-border bg-status-pending-bg px-2 py-0.5 text-[11px] text-status-pending">
                        已修改
                      </span>
                    ) : null}
                    {setting.restartRequired ? (
                      <span className="rounded-full border border-status-pending-border bg-status-pending-bg px-2 py-0.5 text-[11px] text-status-pending">
                        重启生效
                      </span>
                    ) : null}
                  </div>
                  <p className="mb-2 break-all text-[11px] text-secondary">{setting.settingKey}</p>
                  <input
                    data-testid={`setting-value-${setting.settingKey}`}
                    type={inputTypeByValueType(setting.valueType)}
                    value={setting.settingValue}
                    onChange={(event) => onChangeSettingValue(setting.settingKey, event.target.value)}
                    className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-body-sm text-ink outline-none transition-colors focus:border-border-strong"
                  />
                </div>
              );
            })}
          </div>
        </div>
      </section>
    );
  }

  function CompactModeView({
    categories,
    rows,
    tableCategoryFilter,
    modifiedSettingKeys,
    onChangeTableCategoryFilter,
    onChangeSettingValue,
    inputTypeByValueType,
  }: {
    categories: GroupedCategory[];
    rows: Array<{ categoryCode: string; categoryLabel: string; setting: AdminRuntimeSetting }>;
    tableCategoryFilter: string;
    modifiedSettingKeys: Set<string>;
    onChangeTableCategoryFilter: (value: string) => void;
    onChangeSettingValue: (settingKey: string, nextValue: string) => void;
    inputTypeByValueType: (valueType?: string) => React.HTMLInputTypeAttribute;
  }) {
    return (
      <section className="rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-sm border-b border-border-hairline px-lg py-md">
          <h3 className="font-title-md text-title-md text-ink">紧凑编辑表格</h3>
          <label className="inline-flex items-center gap-xs text-[12px] text-secondary">
            分类筛选
            <select
              value={tableCategoryFilter}
              onChange={(event) => onChangeTableCategoryFilter(event.target.value)}
              className="rounded-lg border border-border-hairline bg-surface-container-lowest px-sm py-1.5 text-ink outline-none"
            >
              <option value="all">全部分类</option>
              {categories.map((category) => (
                <option key={category.categoryCode} value={category.categoryCode}>
                  {category.categoryLabel}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1100px] border-collapse text-left">
            <thead>
              <tr className="border-b border-border-hairline bg-surface-container-low">
                <th className="px-md py-sm text-[12px] text-secondary">分类</th>
                <th className="px-md py-sm text-[12px] text-secondary">配置键</th>
                <th className="px-md py-sm text-[12px] text-secondary">说明</th>
                <th className="px-md py-sm text-[12px] text-secondary">当前值</th>
                <th className="px-md py-sm text-[12px] text-secondary">状态</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {rows.map((row) => {
                const modified = modifiedSettingKeys.has(row.setting.settingKey);
                return (
                  <tr key={row.setting.settingKey} className={modified ? 'bg-status-pending-bg/35' : ''}>
                    <td className="px-md py-sm text-[12px] text-secondary">{row.categoryLabel}</td>
                    <td className="px-md py-sm font-data-mono text-[12px] text-ink">{row.setting.settingKey}</td>
                    <td className="px-md py-sm text-[12px] text-secondary">{row.setting.description ?? '-'}</td>
                    <td className="px-md py-sm">
                      <input
                        data-testid={`setting-value-${row.setting.settingKey}`}
                        type={inputTypeByValueType(row.setting.valueType)}
                        value={row.setting.settingValue}
                        onChange={(event) => onChangeSettingValue(row.setting.settingKey, event.target.value)}
                        className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-1.5 text-[12px] text-ink outline-none transition-colors focus:border-border-strong"
                      />
                    </td>
                    <td className="px-md py-sm">
                      <div className="flex flex-wrap items-center gap-xs">
                        {modified ? (
                          <span className="rounded-full border border-status-pending-border bg-status-pending-bg px-2 py-0.5 text-[11px] text-status-pending">
                            已修改
                          </span>
                        ) : (
                          <span className="rounded-full border border-border-hairline bg-surface-container-low px-2 py-0.5 text-[11px] text-secondary">
                            未改动
                          </span>
                        )}
                        {row.setting.restartRequired ? (
                          <span className="rounded-full border border-status-pending-border bg-status-pending-bg px-2 py-0.5 text-[11px] text-status-pending">
                            重启生效
                          </span>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    );
  }
}
