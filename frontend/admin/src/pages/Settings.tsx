import React from 'react';
import { AdminChatApi, AdminRuntimeSetting } from '../api/adminChatApi';

export function Settings() {
  const [settings, setSettings] = React.useState<AdminRuntimeSetting[]>([]);
  const [draftSettings, setDraftSettings] = React.useState<AdminRuntimeSetting[]>([]);
  const [saving, setSaving] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);
  const [successMessage, setSuccessMessage] = React.useState<string | null>(null);

  const categoryLabels: Record<string, string> = {
    'chat.memory': '聊天历史压缩',
    search: '搜索链路',
    queue: '并发门控',
    code_search: '代码检索运行时',
    'ai.routing': '模型路由',
  };

  React.useEffect(() => {
    void loadSettings();
  }, []);

  const groupedSettings = React.useMemo(() => {
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
    return Array.from(grouped.entries());
  }, [draftSettings]);

  async function loadSettings() {
    try {
      const loaded = await AdminChatApi.listSettings();
      setSettings(loaded);
      setDraftSettings(loaded);
      setErrorMessage(null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '加载系统配置失败');
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

  return (
    <div className="p-lg w-full">
      <div className="mb-lg">
        <h2 className="font-headline-md text-headline-md text-ink">系统配置</h2>
        <p className="text-secondary mt-1">管理系统全局参数、环境变量及外部应用凭据。</p>
      </div>

      <div className="space-y-xl">
        <section className="bg-surface-container-lowest border border-border-hairline rounded-xl p-xl shadow-sm">
          <h3 className="font-title-sm text-ink mb-md border-b border-border-hairline pb-sm">基础服务配置</h3>
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
          <div className="space-y-lg mt-md">
            {groupedSettings.map(([categoryCode, categorySettings]) => (
              <div key={categoryCode} className="rounded-lg border border-border-hairline p-md bg-surface-container-low">
                <h4 className="font-medium text-ink text-[13px] mb-sm">
                  {categoryLabels[categoryCode] ?? categoryCode}
                </h4>
                <div className="space-y-md">
                  {categorySettings.map((setting) => (
                    <div key={setting.settingKey}>
                      <label className="block text-secondary text-[12px] mb-1">{setting.description ?? setting.settingKey}</label>
                      <input
                        type={inputTypeByValueType(setting.valueType)}
                        value={setting.settingValue}
                        onChange={(event) => updateDraft(setting.settingKey, event.target.value)}
                        className="w-full border border-border-hairline rounded-lg px-3 py-2 bg-surface-container-lowest focus:ring-1 focus:ring-ink focus:border-ink transition-all text-body-sm"
                      />
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="bg-surface-container-lowest border border-border-hairline rounded-xl p-xl shadow-sm">
          <div className="flex justify-between items-center border-b border-border-hairline pb-sm mb-md">
            <h3 className="font-title-sm text-ink">API Keys 与凭据配置</h3>
            <button className="text-primary text-[12px] hover:underline font-medium">新增凭据</button>
          </div>
          <div className="space-y-md">
            <div className="p-sm bg-surface-container-low rounded-lg border border-border-hairline flex justify-between items-center group">
              <div>
                 <p className="font-medium text-ink text-[13px]">Google GenAI API Key</p>
                 <p className="text-[12px] text-secondary font-data-mono">sk-***************************8f3</p>
              </div>
              <div className="flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                <button className="text-secondary hover:text-ink"><span className="material-symbols-outlined text-[18px]">edit</span></button>
                <button className="text-error hover:text-error/80"><span className="material-symbols-outlined text-[18px]">delete</span></button>
              </div>
            </div>
          </div>
        </section>
        
        <div className="flex justify-end gap-sm">
          <button
            onClick={restoreDraft}
            className="px-lg py-2 text-secondary hover:bg-surface-container rounded-lg font-button text-button transition-colors active:scale-95"
          >
            取消更改
          </button>
          <button
            onClick={() => void saveDraft()}
            disabled={saving}
            className="px-lg py-2 bg-ink text-on-ink rounded-lg font-button text-button transition-colors active:scale-95 shadow-sm disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {saving ? '保存中...' : '保存覆盖配置'}
          </button>
        </div>
      </div>
    </div>
  );
}
