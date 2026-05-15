import React from 'react';
import { AdminChatApi, AdminRuntimeSetting } from '../api/adminChatApi';

export function Settings() {
  const [settings, setSettings] = React.useState<AdminRuntimeSetting[]>([]);

  React.useEffect(() => {
    void AdminChatApi.listSettings().then(setSettings);
  }, []);

  return (
    <div className="p-lg w-full">
      <div className="mb-lg">
        <h2 className="font-headline-md text-headline-md text-ink">系统配置</h2>
        <p className="text-secondary mt-1">管理系统全局参数、环境变量及外部应用凭据。</p>
      </div>

      <div className="space-y-xl">
        <section className="bg-surface-container-lowest border border-border-hairline rounded-xl p-xl shadow-sm">
          <h3 className="font-title-sm text-ink mb-md border-b border-border-hairline pb-sm">基础服务配置</h3>
          <div className="space-y-md">
            {settings.map((setting) => (
              <div key={setting.settingKey}>
                <label className="block text-secondary text-[12px] mb-1">{setting.description ?? setting.settingKey}</label>
                <input type="text" readOnly value={setting.settingValue} className="w-full border border-border-hairline rounded-lg px-3 py-2 bg-surface-container-lowest focus:ring-1 focus:ring-ink focus:border-ink transition-all text-body-sm" />
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
          <button className="px-lg py-2 text-secondary hover:bg-surface-container rounded-lg font-button text-button transition-colors active:scale-95">取消更改</button>
          <button className="px-lg py-2 bg-ink text-on-ink rounded-lg font-button text-button transition-colors active:scale-95 shadow-sm">保存覆盖配置</button>
        </div>
      </div>
    </div>
  );
}
