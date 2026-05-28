import React from 'react';
import clsx from 'clsx';
import { AdminChatApi, AdminRuntimeSetting } from '../api/adminChatApi';
import {
  buildCandidateRows,
  buildProviderRows,
  extractCandidateSlot,
  normalizeCandidatePriorityUpdates,
  summarizeCandidateSlots,
  type CandidatePriorityUpdate,
} from './settings/candidateSettings';

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

type SettingInputTypeResolver = (valueType?: string) => React.HTMLInputTypeAttribute;

interface CardModeViewProps {
  categories: GroupedCategory[];
  expandedCategories: Record<string, boolean>;
  modifiedSettingKeys: Set<string>;
  onToggleCategory: (categoryCode: string) => void;
  onChangeSettingValue: (settingKey: string, nextValue: string) => void;
  inputTypeByValueType: SettingInputTypeResolver;
}

interface NavigatorModeViewProps {
  categories: GroupedCategory[];
  activeCategoryCode: string;
  overviewByCategory: Map<string, CategoryOverview>;
  modifiedSettingKeys: Set<string>;
  onSelectCategory: (categoryCode: string) => void;
  onChangeSettingValue: (settingKey: string, nextValue: string) => void;
  inputTypeByValueType: SettingInputTypeResolver;
}

interface CompactModeViewProps {
  categories: GroupedCategory[];
  rows: Array<{ categoryCode: string; categoryLabel: string; setting: AdminRuntimeSetting }>;
  tableCategoryFilter: string;
  modifiedSettingKeys: Set<string>;
  onChangeTableCategoryFilter: (value: string) => void;
  onChangeSettingValue: (settingKey: string, nextValue: string) => void;
  inputTypeByValueType: SettingInputTypeResolver;
}

const VIEW_MODE_META: Array<{ mode: SettingsViewMode; label: string }> = [
  { mode: 'navigator', label: '目录导航' },
  { mode: 'cards', label: '分组卡片' },
  { mode: 'compact', label: '紧凑表格' },
];

const CATEGORY_LABELS: Record<string, string> = {
  ai: 'AI 基础配置',
  'ai.providers': 'AI 提供商',
  'ai.candidates': '模型候选池',
  'chat.memory': '聊天历史压缩',
  'chat.executor': '聊天执行器',
  // 系统配置页面向管理员展示，运行时策略分类需要中文名称避免暴露内部 key。
  'chat.intent.guidance': '歧义引导',
  search: '搜索链路',
  queue: '并发门控',
  code_search: '代码检索运行时',
  'ai.routing': '模型路由',
};

function secretPlaceholder(setting: AdminRuntimeSetting): string | undefined {
  if (setting.secret !== true) {
    return undefined;
  }
  if (setting.maskedValue) {
    return `留空表示保持不变，当前已配置：${setting.maskedValue}`;
  }
  return '留空表示保持不变';
}

function providerTitle(code: string): string {
  if (code === 'siliconflow') {
    return '硅基流动';
  }
  if (code === 'bailian') {
    return '百炼';
  }
  if (code === 'deepseek') {
    return 'DeepSeek';
  }
  if (code === 'stub') {
    return 'Stub';
  }
  return code;
}

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
  // 默认进入目录导航视图，并与顶部切换按钮首项顺序保持一致。
  const [viewMode, setViewMode] = React.useState<SettingsViewMode>('navigator');
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
    setDraftSettings((previous) => {
      if (settingKey.endsWith('.priority')) {
        const slot = extractCandidateSlot(settingKey);
        const nextPriority = Number(nextValue);
        if (slot && Number.isFinite(nextPriority) && nextPriority > 0) {
          return normalizeCandidatePriorityUpdates(previous, { slot, priority: nextPriority });
        }
      }
      return previous.map((item) => (item.settingKey === settingKey ? { ...item, settingValue: nextValue } : item));
    });
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
    if (normalized === 'INTEGER' || normalized === 'LONG' || normalized === 'DECIMAL') {
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

}

/**
 * 配置编辑视图必须保持稳定的组件类型，避免每次输入导致输入框卸载并丢失焦点。
 */
function CardModeView({
  categories,
  expandedCategories,
  modifiedSettingKeys,
  onToggleCategory,
  onChangeSettingValue,
  inputTypeByValueType,
}: CardModeViewProps) {
  return (
    <section className="space-y-md">
      {categories.map((category) => {
        const expanded = expandedCategories[category.categoryCode] ?? true;
        const modified = category.settings.filter((setting) => modifiedSettingKeys.has(setting.settingKey)).length;
        const candidateSlots = category.categoryCode === 'ai.candidates' ? summarizeCandidateSlots(category.settings) : [];
        const providerRows = category.categoryCode === 'ai.providers' ? buildProviderRows(category.settings) : [];
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
                {candidateSlots.length > 0 ? (
                  <CandidateTableEditor
                    settings={category.settings}
                    modifiedSettingKeys={modifiedSettingKeys}
                    onChangeSettingValue={onChangeSettingValue}
                  />
                ) : null}
                {providerRows.length > 0 ? (
                  <ProviderTableEditor
                    settings={category.settings}
                    modifiedSettingKeys={modifiedSettingKeys}
                    onChangeSettingValue={onChangeSettingValue}
                  />
                ) : null}
                {category.settings.filter((setting) => category.categoryCode !== 'ai.candidates' && category.categoryCode !== 'ai.providers').map((setting) => {
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
                        placeholder={secretPlaceholder(setting)}
                        className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-body-sm text-ink outline-none transition-colors focus:border-border-strong"
                      />
                      {setting.secret ? (
                        <p className="mt-2 text-[11px] text-secondary">留空表示保持不变，输入新值后会重新加密保存。</p>
                      ) : null}
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
}: NavigatorModeViewProps) {
  const activeCategory = categories.find((item) => item.categoryCode === activeCategoryCode) ?? categories[0];
  const candidateSlots = activeCategory.categoryCode === 'ai.candidates' ? summarizeCandidateSlots(activeCategory.settings) : [];
  const providerRows = activeCategory.categoryCode === 'ai.providers' ? buildProviderRows(activeCategory.settings) : [];
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
          {candidateSlots.length > 0 ? (
            <CandidateTableEditor
              settings={activeCategory.settings}
              modifiedSettingKeys={modifiedSettingKeys}
              onChangeSettingValue={onChangeSettingValue}
            />
          ) : null}
          {providerRows.length > 0 ? (
            <ProviderTableEditor
              settings={activeCategory.settings}
              modifiedSettingKeys={modifiedSettingKeys}
              onChangeSettingValue={onChangeSettingValue}
            />
          ) : null}
          {activeCategory.settings.filter((setting) => activeCategory.categoryCode !== 'ai.candidates' && activeCategory.categoryCode !== 'ai.providers').map((setting) => {
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
                  placeholder={secretPlaceholder(setting)}
                  className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-2 text-body-sm text-ink outline-none transition-colors focus:border-border-strong"
                />
                {setting.secret ? (
                  <p className="mt-2 text-[11px] text-secondary">留空表示保持不变，输入新值后会重新加密保存。</p>
                ) : null}
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

function CandidateTableEditor({
  settings,
  modifiedSettingKeys,
  onChangeSettingValue,
}: {
  settings: AdminRuntimeSetting[];
  modifiedSettingKeys: Set<string>;
  onChangeSettingValue: (settingKey: string, nextValue: string) => void;
}) {
  const rows = buildCandidateRows(settings);
  return (
    <div className="md:col-span-2 rounded-xl border border-dashed border-border-hairline bg-surface-container-low p-md">
      <div className="mb-3 flex items-center justify-between gap-sm">
        <div>
          <p className="text-[12px] font-medium text-ink">候选模型表格编辑</p>
          <p className="mt-1 text-[11px] text-secondary">修改优先级时会自动重排其它候选，保持优先级唯一且连续。</p>
        </div>
        <span className="rounded-full border border-border-hairline bg-surface-container-lowest px-2 py-0.5 text-[11px] text-secondary">
          共 {rows.length} 个候选
        </span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[1080px] border-collapse text-left">
          <thead>
            <tr className="border-b border-border-hairline bg-surface-container-lowest">
              <th className="px-sm py-sm text-[12px] text-secondary">槽位</th>
              <th className="px-sm py-sm text-[12px] text-secondary">模型ID</th>
              <th className="px-sm py-sm text-[12px] text-secondary">Provider</th>
              <th className="px-sm py-sm text-[12px] text-secondary">模型名称</th>
              <th className="px-sm py-sm text-[12px] text-secondary">优先级</th>
              <th className="px-sm py-sm text-[12px] text-secondary">启用</th>
              <th className="px-sm py-sm text-[12px] text-secondary">思考</th>
              <th className="px-sm py-sm text-[12px] text-secondary">视觉</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-hairline">
            {rows.map((row) => {
              const rowModified = [
                row.idSetting,
                row.providerSetting,
                row.modelSetting,
                row.prioritySetting,
                row.enabledSetting,
                row.supportsThinkingSetting,
                row.supportsVisionSetting,
              ].some((setting) => setting && modifiedSettingKeys.has(setting.settingKey));
              return (
                <tr key={row.slot} className={rowModified ? 'bg-status-pending-bg/30' : ''}>
                  <td className="px-sm py-sm align-top text-[12px] text-secondary">{row.slot}</td>
                  <td className="px-sm py-sm">
                    <StructuredSettingInput setting={row.idSetting} onChangeSettingValue={onChangeSettingValue} />
                  </td>
                  <td className="px-sm py-sm">
                    <StructuredSettingInput setting={row.providerSetting} onChangeSettingValue={onChangeSettingValue} />
                  </td>
                  <td className="px-sm py-sm">
                    <StructuredSettingInput setting={row.modelSetting} onChangeSettingValue={onChangeSettingValue} />
                  </td>
                  <td className="px-sm py-sm">
                    <StructuredSettingInput setting={row.prioritySetting} onChangeSettingValue={onChangeSettingValue} type="number" />
                  </td>
                  <td className="px-sm py-sm">
                    <StructuredBooleanInput setting={row.enabledSetting} onChangeSettingValue={onChangeSettingValue} />
                  </td>
                  <td className="px-sm py-sm">
                    <StructuredBooleanInput setting={row.supportsThinkingSetting} onChangeSettingValue={onChangeSettingValue} />
                  </td>
                  <td className="px-sm py-sm">
                    <StructuredBooleanInput setting={row.supportsVisionSetting} onChangeSettingValue={onChangeSettingValue} />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ProviderTableEditor({
  settings,
  modifiedSettingKeys,
  onChangeSettingValue,
}: {
  settings: AdminRuntimeSetting[];
  modifiedSettingKeys: Set<string>;
  onChangeSettingValue: (settingKey: string, nextValue: string) => void;
}) {
  const rows = buildProviderRows(settings);
  return (
    <div className="md:col-span-2 rounded-xl border border-dashed border-border-hairline bg-surface-container-low p-md">
      <div className="mb-3 flex items-center justify-between gap-sm">
        <div>
          <p className="text-[12px] font-medium text-ink">AI 提供商表格编辑</p>
          <p className="mt-1 text-[11px] text-secondary">中文名、基础地址、聊天端点和密钥都可直接编辑。</p>
        </div>
        <span className="rounded-full border border-border-hairline bg-surface-container-lowest px-2 py-0.5 text-[11px] text-secondary">
          共 {rows.length} 个提供商
        </span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[960px] border-collapse text-left">
          <thead>
            <tr className="border-b border-border-hairline bg-surface-container-lowest">
              <th className="px-sm py-sm text-[12px] text-secondary">中文名</th>
              <th className="px-sm py-sm text-[12px] text-secondary">Provider</th>
              <th className="px-sm py-sm text-[12px] text-secondary">基础地址</th>
              <th className="px-sm py-sm text-[12px] text-secondary">聊天端点</th>
              <th className="px-sm py-sm text-[12px] text-secondary">API Key</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-hairline">
            {rows.map((row) => {
              const rowModified = [row.baseUrlSetting, row.apiKeySetting, row.chatEndpointSetting].some(
                (setting) => setting && modifiedSettingKeys.has(setting.settingKey),
              );
              return (
                <tr key={row.code} className={rowModified ? 'bg-status-pending-bg/30' : ''}>
                  <td className="px-sm py-sm align-top text-[12px] font-medium text-ink">{providerTitle(row.code)}</td>
                  <td className="px-sm py-sm align-top text-[12px] text-secondary">{row.code}</td>
                  <td className="px-sm py-sm">
                    <StructuredSettingInput setting={row.baseUrlSetting} onChangeSettingValue={onChangeSettingValue} />
                  </td>
                  <td className="px-sm py-sm">
                    <StructuredSettingInput setting={row.chatEndpointSetting} onChangeSettingValue={onChangeSettingValue} />
                  </td>
                  <td className="px-sm py-sm">
                    <StructuredSettingInput setting={row.apiKeySetting} onChangeSettingValue={onChangeSettingValue} />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function StructuredSettingInput({
  setting,
  onChangeSettingValue,
  type = 'text',
}: {
  setting?: AdminRuntimeSetting;
  onChangeSettingValue: (settingKey: string, nextValue: string) => void;
  type?: React.HTMLInputTypeAttribute;
}) {
  if (!setting) {
    return <span className="text-[12px] text-secondary">-</span>;
  }
  return (
    <input
      data-testid={`setting-value-${setting.settingKey}`}
      type={type}
      value={setting.settingValue}
      onChange={(event) => onChangeSettingValue(setting.settingKey, event.target.value)}
      placeholder={secretPlaceholder(setting)}
      className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-1.5 text-[12px] text-ink outline-none transition-colors focus:border-border-strong"
    />
  );
}

function StructuredBooleanInput({
  setting,
  onChangeSettingValue,
}: {
  setting?: AdminRuntimeSetting;
  onChangeSettingValue: (settingKey: string, nextValue: string) => void;
}) {
  if (!setting) {
    return <span className="text-[12px] text-secondary">-</span>;
  }
  return (
    <select
      data-testid={`setting-value-${setting.settingKey}`}
      value={setting.settingValue}
      onChange={(event) => onChangeSettingValue(setting.settingKey, event.target.value)}
      className="w-full rounded-lg border border-border-hairline bg-surface-container-lowest px-3 py-1.5 text-[12px] text-ink outline-none transition-colors focus:border-border-strong"
    >
      <option value="true">true</option>
      <option value="false">false</option>
    </select>
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
}: CompactModeViewProps) {
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
                      placeholder={secretPlaceholder(row.setting)}
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
